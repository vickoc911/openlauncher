package com.openlauncher.app.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.openlauncher.app.model.ObdState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * OBD2 Manager for Kia Ceed JD 1.6 CRDi (Bosch EDC17, ECU 7E0 -> 7E8).
 *
 * The ECU speaks KWP-style Mode 21 ("61 xx" replies); standard DPF PIDs (01 78/7A/7C) return no data.
 * All three DPF values were located and checked against Car Scanner readings from three trips
 * (payload = reply bytes counted from the "61" service byte at index 0):
 *
 *  DISTANCE SINCE REGEN  reply to 2103, bytes [56..58] big-endian / 1000  (km)
 *      Trip 28.10: 273.524 -> 276.831 km vs Car Scanner 273.52 -> 276.83, never decreased in 208 samples.
 *  SOOT MASS             reply to 21948001, bytes [9..10] big-endian * 0.39 - 299.45  (g)
 *      Raw 801 -> 12.94 g (27.10), raw 802 -> 13.33 g (28.10), raw 788 -> 7.87 g (22.10, reported ~7.8).
 *      Resolution is one raw step = 0.39 g, so it changes in 0.39 g jumps.
 *  TEMP BEFORE DPF       reply to 21948001, byte [8] * 4.7092 + 94.305  (°C)
 *      Reproduces all four Car Scanner readings (169.65, 202.62, 197.91, 226.16) within 0.003 °C.
 *      Resolution is one raw step = 4.71 °C (the same byte is repeated in 2103[48]).
 *
 * Caveat: soot has only two exact readings (plus one approximate); recheck the offset when soot
 * changes by a few raw steps. The 100% soot level below is an assumption, not read from the ECU.
 */
class ObdManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var workerJob: Job? = null

    private val _obdState = MutableStateFlow(ObdState())
    val obdState: StateFlow<ObdState> = _obdState

    private var currentAddress: String = ""
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // Distance monotonic guard state
    private var lastDistanceKm: Double? = null
    private var pendingResetKm: Double? = null
    private var pendingResetCount = 0

    // Trip Distance calculation state
    private var accumulatedTripKm = 0.0
    private var lastSpeedPollMs = 0L

    // Active DPF Regeneration audio alert state
    private var wasRegenActive = false

    private fun playGentleRegenBeep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            scope.launch {
                delay(300)
                tg.release()
            }
        } catch (e: Exception) {
            FileLogger.log(context, "OBD Beep Error: ${e.message}")
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** ASSUMPTION (not read from the ECU): soot mass treated as 100% at this value. */

        private const val CMD_DISTANCE = "2103"
        private const val CMD_DPF_MAIN = "21948001"

        private const val DISTANCE_FIRST_BYTE = 56
        private const val DISTANCE_SCALE = 1000.0

        private const val SOOT_HI_BYTE = 9
        private const val SOOT_SCALE = 0.39
        private const val SOOT_OFFSET = -299.45

        private const val TEMP_BYTE = 8
        private const val TEMP_SCALE = 4.7092
        private const val TEMP_OFFSET = 94.305

        /** When true, other Car Scanner frames are logged raw every cycle (slows polling a little). */
        const val CALIBRATION_LOGGING = false
        private val CALIBRATION_CMDS = listOf("2101", "2106", "2107", "211B")

        /**
         * A big drop of the distance counter is accepted as a regeneration reset after this many consecutive
         * readings that keep counting up from the new value (the car may be driving, so they are NOT equal).
         */
        private const val RESET_CONFIRMATIONS = 2
        private const val NOISE_TOLERANCE_KM = 0.05

        data class DpfFrame(
            val sootGrams: Double,
            val dpfTempCelsius: Double
        )

        private fun isHex(c: Char) = c in '0'..'9' || c in 'A'..'F'

        /**
         * Turns raw ELM327 text (AT H0, multi-frame formatted) into the KWP payload bytes.
         *   "00C\r0: 61 FF 15 00 1B 91\r1: 59 00 0C 03 14 FF\r\r>"  ->  61 FF 15 ... 14 FF
         * Returns null for NO DATA / errors / 7F negatives / incomplete multi-frame replies.
         */
        fun decodeResponse(raw: String): List<Int>? {
            val upper = raw.uppercase()
            if (upper.contains("NO DATA") || upper.contains("UNABLE") ||
                upper.contains("ERROR") || upper.contains("STOPPED") || upper.contains("BUFFER FULL")
            ) return null

            var declaredLen = -1
            val hex = StringBuilder()
            for (rawLine in upper.split('\r', '\n')) {
                var line = rawLine.replace(">", "").replace(" ", "").trim()
                if (line.isEmpty() || line.startsWith("SEARCHING") || line == "OK" || line == "?") continue

                // Multi-frame length line: exactly 3 hex chars ("029", "04B").
                if (line.length == 3 && line.all { isHex(it) }) {
                    declaredLen = line.toInt(16)
                    continue
                }
                // Indexed frame line: "0:", "1:" ... "F:"
                if (line.length >= 2 && line[1] == ':') line = line.substring(2)

                if (line.isEmpty() || !line.all { isHex(it) }) continue
                hex.append(line)
            }

            if (hex.isEmpty() || hex.length % 2 != 0) return null
            var bytes = hex.chunked(2).map { it.toInt(16) }

            if (declaredLen > 0) {
                if (bytes.size < declaredLen) return null      // frames missing
                bytes = bytes.take(declaredLen)                // drop 0x55 padding
            }
            if (bytes.isEmpty() || bytes[0] == 0x7F) return null
            return bytes
        }

        /** Standard PID 01 05: coolant = A - 40 (°C). */
        fun parseCoolantTempResponse(response: String): Double? {
            val b = decodeResponse(response) ?: return null
            if (b.size < 3 || b[0] != 0x41 || b[1] != 0x05) return null
            return (b[2] - 40).toDouble().coerceIn(-40.0, 150.0)
        }

        /** Reply to 2103 -> distance since last DPF regeneration in km. */
        fun parseDistanceFrame(response: String): Double? {
            val b = decodeResponse(response) ?: return null
            if (b.size < DISTANCE_FIRST_BYTE + 3 || b[0] != 0x61 || b[1] != 0x03) return null
            val raw = (b[DISTANCE_FIRST_BYTE] shl 16) or (b[DISTANCE_FIRST_BYTE + 1] shl 8) or b[DISTANCE_FIRST_BYTE + 2]
            if (raw == 0xFFFFFF) return null                   // "not available" filler
            return raw / DISTANCE_SCALE
        }

        /** Reply to 21948001 -> soot mass (g, %) and temperature before the DPF (°C). */
        fun parseDpfMainFrame(response: String): DpfFrame? {
            val b = decodeResponse(response) ?: return null
            if (b.size < 11 || b[0] != 0x61 || b[1] != 0xFF) return null

            val sootRaw = (b[SOOT_HI_BYTE] shl 8) or b[SOOT_HI_BYTE + 1]
            val soot = sootRaw * SOOT_SCALE + SOOT_OFFSET
            val temp = b[TEMP_BYTE] * TEMP_SCALE + TEMP_OFFSET
            if (soot !in 0.0..100.0 || temp !in 0.0..1000.0) return null

            return DpfFrame(soot, temp)
        }

        /** Parses ELM327 AT RV response (e.g. "14.2V" -> 14.2) or PID 01 42 */
        fun parseVoltageResponse(response: String): Double? {
            val b = decodeResponse(response)
            if (b != null && b.size >= 4 && b[0] == 0x41 && b[1] == 0x42) {
                val raw = (b[2] shl 8) or b[3]
                return (raw / 1000.0).coerceIn(0.0, 30.0)
            }

            val clean = response.uppercase().replace("V", "").replace(">", "").trim()
            clean.toDoubleOrNull()?.let { return (it + 0.6).coerceIn(0.0, 30.0) } // Adjust for Vlink adapter internal diode drop
            return null
        }

        /** Standard PID 01 0C: Engine RPM = ((A * 256) + B) / 4 */
        fun parseEngineRpmResponse(response: String): Int? {
            val b = decodeResponse(response) ?: return null
            if (b.size < 4 || b[0] != 0x41 || b[1] != 0x0C) return null
            val raw = (b[2] shl 8) or b[3]
            return (raw / 4).coerceIn(0, 10000)
        }

        /** Standard PID 01 0D: Vehicle Speed = A (km/h) */
        fun parseVehicleSpeedResponse(response: String): Int? {
            val b = decodeResponse(response) ?: return null
            if (b.size < 3 || b[0] != 0x41 || b[1] != 0x0D) return null
            return b[2].coerceIn(0, 300)
        }

        fun toHexString(bytes: List<Int>?): String =
            bytes?.joinToString(" ") { "%02X".format(it) } ?: "null"
    }

    /**
     * Distance since regeneration only goes up while driving, and drops to ~0 when a regeneration ends.
     *  - rising value                    -> accept
     *  - tiny decrease (<= 0.05 km)      -> noise, keep the previous value
     *  - big decrease                    -> glitch OR real reset. Accept it once two consecutive readings keep
     *    counting up from the low value (pending follows the counter, so a moving car is fine).
     */
    private fun acceptDistance(km: Double): Double {
        val last = lastDistanceKm
        if (last == null || km >= last) {
            lastDistanceKm = km
            pendingResetKm = null
            pendingResetCount = 0
            return km
        }
        if (last - km <= NOISE_TOLERANCE_KM) return last

        val pending = pendingResetKm
        pendingResetCount =
            if (pending != null && km >= pending - NOISE_TOLERANCE_KM) pendingResetCount + 1 else 1
        pendingResetKm = km                      // follow the counter instead of the first reading

        if (pendingResetCount >= RESET_CONFIRMATIONS) {
            FileLogger.log(context, "OBD: distance reset detected ($last -> $km km) - regeneration")
            lastDistanceKm = km
            pendingResetKm = null
            pendingResetCount = 0
            return km
        }
        return last   // first low reading: keep the previous value for one more cycle
    }

    fun start(deviceAddress: String) {
        if (deviceAddress.isEmpty()) {
            stop()
            return
        }
        if (currentAddress == deviceAddress && workerJob?.isActive == true) return

        currentAddress = deviceAddress
        reconnect()
    }

    fun reconnect() {
        if (currentAddress.isEmpty()) return
        workerJob?.cancel()
        workerJob = scope.launch {
            connectionLoop(currentAddress)
        }
    }

    fun stop() {
        workerJob?.cancel()
        workerJob = null
        currentAddress = ""
        disconnectSocket()
        _obdState.value = ObdState(isConnected = false, isConnecting = false, statusMessage = "DISABLED")
    }

    private suspend fun connectionLoop(address: String) {
        var consecutiveFailures = 0

        while (scope.isActive) {
            _obdState.value = _obdState.value.copy(
                isConnecting = true,
                isConnected = false,
                statusMessage = "CONNECTING..."
            )

            val success = connectAndPoll(address)
            disconnectSocket()

            if (success) {
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
                _obdState.value = ObdState(
                    isConnected = false,
                    isConnecting = false,
                    statusMessage = "NO OBD CONNECTION"
                )

                val retryDelay = when (consecutiveFailures) {
                    1 -> 3_000L
                    2 -> 5_000L
                    3 -> 10_000L
                    else -> 120_000L
                }

                FileLogger.log(context, "OBD: Connection failed (attempt #$consecutiveFailures). Retrying in ${retryDelay / 1000}s...")
                delay(retryDelay)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectAndPoll(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            if (!adapter.isEnabled) return@withContext false

            val device = adapter.getRemoteDevice(address) ?: return@withContext false
            FileLogger.log(context, "OBD: Connecting to Bluetooth SPP: ${device.name} ($address)")

            adapter.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            if (inputStream == null || outputStream == null) return@withContext false

            // Init sequence modelled on the Car Scanner session (ELM327 v2.2 clone).
            val initCmds = listOf(
                "ATZ" to 1000L,
                "ATE0" to 150L,
                "ATL0" to 150L,
                "ATH0" to 150L,      // headers off -> multi-frame replies as "00C / 0: .. / 1: .."
                "ATS0" to 150L,
                "ATCAF1" to 150L,    // automatic ISO-TP formatting + flow control
                "ATSP6" to 150L,     // ISO 15765-4 CAN 11-bit / 500 kbaud
                "ATSH7E0" to 150L,   // engine ECU
                "ATAT1" to 150L,
                "ATST64" to 150L     // 400 ms response timeout
            )
            for ((cmd, wait) in initCmds) {
                val reply = sendCmd("$cmd\r")
                FileLogger.log(context, "OBD init $cmd -> ${reply.replace("\r", " ").trim()}")
                delay(wait)
            }

            FileLogger.log(context, "OBD: Connected & initialized")
            _obdState.value = _obdState.value.copy(
                isConnected = true,
                isConnecting = false,
                statusMessage = "CONNECTED"
            )

            // Reset trip odometer on new connection session
            accumulatedTripKm = 0.0
            lastSpeedPollMs = System.currentTimeMillis()

            while (isActive) {
                val respCoolant = sendCmd("0105\r")
                delay(60)
                val respDist = sendCmd("$CMD_DISTANCE\r")
                delay(60)
                val respDpf = sendCmd("$CMD_DPF_MAIN\r")
                delay(60)

                val coolant = parseCoolantTempResponse(respCoolant)
                val rawDistance = parseDistanceFrame(respDist)
                val distanceKm = rawDistance?.let { acceptDistance(it) }
                val dpf = parseDpfMainFrame(respDpf)

                // Poll Control Module Voltage via PID 0142 or AT RV
                val respVolt0142 = sendCmd("0142\r")
                delay(60)
                var voltage = parseVoltageResponse(respVolt0142)
                if (voltage == null) {
                    val respRv = sendCmd("AT RV\r")
                    delay(60)
                    voltage = parseVoltageResponse(respRv)
                }

                val respSpeed = sendCmd("010D\r")
                delay(60)
                val speedKmh = parseVehicleSpeedResponse(respSpeed)

                val respRpm = sendCmd("010C\r")
                delay(60)
                val rpm = parseEngineRpmResponse(respRpm)

                // Update accumulated trip distance
                val nowMs = System.currentTimeMillis()
                if (lastSpeedPollMs > 0 && speedKmh != null && speedKmh > 0) {
                    val deltaHours = (nowMs - lastSpeedPollMs) / 3_600_000.0
                    accumulatedTripKm += speedKmh * deltaHours
                }
                lastSpeedPollMs = nowMs

                val rawParts = mutableListOf(
                    "0105:${toHexString(decodeResponse(respCoolant))}",
                    "$CMD_DISTANCE:${toHexString(decodeResponse(respDist))}",
                    "$CMD_DPF_MAIN:${toHexString(decodeResponse(respDpf))}"
                )
                if (CALIBRATION_LOGGING) {
                    for (cmd in CALIBRATION_CMDS) {
                        rawParts += "$cmd:${toHexString(decodeResponse(sendCmd("$cmd\r")))}"
                        delay(60)
                    }
                }
                val rawSummary = rawParts.joinToString(" | ")

                FileLogger.log(
                    context,
                    "OBD Stats -> Coolant:$coolant, DPF Temp:${dpf?.dpfTempCelsius?.let { "%.2f".format(it) }}, " +
                            "Soot:${dpf?.sootGrams?.let { "%.2f".format(it) }}g, " +
                            "Dist:${distanceKm?.let { "%.3f".format(it) }}km, Volt:${voltage}V, RPM:$rpm, Speed:${speedKmh}km/h"
                )
                FileLogger.log(context, "OBD Raw -> $rawSummary")

                // ObdState.distanceSinceRegenKm is an Int in the current model; if it is a Double, drop roundToInt().
                _obdState.value = _obdState.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    coolantTemperatureCelsius = coolant ?: _obdState.value.coolantTemperatureCelsius,
                    dpfTemperatureCelsius = dpf?.dpfTempCelsius ?: _obdState.value.dpfTemperatureCelsius,
                    sootGrams = dpf?.sootGrams ?: _obdState.value.sootGrams,
                    distanceSinceRegenKm = distanceKm?.roundToInt() ?: _obdState.value.distanceSinceRegenKm,
                    voltageVolts = voltage ?: _obdState.value.voltageVolts,
                    engineRpm = rpm ?: _obdState.value.engineRpm,
                    tripDistanceKm = accumulatedTripKm,
                    vehicleSpeedKmh = speedKmh ?: _obdState.value.vehicleSpeedKmh,
                    statusMessage = "CONNECTED",
                    rawFrameDump = rawSummary,
                    lastUpdatedMs = System.currentTimeMillis()
                )

                // Gentle audio notification when active DPF regeneration begins (dpfTemp >= 500°C)
                val isRegenNow = _obdState.value.isDpfRegenActive
                if (isRegenNow && !wasRegenActive) {
                    playGentleRegenBeep()
                    FileLogger.log(context, "OBD: Active DPF Regeneration Started! Played notification beep.")
                }
                wasRegenActive = isRegenNow

                delay(1000)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.log(context, "OBD Error: ${e.message}")
            false
        }
    }

    private fun sendCmd(cmd: String): String {
        return try {
            val input = inputStream ?: return ""
            val output = outputStream ?: return ""

            // Drop any late bytes from a previous command so they are not read as this reply.
            val drain = ByteArray(256)
            while (input.available() > 0) input.read(drain)

            output.write(cmd.toByteArray())
            output.flush()

            val buffer = ByteArray(256)
            val sb = StringBuilder()
            val startMs = System.currentTimeMillis()

            while (System.currentTimeMillis() - startMs < 2000) {
                if (input.available() > 0) {
                    val n = input.read(buffer)
                    if (n > 0) {
                        sb.append(String(buffer, 0, n))
                        if (sb.contains(">")) break   // ELM prompt = reply complete
                    }
                } else {
                    Thread.sleep(20)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun disconnectSocket() {
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }
}
