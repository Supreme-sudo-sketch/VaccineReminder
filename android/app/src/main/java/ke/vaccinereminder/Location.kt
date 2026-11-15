package ke.vaccinereminder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Found(val lat: Double, val lon: Double) : LocationResult
    data object Off : LocationResult
    data object NoPermission : LocationResult
    data object Unavailable : LocationResult
}

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LocationResult {
    if (!hasLocationPermission(context)) return LocationResult.NoPermission
    val manager = context.getSystemService(LocationManager::class.java) ?: return LocationResult.Unavailable
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (!LocationManagerCompat.isLocationEnabled(manager) || providers.isEmpty()) return LocationResult.Off
    return try {
        val recent = providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time < 10 * 60_000 }
            .maxByOrNull { it.time }
        val fix = recent ?: providers.firstNotNullOfOrNull { freshFix(context, manager, it) }
        if (fix != null) LocationResult.Found(fix.latitude, fix.longitude) else LocationResult.Unavailable
    } catch (e: SecurityException) {
        LocationResult.NoPermission
    }
}

@SuppressLint("MissingPermission")
private suspend fun freshFix(context: Context, manager: LocationManager, provider: String): Location? =
    withTimeoutOrNull(45_000) {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (cont.isActive) cont.resume(location)
            }
        }
    }
