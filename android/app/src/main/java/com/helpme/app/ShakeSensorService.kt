package com.helpme.app

import android.app.Notification
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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class ShakeSensorService : Service(), SensorEventListener {

    private val TAG = "ShakeSensorService"
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Shake detection parameters
    private val SHAKE_THRESHOLD_GRAVITY = 1.5f
    private val SHAKE_SLOP_TIME_MS = 500
    private val SHAKE_COUNT_RESET_TIME_MS = 3000
    private val SHAKE_MIN_COUNT = 2

    private var mShakeTimestamp: Long = 0
    private var mShakeCount = 0
    private var lastTriggerTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ShakeSensorService Created")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        acquireWakeLock()
        showNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ShakeSensorService Started")
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ShakeSensorService Destroyed")
        sensorManager?.unregisterListener(this)
        releaseWakeLock()
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

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Shake-to-SOS is Active")
            .setContentText("Shake phone violently to trigger SOS")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size) // Placeholder icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // SENSOR type is appropriate for accelerometer reading in background
                startForeground(102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(102, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HelpMe::ShakeSensorLock"
            )
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock acquired for Shake Sensor")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released for Shake Sensor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // gForce will be close to 1 when there is no movement.
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()

            // Cool-down after a trigger (prevent double firing)
            if (now - lastTriggerTime < 10000) {
                return 
            }

            // Ignore shake events that are too close to each other (500ms slop)
            if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            // Reset shake count if too much time has passed since last shake (3000ms)
            if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                mShakeCount = 0
            }

            mShakeTimestamp = now
            mShakeCount++

            Log.d(TAG, "Shake detected! Count: $mShakeCount / $SHAKE_MIN_COUNT, Force: $gForce")

            if (mShakeCount >= SHAKE_MIN_COUNT) {
                Log.w(TAG, "SHAKE-TO-SOS TRIGGERED!")
                lastTriggerTime = now
                mShakeCount = 0
                triggerPanic()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun triggerPanic() {
        Log.w(TAG, "Executing Shake-to-SOS Panic Flow!")
        val store = LocalStore(this)
        val contacts = store.getContacts()
        
        if (contacts.isEmpty()) {
            Log.e(TAG, "No contacts found to notify!")
            return
        }

        // Notify UI if open
        val intent = Intent("com.ajinkya.helpme.VOICE_PANIC") // Reusing same broadcast so React handles it identically
        sendBroadcast(intent)

        // 2. Enqueue WorkManager for Native Offline SMS and Call
        val request = androidx.work.OneTimeWorkRequest.Builder(AlertWorker::class.java)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("PANIC_BUTTON_SHAKE")
            .build()
        
        androidx.work.WorkManager.getInstance(applicationContext).enqueue(request)
        Log.d(TAG, "Native AlertWorker Enqueued successfully from Shake")
    }
}
