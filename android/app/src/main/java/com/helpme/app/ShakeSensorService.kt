package com.helpme.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class ShakeSensorService : Service(), SensorEventListener {

    private val TAG = "ShakeSensorService"
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    // Shake detection parameters
    private val SHAKE_THRESHOLD = 20.0f // m/s^2 (excluding gravity) - Increased for heavier shakes
    private val SHAKE_SLOP_TIME_MS = 100 // Short enough to catch reverse stroke, long enough to debounce
    private val SHAKE_COUNT_RESET_TIME_MS = 2500
    private val SHAKE_MIN_COUNT = 5 // 5 alternating peaks = 2.5 full back-and-forth cycles
    private val COOLDOWN_MS = 60000L // 60 seconds cooldown to prevent SMS spam warning

    private var mShakeTimestamp: Long = 0
    private var mShakeCount = 0
    private var lastShakeAxis = -1
    private var lastShakeSign = 0

    // Low-pass filter for gravity
    private val gravity = FloatArray(3)
    private var filterInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ShakeSensorService Created")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        showNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ShakeSensorService Started")
        if (accelerometer == null) {
            Log.e(TAG, "No suitable accelerometer found. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }
        
        sensorManager?.unregisterListener(this)
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "App swiped away — ShakeSensorService attempting aggressive restart")
        try {
            val restartIntent = Intent(applicationContext, ShakeRestartReceiver::class.java)
            restartIntent.action = "com.ajinkya.helpme.RESTART_SHAKE_SOS"
            
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                3,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 3000,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 3000,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set restart alarm", e)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ShakeSensorService Destroyed")
        sensorManager?.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun showNotification() {
        val channelId = "shake_sensor_channel"
        val channelName = "Shake-to-SOS Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            channel.description = "Listening for emergency shakes"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val restartIntent = Intent(this, ShakeRestartReceiver::class.java)
        restartIntent.action = "com.ajinkya.helpme.RESTART_SHAKE_SOS"
        val deleteIntent = PendingIntent.getBroadcast(
            this, 4, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Shake-to-SOS is Active")
            .setContentText("Shake phone violently to trigger SOS")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(102, notification, 1073741824) // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                startForeground(102, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }
    
    private fun playFeedback(count: Int) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            val isFinal = count >= SHAKE_MIN_COUNT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(if (isFinal) 500 else 50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (isFinal) 500 else 50)
            }
            
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(if (isFinal) android.media.ToneGenerator.TONE_CDMA_ABBR_ALERT else android.media.ToneGenerator.TONE_CDMA_PIP, if (isFinal) 500 else 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play feedback", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (!filterInitialized) {
            gravity[0] = event.values[0]
            gravity[1] = event.values[1]
            gravity[2] = event.values[2]
            filterInitialized = true
            return
        }

        val alpha = 0.8f

        // Apply low-pass filter to isolate gravity
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // Remove gravity to get purely linear acceleration
        val linearX = event.values[0] - gravity[0]
        val linearY = event.values[1] - gravity[1]
        val linearZ = event.values[2] - gravity[2]

        val magnitude = Math.sqrt((linearX * linearX + linearY * linearY + linearZ * linearZ).toDouble()).toFloat()

        if (magnitude > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            val prefs = getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
            val lastTriggerTime = prefs.getLong("lastShakeTriggerTime", 0)

            if (now - lastTriggerTime < COOLDOWN_MS) {
                return 
            }

            if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                mShakeCount = 0
                lastShakeAxis = -1
                lastShakeSign = 0
            }

            // Determine dominant axis
            val absX = abs(linearX)
            val absY = abs(linearY)
            val absZ = abs(linearZ)

            val axis = if (absX > absY && absX > absZ) 0 else if (absY > absX && absY > absZ) 1 else 2
            val sign = if (when(axis) { 0 -> linearX; 1 -> linearY; else -> linearZ } > 0) 1 else -1

            // Axis reversal check: Must alternate sign on the same axis, or switch to a new axis entirely
            if (mShakeCount == 0 || (axis == lastShakeAxis && sign != lastShakeSign) || (axis != lastShakeAxis)) {
                mShakeTimestamp = now
                mShakeCount++
                lastShakeAxis = axis
                lastShakeSign = sign

                Log.d(TAG, "Shake detected! Count: $mShakeCount / $SHAKE_MIN_COUNT, Force: $magnitude")
                
                // Play small beep/vibrate for intermediate counts, big one for final
                playFeedback(mShakeCount)

                if (mShakeCount >= SHAKE_MIN_COUNT) {
                    Log.w(TAG, "SHAKE-TO-SOS TRIGGERED!")
                    prefs.edit().putLong("lastShakeTriggerTime", now).apply()
                    mShakeCount = 0
                    triggerPanic()
                }
            } else {
                Log.d(TAG, "Shake rejected - did not alternate axis sign (Axis: $axis, Sign: $sign)")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun triggerPanic() {
        Log.w(TAG, "Executing Shake-to-SOS Panic Flow!")
        val store = LocalStore(this)
        val contacts = store.getContacts()
        
        if (contacts.isEmpty()) {
            Log.e(TAG, "No contacts found to notify!")
            return
        }

        val intent = Intent("com.ajinkya.helpme.VOICE_PANIC")
        sendBroadcast(intent)

        val request = androidx.work.OneTimeWorkRequest.Builder(AlertWorker::class.java)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("PANIC_BUTTON_SHAKE")
            .build()
        
        androidx.work.WorkManager.getInstance(applicationContext).enqueue(request)
        Log.d(TAG, "Native AlertWorker Enqueued successfully from Shake")
    }
}
