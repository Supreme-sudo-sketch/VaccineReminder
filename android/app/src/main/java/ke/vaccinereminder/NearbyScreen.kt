@file:OptIn(ExperimentalMaterial3Api::class)

package ke.vaccinereminder

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.io.File
import kotlin.math.abs

@Composable
fun NearbyScreen(api: Api, s: Strings, lookups: List<SavedLookup>, onBack: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current
    var query by rememberSaveable { mutableStateOf("") }
    var areaLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var areaLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var areaLabel by rememberSaveable { mutableStateOf("") }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var clinics by remember { mutableStateOf<List<Facility>>(emptyList()) }
    var clinicsKind by remember { mutableStateOf("hospital") }
    var maxSearchKm by remember { mutableIntStateOf(50) }
    var selected by remember { mutableStateOf<Facility?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var problemAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var locating by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var loadingClinics by remember { mutableStateOf(false) }
    var clinicsError by remember { mutableStateOf<ApiException?>(null) }
    var savedArea by remember { mutableStateOf<SavedArea?>(null) }
    var saving by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var fitRequest by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(selected) {
        val index = clinics.indexOf(selected ?: return@LaunchedEffect)
        if (index >= 0) listState.animateScrollToItem(FIRST_CLINIC_ITEM + index)
    }

    fun setArea(lat: Double, lon: Double, label: String) {
        areaLat = lat
        areaLon = lon
        areaLabel = label
        places = emptyList()
        selected = null
        problem = null
        problemAction = null
    }

    LaunchedEffect(lookups) {
        val first = lookups.firstOrNull() ?: return@LaunchedEffect
        savedArea = try {
            api.lookup(first.phone, first.dob).firstNotNullOfOrNull { it.savedArea }
        } catch (e: ApiException) {
            null
        }
        val saved = savedArea
        if (areaLat == null && saved != null) setArea(saved.lat, saved.lon, saved.label.ifBlank { s.myLocation })
    }

    LaunchedEffect(areaLat, areaLon, reload) {
        val lat = areaLat ?: return@LaunchedEffect
        val lon = areaLon ?: return@LaunchedEffect
        loadingClinics = true
        clinicsError = null
        try {
            val result = api.nearbyFacilities(lat, lon)
            clinics = result.facilities
            clinicsKind = result.kind
            maxSearchKm = result.maxSearchKm
        } catch (e: ApiException) {
            clinics = emptyList()
            clinicsError = e
        }
        loadingClinics = false
    }

    fun locate() {
        scope.launch {
            locating = true
            problem = null
            problemAction = null
            when (val result = currentLocation(context)) {
                is LocationResult.Found -> setArea(result.lat, result.lon, s.myLocation)
                LocationResult.Off -> {
                    problem = s.locationOff
                    problemAction = s.openSettings to { openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) }
                }
                LocationResult.NoPermission -> problem = s.locationDenied
                LocationResult.Unavailable -> problem = s.locationUnavailable
            }
            locating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            locate()
        } else {
            problem = s.locationDenied
            val activity = context.findActivity()
            val blocked = activity != null && LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            problemAction = if (blocked) s.openSettings to { openAppSettings(context) } else null
        }
    }

    fun searchTown() {
        val q = query.trim()
        if (q.length < 2 || searching) return
        focus.clearFocus()
        scope.launch {
            searching = true
            problem = null
            problemAction = null
            try {
                val found = api.geocode(q)
                when {
                    found.isEmpty() -> problem = s.noPlaces
                    found.size == 1 -> found[0].let { setArea(it.lat, it.lon, it.name.ifBlank { it.label }) }
                    else -> places = found
                }
            } catch (e: ApiException) {
                problem = s.error(e)
            }
            searching = false
        }
    }

    fun useForReminders(on: Boolean) {
        if (lookups.isEmpty()) {
            notify(s.needChildFirst)
            return
        }
        val lat = areaLat
        val lon = areaLon
        if (on && (lat == null || lon == null)) {
            notify(s.chooseAreaFirst)
            return
        }
        scope.launch {
            saving = true
            try {
                var result: SavedArea? = null
                lookups.forEach { l ->
                    result = api.saveArea(l.phone, l.dob, if (on) lat else null, if (on) lon else null, if (on) areaLabel else null)
                }
                savedArea = result
                notify(if (on) s.areaSaved else s.areaDeleted)
            } catch (e: ApiException) {
                notify(s.error(e))
            }
            saving = false
        }
    }

    AppScaffold(s.nearbyTitle, s, onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(s.searchTown) },
                    placeholder = { Text(s.searchTownHint) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = ::searchTown) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = s.searchTown) }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchTown() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                LoadingButton(
                    s.useMyLocation,
                    locating,
                    { if (hasLocationPermission(context)) locate() else permissionLauncher.launch(LOCATION_PERMISSIONS) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.MyLocation,
                    outlined = true,
                )
                if (locating) Text(s.locating, style = MaterialTheme.typography.bodySmall)
                problem?.let { MessageBanner(it, kind = BannerKind.WARNING, action = problemAction?.first, onAction = problemAction?.second) }
                if (places.isNotEmpty()) {
                    Text(s.choosePlace, style = MaterialTheme.typography.labelLarge)
                    places.forEach { place ->
                        TextButton(onClick = { setArea(place.lat, place.lon, place.name.ifBlank { place.label }) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(place.label, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
            }
            val lat = areaLat
            val lon = areaLon
            val mapShape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(mapShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, mapShape),
            ) {
                ClinicMap(
                    lat = lat,
                    lon = lon,
                    centreTitle = if (areaLabel == s.myLocation) s.youAreHere else areaLabel,
                    clinics = clinics,
                    selected = selected,
                    fitRequest = fitRequest,
                    s = s,
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxSize(),
                )
                OsmCredit(Modifier.align(Alignment.BottomEnd))
                if (lat == null || lon == null) {
                    EmptyMapHint(s, Modifier.align(Alignment.Center))
                } else {
                    FilledTonalIconButton(
                        onClick = {
                            selected = null
                            fitRequest++
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) { Icon(Icons.Filled.ZoomOutMap, contentDescription = s.showAllOnMap) }
                }
                if (loadingClinics) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { MessageBanner(s.osmNote + " " + s.callToConfirm, kind = BannerKind.INFO) }
                if (lat != null && lon != null) {
                    item {
                        val saved = savedArea
                        ReminderAreaCard(
                            s = s,
                            saved = saved,
                            currentLabel = areaLabel,
                            currentDiffers = saved != null && (abs(saved.lat - lat) > 0.001 || abs(saved.lon - lon) > 0.001),
                            saving = saving,
                            linked = lookups.isNotEmpty(),
                            onToggle = ::useForReminders,
                            onReplace = { useForReminders(true) },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(s.nearArea(areaLabel, clinicsKind))
                            if (clinicsKind != "hospital" && clinics.isNotEmpty()) {
                                MessageBanner(s.noHospitalFallback(maxSearchKm), kind = BannerKind.WARNING)
                            }
                        }
                    }
                    clinicsError?.let { e -> item { MessageBanner(s.error(e), action = s.retry, onAction = { reload++ }) } }
                    if (!loadingClinics && clinicsError == null && clinics.isEmpty()) {
                        item { Text(s.noClinics, style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(clinics, key = { "${it.name}|${it.lat}|${it.lon}" }) { facility ->
                        ClinicCard(facility, s, selected == facility, nearest = facility == clinics.firstOrNull()) { selected = facility }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderAreaCard(
    s: Strings,
    saved: SavedArea?,
    currentLabel: String,
    currentDiffers: Boolean,
    saving: Boolean,
    linked: Boolean,
    onToggle: (Boolean) -> Unit,
    onReplace: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = saved != null, enabled = !saving, role = Role.Switch, onValueChange = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(s.useForReminders, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (saving) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Switch(checked = saved != null, onCheckedChange = null)
                }
            }
            Text(s.useForRemindersBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!linked) Text(s.needChildFirst, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (saved != null) {
                Text(s.areaInUse(saved.label.ifBlank { s.myLocation }, formatDate(saved.expiresOn, s.lang)), style = MaterialTheme.typography.bodyMedium)
                if (currentDiffers) TextButton(onClick = onReplace, enabled = !saving) { Text(s.replaceArea(currentLabel)) }
            }
        }
    }
}

@Composable
private fun ClinicCard(facility: Facility, s: Strings, selected: Boolean, nearest: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Card(onClick = onSelect, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (nearest) {
                        Pill(s.nearestTag, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, Modifier.padding(bottom = 4.dp))
                    }
                    Text(facility.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        s.facilityType(facility.type) + " · " + s.distanceKm(facility.distanceKm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(facility.phone ?: s.noPhoneOnMap, style = MaterialTheme.typography.bodySmall)
                    facility.openingHours?.let {
                        Text(s.openingHours(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                s.callToConfirm,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                facility.phone?.let { phone ->
                    OutlinedButton(onClick = { dial(context, phone.filter { it.isDigit() || it == '+' }) }) {
                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.call)
                    }
                }
                FilledTonalButton(onClick = { openDirections(context, facility) }) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.directions)
                }
            }
        }
    }
}

private const val FIRST_CLINIC_ITEM = 3

@Composable
private fun EmptyMapHint(s: Strings, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 2.dp,
        modifier = modifier.padding(24.dp),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(s.nearbyIntro, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                s.chooseAreaPrompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun fitArea(map: MapView, centre: GeoPoint, clinics: List<Facility>) {
    fun fit() {
        if (clinics.isEmpty()) {
            map.controller.animateTo(centre, 14.0, 600L)
        } else {
            val points = listOf(centre) + clinics.map { GeoPoint(it.lat, it.lon) }
            map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.25f), true, 64, 17.0, 600L)
        }
    }
    if (map.width > 0 && map.height > 0) fit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
}

@Composable
private fun ClinicMap(
    lat: Double?,
    lon: Double?,
    centreTitle: String,
    clinics: List<Facility>,
    selected: Facility?,
    fitRequest: Int,
    s: Strings,
    onSelect: (Facility) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnSelect by rememberUpdatedState(onSelect)
    val markers = remember { mutableMapOf<Facility, Marker>() }
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = "${context.packageName}/${appVersion(context)}"
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isTilesScaledToDpi = true
            setMinZoomLevel(5.0)
            setMaxZoomLevel(19.0)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(0.17, 37.9))
        }
    }
    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier.clipToBounds(),
        update = { view ->
            InfoWindow.closeAllInfoWindowsOn(view)
            view.overlays.removeAll { it is Marker }
            markers.clear()
            if (lat != null && lon != null) {
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(lat, lon)
                        title = centreTitle
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_map_you)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
            }
            clinics.forEach { facility ->
                val marker = Marker(view).apply {
                    position = GeoPoint(facility.lat, facility.lon)
                    title = facility.name
                    snippet = listOfNotNull(s.distanceKm(facility.distanceKm), facility.phone, s.callToConfirm).joinToString(" · ")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        currentOnSelect(facility)
                        true
                    }
                }
                markers[facility] = marker
                view.overlays.add(marker)
            }
            selected?.let { markers[it]?.showInfoWindow() }
            view.invalidate()
        },
    )
    LaunchedEffect(lat, lon, clinics, fitRequest) {
        if (lat != null && lon != null) fitArea(map, GeoPoint(lat, lon), clinics.take(8))
    }
    LaunchedEffect(selected) {
        val facility = selected ?: return@LaunchedEffect
        map.controller.animateTo(GeoPoint(facility.lat, facility.lon), 16.5, 600L)
    }
}

@Composable
private fun OsmCredit(modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        color = Color.White.copy(alpha = 0.85f),
        contentColor = Color(0xFF1B1B1B),
        shape = RoundedCornerShape(topStart = 8.dp),
        modifier = modifier.clickable { openUrl(context, "https://www.openstreetmap.org/copyright") },
    ) {
        Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "2"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

private fun openDirections(context: Context, facility: Facility) {
    val geo = "geo:${facility.lat},${facility.lon}?q=${facility.lat},${facility.lon}(${Uri.encode(facility.name)})".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (e: ActivityNotFoundException) {
        openUrl(context, "https://www.openstreetmap.org/directions?to=${facility.lat}%2C${facility.lon}")
    }
}

fun openSettings(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action)) }
}

fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}
