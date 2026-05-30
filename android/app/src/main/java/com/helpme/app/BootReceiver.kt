package com.helpme.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Automatically restarts the WakeWordService after a device reboot,
 * if the user had previously enabled them.
 */
class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Device booted — checking if SOS services should restart")
            
            val voiceSosEnabled = VoiceSettings.isVoiceSosEnabled(context)
            
            if (voiceSosEnabled) {
                Log.d(TAG, "Voice SOS was enabled — Enqueueing BootStart worker")
                try {
                    val request = OneTimeWorkRequest.Builder(VoiceServiceHealthWorker::class.java)
                        .setInitialDelay(10, TimeUnit.SECONDS) // Delay to let system settle
                        .addTag("BOOT_VOICE_RESTART")
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enqueue VoiceServiceHealthWorker", e)
                }
            }
            

        }
    }
}
