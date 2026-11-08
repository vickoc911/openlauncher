package com.openlauncher.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val VEHICLE_NAME       = stringPreferencesKey("vehicle_name")
        val ACCENT_COLOR       = intPreferencesKey("accent_color")
        val BG_COLOR           = intPreferencesKey("bg_color")
        val FONT_COLOR         = intPreferencesKey("font_color")
        val WALLPAPER_URI      = stringPreferencesKey("wallpaper_uri")
        val FONT_BOLD          = booleanPreferencesKey("font_bold")
        val TEXT_SCALE         = floatPreferencesKey("text_scale")
        val UI_SCALE           = floatPreferencesKey("ui_scale")
        val CLOCK_STYLE        = stringPreferencesKey("clock_style")
        val UNIT_SYSTEM        = stringPreferencesKey("unit_system")
        val SHOW_WEATHER       = booleanPreferencesKey("show_weather")
        val SHOW_CLOCK         = booleanPreferencesKey("show_clock")
        val SHOW_TELEMETRY     = booleanPreferencesKey("show_telemetry")
        val SHOW_NOW_PLAYING   = booleanPreferencesKey("show_now_playing")
        val NOW_PLAYING_COMPACT = booleanPreferencesKey("now_playing_compact")
        val SHOW_ALTIMETER     = booleanPreferencesKey("show_altimeter")
        val SHOW_SPEEDOMETER   = booleanPreferencesKey("show_speedometer")
        val SHORTCUTS_JSON     = stringPreferencesKey("shortcuts_json")
        val WIDGET_LAYOUT_JSON = stringPreferencesKey("widget_layout_json")
        val CAR_PLAY_PACKAGE      = stringPreferencesKey("car_play_package")
        val ANDROID_AUTO_PACKAGE  = stringPreferencesKey("android_auto_package")
        val USE_GRADIENT          = booleanPreferencesKey("use_gradient")
        val GRADIENT_END_COLOR    = intPreferencesKey("gradient_end_color")
        val WALLPAPER_DIM         = floatPreferencesKey("wallpaper_dim")
        val RIGHT_HAND_DRIVE      = booleanPreferencesKey("right_hand_drive") // kept for migration
        val SIDEBAR_POSITION           = stringPreferencesKey("sidebar_position")
        val BOTTOM_BAR_SHORTCUTS_RIGHT = booleanPreferencesKey("bottom_bar_shortcuts_right")
        val DAY_NIGHT_MODE        = stringPreferencesKey("day_night_mode")
        val SHOW_PIP              = booleanPreferencesKey("show_pip")
        val PIP_APP_PACKAGE       = stringPreferencesKey("pip_app_package")
        val ONBOARDING_COMPLETED  = booleanPreferencesKey("onboarding_completed")
        val SHOW_VITALS           = booleanPreferencesKey("show_vitals")
        val SHOW_TRIP_TRACKER     = booleanPreferencesKey("show_trip_tracker")
        val COMPASS_OFFSET        = floatPreferencesKey("compass_offset")
        val SHOW_SOUNDBOARD       = booleanPreferencesKey("show_soundboard")
        val SOUNDBOARD_PADS_JSON  = stringPreferencesKey("soundboard_pads_json")
        val VITALS_AS_BARS        = booleanPreferencesKey("vitals_as_bars")
        val SPEEDOMETER_DIGITAL_ONLY = booleanPreferencesKey("speedometer_digital_only")
        val GRADIENT_DIRECTION    = stringPreferencesKey("gradient_direction")
        val USE_CUSTOM_BG_COLOR   = booleanPreferencesKey("use_custom_bg_color")
        val SHOW_MAP      = booleanPreferencesKey("show_map")
        val MAP_PROVIDER  = stringPreferencesKey("map_provider")
        val MAP_TYPE      = stringPreferencesKey("map_type")
        val SHOW_TRAFFIC  = booleanPreferencesKey("show_traffic")
        val AUTOSTART_PACKAGES_JSON = stringPreferencesKey("autostart_packages_json")
        val AUTOSTART_DELAY = intPreferencesKey("autostart_delay")
        val SIDEBAR_WIDTH_DP     = intPreferencesKey("sidebar_width_dp")
        val BOTTOM_BAR_HEIGHT_DP = intPreferencesKey("bottom_bar_height_dp")
        val PLAY_MEDIA_ON_BOOT   = booleanPreferencesKey("play_media_on_boot")
        val OBD_ENABLED          = booleanPreferencesKey("obd_enabled")
        val OBD_DEVICE_ADDRESS   = stringPreferencesKey("obd_device_address")
        val OBD_DEVICE_NAME      = stringPreferencesKey("obd_device_name")
        val OBD_RIGHT_BAR_WIDTH_DP = intPreferencesKey("obd_right_bar_width_dp")
        val SHOW_SOOT_LEVEL      = booleanPreferencesKey("show_soot_level")
        val SHOW_DPF_TEMP        = booleanPreferencesKey("show_dpf_temp")
        val SHOW_LAST_REGEN_DIST = booleanPreferencesKey("show_last_regen_dist")
        val SHOW_COOLANT_TEMP    = booleanPreferencesKey("show_coolant_temp")
        val SHOW_VOLTAGE         = booleanPreferencesKey("show_voltage")
        val SHOW_TRIP_DISTANCE   = booleanPreferencesKey("show_trip_distance")
        val SHOW_ENGINE_RPM      = booleanPreferencesKey("show_engine_rpm")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> readSettings(prefs) }

    private fun readSettings(prefs: Preferences): AppSettings {
            val defaults = AppSettings()
            val shortcutsJson = prefs[Keys.SHORTCUTS_JSON]
            val shortcuts = if (shortcutsJson != null) {
                runCatching {
                    gson.fromJson<List<ShortcutConfig>>(
                        shortcutsJson,
                        object : TypeToken<List<ShortcutConfig>>() {}.type
                    )
                }.getOrNull() ?: defaults.shortcuts
            } else defaults.shortcuts

            val widgetJson = prefs[Keys.WIDGET_LAYOUT_JSON]
            val widgets = if (widgetJson != null) {
                val loaded = runCatching {
                    gson.fromJson<List<WidgetConfig>>(
                        widgetJson,
                        object : TypeToken<List<WidgetConfig>>() {}.type
                    )
                }.getOrNull() ?: defaults.widgetLayout
                if (loaded.none { it.gridX >= 2 }) defaults.widgetLayout else loaded
            } else defaults.widgetLayout

            return AppSettings(
                vehicleName    = prefs[Keys.VEHICLE_NAME]     ?: defaults.vehicleName,
                accentColor    = prefs[Keys.ACCENT_COLOR]     ?: defaults.accentColor,
                backgroundColor = prefs[Keys.BG_COLOR]        ?: defaults.backgroundColor,
                fontColor      = prefs[Keys.FONT_COLOR]       ?: defaults.fontColor,
                wallpaperUri   = prefs[Keys.WALLPAPER_URI]    ?: defaults.wallpaperUri,
                fontBold       = prefs[Keys.FONT_BOLD]        ?: defaults.fontBold,
                textScale      = prefs[Keys.TEXT_SCALE]       ?: defaults.textScale,
                uiScale        = prefs[Keys.UI_SCALE]         ?: defaults.uiScale,
                clockStyle     = prefs[Keys.CLOCK_STYLE]?.let { runCatching { ClockStyle.valueOf(it) }.getOrNull() } ?: defaults.clockStyle,
                unitSystem     = prefs[Keys.UNIT_SYSTEM]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: defaults.unitSystem,
                showWeather    = prefs[Keys.SHOW_WEATHER]     ?: defaults.showWeather,
                showClock      = prefs[Keys.SHOW_CLOCK]       ?: defaults.showClock,
                showTelemetry  = prefs[Keys.SHOW_TELEMETRY]   ?: defaults.showTelemetry,
                showNowPlaying = prefs[Keys.SHOW_NOW_PLAYING] ?: defaults.showNowPlaying,
                nowPlayingCompact = prefs[Keys.NOW_PLAYING_COMPACT] ?: defaults.nowPlayingCompact,
                showAltimeter   = prefs[Keys.SHOW_ALTIMETER]   ?: defaults.showAltimeter,
                showSpeedometer = prefs[Keys.SHOW_SPEEDOMETER] ?: defaults.showSpeedometer,
                shortcuts      = shortcuts,
                widgetLayout   = widgets,
                carPlayPackage      = prefs[Keys.CAR_PLAY_PACKAGE]      ?: defaults.carPlayPackage,
                androidAutoPackage  = prefs[Keys.ANDROID_AUTO_PACKAGE]  ?: defaults.androidAutoPackage,
                useGradient      = prefs[Keys.USE_GRADIENT]        ?: defaults.useGradient,
                gradientEndColor = prefs[Keys.GRADIENT_END_COLOR]  ?: defaults.gradientEndColor,
                wallpaperDim     = prefs[Keys.WALLPAPER_DIM]       ?: defaults.wallpaperDim,
                sidebarPosition  = prefs[Keys.SIDEBAR_POSITION]?.let { runCatching { SidebarPosition.valueOf(it) }.getOrNull() }
                                   ?: if (prefs[Keys.RIGHT_HAND_DRIVE] == true) SidebarPosition.RIGHT else defaults.sidebarPosition,
                bottomBarShortcutsRight = prefs[Keys.BOTTOM_BAR_SHORTCUTS_RIGHT] ?: defaults.bottomBarShortcutsRight,
                dayNightMode     = prefs[Keys.DAY_NIGHT_MODE]?.let { runCatching { DayNightMode.valueOf(it) }.getOrNull() } ?: defaults.dayNightMode,
                showPip          = prefs[Keys.SHOW_PIP]         ?: defaults.showPip,
                pipAppPackage    = prefs[Keys.PIP_APP_PACKAGE]  ?: defaults.pipAppPackage,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
                showVitals       = prefs[Keys.SHOW_VITALS]      ?: defaults.showVitals,
                showTripTracker  = prefs[Keys.SHOW_TRIP_TRACKER] ?: defaults.showTripTracker,
                compassOffset    = prefs[Keys.COMPASS_OFFSET]    ?: defaults.compassOffset,
                showSoundboard   = prefs[Keys.SHOW_SOUNDBOARD]   ?: defaults.showSoundboard,
                soundboardPads   = prefs[Keys.SOUNDBOARD_PADS_JSON]?.let {
                    runCatching {
                        gson.fromJson<List<SoundPadConfig>>(it, object : TypeToken<List<SoundPadConfig>>() {}.type)
                    }.getOrNull()
                } ?: defaults.soundboardPads,
                vitalsAsBars     = prefs[Keys.VITALS_AS_BARS] ?: defaults.vitalsAsBars,
                speedometerDigitalOnly = prefs[Keys.SPEEDOMETER_DIGITAL_ONLY] ?: defaults.speedometerDigitalOnly,
                gradientDirection = prefs[Keys.GRADIENT_DIRECTION]?.let { runCatching { GradientDirection.valueOf(it) }.getOrNull() } ?: defaults.gradientDirection,
                useCustomBackgroundColor = prefs[Keys.USE_CUSTOM_BG_COLOR] ?: defaults.useCustomBackgroundColor,
                showMap      = prefs[Keys.SHOW_MAP] ?: defaults.showMap,
                mapProvider  = prefs[Keys.MAP_PROVIDER]?.let { runCatching { MapProvider.valueOf(it) }.getOrNull() } ?: defaults.mapProvider,
                mapType      = prefs[Keys.MAP_TYPE]?.let { runCatching { MapType.valueOf(it) }.getOrNull() } ?: defaults.mapType,
                showTraffic  = prefs[Keys.SHOW_TRAFFIC] ?: defaults.showTraffic,
                autostartPackages = prefs[Keys.AUTOSTART_PACKAGES_JSON]?.let {
                    runCatching {
                        gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type)
                    }.getOrNull()
                } ?: listOfNotNull(prefs[stringPreferencesKey("autostart_package")]),
                autostartDelay = prefs[Keys.AUTOSTART_DELAY] ?: defaults.autostartDelay,
                sidebarWidthDp    = prefs[Keys.SIDEBAR_WIDTH_DP]    ?: defaults.sidebarWidthDp,
                bottomBarHeightDp = prefs[Keys.BOTTOM_BAR_HEIGHT_DP] ?: defaults.bottomBarHeightDp,
                playMediaOnBoot   = prefs[Keys.PLAY_MEDIA_ON_BOOT]   ?: defaults.playMediaOnBoot,
                obdEnabled        = prefs[Keys.OBD_ENABLED]          ?: defaults.obdEnabled,
                obdDeviceAddress   = prefs[Keys.OBD_DEVICE_ADDRESS]   ?: defaults.obdDeviceAddress,
                obdDeviceName      = prefs[Keys.OBD_DEVICE_NAME]      ?: defaults.obdDeviceName,
                obdRightBarWidthDp = prefs[Keys.OBD_RIGHT_BAR_WIDTH_DP] ?: defaults.obdRightBarWidthDp,
                showSootLevel      = prefs[Keys.SHOW_SOOT_LEVEL]      ?: defaults.showSootLevel,
                showDpfTemp        = prefs[Keys.SHOW_DPF_TEMP]        ?: defaults.showDpfTemp,
                showLastRegenDist = prefs[Keys.SHOW_LAST_REGEN_DIST] ?: defaults.showLastRegenDist,
                showCoolantTemp    = prefs[Keys.SHOW_COOLANT_TEMP]    ?: defaults.showCoolantTemp,
                showVoltage        = prefs[Keys.SHOW_VOLTAGE]         ?: defaults.showVoltage,
                showTripDistance   = prefs[Keys.SHOW_TRIP_DISTANCE]   ?: defaults.showTripDistance,
                showEngineRpm      = prefs[Keys.SHOW_ENGINE_RPM]      ?: defaults.showEngineRpm
            )
    }

    suspend fun saveSettings(s: AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, s) }
    }

    /**
     * Atomic read-modify-write. DataStore serializes edit blocks, so concurrent
     * updates can't overwrite each other (unlike transforming a stale snapshot).
     */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
    }

    private fun writeSettings(prefs: MutablePreferences, s: AppSettings) {
            prefs[Keys.VEHICLE_NAME]       = s.vehicleName
            prefs[Keys.ACCENT_COLOR]       = s.accentColor
            prefs[Keys.BG_COLOR]           = s.backgroundColor
            prefs[Keys.FONT_COLOR]         = s.fontColor
            prefs[Keys.WALLPAPER_URI]      = s.wallpaperUri
            prefs[Keys.FONT_BOLD]          = s.fontBold
            prefs[Keys.TEXT_SCALE]         = s.textScale
            prefs[Keys.UI_SCALE]           = s.uiScale
            prefs[Keys.CLOCK_STYLE]        = s.clockStyle.name
            prefs[Keys.UNIT_SYSTEM]        = s.unitSystem.name
            prefs[Keys.SHOW_WEATHER]       = s.showWeather
            prefs[Keys.SHOW_CLOCK]         = s.showClock
            prefs[Keys.SHOW_TELEMETRY]     = s.showTelemetry
            prefs[Keys.SHOW_NOW_PLAYING]   = s.showNowPlaying
            prefs[Keys.NOW_PLAYING_COMPACT] = s.nowPlayingCompact
            prefs[Keys.SHOW_ALTIMETER]     = s.showAltimeter
            prefs[Keys.SHOW_SPEEDOMETER]   = s.showSpeedometer
            prefs[Keys.SHORTCUTS_JSON]     = gson.toJson(s.shortcuts)
            prefs[Keys.WIDGET_LAYOUT_JSON] = gson.toJson(s.widgetLayout)
            prefs[Keys.CAR_PLAY_PACKAGE]      = s.carPlayPackage
            prefs[Keys.ANDROID_AUTO_PACKAGE]  = s.androidAutoPackage
            prefs[Keys.USE_GRADIENT]       = s.useGradient
            prefs[Keys.GRADIENT_END_COLOR] = s.gradientEndColor
            prefs[Keys.WALLPAPER_DIM]      = s.wallpaperDim
            prefs[Keys.SIDEBAR_POSITION]           = s.sidebarPosition.name
            prefs[Keys.BOTTOM_BAR_SHORTCUTS_RIGHT] = s.bottomBarShortcutsRight
            prefs[Keys.DAY_NIGHT_MODE]     = s.dayNightMode.name
            prefs[Keys.SHOW_PIP]           = s.showPip
            prefs[Keys.PIP_APP_PACKAGE]    = s.pipAppPackage
            prefs[Keys.ONBOARDING_COMPLETED] = s.onboardingCompleted
            prefs[Keys.SHOW_VITALS]        = s.showVitals
            prefs[Keys.SHOW_TRIP_TRACKER]  = s.showTripTracker
            prefs[Keys.COMPASS_OFFSET]     = s.compassOffset
            prefs[Keys.SHOW_SOUNDBOARD]    = s.showSoundboard
            prefs[Keys.SOUNDBOARD_PADS_JSON] = gson.toJson(s.soundboardPads)
            prefs[Keys.VITALS_AS_BARS]     = s.vitalsAsBars
            prefs[Keys.SPEEDOMETER_DIGITAL_ONLY] = s.speedometerDigitalOnly
            prefs[Keys.GRADIENT_DIRECTION] = s.gradientDirection.name
            prefs[Keys.USE_CUSTOM_BG_COLOR] = s.useCustomBackgroundColor
            prefs[Keys.SHOW_MAP]      = s.showMap
            prefs[Keys.MAP_PROVIDER]  = s.mapProvider.name
            prefs[Keys.MAP_TYPE]      = s.mapType.name
            prefs[Keys.SHOW_TRAFFIC]  = s.showTraffic
            prefs[Keys.AUTOSTART_PACKAGES_JSON] = gson.toJson(s.autostartPackages)
            prefs[Keys.AUTOSTART_DELAY] = s.autostartDelay
            prefs[Keys.SIDEBAR_WIDTH_DP]     = s.sidebarWidthDp
            prefs[Keys.BOTTOM_BAR_HEIGHT_DP] = s.bottomBarHeightDp
            prefs[Keys.PLAY_MEDIA_ON_BOOT]   = s.playMediaOnBoot
            prefs[Keys.OBD_ENABLED]          = s.obdEnabled
            prefs[Keys.OBD_DEVICE_ADDRESS]   = s.obdDeviceAddress
            prefs[Keys.OBD_DEVICE_NAME]      = s.obdDeviceName
            prefs[Keys.OBD_RIGHT_BAR_WIDTH_DP] = s.obdRightBarWidthDp
            prefs[Keys.SHOW_SOOT_LEVEL]      = s.showSootLevel
            prefs[Keys.SHOW_DPF_TEMP]        = s.showDpfTemp
            prefs[Keys.SHOW_LAST_REGEN_DIST] = s.showLastRegenDist
            prefs[Keys.SHOW_COOLANT_TEMP]    = s.showCoolantTemp
            prefs[Keys.SHOW_VOLTAGE]         = s.showVoltage
            prefs[Keys.SHOW_TRIP_DISTANCE]   = s.showTripDistance
            prefs[Keys.SHOW_ENGINE_RPM]      = s.showEngineRpm
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
