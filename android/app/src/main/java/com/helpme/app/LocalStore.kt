package com.helpme.app

import android.content.Context
import android.content.SharedPreferences

class LocalStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("safety", Context.MODE_PRIVATE)

    fun setLastCheckin(time: Long) =
        prefs.edit().putLong("last_checkin", time).apply()

    fun getLastCheckin(): Long =
        prefs.getLong("last_checkin", 0)

    fun setInterval(minutes: Int) =
        prefs.edit().putInt("interval", minutes).apply()

    fun getInterval(): Int =
        prefs.getInt("interval", 480) // default 8h (480 min)

    fun setAlertSent(sent: Boolean) =
        prefs.edit().putBoolean("alert_sent", sent).apply()

    fun isAlertSent(): Boolean =
        prefs.getBoolean("alert_sent", false)

    fun setContacts(contacts: String) {
        val success = prefs.edit().putString("contacts", contacts).commit()
        if (success) {
            android.util.Log.d("LocalStore", "✅ Contacts saved successfully: $contacts")
        } else {
            android.util.Log.e("LocalStore", "❌ Failed to commit contacts to disk!")
        }
    }

    fun getRawContacts(): String {
        return prefs.getString("contacts", "NULL_OR_EMPTY") ?: "NULL_RETRIEVED"
    }

    fun getContacts(): List<String> {
        val str = prefs.getString("contacts", "") ?: ""
        return str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setLastPanicLocation(loc: String) =
        prefs.edit().putString("last_panic_location", loc).apply()

    fun getLastPanicLocation(): String =
        prefs.getString("last_panic_location", "") ?: ""

    fun setMissedCheckInCount(count: Int) =
        prefs.edit().putInt("missed_checkin_count", count).apply()

    fun getMissedCheckInCount(): Int =
        prefs.getInt("missed_checkin_count", 0)

    fun setLastCheckinFiredTime(time: Long) =
        prefs.edit().putLong("last_checkin_fired", time).apply()

    fun getLastCheckinFiredTime(): Long =
        prefs.getLong("last_checkin_fired", 0)
}
