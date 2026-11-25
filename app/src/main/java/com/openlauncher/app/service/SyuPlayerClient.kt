package com.openlauncher.app.service

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/**
 * Cliente binder para el servicio de reproducción del reproductor FYT
 * (app `com.syu.media`, action del servicio de música
 * "com.syu.media.music.MusicService" = PlaybackService.AUDIO del launcher).
 *
 * Replica las llamadas transact de `com.syu.media.IPlayer` y `ICallback`
 * del firmware, extraídas del launcher original descompilado
 * (launcher3/sources/com/syu/media/). Sin AIDL local: los códigos
 * transact son los mismos que genera el AIDL del firmware, por lo que
 * no hay que repasar parcelables complejos más allá de [SyuMediaSource].
 *
 * Da acceso a:
 *  - `getPlayingSong()` → pista actual con la ruta del archivo de audio
 *    (de ahí se extrae la carátula embebida en el ID3, como hace el
 *    launcher original con MediaSource.getAlbumCover()).
 *  - `isPlaying()` / callback `onPlayStatusChanged` → estado real de
 *    play/pause (el MCU no da el código de pausa mapeado).
 *  - `registerCallback(ICallback)` → notificaciones de cambio de pista
 *    y de estado de reproducción.
 */

/** Pista actual del reproductor (réplica exacta de com.syu.media.entities.MediaSource). */
class SyuMediaSource {
    var id: Int = -1
    var title: String? = null
    var displayName: String? = null
    var artist: String? = null
    var album: String? = null
    var path: String = ""
    var folder: String = ""
    var duration: Int = -1
    var favorite: Boolean = false
    var length: Int = -1

    constructor()

    constructor(parcel: Parcel) {
        // Mismo orden que MediaSource.writeToParcel() del firmware
        id = parcel.readInt()
        title = parcel.readString()
        displayName = parcel.readString()
        artist = parcel.readString()
        album = parcel.readString()
        path = parcel.readString() ?: ""
        folder = parcel.readString() ?: ""
        duration = parcel.readInt()
        favorite = parcel.readByte() != 0.toByte()
        length = parcel.readInt()
    }
}

/** Llamadas al servicio del reproductor (códigos transact de IPlayer del firmware). */
class SyuPlayerClient(private val binder: IBinder) {

    private inline fun <T> transact(code: Int, writer: (Parcel) -> Unit = {}, reader: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            writer(data)
            binder.transact(code, data, reply, 0)
            reply.readException()
            return reader(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // 1=play, 5=playLast, 6=playNext, 7=pause, 8=isPlaying,
    // 10=getPlayingSong, 15/16=register/unregisterCallback
    fun play(): Boolean = try { transact(1) { it.readInt() != 0 } } catch (_: Exception) { false }
    fun playLast(): Boolean = try { transact(5) { it.readInt() != 0 } } catch (_: Exception) { false }
    fun playNext(): Boolean = try { transact(6) { it.readInt() != 0 } } catch (_: Exception) { false }
    fun pause(): Boolean = try { transact(7) { it.readInt() != 0 } } catch (_: Exception) { false }

    val isPlaying: Boolean
        get() = try { transact(8) { it.readInt() != 0 } } catch (_: Exception) { false }

    // 9=getProgress → posición actual en ms (MediaPlayer.getCurrentPosition)
    val progress: Int
        get() = try { transact(9) { it.readInt() } } catch (_: Exception) { -1 }

    val playingSong: SyuMediaSource?
        get() = try {
            transact(10) { reply -> if (reply.readInt() != 0) SyuMediaSource(reply) else null }
        } catch (_: Exception) {
            null
        }

    fun registerCallback(callback: SyuPlayerCallback) {
        try {
            transact(15, writer = { it.writeStrongBinder(callback.asBinder()) }) {}
        } catch (_: Exception) {}
    }

    fun unregisterCallback(callback: SyuPlayerCallback) {
        try {
            transact(16, writer = { it.writeStrongBinder(callback.asBinder()) }) {}
        } catch (_: Exception) {}
    }

    companion object {
        private const val DESCRIPTOR = "com.syu.media.IPlayer"
    }
}

/**
 * Callback de reproducción (réplica de com.syu.media.ICallback).
 * Los callbacks llegan en un hilo binder; el receptor debe ser thread-safe.
 */
class SyuPlayerCallback(
    private val onSongChanged: (SyuMediaSource?) -> Unit,
    private val onPlayStatusChanged: (Boolean) -> Unit,
    private val onPlayModeChanged: (Int) -> Unit
) : IInterface {

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                1 -> {
                    data.enforceInterface(DESCRIPTOR)
                    onSongChanged(if (data.readInt() != 0) SyuMediaSource(data) else null)
                }
                2 -> {
                    data.enforceInterface(DESCRIPTOR)
                    onPlayStatusChanged(data.readInt() != 0)
                }
                3 -> {
                    data.enforceInterface(DESCRIPTOR)
                    onPlayModeChanged(data.readInt())
                }
                // 1598968902 = IBinder.INTERFACE_TRANSACTION (código AIDL)
                1598968902 -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
            reply?.writeNoException()
            return true
        }
    }

    override fun asBinder(): IBinder = binder

    companion object {
        private const val DESCRIPTOR = "com.syu.media.ICallback"
    }
}
