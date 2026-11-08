package com.openlauncher.app.model

data class ObdState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val sootGrams: Double? = null,
    val dpfTemperatureCelsius: Double? = null,
    val distanceSinceRegenKm: Int? = null,
    val coolantTemperatureCelsius: Double? = null,
    val voltageVolts: Double? = null,
    val engineRpm: Int? = null,
    val tripDistanceKm: Double = 0.0,
    val vehicleSpeedKmh: Int? = null,
    val statusMessage: String? = null,
    val rawFrameDump: String? = null,
    val lastUpdatedMs: Long = 0L
) {
    val isDpfRegenActive: Boolean
        get() = (dpfTemperatureCelsius ?: 0.0) >= 500.0
}
