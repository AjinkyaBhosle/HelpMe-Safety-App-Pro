package com.helpme.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay

object EmergencyCallHelper {
    
    private const val MAX_RETRIES = 2
    private const val CALL_CHANNEL_ID = "emergency_call"
    
    /**
     * HARDENED EMERGENCY CALL with:
     * - Permission check
     * - Retry logic (2 attempts)
     * - Multi-SIM fallback
     * - User notification on failure
     * - Fallback to dialer
     */
    suspend fun makeEmergencyCall(context: Context, phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) {
            Log.w("EmergencyCall", "No emergency contact configured")
            return false
        }
        
        // Format number
        val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
        val finalPhone = cleanPhone
        
        // 1. Check permission first
        if (!hasCallPermission(context)) {
            Log.e("EmergencyCall", "⚠️ CALL_PHONE permission denied!")
            notifyCallFailure(context, finalPhone, "Permission denied")
            fallbackToDialer(context, finalPhone)
            return false
        }
        
        // 2. Try calling with retries
        for (attempt in 1..MAX_RETRIES) {
            try {
                Log.d("EmergencyCall", "Attempt $attempt/$MAX_RETRIES to call $finalPhone")
                
                if (tryAllCallMethods(context, finalPhone)) {
                    Log.i("EmergencyCall", "✅ Call initiated on attempt $attempt")
                    return true
                }
                
            } catch (e: Exception) {
                Log.e("EmergencyCall", "Attempt $attempt failed: ${e.message}")
            }
            
            // Wait before retry
            if (attempt < MAX_RETRIES) {
                delay(2000) // 2 second delay
            }
        }
        
        // All retries failed - notify and fallback
        Log.e("EmergencyCall", "🚨 All call attempts failed")
        notifyCallFailure(context, finalPhone, "Failed after $MAX_RETRIES attempts")
        fallbackToDialer(context, finalPhone)
        return false
    }
    
    /**
     * Try multiple methods to initiate call
     */
    private fun tryAllCallMethods(context: Context, phone: String): Boolean {
        // Method 0 (PRIORITY): TelecomManager.placeCall — the ONLY reliable method
        // for placing calls from a background WorkManager/Service context.
        // context.startActivity(ACTION_CALL) is BLOCKED on Android 10+ from background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (tryTelecomManagerPlaceCall(context, phone)) {
                return true
            }
        }

        // Method 1: Try all available SIMs (Android M+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (tryMultiSIM(context, phone)) {
                return true
            }
        }
        
        // Method 2: Default dialer with package specification
        if (tryDefaultDialer(context, phone)) {
            return true
        }
        
        // Method 3: Generic ACTION_CALL
        return tryGenericCall(context, phone)
    }

    /**
     * TelecomManager.placeCall — bypasses Android 10+ background Activity restrictions.
     * This is the system-level call API that works even when the app has no visible Activity.
     */
    private fun tryTelecomManagerPlaceCall(context: Context, phone: String): Boolean {
        try {
            val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return false
            val uri = Uri.parse("tel:$phone")
            val extras = android.os.Bundle()
            
            // Use the first available SIM if possible
            val accounts = telecomManager.callCapablePhoneAccounts
            if (accounts.isNotEmpty()) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts[0])
            }
            
            telecomManager.placeCall(uri, extras)
            Log.i("EmergencyCall", "✅ Call via TelecomManager.placeCall (background-safe)")
            return true
        } catch (e: SecurityException) {
            Log.w("EmergencyCall", "TelecomManager.placeCall SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.w("EmergencyCall", "TelecomManager.placeCall failed: ${e.message}")
        }
        return false
    }
    
    /**
     * Try all available SIMs
     */
    private fun tryMultiSIM(context: Context, phone: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        
        try {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            val phoneAccounts = telecomManager?.callCapablePhoneAccounts ?: emptyList()
            
            if (phoneAccounts.isEmpty()) {
                Log.w("EmergencyCall", "No phone accounts found")
                return false
            }
            
            Log.d("EmergencyCall", "Found ${phoneAccounts.size} phone account(s)")
            
            for ((index, account) in phoneAccounts.withIndex()) {
                try {
                    val callIntent = Intent(Intent.ACTION_CALL)
                    callIntent.data = Uri.parse("tel:$phone")
                    callIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                    callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    
                    context.startActivity(callIntent)
                    Log.i("EmergencyCall", "✅ Call via SIM $index (${account.id})")
                    return true
                    
                } catch (e: Exception) {
                    Log.w("EmergencyCall", "SIM $index failed: ${e.message}, trying next...")
                }
            }
        } catch (e: Exception) {
            Log.w("EmergencyCall", "Multi-SIM check failed: ${e.message}")
        }
        
        return false
    }
    
    /**
     * SMART DIALER SELECTION (Anti-Zoom Logic)
     * Scans for all apps that can handle calls, filters out Zoom/Skype, and forces the System Dialer.
     */
    private fun tryDefaultDialer(context: Context, phone: String): Boolean {
        try {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$phone")
            callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            val pm = context.packageManager
            
            // 1. First, check if standard 'default dialer' preference is set & strictly honored
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = context.getSystemService(TelecomManager::class.java)
                val defaultPkg = telecomManager?.defaultDialerPackage
                if (!defaultPkg.isNullOrEmpty()) {
                    callIntent.setPackage(defaultPkg)
                    context.startActivity(callIntent)
                    Log.i("EmergencyCall", "✅ Smart Call: Targeted Default Dialer -> $defaultPkg")
                    return true
                }
            }

            // 2. If no default, Query ALL apps and Score them
            val queryIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:112"))
            val resolveInfos = pm.queryIntentActivities(queryIntent, 0)
            
            var bestPackage: String? = null
            var bestActivityName: String? = null
            var bestScore = -1
            
            for (info in resolveInfos) {
                val pkgName = info.activityInfo.packageName
                var score = 0
                
                // BLACKLIST: Skip VoIP/Meeting apps
                if (pkgName.contains("zoom") || pkgName.contains("skype") || 
                    pkgName.contains("whatsapp") || pkgName.contains("viber") ||
                    pkgName.contains("telegram") || pkgName.contains("teams") ||
                    pkgName.contains("slack") || pkgName.contains("webex") ||
                    pkgName.contains("meet") || pkgName.contains("microsoft") ||
                    pkgName.contains("truecaller")) {
                    continue
                }
                
                // SCORING RULES
                if (pkgName == "com.google.android.dialer") score += 10
                else if (pkgName == "com.android.dialer") score += 9
                else if (pkgName == "com.samsung.android.dialer") score += 9
                else if (pkgName == "com.oneplus.dialer") score += 9
                else if (pkgName.contains("android.phone")) score += 8
                else if (pkgName.contains("dialer")) score += 7
                else if (pkgName.contains("callmanagement")) score += 6 // ColorOS/Realme
                else if (pkgName.contains("contacts")) score += 5 // VALID CANDIDATE: Many Asian ROMs use Contacts as Dialer
                else score += 1 // Any other non-blacklisted app is better than "Open With" dialog
                
                Log.d("EmergencyCall", "Candidate: $pkgName, Score: $score")
                
                if (score > bestScore) {
                    bestScore = score
                    bestPackage = pkgName
                    // Capture the specific Activity Class Name too!
                    bestActivityName = info.activityInfo.name
                }
            }
            
            if (bestPackage != null && bestActivityName != null) {
                // FORCE SPECIFIC ACTIVITY via ComponentName (Stronger than setPackage)
                callIntent.setClassName(bestPackage, bestActivityName)
                
                context.startActivity(callIntent)
                Log.i("EmergencyCall", "✅ Smart Call: Targeted Component -> $bestPackage / $bestActivityName")
                return true
            }

        } catch (e: Exception) {
            Log.w("EmergencyCall", "Smart Dialer targeting failed: ${e.message}")
        }
        
        return false
    }
    
    /**
     * Generic ACTION_CALL
     */
    private fun tryGenericCall(context: Context, phone: String): Boolean {
        try {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$phone")
            callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            context.startActivity(callIntent)
            Log.i("EmergencyCall", "✅ Call via generic ACTION_CALL")
            return true
            
        } catch (e: Exception) {
            Log.e("EmergencyCall", "Generic call failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if we have call permission
     */
    private fun hasCallPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Notify user of call failure
     */
    private fun notifyCallFailure(context: Context, phone: String, reason: String) {
        createNotificationChannel(context)
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("⚠️ EMERGENCY CALL FAILED")
            .setContentText("Could not call $phone: $reason")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setAutoCancel(false)
            .build()
        
        notificationManager.notify(9998, notification)
        Log.e("EmergencyCall", "🚨 User notified of call failure")
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Emergency Call Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for emergency call failures"
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Fallback: Open dialer with number (user must tap call)
     */
    private fun fallbackToDialer(context: Context, phone: String) {
        try {
            // Copy to clipboard first
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Emergency", phone))
            
            // Open dialer with pre-filled number
            val dialIntent = Intent(Intent.ACTION_DIAL)
            dialIntent.data = Uri.parse("tel:$phone")
            dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            context.startActivity(dialIntent)
            Log.i("EmergencyCall", "📞 Opened dialer as fallback - number: $phone")
            
        } catch (e: Exception) {
            Log.e("EmergencyCall", "Even dialer fallback failed: ${e.message}")
        }
    }
}
