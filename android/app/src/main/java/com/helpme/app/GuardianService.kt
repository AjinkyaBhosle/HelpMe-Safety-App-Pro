package com.helpme.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log

class GuardianService : Service() {
    private val TAG = "GuardianService"
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Guardian bound to WakeWordService successfully")
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e(TAG, "WakeWordService unexpectedly disconnected! Main process was likely killed.")
            isBound = false
            reviveMainProcess()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "GuardianService Created in isolated process")
        try {
            startForeground(999, createHiddenNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Guardian in foreground", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val enabled = VoiceSettings.isVoiceSosEnabled(this)

        if (!enabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        bindToMainService()
        return START_STICKY
    }

    private fun bindToMainService() {
        if (!isBound) {
            try {
                val bindIntent = Intent(this, WakeWordService::class.java)
                bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind to WakeWordService", e)
            }
        }
    }

    private fun reviveMainProcess() {
        Log.d(TAG, "Attempting to instantly revive WakeWordService...")
        try {
            val restartIntent = Intent(this, VoiceRestartReceiver::class.java)
            restartIntent.action = "com.ajinkya.helpme.RESTART_VOICE_SOS"
            sendBroadcast(restartIntent)
            
            // Attempt to re-bind shortly after revival
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                bindToMainService()
            }, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revive main process", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try {
                unbindService(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding", e)
            }
            isBound = false
        }
    }

    private fun createHiddenNotification(): Notification {
        val channelId = "guardian_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Background Guardian",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Guardian Active")
            .setContentText("Protecting background microphone.")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }
}
