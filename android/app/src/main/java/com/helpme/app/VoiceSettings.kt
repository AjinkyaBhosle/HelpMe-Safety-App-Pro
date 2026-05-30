package com.helpme.app

import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log
import java.io.File

object VoiceSettings {
    private const val TAG = "VoiceSettings"

    fun setVoiceSosEnabled(context: Context, enabled: Boolean) {
        // 1. Write to SharedPreferences
        val prefs = context.getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("voice_sos_enabled", enabled).apply()
        
        // 2. Write to cross-process safe file sentinel in filesDir
        try {
            val file = File(context.filesDir, "voice_sos_enabled.bin")
            if (enabled) {
                if (!file.exists()) {
                    file.createNewFile()
                }
            } else {
                if (file.exists()) {
                    file.delete()
                }
            }
            Log.d(TAG, "Cross-process sentinel set to: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write cross-process sentinel", e)
        }
    }

    fun isVoiceSosEnabled(context: Context): Boolean {
        val file = File(context.filesDir, "voice_sos_enabled.bin")
        if (file.exists()) {
            return true
        }
        
        // Fallback/Inversion cure: If the sentinel file is missing, verify with SharedPreferences
        val prefs = context.getSharedPreferences("helpme_prefs", Context.MODE_PRIVATE)
        val prefsEnabled = prefs.getBoolean("voice_sos_enabled", false)
        
        // Self-healing: if SharedPreferences state is true but the sentinel file is missing, restore it
        if (prefsEnabled) {
            try {
                if (!file.exists()) {
                    file.createNewFile()
                }
                Log.d(TAG, "Self-healed sentinel file discrepancy (restored voice_sos_enabled.bin)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore sentinel file during self-healing", e)
            }
            return true
        }
        
        return false
    }

    fun scheduleHeartbeat(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val intent = Intent(context, HeartbeatReceiver::class.java)
            val operationIntent = PendingIntent.getBroadcast(
                context, 
                777, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val showIntent = Intent(context, MainActivity::class.java)
            val pendingShowIntent = PendingIntent.getActivity(
                context,
                778,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 3 hours interval is a good balance between safety and user visibility fatigue
            val threeHoursMs = 3 * 60 * 60 * 1000L
            val triggerTime = System.currentTimeMillis() + threeHoursMs

            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingShowIntent)
            alarmManager.setAlarmClock(alarmClockInfo, operationIntent)
            Log.d(TAG, "Heartbeat AlarmClock scheduled for 3 hours from now")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule Heartbeat", e)
        }
    }
}
