package com.openlauncher.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.appwidget.AppWidgetManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.SidebarPosition
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.components.Sidebar
import com.openlauncher.app.ui.screen.*
import com.openlauncher.app.ui.theme.OpenLauncherTheme
import com.openlauncher.app.viewmodel.LauncherViewModel
import kotlinx.coroutines.delay
import me.weishu.reflection.Reflection

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

        // ─── REFLECTION PIP ──────────────────────────────────
        override fun attachBaseContext(newBase: Context) {
            super.attachBaseContext(newBase)
            try {
                // 1. Esto desbloquea las restricciones normales del SDK mediante FreeReflection
                Reflection.unseal(newBase)
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            try {
                // 2. Esto desactiva de raíz la lista negra de APIs ocultas de la VM de Android (Dalvik/ART)
                // indicándole con "L" que permita acceso total a todos los métodos ocultos.
                val setHiddenApiMethod = Class.forName("android.dalvik.system.VMRuntime")
                .getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                setHiddenApiMethod.invoke(null, arrayOf("L"))
            } catch (e: Throwable) {
                e.printStackTrace() // Fallará silenciosamente en emuladores, pero en las radios funcionará.
            }
        }

        // ─── CONTROL GLOBAL SYU PIP ──────────────────────────
        private fun setSyuEmbedded(enabled: Boolean) {
            try {
                val value = if (enabled) "1" else "0"
                Runtime.getRuntime().exec(arrayOf("setprop", "persist.launcher.embedded_window", value))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private val widgetPicker = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (result.resultCode == RESULT_OK) {
                if (appWidgetId != -1) {
                    val info = vm.appWidgetManager.getAppWidgetInfo(appWidgetId)
                    if (info?.configure != null) {
                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        intent.component = info.configure
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        widgetConfigLauncher.launch(intent)
                    } else {
                        vm.addAndroidWidget(appWidgetId)
                    }
                }
            } else if (appWidgetId != -1) {
                vm.appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }

        private val widgetConfigLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (result.resultCode == RESULT_OK) {
                if (appWidgetId != -1) vm.addAndroidWidget(appWidgetId)
            } else if (appWidgetId != -1) {
                vm.appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }

        private fun startWidgetPicker() {
            val appWidgetId = vm.appWidgetHost.allocateAppWidgetId()
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            widgetPicker.launch(pickIntent)
        }

        private val locationPermissions = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted ->
            if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                vm.startLocationUpdates()
                }
        }

        private fun hideSystemBars() {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            if (hasFocus) {
                hideSystemBars()
                // 1. Al recuperar el foco de la ventana, reactivamos la ventana embebida SYU
                setSyuEmbedded(true)
            } else {
                // 2. Al perder el foco (p. ej. al abrir un menú o app flotante),
                // deshabilitamos SYU para que NO maximice Waze/Maps
                setSyuEmbedded(false)
            }
        }

      /*  override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            if (hasFocus) hideSystemBars()
        } */

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
         //   hideSystemBars()
            // Registrar el receptor dinámico de FYT
         //   fytReceiver = FytBroadcastReceiver.register(this)

            setContent {
                val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
                val settings       by vm.settings.collectAsStateWithLifecycle()
                val nav            by vm.nav.collectAsStateWithLifecycle()
                val apps        by vm.apps.collectAsStateWithLifecycle()
                val appsLoading by vm.appsLoading.collectAsStateWithLifecycle()
                val nowPlaying  by vm.nowPlaying.collectAsStateWithLifecycle()
                val weather     by vm.weather.collectAsStateWithLifecycle()
                val location    by vm.location.collectAsStateWithLifecycle()
                val bearing     by vm.compassBearing.collectAsStateWithLifecycle()
                val wifiLevel   by vm.wifiLevel.collectAsStateWithLifecycle()
                val mobileLevel by vm.mobileLevel.collectAsStateWithLifecycle()
                val isDayModeVM by vm.isDayMode.collectAsStateWithLifecycle()
                val hardwareRadio by vm.hardwareRadio.collectAsStateWithLifecycle()
                val systemIsDark = isSystemInDarkTheme()
                val isDayMode = if (settings.dayNightMode == DayNightMode.SYSTEM) !systemIsDark else isDayModeVM
                val pickerSlot      by vm.shortcutPickerSlot.collectAsStateWithLifecycle()
                val appPickerTarget by vm.appPickerTarget.collectAsStateWithLifecycle()

                // Performance Optimization: Create a lookup map for icons to avoid O(N) searches in sidebar recompositions
                val appIconMap by remember(apps) {
                    derivedStateOf { apps.associate { it.packageName to it.icon } }
                }

                var editMode by remember { mutableStateOf(false) }
                var widgetLibraryOpen by remember { mutableStateOf(false) }

                var autostartLaunched by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(settingsLoaded) {
                    if (settingsLoaded && !autostartLaunched) {
                        val appsToLaunch = settings.autostartPackages.filter { it.isNotEmpty() }
                        if (appsToLaunch.isNotEmpty()) {
                            // Use user-defined delay (default 2s, range 2-20s)
                            delay(settings.autostartDelay * 1000L)

                            appsToLaunch.forEach { pkg ->
                                vm.launchApp(pkg)
                                // Small gap between multiple app launches to avoid intent collisions
                                delay(500)
                            }
                            autostartLaunched = true
                        }
                    }
                }

                val accent         = Color(settings.accentColor)
                val bg             = if (settings.useCustomBackgroundColor) {
                    Color(settings.backgroundColor)
                } else {
                    if (isDayMode) Color(0xFFEEEEEE) else Color.Black
                }
                val textColor      = if (isDayMode) Color(0xFF111111) else Color(settings.fontColor)
                val bgGradientEnd  = Color(settings.gradientEndColor)
                val bgBrush        = if (settings.useCustomBackgroundColor && settings.useGradient) {
                    val colors = listOf(bg, bgGradientEnd)
                    when (settings.gradientDirection) {
                        GradientDirection.TOP_TO_BOTTOM -> androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                        GradientDirection.LEFT_TO_RIGHT -> androidx.compose.ui.graphics.Brush.horizontalGradient(colors)
                        GradientDirection.DIAGONAL -> androidx.compose.ui.graphics.Brush.linearGradient(colors)
                        GradientDirection.RADIAL -> androidx.compose.ui.graphics.Brush.radialGradient(colors)
                    }
                } else null

                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density   = baseDensity.density * settings.uiScale,
                            fontScale = baseDensity.fontScale
                        )
                    ) {
                        if (!settingsLoaded) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                        } else OpenLauncherTheme(
                            accent     = accent,
                            background = bg,
                            textColor  = textColor,
                            fontBold   = settings.fontBold,
                            textScale  = settings.textScale,
                            isDayMode  = isDayMode,
                            useCustomBg = settings.useCustomBackgroundColor
                        ) {
                            if (!settings.onboardingCompleted) {
                                OnboardingScreen(
                                    accent = accent,
                                    onComplete = {
                                        vm.updateSettings { copy(onboardingCompleted = true) }
                                        // Start location updates immediately upon completion
                                        vm.startLocationUpdates()
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize().let { m ->
                                    if (bgBrush != null) m.background(bgBrush) else m.background(bg)
                                }) {
                                    // Optional wallpaper layer
                                    if (settings.wallpaperUri.isNotEmpty()) {
                                        AsyncImage(
                                            model              = android.net.Uri.parse(settings.wallpaperUri),
                                                   contentDescription = null,
                                                   contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                                   modifier           = Modifier.fillMaxSize()
                                        )
                                        Box(modifier = Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = settings.wallpaperDim)))
                                    }

                                    val isBottomBar    = settings.sidebarPosition == SidebarPosition.BOTTOM
                                    val layoutDivColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF000000)

                                    val sidebarContent: @Composable () -> Unit = {
                                        val sidebarDensity = Density(
                                            density = baseDensity.density * (1.0f + (settings.uiScale - 1.0f) * 0.35f),
                                                                     fontScale = baseDensity.fontScale
                                        )
                                        CompositionLocalProvider(LocalDensity provides sidebarDensity) {
                                            Sidebar(
                                                currentDest   = nav,
                                                settings      = settings,
                                                isHorizontal  = isBottomBar,
                                                installedIconFor = { pkg -> appIconMap[pkg] },
                                                onNavigate    = { dest ->
                                                    vm.cancelShortcutPicker()
                                                    vm.cancelCarPlayPicker()
                                                    vm.exitRearrangeMode()
                                                    vm.navigate(dest)
                                                },
                                                onShortcutClick = { slot ->
                                                    val shortcut = settings.shortcuts[slot]
                                                    if (shortcut.packageName.isNotEmpty()) {
                                                        vm.launchApp(shortcut.packageName)
                                                    }
                                                },
                                                onShortcutLongPress  = { slot -> vm.startShortcutPicker(slot) },
                                                    onShortcutRemove     = { slot -> vm.removeShortcut(slot) },
                                                    onShortcutSetIcon    = { slot, icon -> vm.setShortcutIcon(slot, icon) },
                                                    onReorder            = { from, to -> vm.reorderShortcut(from, to) },
                                                    wifiLevel            = wifiLevel,
                                                    mobileLevel          = mobileLevel,
                                                    editMode             = editMode,
                                                    onToggleEditMode     = { editMode = !editMode },
                                                    onOpenWidgetLibrary  = { widgetLibraryOpen = true }
                                            )
                                        }
                                    }

                                    val mainPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                        // ── Persistent Stack Navigation ─────────────────────────────
                                        Box(modifier = paneModifier) {
                                            HomeScreen(
                                                settings            = settings,
                                                weather             = weather,
                                                nowPlaying          = nowPlaying,
                                                location            = location,
                                                bearing             = bearing,
                                                isDayMode           = isDayMode,
                                                onPlayPause         = { vm.playPause(this@MainActivity) },
                                                       onNext              = vm::skipNext,
                                                       onPrev              = vm::skipPrev,
                                                       onLaunchCarPlay     = { vm.launchApp(settings.carPlayPackage) },
                                                       onLaunchAndroidAuto = { vm.launchApp(settings.androidAutoPackage) },
                                                       onAssignCarPlay     = { vm.startCarPlayPicker() },
                                                       onAssignAndroidAuto = { vm.startAndroidAutoPicker() },
                                                       onClearCarPlay      = { vm.clearCarPlayApp() },
                                                       onClearAndroidAuto  = { vm.clearAndroidAutoApp() },
                                                       onAssignPip         = { vm.startPipPicker() },
                                                       onClearPip          = { vm.clearPipApp() },
                                                      // onLaunchPip         = { vm.launchApp(settings.pipAppPackage) },
                                                       // APLICACIÓN PIP (NO usar launchApp. Solo asignar el paquete al estado)
                                                       onLaunchPip         = { vm.setPipAppPackage() },
                                                       onAddAndroidWidget  = { startWidgetPicker() },
                                                       onTapNowPlaying     = {
                                                           val pkg = nowPlaying?.controller?.packageName
                                                           if (!pkg.isNullOrEmpty()) vm.launchApp(pkg)
                                                               vm.playLastOrOpenActive(this@MainActivity)
                                                       },
                                                       onUpdateWidget      = { id, sx, sy -> vm.updateWidgetConfig(id, sx, sy) },
                                                       onMoveWidget        = { id, gx, gy -> vm.moveWidgetConfig(id, gx, gy) },
                                                       onAddWidget         = { id -> vm.addWidget(id) },
                                                       onRemoveWidget      = { id -> vm.removeWidget(id) },
                                                       onSetClockStyle     = { style -> vm.updateSettings { copy(clockStyle = style) } },
                                                       onSetVitalsAsBars   = { asBars -> vm.updateSettings { copy(vitalsAsBars = asBars) } },
                                                       onSetSpeedometerDigitalOnly = { digital -> vm.updateSettings { copy(speedometerDigitalOnly = digital) } },
                                                       onUpdateSoundPad    = { idx, pad -> vm.updateSoundboardPad(idx, pad) },
                                                       hardwareRadio         = hardwareRadio,
                                                       onLaunchHardwareRadio = { vm.launchHardwareRadioApp() },
                                                       onStopHardwareRadio   = { vm.stopHardwareRadioApp() },
                                                       onRadioSeekUp         = { vm.radioSeekUp() },
                                                       onRadioSeekDown       = { vm.radioSeekDown() },
                                                       onRadioCycleFm        = { vm.radioCycleFm() },
                                                       onRadioSwitchAm       = { vm.radioSwitchAm() },
                                                       onRadioTune           = { band, freq -> vm.radioTune(band, freq) },
                                                       onAssignRadio         = { vm.startRadioPicker() },
                                                       onToggleMapProvider = { vm.toggleMapProvider() },
                                                       onToggleTraffic     = { vm.toggleTraffic() },
                                                       onSetMapType        = { vm.setMapType(it) },
                                                       appWidgetHost       = vm.appWidgetHost,
                                                       editMode            = editMode,
                                                       onToggleEditMode    = { editMode = !editMode },
                                                       widgetLibraryOpen   = widgetLibraryOpen,
                                                       onSetWidgetLibraryOpen = { widgetLibraryOpen = it },
                                                       isOverlayOpen       = nav != NavDestination.HOME,
                                                       modifier = Modifier.fillMaxSize()
                                            )

                                            // Overlay screens
                                            AnimatedVisibility(
                                                visible = nav == NavDestination.APP_LIBRARY,
                                                enter = fadeIn() + slideInHorizontally { it / 10 },
                                                               exit = fadeOut() + slideOutHorizontally { -it / 10 }
                                            ) {
                                                AppLibraryScreen(
                                                    apps                = apps,
                                                    isLoading           = appsLoading,
                                                    isPickerMode        = pickerSlot != null,
                                                    pickerSlot          = pickerSlot,
                                                    isCarPlayPickerMode = appPickerTarget != null,
                                                    carPlayPickerLabel  = when (appPickerTarget) {
                                                        LauncherViewModel.AppPickerTarget.ANDROID_AUTO -> "CHOOSE ANDROID AUTO APP"
                                                        LauncherViewModel.AppPickerTarget.PIP          -> "CHOOSE PIP APP"
                                                        LauncherViewModel.AppPickerTarget.RADIO        -> "CHOOSE RADIO APP"
                                                        LauncherViewModel.AppPickerTarget.AUTOSTART_1 -> "CHOOSE AUTOSTART APP 1"
                                                        LauncherViewModel.AppPickerTarget.AUTOSTART_2 -> "CHOOSE AUTOSTART APP 2"
                                                        LauncherViewModel.AppPickerTarget.AUTOSTART_3 -> "CHOOSE AUTOSTART APP 3"
                                                        LauncherViewModel.AppPickerTarget.AUTOSTART_4 -> "CHOOSE AUTOSTART APP 4"
                                                        else -> "CHOOSE CARPLAY APP"
                                                    },
                                                    accent              = accent,
                                                    onAppClick          = { app -> vm.launchApp(app.packageName) },
                                                                 onPickerSelect      = { slot, app -> vm.assignShortcut(slot, app) },
                                                                 onCarPlaySelect     = { app -> vm.assignPickerApp(app) },
                                                                 modifier = Modifier.fillMaxSize().background(bg).clickable(onClick = {})
                                                )
                                            }

                                            AnimatedVisibility(
                                                visible = nav == NavDestination.SETTINGS,
                                                enter = fadeIn() + slideInHorizontally { it / 10 },
                                                               exit = fadeOut() + slideOutHorizontally { -it / 10 }
                                            ) {
                                                SettingsScreen(
                                                    settings = settings,
                                                    accent   = accent,
                                                    onUpdate = { block -> vm.updateSettings(block) },
                                                    onReset  = { vm.resetSettings() },
                                                    onAssignAutostart = { slot -> vm.startAutostartPicker(slot) },
                                                    onClearAutostart = { slot -> vm.clearAutostartApp(slot) },
                                                    onSelectPipApp    = { vm.startPipPicker() },
                                                    onSelectCarApp    = { vm.startCarPicker() },
                                                    modifier = Modifier.fillMaxSize().background(bg).clickable(onClick = {})
                                                )
                                            }

                                            AnimatedVisibility(
                                                visible = nav == NavDestination.HOMECAR,
                                                enter = fadeIn() + slideInHorizontally { it / 10 },
                                                               exit = fadeOut() + slideOutHorizontally { -it / 10 }
                                            ) {
                                                HomeCarScreen(
                                                    accent   = accent,
                                                    onUpdate = { block -> vm.updateSettings(block) },
                                                               onReset  = { vm.resetSettings() },
                                                               onAssignAutostart = { slot -> vm.startAutostartPicker(slot) },
                                                               onClearAutostart = { slot -> vm.clearAutostartApp(slot) },
                                                               onSelectPipApp    = { vm.startPipPicker() },
                                                               modifier = Modifier.fillMaxSize().background(bg).clickable(onClick = {})
                                                )
                                            }
                                        }
                                    }

                                    if (isBottomBar) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            mainPane(Modifier.weight(1f).fillMaxWidth())
                                            androidx.compose.material3.HorizontalDivider(color = layoutDivColor)
                                            sidebarContent()
                                        }
                                    } else {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            val vDivider: @Composable () -> Unit = {
                                                androidx.compose.material3.VerticalDivider(
                                                    modifier = Modifier.fillMaxHeight(),
                                                                                           color    = layoutDivColor
                                                )
                                            }
                                            if (settings.sidebarPosition == SidebarPosition.LEFT) {
                                                sidebarContent()
                                                vDivider()
                                            }
                                            mainPane(Modifier.weight(1f).fillMaxHeight())
                                            if (settings.sidebarPosition == SidebarPosition.RIGHT) {
                                                vDivider()
                                                sidebarContent()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } // CompositionLocalProvider
            }
        }

        override fun onResume() {
            super.onResume()
            vm.refreshConnectivity()
            vm.refreshMedia()
           // setSyuEmbedded(true)
        }

        override fun onStart() {
            super.onStart()
            vm.startListeningWidgets()
            vm.startLocationUpdates()
            // Registrar el receiver de FYT al iniciar la Activity
          //  setSyuEmbedded(true)
        }

        override fun onStop() {
            super.onStop()
            vm.stopListeningWidgets()
            vm.stopLocationUpdates()
         //   setSyuEmbedded(false)
        }
}
