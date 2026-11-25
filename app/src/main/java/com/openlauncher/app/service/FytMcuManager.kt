package com.openlauncher.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.openlauncher.app.util.FileLogger
import com.syu.ipc.IModuleCallback
import com.syu.ipc.IRemoteModule
import com.syu.ipc.IRemoteToolkit
import java.io.File

class FytMcuManager private constructor(private val context: Context) {

    private var remoteToolkit: IRemoteToolkit? = null
        private var isBound = false
        private var isRegistered = false
        private var retryCount = 0
        private val retryHandler = Handler(Looper.getMainLooper())
        private var pendingRetry: Runnable? = null

        private var currentSId = -1
        private var localTitle = ""
        private var localArtist = ""
        private var currentMusicPath = ""
        private var localAlbumArt: Bitmap? = null

            // Regex estricta para datos técnicos puros (frecuencia, bitrate, canales, tiempos)
            // NO debe incluir "MP3" o "WAV" para no descartar rutas de archivos como /storage/.../song.mp3
            private val technicalDataRegex = Regex("""(?i)^\s*(\d+(\.\d+)?\s*(khz|kbps|mhz)|mono|stereo|\d{2}:\d{2})\s*$""")

            private fun isAudioFilePath(path: String): Boolean {
                if (!path.startsWith("/")) return false
                val lower = path.lowercase()
                return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav") ||
                        lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg") ||
                        lower.endsWith(".wma") || lower.endsWith(".ape")
            }

            private val audioExtensionRegex = Regex("""\.(mp3|flac|wav|m4a|aac|ogg|wma|ape)$""", RegexOption.IGNORE_CASE)

            private fun stripAudioExtension(title: String): String =
                if (audioExtensionRegex.containsMatchIn(title)) title.replace(audioExtensionRegex, "") else title

            // Módulos del toolkit FYT/SYU (UIS7870): 0=Global, 1=Radio, 2=BT, 3=Música
            private val moduleCodeRanges = listOf(0 to 80, 1 to 31, 2 to 31, 3 to 31)

            private var lastLogLine: String? = null
            private var lastLogLineAt = 0L

            private fun log(msg: String) {
                // El MCU emite corrientes de code 70/41 cada 70ms: deduplicar
                // líneas idénticas cercanas para que el log sea legible.
                val now = System.currentTimeMillis()
                if (msg == lastLogLine && now - lastLogLineAt < 2_000) return
                lastLogLine = msg
                lastLogLineAt = now
                FileLogger.log(context.applicationContext, "FytMcu: $msg")
            }

                private val mainCallback = object : IModuleCallback.Stub() {
                    override fun update(updateCode: Int, ints: IntArray?, flts: FloatArray?, strs: Array<out String>?) {
                        log("IPC módulo 0: code=$updateCode ints=${ints?.toList()} strs=${strs?.toList()}")

                        // 1. Cambio de fuente de audio (Radio, BT, USB, etc.).
                        // Solo el módulo global interpreta code 0 como sId.
                        if (updateCode == 0 && ints != null && ints.isNotEmpty()) {
                            val sId = ints[0]
                            if (currentSId != sId) {
                                currentSId = sId
                                Log.d(TAG, "Fuente de audio cambiada a sId: $sId")
                                handleAudioSourceChange(sId)
                            }
                            return
                        }

                        // 2. Procesar cadenas de texto recibidas por IPC
                        if (strs != null && strs.isNotEmpty()) {
                            processMetadataStrings(updateCode, ints, strs)
                        }
                    }
                }

                // Callbacks para los módulos secundarios (radio/BT/música): mismo
                // manejo de metadatos, pero sin interpretar code 0 como cambio
                // de fuente (ese significado solo lo tiene el módulo global).
                private fun makeSideModuleCallback(moduleId: Int) = object : IModuleCallback.Stub() {
                    override fun update(updateCode: Int, ints: IntArray?, flts: FloatArray?, strs: Array<out String>?) {
                        log("IPC módulo $moduleId: code=$updateCode ints=${ints?.toList()} strs=${strs?.toList()}")
                        if (strs != null && strs.isNotEmpty()) {
                            processMetadataStrings(updateCode, ints, strs)
                        }
                    }
                }

                // Formato del título que envía el MCU: "01 Evanescence - everybodys-fool"
                // (número de pista + artista + " - " + canción). Permite separar
                // artista/canción cuando el campo de artista llega "NULL".
                private val trackTitleRegex = Regex("""^(?:\d{1,3}\s+)?(.+?)\s*[-–—]\s*(.+)$""")

                // Devuelve true si cambió de pista (para recargar la carátula)
                private fun applyTitle(raw: String): Boolean {
                    val m = trackTitleRegex.matchEntire(raw)
                    val song = m?.groupValues?.get(2)?.trim().orEmpty()
                    if (song.isNotEmpty()) {
                        if (song != localTitle) {
                            // Nueva pista: descartar el artista de la anterior
                            localTitle = song
                            localArtist = m!!.groupValues[1].trim()
                            return true
                        }
                    } else if (raw != localTitle) {
                        localArtist = ""
                        localTitle = raw
                        return true
                    }
                    return false
                }

                // El reproductor FYT cachea la carátula actual en su directorio
                // de app; el MCU no la envía por IPC.
                private val cacheArtPaths = listOf(
                    "/storage/emulated/0/Android/data/com.syu.media/cache/cover.jpg",
                    "/storage/emulated/0/Android/data/com.syu.music/cache/cover.jpg",
                    "/storage/emulated/0/Android/data/com.syu.media/cache/albumart.jpg",
                    "/storage/emulated/0/Android/data/com.syu.music/cache/albumart.jpg"
                )

                private var cacheArtGeneration = 0

                // ===== Servicio binder del reproductor FYT (com.syu.media) =====
                // Mismo mecanismo que el launcher original descompilado: bind
                // con action PlaybackService.AUDIO ("com.syu.media.music.
                // MusicService"), leer IPlayer.getPlayingSong() → ruta del
                // archivo → carátula ID3 embebida, e ICallback para los
                // cambios de pista/estado. La MediaSession del reproductor no
                // expone metadata ni el RemoteController legacy (verificado
                // en el dispositivo).
                private var playerBound = false
                private var playerClient: SyuPlayerClient? = null
                private var playerCallback: SyuPlayerCallback? = null
                private var playerRetryCount = 0
                private var playerPendingRetry: Runnable? = null
                private var currentSong: SyuMediaSource? = null

                // Ruta a la que corresponde localAlbumArt (null = cache/desconocida).
                // Evita que la cache del reproductor (cover.jpg, que puede ser
                // de la canción anterior) pise el arte ID3 de la pista actual.
                private var localArtPath: String? = null

                // Ticker de progreso: IPlayer.getProgress() cada segundo
                // (el launcher original lo hace cada 500ms).
                private var progressTickCount = 0

                private val progressTicker = object : Runnable {
                    override fun run() {
                        val client = playerClient
                        if (client != null && playerBound) {
                            val pos = client.progress
                            if (pos >= 0) {
                                progressTickCount++
                                // Diagnóstico: cada 5 s; y siempre si
                                // getProgress devuelve 0 (barra atascada).
                                if (progressTickCount % 5 == 0 || pos == 0) {
                                    log("Player: progreso pos=${pos}ms dur=${currentSong?.duration} song='${currentSong?.title}'")
                                }
                                MediaListenerService.updateFytProgress(
                                    pos.toLong(),
                                    currentSong?.duration?.toLong() ?: 0L
                                )
                            }
                        }
                        retryHandler.postDelayed(this, 1_000L)
                    }
                }

                // Al detectar cambio de pista por el MCU, encuestar el
                // reproductor (getPlayingSong) hasta que la ruta cambie:
                // onSongChanged puede no llegar o llegar tarde en la primera
                // transición (p. ej. desde pausa), y con esto la carátula no
                // depende de ese callback.
                private var pendingArtCheck: Runnable? = null

                private fun requestPlayerArtRefresh() {
                    pendingArtCheck?.let { retryHandler.removeCallbacks(it) }
                    var attempts = 0
                    val check = object : Runnable {
                        override fun run() {
                            val client = playerClient
                            if (client == null || !playerBound) {
                                pendingArtCheck = null
                                return
                            }
                            attempts++
                            try {
                                client.playingSong?.let { song ->
                                    currentSong = song
                                    val path = song.path
                                    if (path.isNotBlank() && path != currentMusicPath) {
                                        log("Player: pista actual (encuesta) path='${song.path}'")
                                        currentMusicPath = path
                                        MediaListenerService.updateFytProgress(0, song.duration.toLong())
                                        extractEmbeddedArtAsync(path)
                                        pendingArtCheck = null
                                        return
                                    }
                                }
                            } catch (_: Exception) {}
                            if (attempts < 6) {
                                retryHandler.postDelayed(this, 800L)
                            } else {
                                pendingArtCheck = null
                            }
                        }
                    }
                    pendingArtCheck = check
                    retryHandler.postDelayed(check, 400L)
                }

                private fun scheduleCacheArtLoad() {
                    cacheArtGeneration++
                    val gen = cacheArtGeneration
                    // Reintentos: el reproductor puede escribir la imagen con retardo
                    for (delay in longArrayOf(300L, 1200L, 3000L)) {
                        retryHandler.postDelayed({
                            if (gen != cacheArtGeneration) return@postDelayed
                            loadCacheArt()
                        }, delay)
                    }
                }

                private fun loadCacheArt() {
                    // Ya tenemos el arte ID3 de la pista actual: la cache
                    // (cover.jpg del reproductor) puede ser de la canción
                    // anterior y no debe pisar el arte correcto.
                    if (localAlbumArt != null && localArtPath == currentMusicPath) return
                    for (path in cacheArtPaths) {
                        val file = File(path)
                        if (!file.exists()) continue
                        val bmp = try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                        if (bmp != null) {
                            log("Carátula cargada desde cache: $path")
                            localAlbumArt = bmp
                            MediaListenerService.updateFytAlbumArt(bmp)
                            return
                        }
                    }
                }

                // Protocolo del MCU (verificado en el log del dispositivo):
                //   code 49             → título actual
                //   code 74 ints=[s,0]  → título
                //   code 74 ints=[s,1]  → artista (puede ser "NULL")
                //   code 74 ints=[s,2]  → carpeta/playlist (no se muestra)
                //   code 73 (firmwares antiguos) → título
                private fun processMetadataStrings(updateCode: Int, ints: IntArray?, strs: Array<out String>) {
                    // A. Primero buscar si alguna de las cadenas es la ruta de un archivo de audio
                    for (str in strs) {
                        val text = str.trim()
                        if (isAudioFilePath(text)) {
                            if (currentMusicPath != text) {
                                currentMusicPath = text
                                localAlbumArt = extractAlbumArtFromPath(currentMusicPath)
                                localArtPath = if (localAlbumArt != null) currentMusicPath else null
                            }
                            if (localTitle.isBlank()) {
                                localTitle = File(text).nameWithoutExtension
                            }
                        }
                    }

                    // B. Decodificar metadatos según el código del MCU
                    val rawText = strs.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    val validText = rawText.isNotEmpty() &&
                            !rawText.equals("null", ignoreCase = true) &&
                            !technicalDataRegex.matches(rawText) &&
                            !isAudioFilePath(rawText)

                    var newTrack = false
                    if (validText) {
                        when (updateCode) {
                            49, 73 -> newTrack = applyTitle(rawText)
                            74 -> {
                                val subCode = ints?.getOrNull(1) ?: -1
                                when (subCode) {
                                    0 -> newTrack = applyTitle(rawText)
                                    1 -> localArtist = rawText
                                    2 -> Unit // carpeta/playlist
                                    else -> {
                                        if (localTitle.isBlank()) newTrack = applyTitle(rawText)
                                        else localArtist = rawText
                                    }
                                }
                            }
                        }
                    }
                    if (newTrack) {
                        scheduleCacheArtLoad()
                        requestPlayerArtRefresh()
                    }

                    // Limpieza de extensión de archivo en el título
                    val cleanTitle = stripAudioExtension(localTitle)

                    // Si no hay título válido todavía, ignoramos esta actualización
                    if (cleanTitle.isBlank()) return

                    // Formatear artista dinámicamente según la fuente
                    val cleanArtist = if (localArtist.isBlank() || localArtist.equals("null", ignoreCase = true)) {
                        when (currentSId) {
                            1 -> "Radio FM"
                            2, 3 -> "Bluetooth Audio"
                            else -> "Música USB"
                        }
                    } else {
                        localArtist
                    }

                    // Enviar metadatos + carátula extraída al servicio central de medios
                    MediaListenerService.updateFromFytService(
                        title = cleanTitle,
                        artist = cleanArtist,
                        isPlaying = true,
                        albumArt = localAlbumArt
                    )
                }

                private fun handleAudioSourceChange(sId: Int) {
                    // En FYT/SYU UIS7862:
                    // sId 10 = APP_ID_THIRD_PLAYER (Spotify, Deezer, etc.)
                    // sId 0 = NULL / Desconectado
                    // sId 1 = Radio, 2 = BT Phone, 3 = BT Music, 8 = Reproductor Local/Chino, 13 = Car USB
                    val isFytSource = sId != 10 && sId != 0
                    log("Fuente de audio sId=$sId → fuente FYT activa: $isFytSource")
                    MediaListenerService.notifyFytSourceChanged(isFytSource)

                    if (isFytSource) {
                        // Enlazar el servicio del reproductor: carátula (pista
                        // actual → ID3 embebido) y estado real de play/pause.
                        playerRetryCount = 0
                        bindPlayerService()
                    } else {
                        cancelPlayerRetry()
                        // No desvinculamos: el reproductor puede volver a ser
                        // la fuente sin pagar el coste de volver a enlazar.
                    }

                    if (isFytSource) {
                        // Si ya teníamos título en memoria, emitimos inmediatamente
                        if (localTitle.isNotBlank()) {
                            val cleanTitle = stripAudioExtension(localTitle)
                            val cleanArtist = if (localArtist.isBlank() || localArtist.equals("null", ignoreCase = true)) {
                                when (sId) {
                                    1 -> "Radio FM"
                                    2, 3 -> "Bluetooth Audio"
                                    else -> "Música USB"
                                }
                            } else {
                                localArtist
                            }
                            MediaListenerService.updateFromFytService(
                                title = cleanTitle,
                                artist = cleanArtist,
                                isPlaying = true,
                                albumArt = localAlbumArt
                            )
                        }
                    }
                }

                /**
                 * Extrae la portada embedded (ID3v2) directamente del archivo de música en el almacenamiento USB
                 */
                private fun extractAlbumArtFromPath(filePath: String): Bitmap? {
                    val file = File(filePath)
                    if (!file.exists()) return null

                    val retriever = MediaMetadataRetriever()
                    return try {
                        retriever.setDataSource(filePath)
                        val embeddedPicture = retriever.embeddedPicture
                        if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                            BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extrayendo carátula ID3 de $filePath: ${e.message}")
                        null
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                }

                private val mcuConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Log.d(TAG, "Conectado al MCU Service com.syu.ms")
                        log("Conectado al servicio com.syu.ms")
                        val toolkit = IRemoteToolkit.Stub.asInterface(service)
                        remoteToolkit = toolkit
                        if (toolkit == null) {
                            log("IRemoteToolkit nulo tras conectar")
                            return
                        }

                        // Registrar en todos los módulos disponibles: el firmware
                        // puede entregar la música por el módulo 3 (y BT por el 2),
                        // no solo por el módulo global 0.
                        var anyRegistered = false
                        for ((moduleId, maxCode) in moduleCodeRanges) {
                            try {
                                val module: IRemoteModule? = toolkit.getRemoteModule(moduleId)
                                if (module == null) {
                                    log("Módulo $moduleId no disponible")
                                    continue
                                }
                                val callback = if (moduleId == 0) mainCallback else makeSideModuleCallback(moduleId)
                                var ok = 0
                                for (code in 0..maxCode) {
                                    try {
                                        module.register(callback, code, 1)
                                        ok++
                                    } catch (_: Exception) {}
                                }
                                log("Módulo $moduleId registrado: $ok de ${maxCode + 1} códigos")
                                anyRegistered = anyRegistered || ok > 0
                            } catch (e: Exception) {
                                log("Error al registrar módulo $moduleId: ${e.message}")
                            }
                        }
                        isRegistered = anyRegistered
                        cancelPendingRetry()
                        if (!anyRegistered) {
                            log("Ningún módulo MCU registró códigos")
                        }
                        // Probar ya el servicio del reproductor (el launcher
                        // original se enlaza en cuanto arranca la página de
                        // música, que en su home siempre está visible).
                        bindPlayerService()
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        log("Servicio MCU desconectado; reintentando")
                        remoteToolkit = null
                        isBound = false
                        isRegistered = false
                        bindService()
                    }
                }

                fun bindService() {
                    if (isBound) return
                        try {
                            val intent = Intent("com.syu.ms.toolkit").apply {
                                setPackage("com.syu.ms")
                            }
                            isBound = context.bindService(intent, mcuConnection, Context.BIND_AUTO_CREATE)
                            if (!isBound) {
                                // bindService en falso = el servicio com.syu.ms aún no
                                // existe (arranque del equipo). Sin reintento la vía IPC
                                // queda muerta para todo el proceso.
                                log("bind a com.syu.ms falló; programando reintento")
                                scheduleBindRetry()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al vincular con el servicio MCU: ${e.message}")
                            scheduleBindRetry()
                        }
                }

                private fun scheduleBindRetry() {
                    if (isRegistered || retryCount >= MAX_BIND_RETRIES) {
                        if (!isRegistered) log("Reintentos de conexión MCU agotados ($retryCount)")
                        return
                    }
                    pendingRetry?.let { retryHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        pendingRetry = null
                        if (isRegistered) return@Runnable
                        retryCount++
                        log("Reintento de conexión MCU #$retryCount")
                        if (isBound) {
                            try { context.unbindService(mcuConnection) } catch (_: Exception) {}
                            isBound = false
                        }
                        bindService()
                    }
                    pendingRetry = runnable
                    retryHandler.postDelayed(runnable, BIND_RETRY_DELAY_MS)
                }

                private fun cancelPendingRetry() {
                    pendingRetry?.let { retryHandler.removeCallbacks(it) }
                    pendingRetry = null
                }

                fun unbindService() {
                    cancelPendingRetry()
                    cancelPlayerRetry()
                    retryHandler.removeCallbacks(progressTicker)
                    pendingArtCheck?.let { retryHandler.removeCallbacks(it) }
                    pendingArtCheck = null
                    // Notificar al reproductor que dejamos de recibir callbacks
                    // antes de soltar el binder.
                    playerCallback?.let { cb ->
                        try { playerClient?.unregisterCallback(cb) } catch (_: Exception) {}
                    }
                    playerClient = null
                    playerCallback = null
                    if (playerBound) {
                        try { context.unbindService(playerConnection) } catch (_: Exception) {}
                        playerBound = false
                    }
                    if (isBound) {
                        try {
                            context.unbindService(mcuConnection)
                            isBound = false
                        } catch (_: Exception) {}
                    }
                    isRegistered = false
                    remoteToolkit = null
                }

                // ===== Servicio del reproductor FYT (com.syu.media) =====
                // Carátula: el reproductor no la envía por MCU ni la pone en su
                // MediaSession. El launcher original lee la pista actual vía
                // IPlayer.getPlayingSong() y decodifica la imagen embebida en el
                // ID3 del archivo (MediaSource.getAlbumCover()). Aquí igual.
                private val playerConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        if (service == null) {
                            log("Player: binder nulo al conectar")
                            playerBound = false
                            schedulePlayerRetry()
                            return
                        }
                        playerBound = true
                        playerRetryCount = 0
                        playerActionAttempts = 0
                        playerPendingRetry?.let { retryHandler.removeCallbacks(it) }
                        playerPendingRetry = null
                        log("Conectado al servicio del reproductor com.syu.media")

                        val client = SyuPlayerClient(service)
                        playerClient = client

                        val callback = SyuPlayerCallback(
                            onSongChanged = { song ->
                                log("Player: pista cambiada path='${song?.path}' title='${song?.title}'")
                                currentSong = song
                                val path = song?.path
                                if (!path.isNullOrBlank() && path != currentMusicPath) {
                                    currentMusicPath = path
                                    MediaListenerService.updateFytProgress(0, song?.duration?.toLong() ?: 0L)
                                    extractEmbeddedArtAsync(path)
                                }
                            },
                            onPlayStatusChanged = { playing ->
                                log("Player: estado de reproducción=$playing")
                                MediaListenerService.updateFytPlaybackState(playing)
                            },
                            onPlayModeChanged = { mode ->
                                log("Player: modo de reproducción=$mode")
                            }
                        )
                        playerCallback = callback
                        client.registerCallback(callback)

                        // Estado inicial: reproducción real + pista actual
                        try {
                            MediaListenerService.updateFytPlaybackState(client.isPlaying)
                        } catch (_: Exception) {}
                        try {
                            client.playingSong?.let { song ->
                                log("Player: pista actual path='${song.path}'")
                                currentSong = song
                                if (song.path.isNotBlank()) {
                                    currentMusicPath = song.path
                                    extractEmbeddedArtAsync(song.path)
                                }
                            }
                        } catch (_: Exception) {}
                        // Ticker de progreso (1 s)
                        retryHandler.removeCallbacks(progressTicker)
                        retryHandler.post(progressTicker)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        log("Player: servicio desconectado")
                        playerCallback?.let { cb ->
                            try { playerClient?.unregisterCallback(cb) } catch (_: Exception) {}
                        }
                        playerClient = null
                        playerCallback = null
                        playerBound = false
                        retryHandler.removeCallbacks(progressTicker)
                        // Reintentar solo si seguimos con una fuente FYT
                        if (currentSId != 10 && currentSId != 0) schedulePlayerRetry()
                    }
                }

                fun bindPlayerService() {
                    if (playerBound) return
                    try {
                        val intent = resolvePlayerIntent()
                        val ok = context.bindService(intent, playerConnection, Context.BIND_AUTO_CREATE)
                        if (!ok) {
                            log("bind al servicio del reproductor falló; programando reintento")
                            schedulePlayerRetry()
                        }
                    } catch (e: Exception) {
                        log("Error al vincular con el servicio del reproductor: ${e.message}")
                        schedulePlayerRetry()
                    }
                }

                // Intent para el servicio de MÚSICA del reproductor. El launcher
                // original (MediaPresenter) no usa la clase PlaybackService
                // (esa vive en el propio launcher); usa la action
                // PlaybackService.AUDIO = "com.syu.media.music.MusicService".
                // Si en este firmware el action no está declarado en el
                // manifiesto de com.syu.media, cae a componente explícito.
                private var playerExplicitComponent: String? = null
                private var playerServicesLogged = false
                private var playerActionAttempts = 0

                private fun resolvePlayerIntent(): Intent {
                    // 1. Componente ya elegido en un intento anterior
                    val explicit = playerExplicitComponent
                    if (explicit != null) {
                        return Intent().setComponent(ComponentName(PLAYER_PACKAGE, explicit))
                    }
                    val actionIntent = Intent(PLAYER_SERVICE_ACTION).apply { setPackage(PLAYER_PACKAGE) }
                    // 2. Diagnóstico: servicios que declara el paquete (nombre,
                    //    exported, permission) — sirve si el action no resuelve.
                    val services: List<String> = try {
                        val pkg = context.packageManager
                            .getPackageInfo(PLAYER_PACKAGE, PackageManager.GET_SERVICES)
                        val names = pkg.services?.map { it.name }?.toList().orEmpty()
                        if (!playerServicesLogged && names.isNotEmpty()) {
                            playerServicesLogged = true
                            log("Player: servicios de $PLAYER_PACKAGE: " +
                                pkg.services!!.joinToString(", ") { s ->
                                    "${s.name}(exported=${s.exported})"
                                })
                        }
                        names
                    } catch (e: Exception) {
                        log("Player: no se pudo consultar $PLAYER_PACKAGE: ${e.message}")
                        emptyList()
                    }
                    // 3. Si el action no resuelve (o dos intentos por action
                    //    fallaron: servicio no exportado, etc.), cambiar a
                    //    componente explícito.
                    val matches = try {
                        context.packageManager.queryIntentServices(actionIntent, 0)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (matches.isEmpty() || playerActionAttempts >= 2) {
                        val candidate = services.firstOrNull { it == "com.syu.media.music.MusicService" }
                            ?: services.firstOrNull { it.contains("MusicService", ignoreCase = true) }
                            ?: services.firstOrNull {
                                it.contains("Playback", ignoreCase = true) && it.endsWith("Service")
                            }
                            ?: "com.syu.media.music.MusicService"
                        playerExplicitComponent = candidate
                        log("Player: usando componente explícito $candidate")
                        return Intent().setComponent(ComponentName(PLAYER_PACKAGE, candidate))
                    }
                    playerActionAttempts++
                    return actionIntent
                }

                private fun schedulePlayerRetry() {
                    if (playerBound || playerRetryCount >= MAX_PLAYER_RETRIES) {
                        if (!playerBound) log("Reintentos del servicio del reproductor agotados ($playerRetryCount)")
                        return
                    }
                    playerPendingRetry?.let { retryHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        playerPendingRetry = null
                        if (playerBound) return@Runnable
                        playerRetryCount++
                        log("Reintento del servicio del reproductor #$playerRetryCount")
                        bindPlayerService()
                    }
                    playerPendingRetry = runnable
                    retryHandler.postDelayed(runnable, PLAYER_RETRY_DELAY_MS)
                }

                private fun cancelPlayerRetry() {
                    playerPendingRetry?.let { retryHandler.removeCallbacks(it) }
                    playerPendingRetry = null
                }

                /**
                 * Extrae la carátula ID3 embebida en segundo plano
                 * (MediaMetadataRetriever bloquea; no puede ir en el binder).
                 * Sin imagen embebida, reintenta la cache del reproductor.
                 */
                private fun extractEmbeddedArtAsync(filePath: String) {
                    Thread {
                        val bmp = try { extractAlbumArtFromPath(filePath) } catch (_: Exception) { null }
                        retryHandler.post {
                            if (filePath != currentMusicPath) {
                                // Ya cambió de pista: no pisar la carátula de la
                                // pista actual con la de una anterior.
                                log("Carátula ID3 descartada (pista ya cambió): $filePath")
                                return@post
                            }
                            if (bmp != null) {
                                log("Carátula ID3 extraída: ${bmp.width}x${bmp.height} de $filePath")
                                localAlbumArt = bmp
                                localArtPath = filePath
                                // El ID3 llegó: cancelar cargas pendientes de la
                                // cache (puede contener la canción anterior).
                                cacheArtGeneration++
                                MediaListenerService.updateFytAlbumArt(bmp)
                            } else {
                                log("Sin carátula ID3 en $filePath; probando cache")
                                loadCacheArt()
                            }
                        }
                    }.start()
                }

                companion object {
                    private const val TAG = "FytMcuManager"
                    private const val MAX_BIND_RETRIES = 20
                    private const val BIND_RETRY_DELAY_MS = 5_000L
                    private const val MAX_PLAYER_RETRIES = 10
                    private const val PLAYER_RETRY_DELAY_MS = 5_000L
                    // Paquete y action del servicio de música del reproductor
                    // (PlaybackService.AUDIO del launcher original).
                    private const val PLAYER_PACKAGE = "com.syu.media"
                    private const val PLAYER_SERVICE_ACTION = "com.syu.media.music.MusicService"

                    @Volatile
                    private var INSTANCE: FytMcuManager? = null

                        fun getInstance(context: Context): FytMcuManager {
                            return INSTANCE ?: synchronized(this) {
                                INSTANCE ?: FytMcuManager(context.applicationContext).also { INSTANCE = it }
                            }
                        }
                }
}
