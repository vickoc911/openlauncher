package com.openlauncher.app.model

import android.graphics.Bitmap
import android.media.session.MediaController

data class NowPlayingState(
    val title: String,
    val artist: String,
    val albumArt: Bitmap?,
    // Full-resolution artwork URI when the source app provides one —
    // preferred over the (often downscaled) metadata bitmap
    val artUri: String? = null,
    val isPlaying: Boolean,
    val controller: MediaController?,
    // Progreso de reproducción (ms). Para fuentes FYT llega por el binder del
    // reproductor (IPlayer.getProgress); para apps externas el widget lo lee
    // del MediaController directamente.
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)
