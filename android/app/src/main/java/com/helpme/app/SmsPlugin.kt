package com.helpme.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.PermissionCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import android.os.Build

@CapacitorPlugin(
    name = "SmsPlugin",
    permissions = [
        Permission(strings = [Manifest.permission.SEND_SMS], alias = "sms"),
        Permission(strings = [Manifest.permission.CALL_PHONE], alias = "call"),
        Permission(strings = [Manifest.permission.ACCESS_FINE_LOCATION], alias = "location"),
        Permission(strings = [Manifest.permission.READ_PHONE_STATE], alias = "phone"),
        Permission(strings = [Manifest.permission.RECORD_AUDIO], alias = "microphone"),
        Permission(strings = [Manifest.permission.CAMERA], alias = "camera"),
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = "notifications"),
        Permission(strings = [Manifest.permission.ACCESS_BACKGROUND_LOCATION], alias = "background_location")
    ]
)
class SmsPlugin : Plugin() {

    private var voicePanicReceiver: android.content.BroadcastReceiver? = null

    override fun load() {
        super.load()
        voicePanicReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.helpme.app.VOICE_PANIC") {
                    Log.d("SmsPlugin", "Broadcasting Voice Panic to UI")
                    notifyListeners("onVoicePanic", JSObject())
                }
            }
        }
        val filter = android.content.IntentFilter("com.helpme.app.VOICE_PANIC")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(voicePanicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(voicePanicReceiver, filter)
        }
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        voicePanicReceiver?.let { context.unregisterReceiver(it) }
    }

    @PluginMethod
    fun requestSmsPermission(call: PluginCall) {
        // Request SMS, Call, Location, Microphone, Camera, and Notifications
        val permissions = mutableListOf<String>()
        val aliases = arrayOf("sms", "call", "location", "phone", "microphone", "camera", "notifications")
        
        for (alias in aliases) {
             if (getPermissionState(alias) != com.getcapacitor.PermissionState.GRANTED) {
                 permissions.add(alias)
             }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionForAliases(permissions.toTypedArray(), call, "smsPermissionCallback")
        } else {
            call.resolve(JSObject().put("granted", true))
        }
    }

    @PermissionCallback
    fun smsPermissionCallback(call: PluginCall) {
        // Check if both important permissions are granted (or at least SMS + CALL)
        if (getPermissionState("sms") == com.getcapacitor.PermissionState.GRANTED &&
            getPermissionState("call") == com.getcapacitor.PermissionState.GRANTED) {
             // Location is optional but good to have.
            call.resolve(JSObject().put("granted", true))
        } else {
            call.reject("SMS/CALL permissions denied")
        }
    }

    @PluginMethod
    fun openBatterySettings(call: PluginCall) {
         try {
             val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
             intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
             context.startActivity(intent)
             call.resolve()
         } catch (e: Exception) {
             call.reject("Could not open settings: " + e.message)
         }
    }

    @PluginMethod
    fun isIgnoringBatteryOptimizations(call: PluginCall) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
            call.resolve(JSObject().put("granted", isIgnoring))
        } catch (e: Exception) {
            call.resolve(JSObject().put("granted", false))
        }
    }

    @PluginMethod
    fun openBatteryOptimizationSettings(call: PluginCall) {
        try {
            // Direct intent to whitelist this specific app (one-tap dialog)
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:" + context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            // Fallback: open the general battery optimization list
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                call.resolve()
            } catch (e2: Exception) {
                call.reject("Could not open battery settings: " + e2.message)
            }
        }
    }

    @PluginMethod
    fun openOverlaySettings(call: PluginCall) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            call.resolve()
        } catch (e: Exception) {
            call.reject("Could not open overlay settings: " + e.message)
        }
    }

    @PluginMethod
    fun checkOverlayPermission(call: PluginCall) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        call.resolve(JSObject().put("granted", hasPermission))
    }

    @PluginMethod
    fun startVoiceListener(call: PluginCall) {
        try {
            val intent = Intent(context, WakeWordService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to start voice listener", e)
        }
    }

    @PluginMethod
    fun stopVoiceListener(call: PluginCall) {
        try {
            val intent = Intent(context, WakeWordService::class.java)
            context.stopService(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to stop voice listener", e)
        }
    }

    @PluginMethod
    fun openAppSettings(call: PluginCall) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Could not open app settings: " + e.message)
        }
    }

    @PluginMethod
    fun updateSettings(call: PluginCall) {
        val phoneNumbers = call.getString("phoneNumbers")
        
        Log.d("SmsPlugin", "updateSettings called with: phone=$phoneNumbers")

        if (phoneNumbers == null) {
            call.reject("Missing arguments")
            return
        }

        val store = LocalStore(context)
        store.setContacts(phoneNumbers)
        call.resolve()
    }

    @PluginMethod
    fun sendTestAlert(call: PluginCall) {
        try {
            SmsSender.sendAlerts(context)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to send SMS: " + e.message)
        }
    }

    @PluginMethod
    fun getSettings(call: PluginCall) {
        val store = LocalStore(context)
        val ret = JSObject()
        ret.put("phoneNumbers", store.getContacts().joinToString(",")) // Join back for UI
        ret.put("lastPanicLocation", store.getLastPanicLocation()) // Expose native location
        call.resolve(ret)
    }

    @PluginMethod
    fun triggerPanic(call: PluginCall) {
        Log.d("SmsPlugin", "⚠️ NATIVE PANIC TRIGGERED")
        try {
            // Immediately Enqueue Work (Expedited)
            val request = androidx.work.OneTimeWorkRequest.Builder(AlertWorker::class.java)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("PANIC_BUTTON")
                .build()
            
            androidx.work.WorkManager.getInstance(context).enqueue(request)
            
            call.resolve()
        } catch (e: Exception) {
            Log.e("SmsPlugin", "Failed to trigger panic", e)
            call.reject("Failed to trigger native panic: " + e.message)
        }
    }

    @PluginMethod
    fun maximizeVolume(call: PluginCall) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Maximize Music Volume (Media)
            val maxMusic = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxMusic, 0)
            
            // Maximize Alarm Volume (if possible/relevant)
            val maxAlarm = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxAlarm, 0)
            
            // Maximize Ringtone (for calls)
            val maxRing = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_RING)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_RING, maxRing, 0)

            Log.d("SmsPlugin", "🔊 Volume MAXIMIZED (Music, Alarm, Ring)")
            call.resolve()
        } catch (e: Exception) {
            Log.e("SmsPlugin", "Failed to maximize volume", e)
            call.reject("Volume control failed: " + e.message)
        }
    }

    @PluginMethod
    fun getLastKnownLocation(call: PluginCall) {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            call.resolve(JSObject().put("location", null))
            return
        }

        try {
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        val ret = JSObject()
                        ret.put("latitude", loc.latitude)
                        ret.put("longitude", loc.longitude)
                        call.resolve(ret)
                    } else {
                        call.resolve(JSObject().put("location", null))
                    }
                }
                .addOnFailureListener {
                    call.resolve(JSObject().put("location", null))
                }
        } catch (e: Exception) {
            call.resolve(JSObject().put("location", null))
        }
    }

    @PluginMethod
    fun isLocationServicesEnabled(call: PluginCall) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            call.resolve(JSObject().put("enabled", isGpsEnabled || isNetworkEnabled))
        } catch (e: Exception) {
            call.reject("Failed to check location services: " + e.message)
        }
    }

    @PluginMethod
    fun openLocationSettings(call: PluginCall) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Could not open location settings: " + e.message)
        }
    }

    @PluginMethod
    fun scheduleCheckIn(call: PluginCall) {
        val minutes = call.getInt("minutes")
        if (minutes == null) {
            call.reject("Missing minutes")
            return
        }

        try {
            Log.d("SmsPlugin", "🕒 Scheduling CHECK-IN for $minutes minutes")
            val store = LocalStore(context)
            store.setInterval(minutes)
            
            // Calculate expected expiry for UI
            val expiryTime = System.currentTimeMillis() + (minutes * 60 * 1000)
            store.setLastCheckin(expiryTime) // Reusing 'last_checkin' as 'expected_expiry' contextually
            
            // Create Work Request
            val request = androidx.work.OneTimeWorkRequest.Builder(CheckInWorker::class.java)
                .setInitialDelay(minutes.toLong(), java.util.concurrent.TimeUnit.MINUTES)
                .addTag("CHECK_IN")
                .build()

            // Unique Work: REPLACE existing to reset timer
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "CHECK_IN_WORK",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
            
            call.resolve()
        } catch(e: Exception) {
            Log.e("SmsPlugin", "Failed to schedule", e)
            call.reject("Failed to schedule: ${e.message}")
        }
    }

    @PluginMethod
    fun cancelCheckIn(call: PluginCall) {
        try {
             Log.d("SmsPlugin", "🛑 Cancelling CHECK-IN")
             androidx.work.WorkManager.getInstance(context).cancelUniqueWork("CHECK_IN_WORK")
             
             // Clear stored state
             val store = LocalStore(context)
             store.setLastCheckin(0) // 0 means inactive
             
             call.resolve()
        } catch(e: Exception) {
             call.reject("Failed to cancel: ${e.message}")
        }
    }
}
