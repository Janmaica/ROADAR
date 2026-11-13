package com.roadar.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roadar.app.data.report.PotholeReportRepository
import com.roadar.app.data.report.RoadarDatabase
import com.roadar.app.data.report.RoomPotholeReportRepository
import com.roadar.app.BuildConfig
import com.roadar.app.data.sync.FirebaseSyncPreferences
import com.roadar.app.data.sync.WorkManagerReportSyncScheduler
import com.roadar.app.location.DeviceLocationProvider
import com.roadar.app.location.FusedDeviceLocationProvider
import com.roadar.app.ui.components.RoadarTopBar
import com.roadar.app.ui.navigation.RoadarDestination
import com.roadar.app.ui.screens.DetectionScreen
import com.roadar.app.ui.screens.HomeScreen
import com.roadar.app.ui.screens.MapScreen
import com.roadar.app.ui.screens.ReportsScreen
import com.roadar.app.ui.screens.SettingsScreen

@Composable
fun RoadarApp() {
    val context = LocalContext.current
    val reportRepository: PotholeReportRepository = remember(context) {
        val database = RoadarDatabase.getInstance(context)
        RoomPotholeReportRepository(
            database.potholeReportDao(),
            syncScheduler = WorkManagerReportSyncScheduler(context),
            syncEnabled = {
                BuildConfig.FIREBASE_CONFIGURED &&
                    FirebaseSyncPreferences.isEnabled(context)
            }
        )
    }
    LaunchedEffect(reportRepository) {
        reportRepository.requestSynchronization()
    }
    val deviceLocationProvider: DeviceLocationProvider = remember(context) {
        FusedDeviceLocationProvider(context)
    }
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val onlineState = remember(connectivityManager) {
        mutableStateOf(connectivityManager.hasValidatedInternet())
    }
    val currentDestinationState = remember { mutableStateOf(RoadarDestination.HOME) }
    val currentDestination by currentDestinationState
    val mapReportIdState = remember { mutableStateOf<String?>(null) }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onlineState.value = connectivityManager.hasValidatedInternet(network)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                onlineState.value =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                onlineState.value = connectivityManager.hasValidatedInternet()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(
            callback,
            Handler(Looper.getMainLooper())
        )
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    BackHandler(enabled = currentDestination != RoadarDestination.HOME) {
        currentDestinationState.value = RoadarDestination.HOME
    }

    Scaffold(
        topBar = { RoadarTopBar(isOnline = onlineState.value) },
        bottomBar = {
            RoadarNavigationBar(
                currentDestination = currentDestination,
                onDestinationSelected = { currentDestinationState.value = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { contentPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val contentWidth = minOf(maxWidth, 720.dp)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(contentWidth)
                            .fillMaxHeight()
                    ) {
                        when (currentDestination) {
                            RoadarDestination.HOME -> HomeScreen(
                                onStartDetection = {
                                    currentDestinationState.value = RoadarDestination.DETECTION
                                },
                                onViewReports = {
                                    currentDestinationState.value = RoadarDestination.REPORTS
                                },
                                onOpenMap = {
                                    currentDestinationState.value = RoadarDestination.MAP
                                },
                                reportRepository = reportRepository
                            )

                            RoadarDestination.DETECTION -> DetectionScreen(
                                reportRepository = reportRepository,
                                locationProvider = deviceLocationProvider
                            )
                            RoadarDestination.REPORTS -> ReportsScreen(
                                reportRepository = reportRepository,
                                onShowOnMap = { reportId ->
                                    mapReportIdState.value = reportId
                                    currentDestinationState.value = RoadarDestination.MAP
                                }
                            )
                            RoadarDestination.MAP -> MapScreen(
                                reportRepository = reportRepository,
                                locationProvider = deviceLocationProvider,
                                targetReportId = mapReportIdState.value,
                                onTargetHandled = { mapReportIdState.value = null }
                            )
                            RoadarDestination.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadarNavigationBar(
    currentDestination: RoadarDestination,
    onDestinationSelected: (RoadarDestination) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp
    ) {
        RoadarDestination.entries.forEach { destination ->
            NavigationBarItem(
                modifier = Modifier.testTag("navigation_${destination.name.lowercase()}"),
                selected = currentDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    NavigationGlyph(
                        destination = destination,
                        selected = currentDestination == destination,
                        modifier = Modifier.semantics {
                            contentDescription = destination.label
                        }
                    )
                },
                label = {
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun NavigationGlyph(
    destination: RoadarDestination,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = modifier.size(24.dp)) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        val scaleX = canvasWidth / 24f
        val scaleY = canvasHeight / 24f
        scale(scaleX = scaleX, scaleY = scaleY) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            when (destination) {
                RoadarDestination.HOME -> {
                    val roof = Path().apply {
                        moveTo(3f, 10f)
                        lineTo(12f, 3f)
                        lineTo(21f, 10f)
                    }
                    drawPath(roof, color, style = stroke)
                    drawLine(color, androidx.compose.ui.geometry.Offset(5f, 9f), androidx.compose.ui.geometry.Offset(5f, 21f), 1.8f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(19f, 9f), androidx.compose.ui.geometry.Offset(19f, 21f), 1.8f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(5f, 21f), androidx.compose.ui.geometry.Offset(19f, 21f), 1.8f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(10f, 21f), androidx.compose.ui.geometry.Offset(10f, 14f), 1.8f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(14f, 21f), androidx.compose.ui.geometry.Offset(14f, 14f), 1.8f)
                }

                RoadarDestination.DETECTION -> {
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(2f, 5f),
                        size = androidx.compose.ui.geometry.Size(20f, 14f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                        style = stroke
                    )
                    drawCircle(color, radius = 4f, center = androidx.compose.ui.geometry.Offset(12f, 12f), style = stroke)
                    drawCircle(color, radius = 1f, center = androidx.compose.ui.geometry.Offset(12f, 12f))
                }

                RoadarDestination.REPORTS -> {
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(5f, 2f),
                        size = androidx.compose.ui.geometry.Size(14f, 20f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                        style = stroke
                    )
                    drawLine(color, androidx.compose.ui.geometry.Offset(8f, 8f), androidx.compose.ui.geometry.Offset(16f, 8f), 1.6f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(8f, 12f), androidx.compose.ui.geometry.Offset(16f, 12f), 1.6f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(8f, 16f), androidx.compose.ui.geometry.Offset(14f, 16f), 1.6f)
                }

                RoadarDestination.MAP -> {
                    val map = Path().apply {
                        moveTo(3f, 5f)
                        lineTo(9f, 3f)
                        lineTo(15f, 5f)
                        lineTo(21f, 3f)
                        lineTo(21f, 19f)
                        lineTo(15f, 21f)
                        lineTo(9f, 19f)
                        lineTo(3f, 21f)
                        close()
                    }
                    drawPath(map, color, style = stroke)
                    drawLine(color, androidx.compose.ui.geometry.Offset(9f, 3f), androidx.compose.ui.geometry.Offset(9f, 19f), 1.6f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(15f, 5f), androidx.compose.ui.geometry.Offset(15f, 21f), 1.6f)
                }

                RoadarDestination.SETTINGS -> {
                    drawCircle(color, radius = 8f, style = stroke)
                    drawCircle(color, radius = 2.5f, style = stroke)
                    for (angle in 0 until 360 step 45) {
                        val radians = Math.toRadians(angle.toDouble())
                        val start = androidx.compose.ui.geometry.Offset(
                            x = (12 + 9 * kotlin.math.cos(radians)).toFloat(),
                            y = (12 + 9 * kotlin.math.sin(radians)).toFloat()
                        )
                        val end = androidx.compose.ui.geometry.Offset(
                            x = (12 + 11 * kotlin.math.cos(radians)).toFloat(),
                            y = (12 + 11 * kotlin.math.sin(radians)).toFloat()
                        )
                        drawLine(color, start, end, 1.8f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

private fun ConnectivityManager.hasValidatedInternet(network: Network? = activeNetwork): Boolean {
    val capabilities = network?.let(::getNetworkCapabilities) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
