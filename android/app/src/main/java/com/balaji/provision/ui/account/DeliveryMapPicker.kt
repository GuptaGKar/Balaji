package com.balaji.provision.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.location.ZoneGeometry
import com.balaji.provision.domain.model.formatPaise
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

/**
 * A draggable delivery map for the address form.
 *
 * The pin is whatever sits under the crosshair when the camera stops. The banner below
 * is the server's own answer about that spot, and an unanswered or failed check still
 * lets the customer save the address, because `place_order` is what decides delivery.
 */
@Composable
fun DeliveryMapPicker(
    viewModel: AddressMapPickerViewModel,
    pin: String,
    onPointChanged: (GeoPoint) -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The listener is installed once, so it has to read the PIN as it is now rather than
    // the one that was typed when the map first appeared.
    val currentPin by rememberUpdatedState(pin)
    val mapScrollGuard = rememberMapScrollGuard()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var gestureDriven by remember { mutableStateOf(false) }
    var zoneSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var selectedSource by remember { mutableStateOf<GeoJsonSource?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Style is set once; the overlay sources are appended to it as soon as it loads.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setMinZoomPreference(MIN_ZOOM)
            readyMap.setMaxZoomPreference(MAX_ZOOM)
            readyMap.setStyle(STYLE_URI) { style ->
                // Seed with the zones the ViewModel already holds. The network answer
                // usually lands before the style finishes loading, and starting every
                // source empty would leave the map unshaded until the next drag.
                val initial = viewModel.state.value.zones
                val selectedId = viewModel.state.value.selectedZoneId
                val zones = GeoJsonSource(
                    ZONE_SOURCE_ID,
                    ZoneGeometry.toFeatureCollection(initial)
                )
                val selected = GeoJsonSource(
                    SELECTED_SOURCE_ID,
                    ZoneGeometry.toFeatureCollection(
                        if (selectedId == null) emptyList() else initial.filter { it.id == selectedId }
                    )
                )
                style.addSource(zones)
                style.addSource(selected)
                style.addLayer(
                    FillLayer(ZONE_LAYER_ID, ZONE_SOURCE_ID).withProperties(
                        PropertyFactory.fillColor(ZONE_FILL),
                        PropertyFactory.fillOutlineColor(ZONE_STROKE)
                    )
                )
                style.addLayer(
                    FillLayer(SELECTED_LAYER_ID, SELECTED_SOURCE_ID).withProperties(
                        PropertyFactory.fillColor(SELECTED_FILL),
                        PropertyFactory.fillOutlineColor(SELECTED_STROKE)
                    )
                )
                zoneSource = zones
                selectedSource = selected
                // MapLibre starts at (0,0) in the Atlantic. Open instead on the address
                // being edited, else the first published area, else an overview of the
                // country, so the very first thing a customer sees is placeable map.
                val savedPoint = viewModel.state.value.point
                val focus = savedPoint
                    ?: viewModel.state.value.zones.firstOrNull()?.let { ZoneGeometry.centreOf(it.ring) }
                readyMap.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(
                                LatLng(
                                    focus?.latitude ?: INDIA_CENTRE_LAT,
                                    focus?.longitude ?: INDIA_CENTRE_LNG
                                )
                            )
                            .zoom(if (savedPoint != null) DEFAULT_ZOOM else OVERVIEW_ZOOM)
                            .build()
                    )
                )
                styleReady = true
            }
            readyMap.addOnCameraMoveStartedListener { reason ->
                // Only a finger counts as the customer choosing a spot. Our own camera
                // moves (opening on a saved address, recentering, the initial overview)
                // must not be mistaken for a pick, or the form would adopt a coordinate
                // nobody aimed at - including MapLibre's default (0,0) in the sea.
                gestureDriven = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
            }
            readyMap.addOnCameraIdleListener {
                if (!gestureDriven) return@addOnCameraIdleListener
                gestureDriven = false
                val target = readyMap.cameraPosition?.target ?: return@addOnCameraIdleListener
                val point = GeoPoint(
                    latitude = round6(target.latitude),
                    longitude = round6(target.longitude)
                )
                viewModel.onCameraIdle(point = point, pin = currentPin)
            }
        }
    }

    // A camera move the ViewModel asked for: opening the picker on a saved address, or
    // pressing recenter. Guarded by an id so each request moves the camera exactly once.
    LaunchedEffect(state.cameraRequestId, styleReady) {
        val target = state.cameraTarget ?: return@LaunchedEffect
        val readyMap = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        readyMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(target.latitude, target.longitude))
                    .zoom(DEFAULT_ZOOM)
                    .build()
            ),
            CAMERA_ANIMATION_MS.toInt()
        )
    }

    LaunchedEffect(state.zones) {
        zoneSource?.setGeoJson(ZoneGeometry.toFeatureCollection(state.zones))
    }

    LaunchedEffect(state.selectedZoneId, state.zones) {
        val selectedId = state.selectedZoneId
        val chosen = if (selectedId == null) emptyList()
        else state.zones.filter { it.id == selectedId }
        selectedSource?.setGeoJson(ZoneGeometry.toFeatureCollection(chosen))
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MAP_HEIGHT)
                // The picker sits inside a vertically scrolling dialog. Without taking
                // the drag here, a finger panning the map north or south would scroll
                // the address form instead of the map.
                .nestedScroll(mapScrollGuard)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription =
                            "Delivery location map. Drag to move the pin to your doorstep."
                    }
            )

            // The pin is a fixed crosshair: the map moves under it, which is what makes
            // "drag to the exact door" understandable without any instructions.
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(PIN_SIZE)
                    .align(Alignment.Center)
            )

            if (!styleReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }

            IconButton(
                onClick = onRecenter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .semantics { contentDescription = "Centre the map on my current location" }
            ) {
                if (state.isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        SpotCheckBanner(
            state = state,
            zonesPublished = state.zones.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        val point = state.point
        Text(
            text = if (point == null) {
                "Drag the map so the pin sits on your door."
            } else {
                "Pin at " + String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }

    // Publish the chosen spot to the form as soon as the server has answered, and even
    // when it has not: coordinates are the customer's, the verdict is the store's.
    LaunchedEffect(state.point) {
        val chosen = state.point ?: return@LaunchedEffect
        onPointChanged(chosen)
    }
}

@Composable
private fun SpotCheckBanner(
    state: AddressMapUiState,
    zonesPublished: Boolean,
    modifier: Modifier = Modifier
) {
    val check = state.check
    val container: Color
    val onContainer: Color
    val headline: String
    val detail: String?

    when {
        check is SpotCheck.Checking -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
            headline = "Checking this spot…"
            detail = null
        }
        check is SpotCheck.Answered && check.availability.deliverable -> {
            val availability = check.availability
            container = DELIVERABLE_CONTAINER
            onContainer = DELIVERABLE_INK
            headline = "We deliver here"
            detail = buildString {
                append(
                    when {
                        availability.feePaise <= 0L -> "Free delivery"
                        else -> "${formatPaise(availability.feePaise)} delivery"
                    }
                )
                if (availability.belowChargeThreshold && availability.amountToFreeDeliveryPaise > 0L) {
                    append(" · free above ")
                    append(formatPaise(availability.amountToFreeDeliveryPaise))
                }
                availability.zoneLabel?.let { append(" · $it") }
            }
        }
        check is SpotCheck.Answered -> {
            container = UNDELIVERABLE_CONTAINER
            onContainer = UNDELIVERABLE_INK
            headline = "We do not deliver to this PIN yet"
            detail = check.availability.message.ifBlank { null }
                ?: "Pick a PIN code we serve, or save the address and check again."
        }
        check is SpotCheck.Failed -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            headline = "Could not check this spot"
            detail = check.message
        }
        else -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
            headline = "Drag the map to your door"
            detail = state.notice ?: if (zonesPublished) null else ZONES_MISSING_HINT
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = container,
        contentColor = onContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (check is SpotCheck.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(headline, style = MaterialTheme.typography.bodyMedium)
            }
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
        }
    }

    // A notice raised outside the check (no GPS fix, zones unavailable) still needs to
    // be visible without hiding the answer the customer is waiting for.
    val extraNotice = state.notice
    if (extraNotice != null && check !is SpotCheck.Idle) {
        Text(
            text = extraNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun round6(value: Double): Double = kotlin.math.round(value * 1e6) / 1e6

/**
 * Swallows a drag that starts on the map so the enclosing scrolling dialog stays put.
 * Flings are left alone; the map has its own momentum handling.
 */
@Composable
private fun rememberMapScrollGuard(): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.Drag) available else Offset.Zero
    }
}

private val MAP_HEIGHT = 260.dp
private val PIN_SIZE = 34.dp
private const val STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
private const val ZONE_SOURCE_ID = "balaji-delivery-areas"
private const val SELECTED_SOURCE_ID = "balaji-selected-area"
private const val ZONE_LAYER_ID = "balaji-delivery-areas-fill"
private const val SELECTED_LAYER_ID = "balaji-selected-area-fill"
private const val MIN_ZOOM = 6.0
private const val MAX_ZOOM = 19.0
private const val DEFAULT_ZOOM = 16.0
private const val CAMERA_ANIMATION_MS = 450L
private const val ZONE_FILL = "rgba(33, 136, 56, 0.20)"
private const val ZONE_STROKE = "rgba(33, 136, 56, 0.60)"
private const val SELECTED_FILL = "rgba(255, 107, 0, 0.35)"
private const val SELECTED_STROKE = "rgba(217, 84, 0, 0.90)"
private val DELIVERABLE_CONTAINER = Color(0xFFDCFCE7)
private val UNDELIVERABLE_CONTAINER = Color(0xFFFFE4E6)

// The containers above are fixed colours, so their ink has to be fixed too: pairing a
// pale green card with the theme's onSurface produced near-invisible text in dark mode.
private val DELIVERABLE_INK = Color(0xFF14532D)
private val UNDELIVERABLE_INK = Color(0xFF7F1D1D)

/** Fallback view when nothing has been pinned and no area is published yet. */
private const val INDIA_CENTRE_LAT = 22.9
private const val INDIA_CENTRE_LNG = 79.6
private const val OVERVIEW_ZOOM = 4.5
private const val ZONES_MISSING_HINT =
    "No delivery areas are published yet, so the map shows no shading."
