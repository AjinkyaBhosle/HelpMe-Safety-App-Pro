package com.helpme.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodically checks if the Voice SOS service is running. 
 * If it died (OOM, force-kill, etc.) and should be running, it revives it.
 */
class VoiceServiceHealthWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "VoiceHealthWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running Voice SOS Health Check...")
        
        val voiceSosEnabled = VoiceSettings.isVoiceSosEnabled(applicationContext)

        if (!voiceSosEnabled) {
            Log.d(TAG, "Voice SOS disabled. No action needed.")
            return Result.success()
        }

        if (!isServiceAlive()) {
            Log.w(TAG, "WakeWordService is NOT alive/healthy! Attempting to revive it.")
            try {
                val serviceIntent = Intent(applicationContext, WakeWordService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(serviceIntent)
                } else {
                    applicationContext.startService(serviceIntent)
                }
                Log.d(TAG, "Successfully revived WakeWordService from health worker.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to revive WakeWordService: ${e.message}", e)
                return Result.retry()
            }
        } else {
            Log.d(TAG, "WakeWordService is currently running and healthy.")
        }

        return Result.success()
    }

    private fun isServiceAlive(): Boolean {
        try {
            val file = java.io.File(applicationContext.filesDir, "voice_sos_last_alive.bin")
            if (file.exists()) {
                val lastAliveStr = file.readText().trim()
                val lastAlive = lastAliveStr.toLongOrNull() ?: 0L
                val diff = System.currentTimeMillis() - lastAlive
                // If the last alive timestamp was updated less than 45 seconds ago, the service is alive and healthy
                return diff < 45_000L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read service heartbeat file", e)
        }
        return false
    }
}
