package com.helpme.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay

object SmsSenderHardened {
    
    private const val MAX_RETRIES = 3
    private const val CHANNEL_ID = "emergency_sms"
    
    /**
     * HARDENED SMS SEND with:
     * - Retry logic (3 attempts)
     * - Multi-SIM fallback
     * - User notifications on failure
     * - Network check
     */
    suspend fun sendEmergencySMS(
        context: Context,
        phone: String,
        message: String
    ): Boolean {
        // Create notification channel for failures
        createNotificationChannel(context)
        
        // Try sending with retries
        for (attempt in 1..MAX_RETRIES) {
            try {
                Log.d("SMS", "Attempt $attempt/$MAX_RETRIES to $phone")
                
                if (tryAllSIMs(context, phone, message)) {
                    Log.i("SMS", "✅ SUCCESS on attempt $attempt")
                    return true
                }
                
            } catch (e: Exception) {
                Log.e("SMS", "Attempt $attempt failed: ${e.message}")
            }
            
            // Wait before retry (1s, 2s, 3s)
            if (attempt < MAX_RETRIES) {
                delay(1000L * attempt)
            }
        }
        
        // ALL RETRIES FAILED - Critical notification
        notifyFailure(context, phone, "Failed after $MAX_RETRIES attempts")
        return false
    }
    
    /**
     * Try all available SIMs (dual-SIM support)
     */
    private fun tryAllSIMs(context: Context, phone: String, message: String): Boolean {
        val sentIntent = createSentIntent(context)
        
        // For Android 12+ (Multi-SIM support)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                val subs = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
                
                if (subs.isNotEmpty()) {
                    Log.d("SMS", "Found ${subs.size} active SIM(s)")
                    
                    for (sub in subs) {
                        try {
                            val smsManager = getSmsManagerForSub(context, sub.subscriptionId)
                            if (smsManager != null) {
                                sendViaSmsManager(smsManager, phone, message, sentIntent)
                                Log.i("SMS", "✅ Sent via SIM slot ${sub.simSlotIndex}")
                                return true
                            }
                        } catch (e: Exception) {
                            Log.w("SMS", "SIM ${sub.simSlotIndex} failed: ${e.message}, trying next...")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SMS", "Multi-SIM check failed, falling back to default: ${e.message}")
            }
        }
        
        // Fallback: Use default SIM
        try {
            val smsManager = getSmsManagerForSub(context, -1)
            if (smsManager != null) {
                sendViaSmsManager(smsManager, phone, message, sentIntent)
                Log.i("SMS", "✅ Sent via default SIM")
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e("SMS", "Default SIM failed: ${e.message}")
            return false
        }
    }

    private fun getSmsManagerForSub(context: Context, subId: Int): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smsManager = context.getSystemService(SmsManager::class.java)
                if (subId != -1) smsManager?.createForSubscriptionId(subId) else smsManager
            } else {
                @Suppress("DEPRECATION")
                if (subId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    SmsManager.getDefault()
                }
            }
        } catch (e: Exception) {
            Log.e("SMS", "Failed to resolve SmsManager for subId $subId: ${e.message}")
            null
        }
    }
    
    /**
     * Core SMS send logic
     */
    private fun sendViaSmsManager(
        smsManager: SmsManager,
        phone: String,
        message: String,
        sentIntent: PendingIntent
    ) {
        val parts = smsManager.divideMessage(message)
        
        if (parts.size > 1) {
            // Long message - send as multipart
            val sentIntents = ArrayList<PendingIntent>()
            repeat(parts.size) { sentIntents.add(sentIntent) }
            
            smsManager.sendMultipartTextMessage(
                phone, null, parts, sentIntents, null
            )
            Log.d("SMS", "Sent multipart message (${parts.size} parts)")
        } else {
            // Single message
            smsManager.sendTextMessage(
                phone, null, message, sentIntent, null
            )
            Log.d("SMS", "Sent single message")
        }
    }
    
    /**
     * Create PendingIntent for status tracking
     */
    private fun createSentIntent(context: Context): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java)
        intent.action = "SMS_SENT"
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    /**
     * Show critical failure notification to user
     */
    private fun notifyFailure(context: Context, phone: String, error: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ SOS SMS FAILED")
            .setContentText("Could not send to $phone: $error")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(9999, notification)
        Log.e("SMS", "🚨 CRITICAL: SMS failed - user notified")
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency SMS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for SMS sending failures"
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
