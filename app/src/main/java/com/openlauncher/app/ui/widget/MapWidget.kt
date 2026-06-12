package com.openlauncher.app.ui.widget

import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openlauncher.app.data.MapProvider
import com.openlauncher.app.util.LocationData
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapWidget(
    location: LocationData?,
    mapProvider: MapProvider,
    accent: Color,
    isDayMode: Boolean = false,
    editMode: Boolean = false,
    onToggleProvider: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Configure OSMDroid user agent
    LaunchedEffect(Unit) {
        org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
    }

    // Keep track of MapView instance
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
        }
    }

    // Add MapEventsOverlay for long press handling
    DisposableEffect(mapView) {
        val receiver = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                onLongClick()
                return true
            }
        }
        val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(receiver)
        mapView.overlays.add(eventsOverlay)
        onDispose {
            mapView.overlays.remove(eventsOverlay)
        }
    }

    // Manage Location Marker
    val marker = remember {
        Marker(mapView).apply {
            val size = (16 * context.resources.displayMetrics.density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = accent.toArgb()
            }
            // Draw outer glow/halo
            paint.alpha = 50
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            // Draw inner solid circle
            paint.alpha = 255
            canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
            
            icon = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
    }

    // Auto-center tracking state
    var autoFollow by remember { mutableStateOf(true) }

    // Update Tile Source when provider changes
    LaunchedEffect(mapProvider) {
        if (mapProvider == MapProvider.GOOGLE) {
            val googleTiles = object : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
                "GoogleRoads",
                0, 20, 256, "",
                arrayOf(
                    "https://mt0.google.com/vt/lyrs=m",
                    "https://mt1.google.com/vt/lyrs=m",
                    "https://mt2.google.com/vt/lyrs=m",
                    "https://mt3.google.com/vt/lyrs=m"
                )
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    val zoom = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
                    val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
                    val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
                    return getBaseUrl() + "&x=" + x + "&y=" + y + "&z=" + zoom
                }
            }
            mapView.setTileSource(googleTiles)
        } else {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
        }
    }

    // Update Dark Mode Filter
    LaunchedEffect(isDayMode) {
        if (isDayMode) {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        } else {
            val matrix = floatArrayOf(
                -0.6f,  0f,    0f,    0f, 200f,
                 0f,   -0.6f,  0f,    0f, 200f,
                 0f,    0f,   -0.6f,  0f, 200f,
                 0f,    0f,    0f,    1.0f, 0f
            )
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        }
        mapView.invalidate()
    }

    // Update location marker and auto-follow
    LaunchedEffect(location, autoFollow) {
        if (location != null) {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            marker.position = geoPoint
            if (!mapView.overlays.contains(marker)) {
                mapView.overlays.add(marker)
            }
            if (autoFollow) {
                mapView.controller.animateTo(geoPoint)
            }
            mapView.invalidate()
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay UI
        // Top-Left: Map Provider Toggle
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onToggleProvider() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (mapProvider == MapProvider.GOOGLE) "GOOGLE" else "OSM",
                    color = Color.White,
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Top-Right: Zoom Buttons
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = {
                    autoFollow = false
                    mapView.controller.zoomIn()
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    autoFollow = false
                    mapView.controller.zoomOut()
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // Bottom-Right: Recenter/Auto-follow Button
        IconButton(
            onClick = {
                autoFollow = true
                if (location != null) {
                    mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "Center on location",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        // In editMode, block input to MapView so parent gestures (drag, resize, context menu) work perfectly.
        if (editMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .pointerInput(Unit) {}
            )
        }
    }
}
