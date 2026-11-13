package com.example.alarmclock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Lấy vị trí hiện tại (một lần) để gắn vào SMS hoặc kiểm tra gần địa điểm.
 * Dùng Fused Location nếu có Play Services, fallback LocationManager.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun getLastLocation(context: Context, onResult: (String) -> Unit) {
        if (!hasPermission(context)) {
            onResult("Chưa có quyền vị trí")
            return
        }
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.lastLocation
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        onResult(format(loc))
                    } else {
                        // Request one update
                        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                            .setMaxUpdates(1)
                            .build()
                        fused.requestLocationUpdates(req, object : LocationCallback() {
                            override fun onLocationResult(result: LocationResult) {
                                fused.removeLocationUpdates(this)
                                val l = result.lastLocation
                                onResult(if (l != null) format(l) else "Không lấy được GPS")
                            }
                        }, Looper.getMainLooper())
                    }
                }
                .addOnFailureListener {
                    fallbackLocationManager(context, onResult)
                }
        } catch (e: Exception) {
            fallbackLocationManager(context, onResult)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackLocationManager(context: Context, onResult: (String) -> Unit) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var best: Location? = null
            for (p in providers) {
                if (!lm.isProviderEnabled(p)) continue
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.accuracy < best.accuracy) best = loc
            }
            onResult(if (best != null) format(best) else "Không lấy được GPS")
        } catch (e: Exception) {
            onResult("Lỗi GPS: ${e.message}")
        }
    }

    private fun format(loc: Location): String {
        val maps = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        return "%.5f, %.5f | $maps".format(loc.latitude, loc.longitude)
    }

    /** Lưu tọa độ “nhà / nơi làm” để so sánh khoảng cách (m) */
    fun setTargetLocation(context: Context, lat: Double, lng: Double, radiusM: Float = 200f) {
        context.getSharedPreferences("gps_alarm", Context.MODE_PRIVATE).edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lng", lng.toFloat())
            .putFloat("radius", radiusM)
            .apply()
    }

    fun getTarget(context: Context): Triple<Double, Double, Float>? {
        val p = context.getSharedPreferences("gps_alarm", Context.MODE_PRIVATE)
        if (!p.contains("lat")) return null
        return Triple(
            p.getFloat("lat", 0f).toDouble(),
            p.getFloat("lng", 0f).toDouble(),
            p.getFloat("radius", 200f)
        )
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, r)
        return r[0]
    }
}
