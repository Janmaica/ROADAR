package com.roadar.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.roadar.app.BuildConfig
import com.roadar.app.data.report.PotholeReport
import com.roadar.app.data.report.PotholeReportRepository
import com.roadar.app.location.DeviceLocationProvider
import com.roadar.app.location.LocationFixException
import com.roadar.app.ui.components.EmptyStateCard
import com.roadar.app.ui.components.PageHeader
import com.roadar.app.ui.components.RoadarCard
import com.roadar.app.ui.components.SectionHeading
import okhttp3.OkHttpClient
import java.util.Locale
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil

private const val MAPTILER_STYLE_URL = "https://api.maptiler.com/maps/streets-v4/style.json"
private const val ROADAR_USER_AGENT = "ROADAR-Android"

@Composable
fun MapScreen(
    reportRepository: PotholeReportRepository,
    locationProvider: DeviceLocationProvider,
    targetReportId: String? = null,
    onTargetHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reports by remember(reportRepository) {
        reportRepository.observeReports()
    }.collectAsState(initial = null)
    val loadedReports = reports
    val scope = rememberCoroutineScope()
    var selectedReport by remember { mutableStateOf<PotholeReport?>(null) }
    var mapMessage by remember { mutableStateOf<String?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var initialCameraPositionSet by remember { mutableStateOf(false) }
    val markerReports = remember { mutableStateMapOf<Long, String>() }
    val markerHandles = remember { mutableMapOf<Long, Marker>() }
    val apiKey = BuildConfig.MAPTILER_API_KEY.trim()
    var locateAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) locateAfterPermission?.invoke()
        else locationMessage = "Location permission was denied. Saved reports are still available."
        locateAfterPermission = null
    }
    val hasLocationPermission = {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    val locateUser: () -> Unit = {
        if (!hasLocationPermission()) {
            locateAfterPermission = {
                scope.launch {
                    locationMessage = "Getting a recent device location…"
                    try {
                        val fix = locationProvider.getRecentLocation()
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(fix.latitude, fix.longitude),
                                15.0
                            )
                        )
                        locationMessage =
                            "Centered on device position (±${fix.horizontalAccuracyMeters.toInt()} m)."
                    } catch (exception: LocationFixException) {
                        locationMessage =
                            exception.message ?: "A recent location is unavailable."
                    } catch (exception: SecurityException) {
                        locationMessage = "Location permission changed. Grant access and retry."
                    }
                }
            }
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            scope.launch {
                locationMessage = "Getting a recent device location…"
                try {
                    val fix = locationProvider.getRecentLocation()
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(fix.latitude, fix.longitude),
                            15.0
                        )
                    )
                    locationMessage =
                        "Centered on device position (±${fix.horizontalAccuracyMeters.toInt()} m)."
                } catch (exception: LocationFixException) {
                    locationMessage = exception.message ?: "A recent location is unavailable."
                } catch (exception: SecurityException) {
                    locationMessage = "Location permission changed. Grant access and retry."
                }
            }
        }
    }
    LaunchedEffect(targetReportId, loadedReports, mapLibreMap) {
        val reportId = targetReportId ?: return@LaunchedEffect
        val currentReports = loadedReports ?: return@LaunchedEffect
        val report = currentReports.firstOrNull { it.id == reportId }
        if (report != null) {
            selectedReport = report
            val map = mapLibreMap
            if (map != null) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(report.latitude, report.longitude),
                        16.0
                    )
                )
                initialCameraPositionSet = true
                onTargetHandled()
            } else if (apiKey.isEmpty()) {
                onTargetHandled()
            }
        } else {
            mapMessage = "That report is no longer saved on this device."
            onTargetHandled()
        }
    }

    LaunchedEffect(loadedReports) {
        val selectedId = selectedReport?.id ?: return@LaunchedEffect
        if (loadedReports?.none { it.id == selectedId } == true) {
            selectedReport = null
        }
    }

    LaunchedEffect(mapLibreMap, loadedReports, mapReady, targetReportId) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        val currentReports = loadedReports ?: return@LaunchedEffect
        markerReports.keys.toList().forEach { markerId ->
            markerHandles.remove(markerId)?.let(map::removeMarker)
        }
        markerReports.clear()
        currentReports.forEach { report ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(report.latitude, report.longitude))
                    .title("ROADAR report")
                    .snippet(
                        "${report.reportingMode.name} · " +
                            String.format(Locale.US, "%.1f%%", report.detectionConfidence * 100f) +
                            " · Device position"
                    )
            )
            markerReports[marker.id] = report.id
            markerHandles[marker.id] = marker
        }
        if (currentReports.isNotEmpty() && targetReportId == null && !initialCameraPositionSet) {
            val first = currentReports.first()
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(first.latitude, first.longitude),
                    12.0
                )
            )
            initialCameraPositionSet = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader(
            eyebrow = "Geospatial view",
            title = "Road map",
            description = "Markers show device positions associated with detections, not surveyed pothole locations."
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (hasLocationPermission()) locateUser()
                    else locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                },
                enabled = apiKey.isNotEmpty(),
                modifier = Modifier.testTag("map_center_on_device")
            ) {
                Text("Center on device")
            }
            Text(
                text = loadedReports?.let { "${it.size} local reports" } ?: "Loading reports…",
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (apiKey.isEmpty()) {
            EmptyStateCard(
                title = "Online basemap is not configured",
                description = "Add a restricted MapTiler API key to local.properties to display the interactive map. Saved report details remain available below and in Reports while offline.",
                marker = "M"
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .testTag("roadar_interactive_map")
            ) {
                val mapView = remember(context, apiKey) {
                    configureMapLibre(context)
                    MapView(context).also { it.onCreate(Bundle()) }
                }
                DisposableEffect(mapView, lifecycleOwner) {
                    var started = false
                    var resumed = false
                    var destroyed = false
                    fun start() {
                        if (!started) {
                            mapView.onStart()
                            started = true
                        }
                    }
                    fun resume() {
                        start()
                        if (!resumed) {
                            mapView.onResume()
                            resumed = true
                        }
                    }
                    fun pause() {
                        if (resumed) {
                            mapView.onPause()
                            resumed = false
                        }
                    }
                    fun stop() {
                        pause()
                        if (started) {
                            mapView.onStop()
                            started = false
                        }
                    }
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> start()
                            Lifecycle.Event.ON_RESUME -> resume()
                            Lifecycle.Event.ON_PAUSE -> pause()
                            Lifecycle.Event.ON_STOP -> stop()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        stop()
                        if (!destroyed) {
                            mapView.onDestroy()
                            destroyed = true
                        }
                        mapLibreMap = null
                        mapReady = false
                        initialCameraPositionSet = false
                    }
                }
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update = {}
                )
                LaunchedEffect(mapView, apiKey) {
                    mapView.addOnDidFailLoadingMapListener(
                        MapView.OnDidFailLoadingMapListener { error ->
                            mapReady = false
                            mapMessage = error ?: "The online basemap could not be loaded."
                        }
                    )
                    mapView.getMapAsync { map ->
                        mapLibreMap = map
                        map.setOnMarkerClickListener { marker: Marker ->
                            markerReports[marker.id]?.let { id ->
                                selectedReport = loadedReports?.firstOrNull { it.id == id }
                            }
                            true
                        }
                        val styleUri = Uri.parse(MAPTILER_STYLE_URL)
                            .buildUpon()
                            .appendQueryParameter("key", apiKey)
                            .build()
                            .toString()
                        map.setStyle(
                            Style.Builder().fromUri(styleUri),
                            Style.OnStyleLoaded {
                                mapReady = true
                                mapMessage = null
                            }
                        )
                    }
                }
                if (!mapReady) {
                    RoadarCard(modifier = Modifier.align(Alignment.TopCenter)) {
                        Text(
                            text = mapMessage ?: "Loading online basemap…",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.testTag("map_attribution"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.maptiler.com/copyright/")
                            )
                        )
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("© MapTiler", style = MaterialTheme.typography.labelSmall)
                }
                Text(" · ", style = MaterialTheme.typography.labelSmall)
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.openstreetmap.org/copyright")
                            )
                        )
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        "© OpenStreetMap contributors",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        locationMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("map_location_status")
            )
        }
        mapMessage?.takeIf { !mapReady }?.let {
            Text(
                text = "Basemap unavailable: $it. Interactive tiles require an internet connection; local report details remain available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("map_unavailable_message")
            )
        }
        selectedReport?.let { report ->
            SelectedReportCard(report)
        }
        loadedReports?.takeIf { it.isNotEmpty() }?.let { savedReports ->
            SectionHeading(
                title = "Saved report positions",
                supportingText = "Local details remain available without basemap tiles"
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedReports.forEach { report ->
                    OutlinedButton(
                        onClick = {
                            selectedReport = report
                            mapLibreMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(report.latitude, report.longitude),
                                    16.0
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_report_${report.id}")
                    ) {
                        Text(
                            text = "${report.reportingMode.name} · " +
                                String.format(Locale.US, "%.6f, %.6f", report.latitude, report.longitude) +
                                " · ±${report.horizontalAccuracyMeters.toInt()} m"
                        )
                    }
                }
            }
        } ?: if (loadedReports == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp))
                Text(" Loading saved reports…", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            EmptyStateCard(
                title = "No local reports yet",
                description = "Saved reports from either reporting mode will appear here as map markers when the online basemap is available.",
                marker = "R"
            )
        }
        if (apiKey.isNotEmpty()) {
            Text(
                text = "Interactive online map · Offline basemap tiles are not configured.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectedReportCard(report: PotholeReport) {
    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Selected report · ${report.reportingMode.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Device position ${String.format(Locale.US, "%.6f, %.6f", report.latitude, report.longitude)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Confidence ${String.format(Locale.US, "%.1f%%", report.detectionConfidence * 100f)} · " +
                    "GPS accuracy ±${report.horizontalAccuracyMeters.toInt()} m",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun configureMapLibre(context: Context) {
    MapLibre.getInstance(context)
    HttpRequestUtil.setOkHttpClient(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "$ROADAR_USER_AGENT/${BuildConfig.VERSION_NAME} (+https://github.com/Janmaica/ROADAR)"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()
    )
}
