package com.helpme.app

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.pm.ServiceInfo
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlinx.coroutines.delay

class CheckInWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("CheckInWorker", "⏰ CHECK-IN TIMER EXPIRED - Executing Logic")
        
        try {
            // NOTE: We do NOT call setForeground() here because this is a directed background work
            // started with a delay. Android 12+ prevents starting foreground services from the background
            // unless 'Expedited' (which cancels delay) or other exemptions.
            // Standard background execution (10 min window) is sufficient for SMS.

            val store = LocalStore(applicationContext)
            
            // 0. De-duplication Guard
            // Prevent double send if WorkManager retries or fires multiple times close together
            val lastFired = store.getLastCheckinFiredTime()
            val now = System.currentTimeMillis()
            // If we fired less than 1 minute ago, skip (dedupe)
            if (now - lastFired < 60000) {
                 Log.w("CheckInWorker", "Duplicate firing detected. Skipping.")
                 return Result.success()
            }
            store.setLastCheckinFiredTime(now)

            // 1. Get Location (Smart Fallback)
            var locationLink = "Location Unavailable"
            try {
                // Try to get fresh location with timeout
                val freshLocation = withTimeoutOrNull(30000) { // Increased to 30s
                    fetchLocation()
                }

                if (freshLocation != null) {
                    locationLink = "https://maps.google.com/?q=${freshLocation.latitude},${freshLocation.longitude}"
                    store.setLastPanicLocation(locationLink)
                } else {
                    Log.w("CheckInWorker", "Fetch timed out or returned null. Using fallback.")
                    // Fallback to Last Known Cached
                    val savedLoc = store.getLastPanicLocation()
                    if (!savedLoc.isNullOrEmpty() && savedLoc.contains("http")) {
                        locationLink = "$savedLoc (Cached)"
                    }
                }
            } catch (e: Exception) {
                Log.e("CheckInWorker", "Location fetch failed with exception", e)
            }

            // 2. Get Battery Level
            val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val time = java.text.SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a z", java.util.Locale.ENGLISH).apply {
                timeZone = java.util.TimeZone.getDefault()
            }.format(java.util.Date())
            val message = """
                🚨 MISSED CHECK-IN 🚨
                
                I failed to confirm I am safe.
                
                Location: $locationLink
                Battery: $batLevel%
                Time: $time
            """.trimIndent()

            // 3. Send SMS to all contacts
            val contacts = store.getContacts()
            if (contacts.isEmpty()) {
                val raw = store.getRawContacts()
                Log.e("CheckInWorker", "No contacts found! Context pkg=${applicationContext.packageName}. Raw content: '$raw'")
                return Result.failure()
            }

            // Send SMS (TWICE as requested)
            for (contact in contacts) {
                try {
                    val message1 = message.replace("🚨 MISSED CHECK-IN 🚨", "🚨 MISSED CHECK-IN [1/2] 🚨")
                    SmsSenderHardened.sendEmergencySMS(applicationContext, contact, message1)
                    
                    kotlinx.coroutines.delay(3000)
                    
                    val message2 = message.replace("🚨 MISSED CHECK-IN 🚨", "🚨 MISSED CHECK-IN [2/2] 🚨")
                    SmsSenderHardened.sendEmergencySMS(applicationContext, contact, message2)
                } catch (e: Exception) {
                    Log.e("CheckInWorker", "Failed to send SMS to $contact", e)
                }
            }

            // 4. Trigger Emergency Call to the Primary (First) Contact
            try {
                if (contacts.isNotEmpty()) {
                    Log.d("CheckInWorker", "Triggering Emergency Call to primary contact")
                    EmergencyCallHelper.makeEmergencyCall(applicationContext, contacts[0])
                }
            } catch (e: Exception) {
                Log.e("CheckInWorker", "Failed to place emergency call", e)
            }

            // 5. Save Panic History to Native SQLite Database
            try {
                val dbFile = File(applicationContext.getDatabasePath("helpme_safety_dbSQLite.db").absolutePath)
                if (dbFile.exists()) {
                    val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                    
                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ENGLISH).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())

                    // The Capacitor SQLite database stores boolean as 1/0
                    val sql = "INSERT INTO panic_history (timestamp, location, battery, contactNumber, smsSent, callMade) VALUES (?, ?, ?, ?, 1, 1)"
                    db.execSQL(sql, arrayOf(timestamp, locationLink, batLevel.toString(), contacts.firstOrNull() ?: "Unknown"))
                    
                    db.close()
                    Log.d("CheckInWorker", "Native panic history recorded to SQLite")
                } else {
                    Log.w("CheckInWorker", "SQLite DB not found, cannot record panic history natively")
                }
            } catch (e: Exception) {
                Log.e("CheckInWorker", "Failed to save panic history natively: ${e.message}")
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e("CheckInWorker", "Critical worker failure", e)
            return Result.failure()
        }
    }

    private suspend fun fetchLocation(): Location? {
        return try {
            val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(applicationContext)
            
            // Use await() for cleaner async syntax
            // Try to get fresh current location with High Accuracy
            // Note: This might return null if location is turned off
            try {
                client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                    null
                ).await()
            } catch (e: Exception) {
                // Fallback to last known location if current fetch fails
                 client.lastLocation.await()
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationId = 888
        val channelId = "checkin_worker"
        val channelName = "Safety Check-in Monitoring"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW) // Silent
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(applicationContext, channelId)
            .setContentTitle("Safety Check-in")
            .setContentText("Monitoring your safety status...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
