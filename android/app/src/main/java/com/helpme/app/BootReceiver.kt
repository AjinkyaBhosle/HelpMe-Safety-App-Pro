package com.helpme.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Automatically restarts the WakeWordService (Voice SOS) after a device reboot,
 * if the user had previously enabled it.
 */
class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.helpme.app.RESTART_VOICE_SOS") {
            
            Log.d(TAG, "Device booted — checking if Voice SOS should restart")
            
            val prefs = context.getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
            val voiceSosEnabled = prefs.getBoolean("voice_sos_enabled", false)
            
            if (voiceSosEnabled) {
                Log.d(TAG, "Voice SOS was enabled — restarting WakeWordService")
                try {
                    val serviceIntent = Intent(context, WakeWordService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "WakeWordService restart initiated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart WakeWordService after boot", e)
                }
            } else {
                Log.d(TAG, "Voice SOS was not enabled — skipping restart")
            }
        }
    }
}
