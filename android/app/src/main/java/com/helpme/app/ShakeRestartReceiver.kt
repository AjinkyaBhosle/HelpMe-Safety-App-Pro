package com.helpme.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class ShakeRestartReceiver : BroadcastReceiver() {

    private val TAG = "ShakeRestartReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.ajinkya.helpme.RESTART_SHAKE_SOS") {
            Log.d(TAG, "Shake SOS restart requested (e.g., from swipe-away or dismiss)")

            val prefs = context.getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
            val shakeSosEnabled = prefs.getBoolean("shake_sos_enabled", false)

            if (!shakeSosEnabled) {
                Log.d(TAG, "Shake SOS is not enabled — skipping restart")
                return
            }

            try {
                val serviceIntent = Intent(context, ShakeSensorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "ShakeSensorService restart initiated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart ShakeSensorService immediately: ${e.message}. Falling back to WorkManager.")
                
                try {
                    val request = OneTimeWorkRequest.Builder(ShakeServiceHealthWorker::class.java)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .addTag("SHAKE_SOS_RESTART_FALLBACK")
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                    Log.d(TAG, "Enqueued expedited ShakeServiceHealthWorker as fallback")
                } catch (workEx: Exception) {
                    Log.e(TAG, "Failed to enqueue fallback WorkManager job", workEx)
                }
            }
        }
    }
}
