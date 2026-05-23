package com.helpme.app

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.*

@CapacitorPlugin(name = "TorchPlugin")
class TorchPlugin : Plugin() {

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var isStrobing = false
    private var strobeJob: Job? = null

    override fun load() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                cameraId = cameraManager?.cameraIdList?.firstOrNull()
            } catch (e: Exception) {
                Log.e("TorchPlugin", "Failed to get camera ID", e)
            }
        }
    }

    @PluginMethod
    fun toggle(call: PluginCall) {
        val enable = call.getBoolean("enable") ?: false
        try {
            setTorchState(enable)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Failed to toggle torch: " + e.message)
        }
    }

    @PluginMethod
    fun setStrobe(call: PluginCall) {
        val enable = call.getBoolean("enable") ?: false
        if (enable) {
            startStrobe()
        } else {
            stopStrobe()
        }
        call.resolve()
    }

    private fun startStrobe() {
        if (isStrobing) return
        isStrobing = true
        
        // Run in background coroutine
        strobeJob = CoroutineScope(Dispatchers.IO).launch {
            var state = true
            while (isActive && isStrobing) {
                try {
                    setTorchState(state)
                    state = !state
                    // 50ms On / 50ms Off = 10Hz Strobe (Disorienting/Visible)
                    delay(50) 
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e("TorchPlugin", "Strobe error", e)
                    break
                }
            }
        }
    }

    private fun stopStrobe() {
        isStrobing = false
        strobeJob?.cancel()
        strobeJob = null
        // Ensure off
        try {
            setTorchState(false)
        } catch (e: Exception) {
            Log.e("TorchPlugin", "Failed to turn off after strobe", e)
        }
    }

    private fun setTorchState(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cameraId != null) {
            cameraManager?.setTorchMode(cameraId!!, on)
        }
    }
}
