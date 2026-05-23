package com.helpme.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "SMS_SENT") {
            val code = resultCode
            // Log the exact error code
            when (code) {
                Activity.RESULT_OK -> {
                    Log.i("SmsStatus", "✅ SMS sent successfully (Carrier Accepted)")
                }
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                    Log.e("SmsStatus", "❌ SMS Failed: Generic Failure (Code $code). Carrier may be blocking.")
                }
                SmsManager.RESULT_ERROR_NO_SERVICE -> {
                    Log.e("SmsStatus", "❌ SMS Failed: No Service (Code $code).")
                }
                SmsManager.RESULT_ERROR_NULL_PDU -> {
                    Log.e("SmsStatus", "❌ SMS Failed: Null PDU (Code $code).")
                }
                SmsManager.RESULT_ERROR_RADIO_OFF -> {
                    Log.e("SmsStatus", "❌ SMS Failed: Radio Off (Code $code).")
                }
                else -> {
                    Log.e("SmsStatus", "❌ SMS Failed: Unknown Code $code")
                }
            }
        }
    }
}
