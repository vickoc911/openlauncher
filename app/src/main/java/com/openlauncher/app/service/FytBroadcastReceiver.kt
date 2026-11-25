package com.openlauncher.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import com.openlauncher.app.util.FileLogger
import java.io.File

class FytBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

            val action = intent.action ?: return
            context?.let { FileLogger.log(it, "FytBroadcast: action=$action extras=${intent.extras?.keySet()}") }

            // Estrategia 1: Extraer Bitmap directo desde las extras del Intent
            var albumArt: Bitmap? = intent.getParcelableExtra("albumart")
            ?: intent.getParcelableExtra("cover")
            ?: intent.getParcelableExtra("bitmap")

            // Estrategia 2: Extraer mediante Ruta de Archivo Temporal de FYT o archivo local
            var filePath: String? = null
            if (albumArt == null) {
                val path = intent.getStringExtra("path")
                    ?: intent.getStringExtra("url")
                    ?: intent.getStringExtra("cover_path")
                    ?: intent.getStringExtra("music_path")

                if (!path.isNullOrEmpty() && File(path).exists()) {
                    filePath = path
                    // Intentar decodificar como imagen directa
                    try {
                        albumArt = BitmapFactory.decodeFile(path)
                    } catch (_: Exception) {}

                    // Si no es imagen (ej. es un .mp3 o .flac), extraer la carátula ID3 embebida
                    if (albumArt == null) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(path)
                            val embeddedPicture = retriever.embeddedPicture
                            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                                albumArt = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                            }
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                }
            }

            // Estrategia 3: Buscar en la ubicación cache habitual de la app de música FYT
            // (el paquete varía según el equipo: com.syu.music o com.syu.media)
            if (albumArt == null) {
                val artCandidates = listOf(
                    "/storage/emulated/0/Android/data/com.syu.music/cache/cover.jpg",
                    "/storage/emulated/0/Android/data/com.syu.media/cache/cover.jpg"
                )
                for (artPath in artCandidates) {
                    val file = File(artPath)
                    if (file.exists()) {
                        try {
                            albumArt = BitmapFactory.decodeFile(file.absolutePath)
                        } catch (_: Exception) {}
                        if (albumArt != null) break
                    }
                }
            }

            // Título y Artista transmitidos vía Broadcast (si vienen)
            var title = intent.getStringExtra("title")
                ?: intent.getStringExtra("song")
                ?: intent.getStringExtra("track")
                ?: intent.getStringExtra("name")
                ?: ""

            val artist = intent.getStringExtra("artist")
                ?: intent.getStringExtra("singer")
                ?: ""

            // Si no vino título pero tenemos la ruta del archivo, usar el nombre del archivo
            if (title.isBlank() && !filePath.isNullOrBlank()) {
                title = File(filePath).nameWithoutExtension
            }

            // Si tenemos portada o metadatos válidos, se los enviamos al servicio
            if (albumArt != null || title.isNotEmpty() || artist.isNotEmpty()) {
                context?.let { FileLogger.log(it, "FytBroadcast: enviado title='$title' artist='$artist' art=${albumArt != null}") }
                MediaListenerService.updateFromFytBroadcast(
                    title = title,
                    artist = artist,
                    albumArt = albumArt
                )
            }
    }

    companion object {
        fun register(context: Context): FytBroadcastReceiver {
            val receiver = FytBroadcastReceiver()
            val filter = IntentFilter().apply {
                addAction("com.syu.music.send")
                addAction("com.syu.music")
                addAction("com.syu.music.info")
                addAction("com.syu.music.title")
                addAction("com.syu.music.albumart")
                addAction("com.syu.music.playinfo")
                addAction("syu.widget.music.playinfo")
                addAction("com.syu.music.metachanged")
                // Mismas acciones con el paquete del reproductor en Junsun V1
                addAction("com.syu.media")
                addAction("com.syu.media.info")
                addAction("com.syu.media.title")
                addAction("com.syu.media.albumart")
                addAction("com.syu.media.playinfo")
                addAction("com.syu.media.playstate")
                addAction("com.syu.media.metachanged")
                addAction("com.syu.bt.music")
                addAction("com.syu.bt.playinfo")
                addAction("com.syu.bt.bykey")
                addAction("action.colormusic.title")
                addAction("com.syu.radio.playinfo")
                addAction("syu.widget.radio.playinfo")
                addAction("com.android.music.metachanged")
                addAction("com.android.music.playstatechanged")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            return receiver
        }
    }
}
