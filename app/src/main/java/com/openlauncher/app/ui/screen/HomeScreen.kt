package com.openlauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.ClockStyle
import com.openlauncher.app.data.GRID_COLS
import com.openlauncher.app.data.GRID_ROWS
import com.openlauncher.app.data.WidgetConfig
import com.openlauncher.app.data.activeWidgetIds
import com.openlauncher.app.data.computeWidgetMove
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.model.WeatherState
import com.openlauncher.app.ui.widget.AltimeterWidget
import com.openlauncher.app.ui.widget.ClockWidget
import com.openlauncher.app.ui.widget.MapWidget
import com.openlauncher.app.ui.widget.NowPlayingWidget
import com.openlauncher.app.ui.widget.PipWidget
import com.openlauncher.app.ui.widget.SoundboardWidget
import com.openlauncher.app.ui.widget.SpeedometerWidget
import com.openlauncher.app.ui.widget.TelemetryWidget
import com.openlauncher.app.ui.widget.TripTrackerWidget
import com.openlauncher.app.ui.widget.VitalsWidget
import com.openlauncher.app.ui.widget.WeatherWidget
import com.openlauncher.app.ui.widget.clockTimeLabel
import com.openlauncher.app.util.LocationData
import java.util.Calendar
import kotlin.math.roundToInt

private data class WidgetTypeInfo(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val description: String
)

private val ALL_WIDGET_TYPES = listOf(
    WidgetTypeInfo("CLOCK",       "CLOCK",       Icons.Default.AccessTime,  "Time & date"),
    WidgetTypeInfo("WEATHER",     "WEATHER",     Icons.Default.Cloud,       "Current conditions"),
    WidgetTypeInfo("NOW_PLAYING", "NOW PLAYING", Icons.Default.MusicNote,   "Media controls"),
    WidgetTypeInfo("TELEMETRY",   "COMPASS",     Icons.Default.Explore,     "Speed & heading"),
    WidgetTypeInfo("ALTIMETER",   "ALTIMETER",   Icons.Default.FlightTakeoff, "Roll, pitch & altitude"),
    WidgetTypeInfo("SPEEDOMETER", "SPEED",       Icons.Default.Speed,         "GPS speed"),
    WidgetTypeInfo("VITALS",      "VITALS",      Icons.Default.Dns,           "Head Unit Health / Vitals"),
    WidgetTypeInfo("TRIP_TRACKER", "TRIP TRACKER", Icons.Default.Map,          "Trip logs & stats"),
    WidgetTypeInfo("SOUNDBOARD",  "SOUNDBOARD",  Icons.Default.Piano,         "Custom sound pads"),
    WidgetTypeInfo("MAP", "MAP", Icons.Default.Map, "Live GPS map"),
    WidgetTypeInfo("PIP", "PIP", Icons.Default.Cast, "Picture in Picture"),
    WidgetTypeInfo("ANDROID_WIDGET", "ANDROID", Icons.Default.Layers, "System App Widget")
)

private fun canAddWidget(settings: AppSettings): Boolean {
    val visibleIds = settings.activeWidgetIds()
    val activeWidgets = settings.widgetLayout.filter { it.enabled && it.id in visibleIds }
    val occupied = buildSet<Pair<Int, Int>> {
        activeWidgets.forEach { w ->
            for (dx in 0 until w.spanX) for (dy in 0 until w.spanY) add(w.gridX + dx to w.gridY + dy)
        }
    }
    val hasFreeCell = (0 until GRID_ROWS).any { r ->
        (0 until GRID_COLS).any { c -> (c to r) !in occupied }
    }
    val hasShrinkable = activeWidgets.any { it.spanX * it.spanY > 1 }
    return hasFreeCell || hasShrinkable
}

@Composable
fun HomeScreen(
    settings: AppSettings,
    weather: WeatherState?,
    nowPlaying: NowPlayingState?,
    location: LocationData?,
    bearing: Float,
    isDayMode: Boolean = false,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLaunchCarPlay: () -> Unit,
    onLaunchAndroidAuto: () -> Unit,
    onAssignCarPlay: () -> Unit,
    onAssignAndroidAuto: () -> Unit,
    onClearCarPlay: () -> Unit,
    onClearAndroidAuto: () -> Unit,
    onAssignPip: () -> Unit,
    onClearPip: () -> Unit,
    onLaunchPip: () -> Unit,
    onAddAndroidWidget: () -> Unit,
    onTapNowPlaying: () -> Unit,
    onUpdateWidget: (id: String, spanX: Int, spanY: Int) -> Unit,
    onMoveWidget: (id: String, gridX: Int, gridY: Int) -> Unit,
    onAddWidget: (id: String) -> Unit,
    onRemoveWidget: (id: String) -> Unit,
    onSetClockStyle: (ClockStyle) -> Unit,
    onSetVitalsAsBars: (Boolean) -> Unit = {},
    onSetSpeedometerDigitalOnly: (Boolean) -> Unit = {},
    onUpdateSoundPad: (index: Int, pad: com.openlauncher.app.data.SoundPadConfig) -> Unit = { _, _ -> },
    hardwareRadio: com.openlauncher.app.viewmodel.LauncherViewModel.HardwareRadioState? = null,
    onLaunchHardwareRadio: () -> Unit = {},
    onStopHardwareRadio: () -> Unit = {},
    onRadioSeekUp: () -> Unit = {},
    onRadioSeekDown: () -> Unit = {},
    onRadioCycleFm: () -> Unit = {},
    onRadioSwitchAm: () -> Unit = {},
    onRadioTune: (band: String, freq: Float) -> Unit = { _, _ -> },
    onAssignRadio: () -> Unit = {},
    onToggleMapProvider: () -> Unit,
    onToggleTraffic: () -> Unit = {},
    onSetMapType: (com.openlauncher.app.data.MapType) -> Unit = {},
    appWidgetHost: android.appwidget.AppWidgetHost,
    editMode: Boolean = false,
    isOverlayOpen: Boolean = false,
    onToggleEditMode: () -> Unit = {},
    widgetLibraryOpen: Boolean = false,
    onSetWidgetLibraryOpen: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent       = Color(settings.accentColor)
    val gap          = 6.dp
    val widgetShape  = MaterialTheme.shapes.large
    val hasWallpaper = settings.wallpaperUri.isNotEmpty()
    val widgetBg     = when {
        isDayMode    -> Color(0xFFFFFFFF)
        hasWallpaper -> Color(0xCC000000)
        else         -> Color.Black.copy(alpha = 0.35f)
    }
    val widgetBorder = when {
        isDayMode    -> Color(0xFFCCCCCC)
        hasWallpaper -> Color(0x22FFFFFF)
        else         -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    }

    var resizingId    by remember { mutableStateOf<String?>(null) }
    var contextMenuId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(gap)
        ) {
            val scope = this
            val cellW = remember(scope.maxWidth, gap) { (scope.maxWidth - gap * (GRID_COLS - 1)) / GRID_COLS }
            val cellH = remember(scope.maxHeight, gap) { (scope.maxHeight - gap * (GRID_ROWS - 1)) / GRID_ROWS }
            val density = LocalDensity.current
            val cellStepXPx = remember(density, cellW, gap) { with(density) { (cellW + gap).toPx() } }
            val cellStepYPx = remember(density, cellH, gap) { with(density) { (cellH + gap).toPx() } }

            val visibleIds = remember(settings) { settings.activeWidgetIds() }

            val visible = remember(settings.widgetLayout, visibleIds) {
                settings.widgetLayout.filter { it.enabled && it.id in visibleIds }
            }

            var draggingId   by remember { mutableStateOf<String?>(null) }
            var dragOffsetPx by remember { mutableStateOf(Offset.Zero) }

            val draggingOriginal = remember(draggingId, visible) {
                if (draggingId != null) visible.find { it.id == draggingId } else null
            }
            val targetGridX = draggingOriginal?.let {
                (it.gridX + (dragOffsetPx.x / cellStepXPx).roundToInt()).coerceIn(0, GRID_COLS - it.spanX)
            }
            val targetGridY = draggingOriginal?.let {
                (it.gridY + (dragOffsetPx.y / cellStepYPx).roundToInt()).coerceIn(0, GRID_ROWS - it.spanY)
            }

            val proposedLayout = remember(draggingOriginal, targetGridX, targetGridY) {
                if (draggingOriginal != null && targetGridX != null && targetGridY != null)
                    computeWidgetMove(visible, draggingOriginal.id, targetGridX, targetGridY)
                else null
            }

            if (draggingOriginal != null && targetGridX != null && targetGridY != null) {
                val gX = (cellW + gap) * targetGridX
                val gY = (cellH + gap) * targetGridY
                val gW = cellW * draggingOriginal.spanX + gap * (draggingOriginal.spanX - 1)
                val gH = cellH * draggingOriginal.spanY + gap * (draggingOriginal.spanY - 1)
                Box(
                    modifier = Modifier
                        .absoluteOffset(x = gX, y = gY)
                        .size(gW, gH)
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.5f), widgetShape)
                )
            }

            if (proposedLayout != null && draggingOriginal != null) {
                proposedLayout
                    .filter { it.id != draggingOriginal.id }
                    .forEach { proposed ->
                        val original = visible.find { it.id == proposed.id } ?: return@forEach
                        if (proposed.gridX != original.gridX || proposed.gridY != original.gridY) {
                            val dX = (cellW + gap) * proposed.gridX
                            val dY = (cellH + gap) * proposed.gridY
                            val dW = cellW * proposed.spanX + gap * (proposed.spanX - 1)
                            val dH = cellH * proposed.spanY + gap * (proposed.spanY - 1)
                            Box(
                                modifier = Modifier
                                    .absoluteOffset(x = dX, y = dY)
                                    .size(dW, dH)
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), widgetShape)
                            )
                        }
                    }
            }

            visible.forEach { w ->
                WidgetItem(
                    w = w,
                    cellW = cellW,
                    cellH = cellH,
                    gap = gap,
                    editMode = editMode,
                    draggingId = draggingId,
                    dragOffsetPx = dragOffsetPx,
                    widgetShape = widgetShape,
                    widgetBg = widgetBg,
                    widgetBorder = widgetBorder,
                    weather = weather,
                    settings = settings,
                    accent = accent,
                    isDayMode = isDayMode,
                    nowPlaying = nowPlaying,
                    location = location,
                    bearing = bearing,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrev = onPrev,
                    onLaunchCarPlay = onLaunchCarPlay,
                    onLaunchAndroidAuto = onLaunchAndroidAuto,
                    onTapNowPlaying = onTapNowPlaying,
                    onMoveWidget = onMoveWidget,
                    onUpdateSoundPad = onUpdateSoundPad,
                    hardwareRadio = hardwareRadio,
                    onLaunchHardwareRadio = onLaunchHardwareRadio,
                    onStopHardwareRadio = onStopHardwareRadio,
                    onRadioSeekUp = onRadioSeekUp,
                    onRadioSeekDown = onRadioSeekDown,
                    onRadioCycleFm = onRadioCycleFm,
                    onRadioSwitchAm = onRadioSwitchAm,
                    onRadioTune = onRadioTune,
                    onAssignRadio = onAssignRadio,
                    onToggleMapProvider = onToggleMapProvider,
                    onToggleTraffic = onToggleTraffic,
                    onSetMapType = onSetMapType,
                    appWidgetHost = appWidgetHost,
                    onLongClick = { contextMenuId = it },
                    onDragStart = { draggingId = it },
                    onDragUpdate = { dragOffsetPx = it },
                    onDragEnd = { draggingId = null; dragOffsetPx = Offset.Zero },
                    cellStepXPx = cellStepXPx,
                    cellStepYPx = cellStepYPx,
                    isOverlayOpen = isOverlayOpen
                )
            }
        }
    }

    contextMenuId?.let { id ->
        WidgetContextMenu(
            widgetId            = id,
            accent              = accent,
            clockStyle          = settings.clockStyle,
            vitalsAsBars        = settings.vitalsAsBars,
            speedometerDigitalOnly = settings.speedometerDigitalOnly,
            carPlayPackage      = settings.carPlayPackage,
            androidAutoPackage  = settings.androidAutoPackage,
            pipAppPackage       = settings.pipAppPackage,
            mapProvider         = settings.mapProvider,
            settings            = settings,
            isDayMode           = isDayMode,
            onResize            = { contextMenuId = null; resizingId = id },
            onAssignCarPlay     = { contextMenuId = null; onAssignCarPlay() },
            onAssignAndroidAuto = { contextMenuId = null; onAssignAndroidAuto() },
            onClearCarPlay      = { contextMenuId = null; onClearCarPlay() },
            onClearAndroidAuto  = { contextMenuId = null; onClearAndroidAuto() },
            onAssignPip         = { contextMenuId = null; onAssignPip() },
            onClearPip          = { contextMenuId = null; onClearPip() },
            onSetClockStyle     = { onSetClockStyle(it) },
            onSetVitalsAsBars   = { onSetVitalsAsBars(it) },
            onSetSpeedometerDigitalOnly = { onSetSpeedometerDigitalOnly(it) },
            onRemove            = { onRemoveWidget(id) },
            onDismiss           = { contextMenuId = null },
            onToggleMapProvider = onToggleMapProvider,
            onToggleTraffic     = onToggleTraffic,
            onSetMapType        = onSetMapType
        )
    }

    resizingId?.let { id ->
        val config = settings.widgetLayout.find { it.id == id }
        if (config != null) {
            WidgetResizeDialog(
                config    = config,
                accent    = accent,
                isDayMode = isDayMode,
                onDismiss = { resizingId = null },
                onConfirm = { sx, sy ->
                    onUpdateWidget(id, sx, sy)
                    resizingId = null
                }
            )
        }
    }

    if (widgetLibraryOpen) {
        WidgetLibraryDialog(
            settings  = settings,
            accent    = accent,
            isDayMode = isDayMode,
            onAdd     = { id -> 
                if (id == "ANDROID_WIDGET") onAddAndroidWidget()
                else onAddWidget(id) 
            },
            onRemove  = { id -> onRemoveWidget(id) },
            onDismiss = { onSetWidgetLibraryOpen(false) }
        )
    }
}

@Composable
private fun WidgetItem(
    w: WidgetConfig,
    cellW: androidx.compose.ui.unit.Dp,
    cellH: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
    editMode: Boolean,
    draggingId: String?,
    dragOffsetPx: Offset,
    widgetShape: androidx.compose.ui.graphics.Shape,
    widgetBg: Color,
    widgetBorder: Color,
    weather: WeatherState?,
    settings: AppSettings,
    accent: Color,
    isDayMode: Boolean,
    nowPlaying: NowPlayingState?,
    location: LocationData?,
    bearing: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLaunchCarPlay: () -> Unit,
    onLaunchAndroidAuto: () -> Unit,
    onTapNowPlaying: () -> Unit,
    onMoveWidget: (id: String, gridX: Int, gridY: Int) -> Unit,
    onUpdateSoundPad: (index: Int, pad: com.openlauncher.app.data.SoundPadConfig) -> Unit,
    hardwareRadio: com.openlauncher.app.viewmodel.LauncherViewModel.HardwareRadioState?,
    onLaunchHardwareRadio: () -> Unit,
    onStopHardwareRadio: () -> Unit,
    onRadioSeekUp: () -> Unit,
    onRadioSeekDown: () -> Unit,
    onRadioCycleFm: () -> Unit,
    onRadioSwitchAm: () -> Unit,
    onRadioTune: (band: String, freq: Float) -> Unit,
    onAssignRadio: () -> Unit,
    onToggleMapProvider: () -> Unit,
    onToggleTraffic: () -> Unit,
    onSetMapType: (com.openlauncher.app.data.MapType) -> Unit,
    appWidgetHost: android.appwidget.AppWidgetHost,
    onLongClick: (String) -> Unit,
    onDragStart: (String) -> Unit,
    onDragUpdate: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    cellStepXPx: Float,
    cellStepYPx: Float,
    isOverlayOpen: Boolean = false
) {
    val xOff   = (cellW + gap) * w.gridX
    val yOff   = (cellH + gap) * w.gridY
    val width  = cellW * w.spanX + gap * (w.spanX - 1)
    val height = cellH * w.spanY + gap * (w.spanY - 1)

    val isDragging = draggingId == w.id
    val isGhost    = w.id == "WEATHER" && weather == null && !editMode
    val density    = LocalDensity.current
    val dragDpX    = if (isDragging) with(density) { dragOffsetPx.x.toDp() } else 0.dp
    val dragDpY    = if (isDragging) with(density) { dragOffsetPx.y.toDp() } else 0.dp

    Box(
        modifier = Modifier
            .absoluteOffset(x = xOff + dragDpX, y = yOff + dragDpY)
            .size(width, height)
            .zIndex(if (isDragging) 1f else 0f)
            .clip(widgetShape)
            .background(if (isGhost) Color.Transparent else widgetBg)
            .border(
                width = if (editMode) 1.5.dp else 1.dp,
                color = when {
                    editMode -> accent.copy(alpha = 0.45f)
                    isGhost  -> Color.Transparent
                    else     -> widgetBorder
                },
                shape = widgetShape
            )
            .then(
                if (editMode) {
                    Modifier.clickable { onLongClick(w.id) }
                } else {
                    Modifier.pointerInput(w.id) {
                        detectTapGestures(onLongPress = { onLongClick(w.id) })
                    }
                }
            )
            .then(
                if (editMode) Modifier.pointerInput(w.id) {
                    var hasSignificantDrag = false
                    val slop = viewConfiguration.touchSlop
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart(w.id) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragUpdate(dragAmount)
                            hasSignificantDrag = true
                        },
                        onDragEnd = {
                            if (hasSignificantDrag) {
                                val newX = (w.gridX + (dragOffsetPx.x / cellStepXPx).roundToInt())
                                    .coerceIn(0, GRID_COLS - w.spanX)
                                val newY = (w.gridY + (dragOffsetPx.y / cellStepYPx).roundToInt())
                                    .coerceIn(0, GRID_ROWS - w.spanY)
                                onMoveWidget(w.id, newX, newY)
                            } else {
                                onLongClick(w.id)
                            }
                            onDragEnd()
                        },
                        onDragCancel = { onDragEnd() }
                    )
                } else Modifier
            )
    ) {
        when (w.id) {
            "CLOCK" -> ClockWidget(style = settings.clockStyle, accent = accent, isDayMode = isDayMode, modifier = Modifier.fillMaxSize())
            "WEATHER" -> WeatherWidget(state = weather, accent = accent, metric = settings.unitSystem.name == "METRIC", isDayMode = isDayMode, modifier = Modifier.fillMaxSize())
            "NOW_PLAYING" -> NowPlayingWidget(state = nowPlaying, accent = accent, carPlayPackage = settings.carPlayPackage, androidAutoPackage = settings.androidAutoPackage, onPlayPause = onPlayPause, onNext = onNext, onPrev = onPrev, onLaunchCarPlay = onLaunchCarPlay, onLaunchAndroidAuto = onLaunchAndroidAuto, onTapToOpenApp = onTapNowPlaying, modifier = Modifier.fillMaxSize(), isEditing = editMode, isDayMode = isDayMode, hardwareRadio = hardwareRadio, onLaunchHardwareRadio = onLaunchHardwareRadio, onStopHardwareRadio = onStopHardwareRadio, onRadioSeekUp = onRadioSeekUp, onRadioSeekDown = onRadioSeekDown, onRadioCycleFm = onRadioCycleFm, onRadioSwitchAm = onRadioSwitchAm, onRadioTune = onRadioTune, onAssignRadio = onAssignRadio, coverBackground = settings.coverBackground)
            "TELEMETRY" -> TelemetryWidget(location = location, bearing = (bearing + settings.compassOffset + 360f) % 360f, accent = accent, isDayMode = isDayMode, modifier = Modifier.fillMaxSize())
            "ALTIMETER" -> AltimeterWidget(location = location, isMetric  = settings.unitSystem == com.openlauncher.app.data.UnitSystem.METRIC, accent = accent, isDayMode = isDayMode, modifier = Modifier.fillMaxSize())
            "SPEEDOMETER" -> SpeedometerWidget(packageName = settings.carAppPackage, location = location, isMetric  = settings.unitSystem == com.openlauncher.app.data.UnitSystem.METRIC, accent = accent, isDayMode = isDayMode, digitalOnly = settings.speedometerDigitalOnly, vehicleImage = settings.vehicleImage, modifier = Modifier.fillMaxSize())
            "VITALS" -> VitalsWidget(accent = accent, isDayMode = isDayMode, asBars = settings.vitalsAsBars, modifier = Modifier.fillMaxSize())
            "TRIP_TRACKER" -> TripTrackerWidget(location = location, isMetric  = settings.unitSystem == com.openlauncher.app.data.UnitSystem.METRIC, accent = accent, isDayMode = isDayMode, modifier = Modifier.fillMaxSize())
            "SOUNDBOARD" -> SoundboardWidget(pads = settings.soundboardPads, accent = accent, isDayMode = isDayMode, isEditing = editMode, onUpdatePad = onUpdateSoundPad, modifier = Modifier.fillMaxSize())
            "MAP" -> MapWidget(location = location, bearing = (bearing + settings.compassOffset + 360f) % 360f, mapProvider = settings.mapProvider, mapType = settings.mapType, showTraffic = settings.showTraffic, accent = accent, isDayMode = isDayMode, editMode = editMode, onToggleProvider = onToggleMapProvider, onToggleTraffic = onToggleTraffic, onLongClick = { onLongClick(w.id) }, modifier = Modifier.fillMaxSize())
            "PIP" -> PipWidget(packageName = settings.pipAppPackage, pipDpiCustom = settings.pipDpi, isOverlayOpen = isOverlayOpen, modifier = Modifier.fillMaxSize())
            else -> if (w.appWidgetId != null) {
                AndroidWidgetView(appWidgetId = w.appWidgetId, host = appWidgetHost, modifier = Modifier.fillMaxSize())
                if (editMode) {
                    // Overlay to capture touches in edit mode, as AndroidView consumes them
                    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun AndroidWidgetView(
    appWidgetId: Int,
    host: android.appwidget.AppWidgetHost,
    modifier: Modifier = Modifier
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (info != null) {
                host.createView(context, appWidgetId, info).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            } else {
                android.view.View(context).apply {
                    setBackgroundColor(android.graphics.Color.DKGRAY)
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun WidgetContextMenu(
    widgetId: String,
    accent: Color,
    clockStyle: ClockStyle,
    vitalsAsBars: Boolean,
    speedometerDigitalOnly: Boolean,
    carPlayPackage: String = "",
    androidAutoPackage: String = "",
    pipAppPackage: String = "",
    mapProvider: com.openlauncher.app.data.MapProvider,
    settings: AppSettings,
    isDayMode: Boolean,
    onResize: () -> Unit,
    onAssignCarPlay: () -> Unit,
    onAssignAndroidAuto: () -> Unit,
    onClearCarPlay: () -> Unit,
    onClearAndroidAuto: () -> Unit,
    onAssignPip: () -> Unit,
    onClearPip: () -> Unit,
    onSetClockStyle: (ClockStyle) -> Unit,
    onSetVitalsAsBars: (Boolean) -> Unit,
    onSetSpeedometerDigitalOnly: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    onToggleMapProvider: () -> Unit,
    onToggleTraffic: () -> Unit = {},
    onSetMapType: (com.openlauncher.app.data.MapType) -> Unit = {}
) {
    val menuBg    = if (isDayMode) Color(0xFFFFFFFF) else Color(0xFF111111)
    val menuBorder = if (isDayMode) Color(0xFFDDE1E5) else Color(0xFF1E1E1E)
    val menuDivider = if (isDayMode) Color(0xFFF1F3F5) else Color(0xFF1A1A1A)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(menuBg)
                .border(1.dp, menuBorder, RoundedCornerShape(4.dp))
                .padding(vertical = 4.dp)
                .width(200.dp)
        ) {
            val inactiveMenuTint = if (isDayMode) Color(0xFF777777) else Color(0xFF555555)
            ContextRow("RESIZE", Icons.Default.OpenWith, accent, onResize, isDayMode = isDayMode)
            if (widgetId == "CLOCK") {
                HorizontalDivider(color = menuDivider)
                ContextRow("DIGITAL", Icons.Default.Schedule, if (clockStyle == ClockStyle.DIGITAL) accent else inactiveMenuTint, { onSetClockStyle(ClockStyle.DIGITAL); onDismiss() }, isDayMode)
                HorizontalDivider(color = menuDivider)
                ContextRow("ANALOG", Icons.Default.Watch, if (clockStyle == ClockStyle.ANALOG) accent else inactiveMenuTint, { onSetClockStyle(ClockStyle.ANALOG); onDismiss() }, isDayMode)
            }
            if (widgetId == "VITALS") {
                HorizontalDivider(color = menuDivider)
                ContextRow("DIAL GAUGES", Icons.Default.Adjust, if (!vitalsAsBars) accent else inactiveMenuTint, { onSetVitalsAsBars(false); onDismiss() }, isDayMode)
                HorizontalDivider(color = menuDivider)
                ContextRow("BARS VIEW", Icons.Default.FormatAlignLeft, if (vitalsAsBars) accent else inactiveMenuTint, { onSetVitalsAsBars(true); onDismiss() }, isDayMode)
            }
            if (widgetId == "SPEEDOMETER") {
                HorizontalDivider(color = menuDivider)
                ContextRow("DIAL TRACK", Icons.Default.Speed, if (!speedometerDigitalOnly) accent else inactiveMenuTint, { onSetSpeedometerDigitalOnly(false); onDismiss() }, isDayMode)
                HorizontalDivider(color = menuDivider)
                ContextRow("DIGITAL ONLY", Icons.Default.Dialpad, if (speedometerDigitalOnly) accent else inactiveMenuTint, { onSetSpeedometerDigitalOnly(true); onDismiss() }, isDayMode)
            }
            if (widgetId == "NOW_PLAYING") {
                HorizontalDivider(color = menuDivider)
                ContextRow("ASSIGN CARPLAY APP", Icons.Default.PhoneAndroid, accent, onAssignCarPlay, isDayMode)
                if (carPlayPackage.isNotEmpty()) {
                    HorizontalDivider(color = menuDivider)
                    ContextRow("CLEAR CARPLAY APP", Icons.Default.PhoneAndroid, Color(0xFF884444), onClearCarPlay, isDayMode)
                }
                HorizontalDivider(color = menuDivider)
                ContextRow("ASSIGN ANDROID AUTO APP", Icons.Default.DirectionsCar, accent, onAssignAndroidAuto, isDayMode)
                if (androidAutoPackage.isNotEmpty()) {
                    HorizontalDivider(color = menuDivider)
                    ContextRow("CLEAR ANDROID AUTO APP", Icons.Default.DirectionsCar, Color(0xFF884444), onClearAndroidAuto, isDayMode)
                }
            }
            if (widgetId == "MAP") {
                HorizontalDivider(color = menuDivider)
                ContextRow(if (mapProvider == com.openlauncher.app.data.MapProvider.GOOGLE) "SWITCH TO OSM" else "SWITCH TO GOOGLE", Icons.Default.Map, accent, { onToggleMapProvider(); onDismiss() }, isDayMode)
                if (mapProvider == com.openlauncher.app.data.MapProvider.GOOGLE) {
                    HorizontalDivider(color = menuDivider)
                    ContextRow(if (settings.mapType == com.openlauncher.app.data.MapType.ROADMAP) "SATELLITE VIEW" else "ROADMAP VIEW", Icons.Default.Layers, accent, { onSetMapType(if (settings.mapType == com.openlauncher.app.data.MapType.ROADMAP) com.openlauncher.app.data.MapType.HYBRID else com.openlauncher.app.data.MapType.ROADMAP); onDismiss() }, isDayMode)
                    HorizontalDivider(color = menuDivider)
                    ContextRow(if (settings.showTraffic) "HIDE TRAFFIC" else "SHOW TRAFFIC", Icons.Default.Traffic, if (settings.showTraffic) accent else inactiveMenuTint, { onToggleTraffic(); onDismiss() }, isDayMode)
                }
            }
            
            HorizontalDivider(color = menuDivider)
            ContextRow("REMOVE WIDGET", Icons.Default.Delete, Color(0xFF884444), { onRemove(); onDismiss() }, isDayMode)
        }
    }
}

@Composable
private fun ContextRow(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit, isDayMode: Boolean = false) {
    val finalTint = if (isDayMode) (if (tint == Color(0xFF884444)) tint else if (tint == Color(0xFF777777)) Color(0xFF888888) else Color(0xFF111111)) else tint
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = finalTint, modifier = Modifier.size(16.dp))
        Text(label, color = finalTint, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun WidgetResizeDialog(config: WidgetConfig, accent: Color, isDayMode: Boolean, onDismiss: () -> Unit, onConfirm: (spanX: Int, spanY: Int) -> Unit) {
    var spanX by remember { mutableStateOf(config.spanX) }
    var spanY by remember { mutableStateOf(config.spanY) }
    val maxSpanX = GRID_COLS - config.gridX
    val maxSpanY = GRID_ROWS - config.gridY
    val dialogBg = if (isDayMode) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.background
    val dialogText = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val cancelColor = if (isDayMode) Color(0xFF6C757D) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(text = config.id.replace('_', ' '), color = dialogText, fontSize = 11.sp, letterSpacing = 2.sp) }, text = { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { SpanRow(label = "WIDTH",  value = spanX, min = 1, max = maxSpanX, accent = accent, isDayMode = isDayMode) { spanX = it }; SpanRow(label = "HEIGHT", value = spanY, min = 1, max = maxSpanY, accent = accent, isDayMode = isDayMode) { spanY = it } } }, confirmButton = { TextButton(onClick = { onConfirm(spanX, spanY) }) { Text("APPLY", color = accent, fontSize = 11.sp, letterSpacing = 1.sp) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = cancelColor, fontSize = 11.sp, letterSpacing = 1.sp) } }, containerColor = dialogBg, titleContentColor = dialogText, textContentColor = dialogText)
}

@Composable
private fun SpanRow(label: String, value: Int, min: Int, max: Int, accent: Color, isDayMode: Boolean, onChange: (Int) -> Unit) {
    val textColor = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val dimColor = if (isDayMode) Color(0xFF495057) else Color(0xFF666666)
    val disabledC = if (isDayMode) Color(0xFFCED4DA) else Color(0xFF333333)
    val inactiveBg = if (isDayMode) Color(0xFFE9ECEF) else Color(0xFF2A2A2A)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, color = dimColor, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.width(52.dp))
        IconButton(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, null, tint = if (value > min) textColor else disabledC, modifier = Modifier.size(16.dp)) }
        Text(text = "$value", color = textColor, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        IconButton(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, null, tint = if (value < max) accent else disabledC, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(max) { i -> Box(modifier = Modifier.size(width = 14.dp, height = 10.dp).background(if (i < value) accent.copy(alpha = 0.7f) else inactiveBg, RoundedCornerShape(1.dp))) } }
    }
}

@Composable
private fun WidgetLibraryDialog(settings: AppSettings, accent: Color, isDayMode: Boolean, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    val dialogBg = if (isDayMode) Color(0xFFEEEEEE) else Color(0xFF0C0C0C)
    val dialogBorder = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF1E1E1E)
    val titleColor = if (isDayMode) Color(0xFF495057) else Color(0xFF555555)
    val closeColor = if (isDayMode) Color(0xFF495057) else Color(0xFF444444)
    val activeIds = remember(settings) { settings.activeWidgetIds() }
    val canAdd = canAddWidget(settings)
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(dialogBg).border(1.dp, dialogBorder, RoundedCornerShape(4.dp)).padding(16.dp).widthIn(min = 320.dp, max = 520.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "WIDGET LIBRARY", color = titleColor, fontSize = 9.sp, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = closeColor, modifier = Modifier.size(14.dp)) }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(ALL_WIDGET_TYPES) { info ->
                    val isAndroidWidget = info.id == "ANDROID_WIDGET"
                    val isActive = if (isAndroidWidget) {
                        activeIds.any { it.startsWith("ANDROID_WIDGET_") }
                    } else {
                        info.id in activeIds
                    }
                    WidgetLibraryCard(
                        info = info, 
                        isActive = isActive, 
                        canAdd = canAdd, 
                        accent = accent, 
                        isDayMode = isDayMode, 
                        onToggle = { 
                            if (isAndroidWidget) onAdd(info.id)
                            else if (isActive) onRemove(info.id) 
                            else onAdd(info.id) 
                        }
                    )
                }
            }
            if (!canAdd) {
                Spacer(Modifier.height(10.dp))
                Text(text = "ALL CELLS OCCUPIED — REMOVE A WIDGET TO ADD MORE", color = if (isDayMode) Color(0xFFE03131) else Color(0xFF3A3A3A), fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WidgetLibraryCard(info: WidgetTypeInfo, isActive: Boolean, canAdd: Boolean, accent: Color, isDayMode: Boolean, onToggle: () -> Unit) {
    val enabled = isActive || canAdd
    val cardBorder = if (isActive) accent else if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF1A1A1A)
    val cardBg = if (isActive) accent.copy(alpha = 0.15f) else if (isDayMode) Color(0xFFFFFFFF) else Color(0xFF0E0E0E)
    val iconTint = if (isActive) accent else if (isDayMode) Color(0xFF495057) else Color(0xFF333333)
    val labelColor = if (isActive) accent else if (isDayMode) Color(0xFF212529) else Color(0xFF3A3A3A)
    Column(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(cardBg).border(1.dp, cardBorder, RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onToggle).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(info.icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(5.dp))
        Text(text = info.label, color = labelColor, fontSize = 7.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 9.sp)
        Spacer(Modifier.height(3.dp))
        Text(text = if (isActive) "ACTIVE" else if (!canAdd) "FULL" else "ADD", color = if (isActive) accent.copy(alpha = 0.75f) else if (isDayMode) Color(0xFFADB5BD) else Color(0xFF282828), fontSize = 6.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}
