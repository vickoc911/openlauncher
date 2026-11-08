package com.openlauncher.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import kotlin.math.roundToInt
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlauncher.app.BuildConfig
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.ShortcutConfig
import com.openlauncher.app.service.MediaListenerService
import com.openlauncher.app.ui.components.ColorPickerDialog
import com.openlauncher.app.ui.components.ConfirmDialog
import com.openlauncher.app.ui.theme.LocalDayMode
import com.openlauncher.app.util.FileLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    accent: Color,
    onUpdate: (AppSettings.() -> AppSettings) -> Unit,
    onReset: () -> Unit,
    onAssignAutostart: (Int) -> Unit,
    onClearAutostart: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showResetDialog     by remember { mutableStateOf(false) }
    var showAccentPicker    by remember { mutableStateOf(false) }
    var showFontColorPicker by remember { mutableStateOf(false) }

    val isDayMode = LocalDayMode.current
    val screenBg  = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Title ────────────────────────────────────────────────────────────
        Text(
            text          = "SETTINGS",
            style         = MaterialTheme.typography.titleLarge,
            color         = if (isDayMode) Color(0xFF111111) else accent,
            letterSpacing = 3.sp,
            fontSize      = 14.sp
        )

        Spacer(Modifier.height(4.dp))

        // ── Permissions ──────────────────────────────────────────────────────
        SettingsSection("Permissions") {
            val isMediaConnected by MediaListenerService.isConnected.collectAsState()

            var permissionRefresh by remember { mutableIntStateOf(0) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val hasLocation = remember(permissionRefresh) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
            val isDefaultLauncher = remember(permissionRefresh) {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager.resolveActivity(
                    home, PackageManager.MATCH_DEFAULT_ONLY
                )?.activityInfo?.packageName == context.packageName
            }

            val homeRoleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { permissionRefresh++ }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionRefresh++ }

            SettingsButton(
                label    = "Set as Default Launcher",
                sublabel = if (isDefaultLauncher) "Active — Open Launcher is the home app"
                           else "Required so the head unit boots into Open Launcher",
                icon     = Icons.Default.Home,
                accent   = if (isDefaultLauncher) accent else Color(0xFF993333),
                onClick  = {
                    var launched = false
                    if (Build.VERSION.SDK_INT >= 29) {
                        val rm = context.getSystemService(RoleManager::class.java)
                        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                            !rm.isRoleHeld(RoleManager.ROLE_HOME)
                        ) {
                            launched = runCatching {
                                homeRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                            }.isSuccess
                        }
                    }
                    if (!launched) {
                        launched = runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                    }
                    if (!launched) {
                        runCatching {
                            context.startActivity(
                                Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            )
            SettingsDivider()
            SettingsButton(
                label    = "Notification Access",
                sublabel = if (isMediaConnected) "Granted — media controls active" else "Required for Now Playing controls",
                icon     = if (isMediaConnected) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                accent   = if (isMediaConnected) accent else Color(0xFF993333),
                onClick  = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            SettingsDivider()
            SettingsButton(
                label    = "Location Access",
                sublabel = if (hasLocation) "Granted — GPS & weather active" else "Required for weather and GPS",
                icon     = if (hasLocation) Icons.Default.LocationOn else Icons.Default.LocationOff,
                accent   = if (hasLocation) accent else Color(0xFF993333),
                onClick  = {
                    if (!hasLocation) {
                        locationPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            )
        }

        // ── Startup ──────────────────────────────────────────────────────────
        SettingsSection("Startup") {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                SettingsRow(
                    label    = "Autostart Delay",
                    sublabel = "${settings.autostartDelay} seconds — wait time before launching apps",
                    icon     = Icons.Default.Timer
                ) {}
                Slider(
                    value         = settings.autostartDelay.toFloat(),
                    onValueChange = { onUpdate { copy(autostartDelay = it.toInt()) } },
                    valueRange    = 2f..20f,
                    steps         = 17,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            SettingsDivider()

            SettingsRow(
                label    = "Play Media on Boot",
                sublabel = if (settings.playMediaOnBoot) "Enabled — plays last media when launcher starts" else "Disabled",
                icon     = Icons.Default.MusicNote,
                onClick  = { onUpdate { copy(playMediaOnBoot = !playMediaOnBoot) } }
            ) {
                Switch(
                    checked         = settings.playMediaOnBoot,
                    onCheckedChange = { onUpdate { copy(playMediaOnBoot = it) } },
                    colors          = switchColors(accent)
                )
            }

            SettingsDivider()

            repeat(4) { index ->
                val pkg = settings.autostartPackages.getOrNull(index).orEmpty()
                if (index > 0) SettingsDivider()
                SettingsButton(
                    label    = "Autostart App ${index + 1}",
                    sublabel = if (pkg.isNotEmpty()) "Launch $pkg on boot"
                    else "No app selected",
                    icon     = Icons.Default.Launch,
                    accent   = accent,
                    onClick  = { onAssignAutostart(index) }
                )
                if (pkg.isNotEmpty()) {
                    TextButton(
                        onClick  = { onClearAutostart(index) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("CLEAR SLOT ${index + 1}", color = Color(0xFF993333), fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }

        // ── Layout Dimensions ──────────────────────────────────────────────────
        SettingsSection("Layout Dimensions") {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                SettingsRow(
                    label    = "Sidebar Width",
                    sublabel = "${settings.sidebarWidthDp} dp",
                    icon     = Icons.Default.SwapHoriz
                ) {}
                Slider(
                    value         = settings.sidebarWidthDp.toFloat(),
                    onValueChange = { onUpdate { copy(sidebarWidthDp = it.toInt()) } },
                    valueRange    = 50f..120f,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            SettingsDivider()

            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                SettingsRow(
                    label    = "Bottom Bar Height",
                    sublabel = "${settings.bottomBarHeightDp} dp",
                    icon     = Icons.Default.FormatAlignRight
                ) {}
                Slider(
                    value         = settings.bottomBarHeightDp.toFloat(),
                    onValueChange = { onUpdate { copy(bottomBarHeightDp = it.toInt()) } },
                    valueRange    = 50f..120f,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }

        // ── Sidebar Shortcuts ─────────────────────────────────────────────────
        SettingsSection("Sidebar Shortcuts") {
            settings.shortcuts.forEachIndexed { index, shortcut ->
                if (index > 0) SettingsDivider()
                SettingsRow(
                    label    = "Slot ${index + 1}",
                    sublabel = when {
                        shortcut.label.isNotEmpty()       -> shortcut.label
                        shortcut.packageName.isNotEmpty() -> shortcut.packageName
                        else                              -> "Empty"
                    },
                    icon     = Icons.Default.Apps
                ) {
                    if (settings.shortcuts.size > 1) {
                        IconButton(
                            onClick  = {
                                onUpdate {
                                    copy(shortcuts = shortcuts.toMutableList().also { it.removeAt(index) })
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF993333), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            SettingsDivider()

            SettingsButton(
                label    = "Add Slot",
                sublabel = "Append an empty shortcut to the sidebar",
                icon     = Icons.Default.Add,
                accent   = accent,
                onClick  = { onUpdate { copy(shortcuts = shortcuts + ShortcutConfig()) } }
            )
        }

        // ── Appearance ───────────────────────────────────────────────────────
        SettingsSection("Appearance") {
            // Display Mode
            SettingsRow(
                label    = "Display Mode",
                sublabel = when (settings.dayNightMode) {
                    DayNightMode.DARK   -> "Always dark"
                    DayNightMode.LIGHT  -> "Always light"
                    DayNightMode.AUTO   -> "Sunrise / sunset"
                    DayNightMode.SYSTEM -> "Follows system theme"
                },
                icon = when (settings.dayNightMode) {
                    DayNightMode.DARK   -> Icons.Default.NightlightRound
                    DayNightMode.LIGHT  -> Icons.Default.LightMode
                    DayNightMode.AUTO   -> Icons.Default.Brightness4
                    DayNightMode.SYSTEM -> Icons.Default.PhoneAndroid
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayNightMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.dayNightMode == mode,
                            onClick  = { onUpdate { copy(dayNightMode = mode) } },
                            label    = {
                                Text(
                                    text      = when (mode) {
                                        DayNightMode.DARK   -> "Dark"
                                        DayNightMode.LIGHT  -> "Light"
                                        DayNightMode.AUTO   -> "Sunset"
                                        DayNightMode.SYSTEM -> "System"
                                    },
                                    fontSize  = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor     = Color.Black
                            )
                        )
                    }
                }
            }

            SettingsDivider()

            // Accent color
            SettingsRow(
                label    = "Accent Color",
                sublabel = "UI highlight color",
                icon     = Icons.Default.Palette
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(settings.accentColor))
                        .clickable { showAccentPicker = true }
                )
            }

            SettingsDivider()

            // Font Color row
            SettingsRow(
                label    = "Font Color",
                sublabel = "Custom text color in dark mode",
                icon     = Icons.Default.FormatSize
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(settings.fontColor))
                        .clickable { showFontColorPicker = true }
                )
            }
        }

        // ── Typography ───────────────────────────────────────────────────────
        SettingsSection("Typography") {
            SettingsRow(label = "Bold Font", sublabel = "Heavier weight across all text", icon = Icons.Default.FormatBold) {
                Switch(
                    checked         = settings.fontBold,
                    onCheckedChange = { onUpdate { copy(fontBold = it) } },
                    colors          = switchColors(accent)
                )
            }

            SettingsDivider()

            Column {
                SettingsRow(
                    label    = "Text Scale",
                    sublabel = "${"%.0f".format(settings.textScale * 100)}%",
                    icon     = Icons.Default.TextFields
                ) {}
                Slider(
                    value         = settings.textScale,
                    onValueChange = { onUpdate { copy(textScale = it) } },
                    valueRange    = 0.8f..1.4f,
                    steps         = 5,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            SettingsDivider()

            Column {
                SettingsRow(
                    label    = "UI Scale",
                    sublabel = "${"%.0f".format(settings.uiScale * 100)}%  — scales all elements",
                    icon     = Icons.Default.ZoomIn
                ) {}
                Slider(
                    value         = settings.uiScale,
                    onValueChange = { onUpdate { copy(uiScale = it) } },
                    valueRange    = 0.7f..1.5f,
                    steps         = 7,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }

        // ── OBD2 / Vehicle Monitoring ─────────────────────────────────────────
        SettingsSection("OBD2 / Vehicle Monitoring") {
            SettingsRow(
                label    = "Enable OBD2 Connection",
                sublabel = "Auto-connect to ELM327 / Vlink via Bluetooth",
                icon     = Icons.Default.DirectionsCar
            ) {
                Switch(
                    checked         = settings.obdEnabled,
                    onCheckedChange = { onUpdate { copy(obdEnabled = it) } },
                    colors          = switchColors(accent)
                )
            }

            if (settings.obdEnabled) {
                SettingsDivider()

                val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
                @SuppressLint("MissingPermission")
                val pairedDevices = remember(bluetoothAdapter) {
                    try {
                        bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }

                SettingsRow(
                    label    = "Vlink Bluetooth Device",
                    sublabel = settings.obdDeviceName.ifEmpty { settings.obdDeviceAddress.ifEmpty { "Select Paired Device" } },
                    icon     = Icons.Default.Bluetooth
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = true,
                            onClick  = { expanded = true },
                            label    = { Text(settings.obdDeviceName.ifEmpty { "SELECT" }, fontSize = 10.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor = Color.Black
                            )
                        )
                        DropdownMenu(
                            expanded  = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (pairedDevices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No paired Bluetooth devices", fontSize = 11.sp) },
                                    onClick = { expanded = false }
                                )
                            } else {
                                pairedDevices.forEach { device ->
                                    @SuppressLint("MissingPermission")
                                    val name = device.name ?: "Unknown Device"
                                    val addr = device.address
                                    DropdownMenuItem(
                                        text = { Text("$name ($addr)", fontSize = 11.sp) },
                                        onClick = {
                                            onUpdate { copy(obdDeviceAddress = addr, obdDeviceName = name) }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsDivider()

                Column {
                    SettingsRow(
                        label    = "OBD Bar Width",
                        sublabel = "${settings.obdRightBarWidthDp} dp",
                        icon     = Icons.Default.FormatAlignRight
                    ) {}
                    Slider(
                        value         = settings.obdRightBarWidthDp.toFloat(),
                        onValueChange = { onUpdate { copy(obdRightBarWidthDp = it.roundToInt()) } },
                        valueRange    = 60f..120f,
                        steps         = 5,
                        colors        = sliderColors(accent),
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }

                SettingsDivider()

                SettingsRow(label = "Show DPF Temp Before DPF", sublabel = "Highlight accent color when > 500°C", icon = Icons.Default.Thermostat) {
                    Switch(
                        checked         = settings.showDpfTemp,
                        onCheckedChange = { onUpdate { copy(showDpfTemp = it) } },
                        colors          = switchColors(accent)
                    )
                }

                SettingsDivider()

                SettingsRow(label = "Show Soot Level", sublabel = "DPF soot accumulation %", icon = Icons.Default.FilterAlt) {
                    Switch(
                        checked         = settings.showSootLevel,
                        onCheckedChange = { onUpdate { copy(showSootLevel = it) } },
                        colors          = switchColors(accent)
                    )
                }

                SettingsDivider()

                SettingsRow(label = "Show Distance Since Last Clean", sublabel = "Distance in km since DPF regen", icon = Icons.Default.DirectionsCar) {
                    Switch(
                        checked         = settings.showLastRegenDist,
                        onCheckedChange = { onUpdate { copy(showLastRegenDist = it) } },
                        colors          = switchColors(accent)
                    )
                }

                SettingsDivider()

                SettingsRow(label = "Show Engine Coolant Temp", sublabel = "Engine coolant temperature in °C", icon = Icons.Default.Water) {
                    Switch(
                        checked         = settings.showCoolantTemp,
                        onCheckedChange = { onUpdate { copy(showCoolantTemp = it) } },
                        colors          = switchColors(accent)
                    )
                }

                SettingsDivider()

                SettingsRow(label = "Show Battery Voltage", sublabel = "Control module voltage in Volts", icon = Icons.Default.Bolt) {
                    Switch(
                        checked         = settings.showVoltage,
                        onCheckedChange = { onUpdate { copy(showVoltage = it) } },
                        colors          = switchColors(accent)
                    )
                }

                SettingsDivider()

                SettingsRow(label = "Show Trip Distance", sublabel = "Resettable trip odometer in km", icon = Icons.Default.Navigation) {
                    Switch(
                        checked         = settings.showTripDistance,
                        onCheckedChange = { onUpdate { copy(showTripDistance = it) } },
                        colors          = switchColors(accent)
                    )
                }

                SettingsDivider()

                SettingsRow(label = "Show Engine RPM", sublabel = "Engine tachometer speed in RPM", icon = Icons.Default.Speed) {
                    Switch(
                        checked         = settings.showEngineRpm,
                        onCheckedChange = { onUpdate { copy(showEngineRpm = it) } },
                        colors          = switchColors(accent)
                    )
                }
            }
        }

        // ── GPS & Calibration ───────────────────────────────────────────────
        SettingsSection("GPS & Calibration") {
            var calibrationStatus by remember { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()
            var isCalibratingCompass by remember { mutableStateOf(false) }
            var compassCountdown by remember { mutableIntStateOf(0) }

            // 1. Reset A-GPS Button
            SettingsButton(
                label    = "Reset A-GPS Assistance Data",
                sublabel = calibrationStatus ?: "Forces cold start to download fresh satellite orbits entirely offline",
                icon     = Icons.Default.MyLocation,
                accent   = accent,
                onClick  = {
                    calibrationStatus = "Clearing A-GPS cache..."
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    var success = false
                    try {
                        success = lm.sendExtraCommand(
                            LocationManager.GPS_PROVIDER, "delete_aiding_data",
                            Bundle()
                        )
                        lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
                        lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    calibrationStatus = if (success) {
                        "Cold start forced — go outdoors for a fresh satellite lock (2–3 min)"
                    } else {
                        "Not supported by this device's GPS driver — no data was cleared"
                    }
                }
            )
            
            SettingsDivider()
            
            SettingsButton(
                label    = "Magnetometer Sweep (Parking Lot)",
                sublabel = if (isCalibratingCompass) {
                    "Sweep active: Drive slowly in two 360° circles... (${compassCountdown}s remaining)"
                } else {
                    "Guided sweep — Android self-calibrates the compass while you circle"
                },
                icon     = Icons.Default.Navigation,
                accent   = if (isCalibratingCompass) Color.Green else accent,
                onClick  = {
                    if (!isCalibratingCompass) {
                        isCalibratingCompass = true
                        compassCountdown = 30
                        coroutineScope.launch {
                            while (compassCountdown > 0) {
                                delay(1000)
                                compassCountdown--
                            }
                            isCalibratingCompass = false
                            calibrationStatus = "Sweep complete — check heading"
                        }
                    }
                }
            )

            SettingsDivider()

            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                SettingsRow(
                    label    = "Compass Heading Offset",
                    sublabel = "Manual Alignment: ${if (settings.compassOffset >= 0) "+" else ""}${settings.compassOffset.toInt()}°  — aligns compass with vehicle front",
                    icon     = Icons.Default.Explore
                ) {}
                Slider(
                    value         = settings.compassOffset,
                    onValueChange = { onUpdate { copy(compassOffset = it) } },
                    valueRange    = -180f..180f,
                    steps         = 71,
                    colors        = sliderColors(accent),
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }

        // ── Maintenance ──────────────────────────────────────────────────────
        SettingsSection("Maintenance") {
            SettingsButton(
                label = "View Activity Logs",
                sublabel = "Debug info for PIP and system events",
                icon = Icons.Default.Description,
                accent = accent,
                onClick = {
                    val logFile = FileLogger.getLogFilePath(context)
                    Toast.makeText(context, "Logs at: $logFile", Toast.LENGTH_LONG).show()
                }
            )
            
            SettingsDivider()

            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = { showResetDialog = true },
                shape    = RoundedCornerShape(4.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0000)),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.RestartAlt, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset to Defaults", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text          = "v${BuildConfig.VERSION_NAME}  ·  Made by David Lam modded by Cherrypick6641  ·  2026",
            color         = if (isDayMode) Color(0xFFAAAAAA) else Color(0xFF2A2A2A),
            fontSize      = 10.sp,
            letterSpacing = 1.sp,
            modifier      = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }
    } // end Box

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showResetDialog) {
        ConfirmDialog(
            title        = "Reset Settings",
            message      = "Are you sure you want to reset all settings to default? This cannot be undone.",
            confirmLabel = "Reset",
            onConfirm    = { onReset(); showResetDialog = false },
            onDismiss    = { showResetDialog = false }
        )
    }

    if (showAccentPicker) {
        ColorPickerDialog(
            title           = "Accent Color",
            initialColor    = Color(settings.accentColor),
            onColorSelected = { c -> onUpdate { copy(accentColor = c.toArgb()) } },
            onDismiss       = { showAccentPicker = false }
        )
    }

    if (showFontColorPicker) {
        ColorPickerDialog(
            title           = "Font Color",
            initialColor    = Color(settings.fontColor),
            onColorSelected = { c -> onUpdate { copy(fontColor = c.toArgb()) } },
            onDismiss       = { showFontColorPicker = false }
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDayMode     = LocalDayMode.current
    val sectionColor  = if (isDayMode) Color(0xFF888888) else Color(0xFF3A3A3A)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (isDayMode) Color(0xFFF8F9FA) else Color(0xFF0A0A0A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text          = title.uppercase(),
            style         = MaterialTheme.typography.titleSmall,
            color         = sectionColor,
            letterSpacing = 2.sp,
            modifier      = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    sublabel: String = "",
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val isDayMode   = LocalDayMode.current
    val labelColor  = if (isDayMode) Color(0xFF111111) else Color(0xFFDDDDDD)
    val subColor    = if (isDayMode) Color(0xFF888888) else Color(0xFF444444)
    val iconTint    = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = labelColor, fontSize = 15.sp)
            if (sublabel.isNotEmpty())
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = subColor, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
private fun ColumnScope.SettingsButton(
    label: String,
    sublabel: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    val isDayMode  = LocalDayMode.current
    val labelColor = if (isDayMode) Color(0xFF111111) else Color(0xFFDDDDDD)
    val subColor   = if (isDayMode) Color(0xFF888888) else Color(0xFF444444)
    val chevronC   = if (isDayMode) Color(0xFFBBBBBB) else Color(0xFF2A2A2A)
    val iconTint   = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = labelColor, fontSize = 15.sp)
            if (sublabel.isNotEmpty())
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = subColor, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = chevronC, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ColumnScope.SettingsDivider() {
    // Hidden in expressive mode as sections use card containers
}

@Composable
private fun switchColors(accent: Color): SwitchColors {
    val isDayMode = LocalDayMode.current
    return SwitchDefaults.colors(
        checkedThumbColor    = if (isDayMode) Color.White else Color.Black,
        checkedTrackColor    = accent,
        uncheckedThumbColor  = if (isDayMode) Color(0xFFBBBBBB) else Color(0xFF888888),
        uncheckedTrackColor  = if (isDayMode) Color(0xFFDDDDDD) else Color(0xFF1E1E1E),
        uncheckedBorderColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF3A3A3A)
    )
}

@Composable
private fun sliderColors(accent: Color): SliderColors {
    val isDayMode = LocalDayMode.current
    return SliderDefaults.colors(
        thumbColor         = accent,
        activeTrackColor   = accent,
        inactiveTrackColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF2A2A2A)
    )
}
