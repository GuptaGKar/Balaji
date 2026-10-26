package com.balaji.provision.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.ContextCompat

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class GeocodedPlace(
    val addressLine: String? = null,
    val area: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null
)

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Result<GeoPoint> {
        if (!hasPermission()) {
            return Result.failure(SecurityException("Location permission is required."))
        }
        return try {
            val location = withTimeoutOrNull(15_000) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            } ?: withTimeoutOrNull(5_000) {
                client.lastLocation.await()
            }
            if (location != null) {
                Result.success(GeoPoint(location.latitude, location.longitude))
            } else {
                Result.failure(IllegalStateException("Could not get a location fix. Try again near a window."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    suspend fun describe(point: GeoPoint): GeocodedPlace? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val address = runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(point.latitude, point.longitude, 1)
                ?.firstOrNull()
        }.getOrNull() ?: return@withContext null

        val line = listOfNotNull(address.subThoroughfare, address.thoroughfare)
            .joinToString(" ")
            .ifBlank { address.featureName.orEmpty() }
            .trim()
            .takeIf(String::isNotBlank)

        GeocodedPlace(
            addressLine = line,
            area = (address.subLocality ?: address.locality)?.trim()?.takeIf(String::isNotBlank),
            city = (address.locality ?: address.subAdminArea)?.trim()?.takeIf(String::isNotBlank),
            state = address.adminArea?.trim()?.takeIf(String::isNotBlank),
            postalCode = address.postalCode
                ?.filter(Char::isDigit)
                ?.take(6)
                ?.takeIf { it.length == 6 }
        )
    }
}
