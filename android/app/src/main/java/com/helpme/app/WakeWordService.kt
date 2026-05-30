package com.helpme.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

enum class ListenerState {
    STARTING,
    ACTIVE,
    STALLED,
    RESTARTING,
    FAILED,
    PAUSED
}

class WakeWordService : Service(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val binder = LocalBinder()
    private val TAG = "WakeWordService"
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: android.media.AudioManager? = null
    private var audioRecordingCallback: Any? = null
    
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null
    
    private var currentState = ListenerState.STARTING
    private var lastAudioTime = 0L
    private val watchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkWatchdog()
            watchdogHandler.postDelayed(this, 15_000)
        }
    }
    
    // We no longer constrain the grammar. Using the full vocabulary prevents Vosk from
    // force-matching similar words (like "hello") into "help me".
    // ── Defense Layer 1: Cooldown ──
    // Prevents rapid-fire SOS spam after a successful trigger.
    private var panicTriggeredRecently = false
    private val COOLDOWN_MS = 20_000L  // 20 seconds

    // ── Defense Layer 2: Startup Grace Period ──
    // Ignores all audio for the first few seconds after engine starts,
    // preventing Vosk initialization artifacts from triggering false SOS.
    private var listeningStartTime = 0L
    private val STARTUP_GRACE_MS = 3_000L  // 3 seconds

    // Defense Layer 3 (Audio Energy Gate) has been removed to fix mic starvation 
    // and allow whispers/normal speaking to trigger SOS reliably.
    private val SAMPLE_RATE = 16000
    
    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WakeWordService Created")
        acquireWakeLock()
        showNotification(ListenerState.STARTING, "Starting microphone...")
        cleanStaleCachedModels()
        initModel()
        watchdogHandler.postDelayed(watchdogRunnable, 15_000)
        registerPhoneListener()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioRecordingCallback = object : android.media.AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<android.media.AudioRecordingConfiguration>?) {
                    super.onRecordingConfigChanged(configs)
                    Log.d(TAG, "Audio recording config changed. Current State: $currentState")
                    if (currentState != ListenerState.ACTIVE && model != null) {
                        Log.d(TAG, "Microphone might be free. Attempting to restart listener...")
                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        handler.postDelayed({ startListening() }, 1500)
                    }
                }
            }
            audioManager?.registerAudioRecordingCallback(
                audioRecordingCallback as android.media.AudioManager.AudioRecordingCallback,
                null
            )
        }
    }

    private fun registerPhoneListener() {
        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                telephonyManager?.registerTelephonyCallback(
                    mainExecutor,
                    telephonyCallback as TelephonyCallback
                )
            } else {
                phoneStateListener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing READ_PHONE_STATE permission for telephony listener")
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Phone call active - pausing Voice SOS")
                showNotification(ListenerState.PAUSED, "Paused (mic busy with phone call)")
                try {
                    speechService?.stop()
                    speechService?.shutdown()
                } catch (e: Exception) {}
                speechService = null
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Phone call ended - resuming Voice SOS")
                if (model != null && currentState == ListenerState.PAUSED) {
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    handler.postDelayed({ startListening() }, 2000)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WakeWordService Started")
        return START_STICKY // Restart if killed by the system
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "App swiped away — WakeWordService attempting aggressive restart")
        
        try {
            // Aggressively attempt to restart the service via AlarmManager if swiped away
            val restartIntent = Intent(applicationContext, VoiceRestartReceiver::class.java)
            restartIntent.action = "com.ajinkya.helpme.RESTART_VOICE_SOS"
            
            // 1. Immediate Broadcast (Shotgun approach layer 1)
            sendBroadcast(restartIntent)
            
            // 2. Scheduled Alarm Backup (Shotgun approach layer 2)
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                1,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            // Use setExactAndAllowWhileIdle() for robust recovery when swiped away
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 1000,
                        pendingIntent
                    )
                } catch (se: SecurityException) {
                    Log.w(TAG, "Exact alarm permission denied. Falling back to inexact alarm.")
                    alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 1000,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.set(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 1000,
                    pendingIntent
                )
            }
            Log.d(TAG, "AlarmManager set to revive WakeWordService robustly via Broadcast")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set restart alarm", e)
        }

        super.onTaskRemoved(rootIntent)
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HelpMe::WakeWordLock"
            )
            // Acquire without timeout for indefinite permanent WakeLock
            wakeLock?.acquire()
            Log.d(TAG, "Permanent WakeLock acquired — CPU will stay active for audio listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun showNotification(state: ListenerState, text: String) {
        val channelId = "helpme_voice_service"
        val channelName = "SOS Voice Detection"
        
        currentState = state

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Listening for the 'Help Me' wake word"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Open app when notification is clicked
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Catch if the user swipes away the notification on Android 13+
        val restartIntent = Intent(this, VoiceRestartReceiver::class.java)
        restartIntent.action = "com.ajinkya.helpme.RESTART_VOICE_SOS"
        val deleteIntent = PendingIntent.getBroadcast(
            this, 2, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Help Me! is Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(101, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun checkWatchdog() {
        if (lastAudioTime == 0L) return // Not yet received any audio
        val elapsed = System.currentTimeMillis() - lastAudioTime
        if (elapsed > 30_000L) {
            Log.w(TAG, "Watchdog detected silent audio freeze (no frames in ${elapsed}ms). Restarting recognizer!")
            showNotification(ListenerState.STALLED, "Recognizer stalled — recovering...")
            startListening()
        }
    }

    private fun cleanStaleCachedModels() {
        // Remove the old "model" directory from before the US/IN rename
        // to prevent Vosk StorageService from getting confused
        val oldModelDir = java.io.File(filesDir, "model")
        if (oldModelDir.exists()) {
            Log.d(TAG, "Cleaning stale cached model directory: ${oldModelDir.absolutePath}")
            oldModelDir.deleteRecursively()
        }
    }

    private fun initModel() {
        val prefs = getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
        val accent = prefs.getString("voice_sos_accent", "us") ?: "us"
        val accentLabel = if (accent == "in") "Indian" else "US"
        val assetPath = if (accent == "in") "vosk-model-in" else "vosk-model-us"
        val targetPath = if (accent == "in") "model-in" else "model-us"
        
        Log.d(TAG, "Unpacking Vosk Model ($accent)...")
        showNotification(ListenerState.STARTING, "Loading $accentLabel English model...")
        
        Thread {
            try {
                val targetDir = java.io.File(filesDir, targetPath)
                if (!targetDir.exists()) {
                    copyAssets(assetPath, targetDir)
                }
                
                val newModel = Model(targetDir.absolutePath)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    this.model = newModel
                    Log.d(TAG, "Vosk model ($accent) loaded successfully")
                    showNotification(ListenerState.STARTING, "Model ready — opening microphone...")
                    startListening()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unpack or load model", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    showNotification(ListenerState.FAILED, "Model load failed — restart Voice SOS")
                    try {
                        wakeLock?.release()
                    } catch (ignore: Exception) {}
                }
            }
        }.start()
    }

    private fun copyAssets(assetPath: String, targetDir: java.io.File) {
        val assets = assets.list(assetPath) ?: return
        if (!targetDir.exists()) targetDir.mkdirs()
        
        for (asset in assets) {
            val srcPath = "$assetPath/$asset"
            val destFile = java.io.File(targetDir, asset)
            
            var isDir = false
            try {
                this.assets.open(srcPath).use { it.close() }
            } catch (e: java.io.FileNotFoundException) {
                isDir = true
            }
            
            if (isDir) {
                copyAssets(srcPath, destFile)
            } else {
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.post {
                    showNotification(ListenerState.STARTING, "Loading model... ($asset)")
                }
                
                var expectedSize = -1L
                this.assets.open(srcPath).use { inputStream ->
                    expectedSize = inputStream.available().toLong()
                    java.io.FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                if (expectedSize != -1L && destFile.length() != expectedSize) {
                    throw IOException("Failed to copy $srcPath: expected $expectedSize bytes, got ${destFile.length()}")
                }
            }
        }
    }

    // Energy monitor logic removed to prevent microphone starvation

    private fun startListening() {
        try {
            if (model == null) {
                Log.e(TAG, "Model is null — cannot start listening. Voice SOS is NOT active!")
                return
            }
            
            // Fully clean up previous session
            try {
                speechService?.stop()
                speechService?.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup of previous speech service: ${e.message}")
            }
            speechService = null
            
            // Set the startup grace period timestamp
            listeningStartTime = System.currentTimeMillis()
            
            // (Energy monitor logic removed)
            // Use full vocabulary so it doesn't force-match noise into our trigger phrase
            val rec = Recognizer(model, 16000.0f)
            
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            
            // Initialize watchdog timer as soon as mic opens
            lastAudioTime = System.currentTimeMillis()
            
            Log.d(TAG, "Listening for 'Help Me'... (grace period: ${STARTUP_GRACE_MS}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            // Retry after 5 seconds with a clean slate
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({ startListening() }, 5000)
        }
    }

    private fun extractText(json: String): String {
        return try {
            val obj = JSONObject(json)
            val text = obj.optString("partial", "").ifEmpty {
                obj.optString("text", "")
            }
            text.lowercase().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun getActiveAccentLabel(): String {
        val prefs = getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
        val accent = prefs.getString("voice_sos_accent", "us") ?: "us"
        return if (accent == "in") "Indian" else "US"
    }

    override fun onPartialResult(hypothesis: String) {
        lastAudioTime = System.currentTimeMillis()
        if (currentState != ListenerState.ACTIVE) {
            showNotification(ListenerState.ACTIVE, "Listening (${getActiveAccentLabel()} English)")
        }
        val text = extractText(hypothesis)
        if (text.isEmpty() || text == "[unk]") return
        
        Log.d(TAG, "Partial: $text")
        checkTrigger(text)
    }

    override fun onResult(hypothesis: String) {
        lastAudioTime = System.currentTimeMillis()
        if (currentState != ListenerState.ACTIVE) {
            showNotification(ListenerState.ACTIVE, "Listening (${getActiveAccentLabel()} English)")
        }
        val text = extractText(hypothesis)
        if (text.isEmpty() || text == "[unk]") return
        
        Log.d(TAG, "Result: $text")
        checkTrigger(text)
    }

    override fun onFinalResult(hypothesis: String) {
        lastAudioTime = System.currentTimeMillis()
        if (currentState != ListenerState.ACTIVE) {
            showNotification(ListenerState.ACTIVE, "Listening (${getActiveAccentLabel()} English)")
        }
        val text = extractText(hypothesis)
        if (text.isEmpty() || text == "[unk]") return
        
        Log.d(TAG, "Final: $text")
        checkTrigger(text)
    }

    private fun checkTrigger(text: String) {
        // ── Defense Layer 1: Cooldown ──
        if (panicTriggeredRecently) {
            Log.d(TAG, "Ignored (cooldown active)")
            return
        }

        // ── Defense Layer 2: Startup Grace Period ──
        val elapsed = System.currentTimeMillis() - listeningStartTime
        if (elapsed < STARTUP_GRACE_MS) {
            Log.d(TAG, "Ignored (startup grace: ${elapsed}ms < ${STARTUP_GRACE_MS}ms)")
            return
        }

        // ── Defense Layer 3: Audio Energy Gate (REMOVED) ──

        // ── Defense Layer 4: Phrase Match (Fuzzy Matching) ──
        // Covers US-model outputs: "help me", "halp me", "elp me"
        // Covers IN-model outputs: "help mi", "halp mi", "hep me", "hilp me", "help mee"
        val cleaned = text.lowercase()
        val regex = Regex("h[eaoi]?l?p\\s?m[eiy]|elp\\s?m[eiy]")
        val exactMatches = listOf("help me", "help mi", "halp me", "halp mi", "hep me", "hep mi", "hilp me", "hilp mi")
        if (!regex.containsMatchIn(cleaned) && exactMatches.none { cleaned.contains(it) }) {
            Log.d(TAG, "Ignored (no fuzzy trigger match: '$cleaned')")
            return
        }

        // ═══════════════════════════════════════
        // ALL 4 DEFENSE LAYERS PASSED — TRIGGER!
        // ═══════════════════════════════════════
        Log.d(TAG, "🚨🚨 VOICE SOS TRIGGERED: '$text' 🚨🚨")
        panicTriggeredRecently = true
        
        triggerPanic()

        // Reset cooldown after 30 seconds
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({ panicTriggeredRecently = false }, COOLDOWN_MS)
    }

    private fun triggerPanic() {
        try {
            // 1. Send Broadcast to React Native (If app is open)
            val intent = Intent("com.ajinkya.helpme.VOICE_PANIC")
            sendBroadcast(intent)

            // 2. Enqueue WorkManager for Native Offline SMS (Works even if app is closed)
            val request = OneTimeWorkRequest.Builder(AlertWorker::class.java)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("PANIC_BUTTON_VOICE")
                .build()
            
            WorkManager.getInstance(applicationContext).enqueue(request)
            Log.d(TAG, "Native AlertWorker Enqueued successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger panic from voice", e)
        }
    }

    override fun onError(exception: Exception) {
        Log.e(TAG, "Vosk Error", exception)
        // Clean up and retry after a delay
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({ startListening() }, 5000)
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk Timeout — restarting listener")
        startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogHandler.removeCallbacks(watchdogRunnable)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                telephonyManager?.unregisterTelephonyCallback(it as TelephonyCallback)
            }
        } else {
            phoneStateListener?.let {
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioRecordingCallback?.let {
                audioManager?.unregisterAudioRecordingCallback(it as android.media.AudioManager.AudioRecordingCallback)
            }
        }
        
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping speech service: ${e.message}")
        }
        try {
            wakeLock?.release()
        } catch (e: Exception) {}
        Log.d(TAG, "WakeWordService Destroyed")
        
        // Final fallback logic removed to prevent overlapping restart loops 
        // with onTaskRemoved() and MainActivity.onCreate()
    }
}
