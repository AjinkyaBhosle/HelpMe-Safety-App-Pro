package com.helpme.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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

class WakeWordService : Service(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val binder = LocalBinder()
    private val TAG = "WakeWordService"
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Grammar: "help me" is the ONLY trigger phrase.
    // "[unk]" absorbs all other audio so Vosk doesn't force-match noise to "help me".
    private val WAKE_GRAMMAR = "[\"help me\", \"help\", \"[unk]\"]"
    
    private var panicTriggeredRecently = false

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
        showNotification()
        initModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WakeWordService Started")
        return START_STICKY // Restart if killed by the system
    }

    /**
     * CRITICAL: When the system kills and restarts the service via START_STICKY,
     * onTaskRemoved is called first. We must NOT stop the service here.
     * Instead, we ensure the service survives the app swipe.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "App swiped away — WakeWordService attempting aggressive restart")
        
        try {
            // Aggressively attempt to restart the service via AlarmManager if swiped away
            val restartIntent = Intent(applicationContext, WakeWordService::class.java)
            restartIntent.setPackage(packageName)
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                1,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.setExact(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 1000, // Restart in 1 second
                pendingIntent
            )
            Log.d(TAG, "AlarmManager set to revive WakeWordService")
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
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock acquired — CPU will stay active for audio listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun showNotification() {
        val channelId = "helpme_voice_service"
        val channelName = "SOS Voice Detection"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            channel.description = "Listening for the 'Help Me' wake word"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Open app when notification is clicked
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Help Me! is Active")
            .setContentText("Actively listening for voice SOS commands.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Default icon, can be changed later
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
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

    private fun initModel() {
        Log.d(TAG, "Unpacking Vosk Model...")
        StorageService.unpack(this, "vosk-model", "model",
            { model: Model? ->
                this.model = model
                Log.d(TAG, "Vosk model loaded successfully")
                startListening()
            },
            { exception: IOException ->
                Log.e(TAG, "Failed to unpack model", exception)
            }
        )
    }

    private fun startListening() {
        try {
            if (model == null) return
            
            // Constrain engine to our panic grammar for real-time speed
            val rec = Recognizer(model, 16000.0f, WAKE_GRAMMAR)
            
            speechService?.stop()
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            
            Log.d(TAG, "Listening for 'Help Me'...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
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

    override fun onPartialResult(hypothesis: String) {
        val text = extractText(hypothesis)
        if (text.isEmpty() || text == "[unk]") return
        
        Log.d(TAG, "Partial: $text")
        checkTrigger(text, isPartial = true)
    }

    override fun onResult(hypothesis: String) {
        val text = extractText(hypothesis)
        if (text.isEmpty() || text == "[unk]") return
        
        Log.d(TAG, "Result: $text")
        checkTrigger(text, isPartial = false)
    }

    override fun onFinalResult(hypothesis: String) {
        val text = extractText(hypothesis)
        if (text.isEmpty() || text == "[unk]") return
        
        Log.d(TAG, "Final: $text")
        checkTrigger(text, isPartial = false)
    }

    private fun checkTrigger(text: String, isPartial: Boolean) {
        if (panicTriggeredRecently) return

        // Trigger ONLY on "help me" (exact two-word phrase).
        if (text.contains("help me")) {
            // If it's a long conversation, Vosk outputs many [unk]s. 
            // We ignore partial results if there are too many [unk]s to avoid false triggers from noise.
            val unkCount = text.split("[unk]").size - 1
            if (isPartial && unkCount >= 2) {
                return
            }

            Log.d(TAG, "🚨🚨 VOICE SOS TRIGGERED: '$text' 🚨🚨")
            panicTriggeredRecently = true
            
            triggerPanic()

            // Reset after 10 seconds to prevent spamming but allow quicker re-triggers
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({ panicTriggeredRecently = false }, 10000)
        }
    }

    private fun triggerPanic() {
        try {
            // 1. Send Broadcast to React Native (If app is open)
            val intent = Intent("com.helpme.app.VOICE_PANIC")
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
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({ startListening() }, 5000)
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk Timeout")
        startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService?.shutdown()
        try {
            wakeLock?.release()
        } catch (e: Exception) {}
        Log.d(TAG, "WakeWordService Destroyed")
    }
}
