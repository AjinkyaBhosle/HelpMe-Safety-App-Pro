package com.helpme.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Handles immediate restart requests for the WakeWordService (e.g. from swipe-away).
 * This receiver is NOT exported, making it safe from external abuse.
 */
class VoiceRestartReceiver : BroadcastReceiver() {

    private val TAG = "VoiceRestartReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.ajinkya.helpme.RESTART_VOICE_SOS") {
            Log.d(TAG, "Voice SOS restart requested (e.g., from swipe-away or dismiss)")

            val prefs = context.getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
            val voiceSosEnabled = prefs.getBoolean("voice_sos_enabled", false)

            if (!voiceSosEnabled) {
                Log.d(TAG, "Voice SOS is not enabled — skipping restart")
                return
            }

            try {
                val serviceIntent = Intent(context, WakeWordService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "WakeWordService restart initiated successfully")
            } catch (e: Exception) {
                // Catch ForegroundServiceStartNotAllowedException (Android 12+) or IllegalStateException
                Log.e(TAG, "Failed to restart WakeWordService immediately: ${e.message}. Falling back to WorkManager.")
                
                // Fallback to expedited WorkManager job which is allowed to start FGS
                try {
                    val request = OneTimeWorkRequest.Builder(VoiceServiceHealthWorker::class.java)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .addTag("VOICE_SOS_RESTART_FALLBACK")
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                    Log.d(TAG, "Enqueued expedited VoiceServiceHealthWorker as fallback")
                } catch (workEx: Exception) {
                    Log.e(TAG, "Failed to enqueue fallback WorkManager job", workEx)
                }
            }
        }
    }
}
