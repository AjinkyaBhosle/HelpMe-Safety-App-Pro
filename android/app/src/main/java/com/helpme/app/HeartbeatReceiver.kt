package com.helpme.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class HeartbeatReceiver : BroadcastReceiver() {
    private val TAG = "HeartbeatReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Heartbeat AlarmClock triggered! Reviving background services if needed...")
        
        // 1. Reschedule the next heartbeat (self-rescheduling watchdog backup)
        VoiceSettings.scheduleHeartbeat(context)

        // 2. Check and revive if enabled
        val voiceSosEnabled = VoiceSettings.isVoiceSosEnabled(context)

        if (voiceSosEnabled) {
            try {
                // Instantly bounce the WakeWordService back to life (or confirm it's running)
                val restartIntent = Intent(context, VoiceRestartReceiver::class.java)
                restartIntent.action = "com.ajinkya.helpme.RESTART_VOICE_SOS"
                context.sendBroadcast(restartIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger VoiceRestartReceiver from Heartbeat", e)
            }
        } else {
            Log.d(TAG, "Voice SOS is disabled, ignoring heartbeat.")
        }
    }
}
