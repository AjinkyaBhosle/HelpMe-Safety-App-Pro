package com.helpme.app

import android.content.Context
import android.telephony.SmsManager
import android.os.Build
import android.util.Log

object SmsSender {

    fun sendAlerts(context: Context) {
        val store = LocalStore(context)
        
        // Handle different SmsManager versions
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        // BASELINE SMS - No Location, No Async
        val message = "⚠️ Safety Alert: Check-in missed. Please contact me.\n(Location unavailable temporarily)"

        try {
            send(context, store, smsManager, message)
        } catch (e: Exception) {
            Log.e("SmsSender", "Critical error sending SMS: ${e.message}")
        }
    }
    
    private fun send(
        context: Context,
        store: LocalStore,
        smsManager: SmsManager,
        message: String
    ) {
        val uniqueMessage = "$message\n[Ref: ${System.currentTimeMillis().toString().takeLast(6)}]"

        val intent = android.content.Intent("SMS_SENT")
        intent.setPackage(context.packageName) // FIX: Explicit Intent for Android 8+
        
        val sentIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        store.getContacts().forEach { phone ->
            try {
                smsManager.sendTextMessage(phone, null, uniqueMessage, sentIntent, null)
                Log.d("SmsSender", "SMS sent to $phone with explicit tracking.")
            } catch (e: Exception) {
                Log.e("SmsSender", "Failed to send SMS to $phone: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
