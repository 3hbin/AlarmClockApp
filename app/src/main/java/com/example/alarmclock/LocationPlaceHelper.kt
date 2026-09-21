package com.example.alarmclock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale

data class PlaceInfo(
    val lat: Double,
    val lon: Double,
    val ward: String,
    val district: String,
    val city: String
) {
    fun speakLine(): String {
        val parts = listOf(ward, district, city).filter { it.isNotBlank() }.distinct()
        if (parts.isEmpty()) return ""
        return "Bạn đang ở ${parts.joinToString(", ")}. "
    }

    fun shortCity(): String = city.ifBlank { district }.ifBlank { "vị trí hiện tại" }
}

object LocationPlaceHelper {
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            try {
                if (!lm.isProviderEnabled(p)) continue
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.time > best.time) best = loc
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        return best
    }

    fun resolve(context: Context): PlaceInfo? {
        val loc = lastKnown(context)
        if (loc != null) {
            val place = geocode(context, loc.latitude, loc.longitude)
            if (place != null) {
                save(context, place)
                return place
            }
        }
        return load(context)
    }

    fun geocode(context: Context, lat: Double, lon: Double): PlaceInfo? {
        return try {
            if (!Geocoder.isPresent()) return PlaceInfo(lat, lon, "", "", "")
            val geo = Geocoder(context, Locale("vi", "VN"))
            val list = if (Build.VERSION.SDK_INT >= 33) {
                var result: List<android.location.Address>? = null
                val lock = Object()
                geo.getFromLocation(lat, lon, 1) { addresses ->
                    result = addresses
                    synchronized(lock) { lock.notifyAll() }
                }
                synchronized(lock) {
                    if (result == null) lock.wait(2500)
                }
                result
            } else {
                @Suppress("DEPRECATION")
                geo.getFromLocation(lat, lon, 1)
            }
            val a = list?.firstOrNull() ?: return PlaceInfo(lat, lon, "", "", "")
            val ward = listOf(a.subLocality, a.thoroughfare, a.featureName)
                .firstOrNull { !it.isNullOrBlank() && !looksLikePlusCode(it) }.orEmpty()
            val district = listOf(a.subAdminArea, a.locality)
                .firstOrNull { !it.isNullOrBlank() }.orEmpty()
            val city = listOf(a.adminArea, a.locality)
                .firstOrNull { !it.isNullOrBlank() }.orEmpty()
            PlaceInfo(lat, lon, cleanVn(ward), cleanVn(district), cleanVn(city))
        } catch (_: Exception) {
            PlaceInfo(lat, lon, "", "", "")
        }
    }

    private fun looksLikePlusCode(s: String): Boolean =
        s.contains("+") || s.matches(Regex("^[A-Z0-9]{4,}\\+.*"))

    private fun cleanVn(s: String): String = s.replace("Thành phố ", "thành phố ")
        .replace("Tỉnh ", "tỉnh ")
        .replace("Quận ", "quận ")
        .replace("Huyện ", "huyện ")
        .replace("Phường ", "phường ")
        .replace("Xã ", "xã ")
        .replace("Thị trấn ", "thị trấn ")
        .trim()

    private fun save(context: Context, p: PlaceInfo) {
        context.getSharedPreferences("place", Context.MODE_PRIVATE).edit()
            .putString("ward", p.ward)
            .putString("district", p.district)
            .putString("city", p.city)
            .putString("lat", p.lat.toString())
            .putString("lon", p.lon.toString())
            .apply()
    }

    private fun load(context: Context): PlaceInfo? {
        val sp = context.getSharedPreferences("place", Context.MODE_PRIVATE)
        val city = sp.getString("city", "") ?: ""
        val lat = sp.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = sp.getString("lon", null)?.toDoubleOrNull() ?: return null
        return PlaceInfo(
            lat, lon,
            sp.getString("ward", "") ?: "",
            sp.getString("district", "") ?: "",
            city
        )
    }
}
