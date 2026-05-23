package com.helpme.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.util.concurrent.TimeUnit

data class LocationResultData(
    val location: Location,
    val accuracyMeters: Float,
    val updatedMinutesAgo: Long
)

object LocationHelper {

    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun getBestLocation(
        context: Context,
        timeoutSeconds: Long = 5,
        callback: (LocationResultData?) -> Unit
    ) {
        val isFinished = java.util.concurrent.atomic.AtomicBoolean(false)

        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.SECONDS.toMillis(1)
        )
            .setWaitForAccurateLocation(true)
            .setMaxUpdates(1)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Try to set flag to true. If already true, we lost race (timeout happened).
                if (!isFinished.compareAndSet(false, true)) return
                
                client.removeLocationUpdates(this)
                val loc = result.lastLocation
                if (loc != null) {
                    callback(buildResult(loc))
                } else {
                    fallbackLastKnown(context, client, callback)
                }
            }
        }

        client.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())

        // ⏱️ TIMEOUT
        Thread {
            Thread.sleep(timeoutSeconds * 1000)
            // Try to set flag to true. If already true, we lost race (location found).
            if (isFinished.compareAndSet(false, true)) {
                 client.removeLocationUpdates(locationCallback)
                 fallbackLastKnown(context, client, callback)
            }
        }.start()
    }

    private fun fallbackLastKnown(
        context: Context,
        client: FusedLocationProviderClient,
        callback: (LocationResultData?) -> Unit
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }
        
        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) callback(buildResult(loc))
                else callback(null)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun buildResult(location: Location): LocationResultData {
        val minutesAgo =
            (System.currentTimeMillis() - location.time) / (60 * 1000)

        return LocationResultData(
            location = location,
            accuracyMeters = location.accuracy,
            updatedMinutesAgo = minutesAgo
        )
    }
}
