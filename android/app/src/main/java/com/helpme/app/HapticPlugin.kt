package com.helpme.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "HapticPlugin")
class HapticPlugin : Plugin() {

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    @PluginMethod
    fun impact(call: PluginCall) {
        val style = call.getString("style", "medium")
        val vibrator = getVibrator()

        if (!vibrator.hasVibrator()) {
            call.resolve()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (style) {
                    "light" -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    "medium" -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    "heavy" -> {
                        // SOS: Very strong repeated pulses for 800ms total (5 pulses)
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 150, 30, 150, 30, 150, 30, 150, 30, 150),
                            intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255),
                            -1
                        )
                    }
                    "success" -> {
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 50, 50, 50),
                            intArrayOf(0, 255, 0, 255),
                            -1
                        )
                    }
                    "error" -> {
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 200, 100, 200),
                            intArrayOf(0, 255, 0, 255),
                            -1
                        )
                    }
                    else -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                when (style) {
                    "light" -> vibrator.vibrate(50)
                    "medium" -> vibrator.vibrate(100)
                    "heavy" -> vibrator.vibrate(200)
                    "success" -> vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
                    "error" -> vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
                    else -> vibrator.vibrate(100)
                }
            }
            call.resolve()
        } catch (e: Exception) {
            call.reject("Haptic failed: " + e.message)
        }
    }
}
