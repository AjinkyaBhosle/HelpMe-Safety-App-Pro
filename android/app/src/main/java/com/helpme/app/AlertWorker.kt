package com.helpme.app

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ForegroundInfo
import android.content.pm.ServiceInfo

class AlertWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Log.d("AlertWorker", "⚠️ ALERT WORKER STARTED - Manual/Panic Mode")

            // Set Foreground immediately (Vital for Android 14+)
            try {
                setForeground(getForegroundInfo())
            } catch (e: Exception) {
                Log.w("AlertWorker", "Failed to set foreground: ${e.message}")
            }
            
            // DIRECT EXECUTION: Panic is triggered manually, so we execute immediately.
            Log.d("AlertWorker", "Initiating SOS sequence...")
            
            val store = LocalStore(applicationContext)
            
            // 1. Get Battery Level
            val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            
            // 2. Get Location
            var locationLink = "Unknown Location"
            try {
                // Priority 1: Get last known from FusedLocation
                val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(applicationContext)
                val lastLocation = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                    client.lastLocation.addOnSuccessListener { location ->
                        if (cont.isActive) cont.resume(location) {}
                    }.addOnFailureListener {
                        if (cont.isActive) cont.resume(null) {}
                    }
                }
                
                if (lastLocation != null) {
                    locationLink = "https://maps.google.com/?q=${lastLocation.latitude},${lastLocation.longitude}"
                    store.setLastPanicLocation(locationLink)
                } else {
                     // Fallback logic omitted for brevity, using cached if available
                     val savedLoc = store.getLastPanicLocation()
                     if (!savedLoc.isNullOrEmpty() && savedLoc.contains("http")) {
                        locationLink = savedLoc
                     }
                }
            } catch (e: Exception) {
                 val savedLoc = store.getLastPanicLocation()
                 if (!savedLoc.isNullOrEmpty() && savedLoc.contains("http")) {
                     locationLink = savedLoc
                 }
            }

            // 3. Get Current Time (IST)
            val currentTime = java.text.SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a", java.util.Locale.ENGLISH).apply {
                timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
            }.format(java.util.Date())

            // 4. Construct Rich Message
            val baseMessage = """
    🚨 EMERGENCY ALERT 🚨

    I NEED HELP IMMEDIATELY!

    Location: $locationLink

    Battery: $batLevel%
    Time: $currentTime IST

    Please respond or call back urgently.
            """.trimIndent()

            // 5. CALL FIRST (EmergencyCallHelper handles failure notifications internally)
            try {
                EmergencyCallHelper.makeEmergencyCall(
                    applicationContext, 
                    store.getContacts().firstOrNull()
                )
            } catch (e: Exception) {
                Log.e("AlertWorker", "Call attempt threw: ${e.message}")
            }
            
            // 6. SEND SMS
             // Send TWICE with unique identifiers (avoids spam detection)
            for (contact in store.getContacts()) {
                try {
                    val message1 = baseMessage.replace("🚨 EMERGENCY ALERT 🚨", "🚨 EMERGENCY ALERT [1/2] 🚨")
                    SmsSenderHardened.sendEmergencySMS(applicationContext, contact, message1)
                    
                    delay(3000)
                    
                    val message2 = baseMessage.replace("🚨 EMERGENCY ALERT 🚨", "🚨 EMERGENCY ALERT [2/2] 🚨")
                    SmsSenderHardened.sendEmergencySMS(applicationContext, contact, message2)
                } catch (e: Exception) {
                    Log.e("AlertWorker", "SMS send exception: ${e.message}")
                }
            }
            
            store.setAlertSent(true)
            return Result.success()
            
        } catch(e: Exception) { 
            return Result.failure() 
        }
    }



    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationId = 999
        val channelId = "sos_channel"
        val channelName = "Emergency SOS"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Create explicit call intent for the Action Button
        val store = LocalStore(applicationContext)
        val contact = store.getContacts().firstOrNull() ?: ""
        val callIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$contact")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingCall = PendingIntent.getActivity(
            applicationContext, 
            911, 
            callIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action: Call Help
        val callAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_call,
            "📞 CALL HELP",
            pendingCall
        ).build()

        val notification = Notification.Builder(applicationContext, channelId)
            .setContentTitle("Sending Emergency SOS")
            .setContentText("Tap CALL if auto-dial fails. Sending location...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .addAction(callAction) // Add the action button
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
    
    /**
     * Show high-priority notification when call is blocked by Android
     */
    private fun showCallFailureNotification(phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) return
        
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "emergency_call_blocked",
                "Emergency Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when emergency calls are blocked"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create call intent that user can tap
        val callIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "emergency_call_blocked")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("🚨 TAP TO CALL EMERGENCY CONTACT")
            .setContentText("Background call blocked. Tap to dial $phoneNumber")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Try to launch full-screen
            .build()
        
        notificationManager.notify(9999, notification)
        Log.i("AlertWorker", "🔔 Call blocked notification shown")
    }
}
