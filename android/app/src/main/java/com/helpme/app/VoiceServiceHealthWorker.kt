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
        
        val prefs = applicationContext.getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
        val voiceSosEnabled = prefs.getBoolean("voice_sos_enabled", false)

        if (!voiceSosEnabled) {
            Log.d(TAG, "Voice SOS disabled in prefs. No action needed.")
            return Result.success()
        }

        if (!isServiceRunning(WakeWordService::class.java)) {
            Log.w(TAG, "WakeWordService is NOT running! Attempting to revive it.")
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
            Log.d(TAG, "WakeWordService is currently running smoothly.")
        }

        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
