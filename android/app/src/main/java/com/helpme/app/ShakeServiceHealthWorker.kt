package com.helpme.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class ShakeServiceHealthWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Log.d("ShakeServiceHealthWorker", "Health check triggered for ShakeSensorService")
        
        val prefs = applicationContext.getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
        val shakeSosEnabled = prefs.getBoolean("shake_sos_enabled", false)
        
        if (!shakeSosEnabled) {
            Log.d("ShakeServiceHealthWorker", "Shake SOS is disabled, no need to restart")
            return Result.success()
        }
        
        try {
            val serviceIntent = Intent(applicationContext, ShakeSensorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
            Log.d("ShakeServiceHealthWorker", "Successfully restarted ShakeSensorService via WorkManager")
            return Result.success()
        } catch (e: Exception) {
            Log.e("ShakeServiceHealthWorker", "Failed to restart ShakeSensorService", e)
            return Result.retry()
        }
    }
}
