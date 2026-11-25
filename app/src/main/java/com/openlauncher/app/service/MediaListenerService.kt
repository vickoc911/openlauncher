package com.openlauncher.app.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.input.InputManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaMetadataEditor
import android.media.RemoteController
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.util.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaListenerService : NotificationListenerService() {
    private var fytReceiver: FytBroadcastReceiver? = null
    private var activeController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var mediaSessionManager: MediaSessionManager
    private var lastLoggedSelection: String? = null
    private var lastLoggedSessions: String? = null

    private val ignoredPackages = setOf(
                    "com.txznet.smartadapter",
                    "com.txznet.music",
                    "com.txznet.txz",
                    "android",
                    "com.zjinnova.zlink",
                    "com.zjinnova.zlink5",
                    "com.zjinnova.zlink.auto",
                    "com.google.android.projection.gearhead"
                )

    override fun attachBaseContext(base: Context) {
                    super.attachBaseContext(object : ContextWrapper(base) {
                        override fun unbindService(conn: ServiceConnection) {
                            try {
                                super.unbindService(conn)
                            } catch (_: IllegalArgumentException) {}
                        }
                    })
                }

    override fun onCreate() {
                    super.onCreate()
                    instance = this

                    // 1. Iniciar BroadcastReceiver legacy (de respaldo)
        try {
                        fytReceiver = FytBroadcastReceiver.register(this)
        } catch (e: Exception) {
            logMediaEvent("No se pudo registrar el receptor FYT", e)
        }

                    // 2. Iniciar enlace IPC AIDL directo con el MCU de FYT (USB/Radio/BT)
        try {
                        FytMcuManager.getInstance(this).bindService()
        } catch (e: Exception) {
            logMediaEvent("No se pudo iniciar el IPC FYT", e)
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener {
        // Android no garantiza el hilo de este callback. Serializamos la lectura y
        // el registro de callbacks en el hilo principal del servicio.
        mainHandler.post { refreshNowPlaying() }
    }

    private val controllerCallback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        refreshNowPlaying()
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        refreshNowPlaying()
                    }

                    override fun onSessionDestroyed() {
                        refreshNowPlaying()
                    }
                }

    // API legacy (pre-Android 5) que usa el reproductor FYT com.syu.media para
    // empujar metadata y carátula. El launcher original (com.syu.launcher)
    // muestra el cover del carro exactamente así:
    // AudioManager.registerRemoteController + setArtworkConfiguration(960,960).
    private var legacyRemoteController: RemoteController? = null

    private val audioFileRegex = Regex("""\.(mp3|flac|wav|m4a|aac|ogg|wma|ape)$""", RegexOption.IGNORE_CASE)

    private val legacyListener = object : RemoteController.OnClientUpdateListener {
        override fun onClientChange(clearing: Boolean) {
            logMediaEvent("RemoteController legacy: cambio de cliente (clearing=$clearing)")
        }

        override fun onClientMetadataUpdate(metadataEditor: RemoteController.MetadataEditor) {
            applyLegacyMetadata(metadataEditor)
        }

        override fun onClientPlaybackStateUpdate(state: Int) {
            handleLegacyPlaybackState(state)
        }

        override fun onClientPlaybackStateUpdate(state: Int, stateChangeTimeMs: Long, currentPosMs: Long, speed: Float) {
            handleLegacyPlaybackState(state)
        }

        override fun onClientTransportControlUpdate(transportControlFlags: Int) {}
    }

    private fun applyLegacyMetadata(editor: MediaMetadataEditor) {
        fun safeString(key: Int): String =
            try { editor.getString(key, "").trim() } catch (_: Exception) { "" }

        // Claves legacy: TITLE=7, ARTIST=2, ART=100, ALBUM_ART=102, DISPLAY_ICON=103
        // Se leen por separado: la store legacy guarda algunas claves de bitmap
        // como String (p. ej. key 102 = ruta; el dispositivo lanzaba
        // IllegalArgumentException al pedir getBitmap), y un fallo en una clave
        // no debe tumbar las demás.
        val title = safeString(7)
        val artist = safeString(2)
        var art: Bitmap? = null
        for (key in intArrayOf(100, 102, 103)) {
            try {
                art = editor.getBitmap(key, null)
            } catch (_: Exception) {
                val raw = safeString(key)
                if (raw.startsWith("/") || raw.startsWith("file://")) {
                    art = try {
                        BitmapFactory.decodeFile(raw.removePrefix("file://"))
                    } catch (_: Exception) { null }
                }
            }
            if (art != null) break
        }
        logMediaEvent("RemoteController legacy: title='$title' artist='$artist' art=${if (art != null) "${art.width}x${art.height}" else "none"}")
        if (art != null) updateFytAlbumArt(art)
        // Título/artista ya llegan por el MCU (verificado en el carro);
        // solo se usa este dato legacy si el MCU aún no nos dio nada.
        if (fytTitle.isBlank() && title.isNotBlank()
            && !title.equals("null", ignoreCase = true)
            && !title.equals("Unknown", ignoreCase = true)
            && !audioFileRegex.containsMatchIn(title)
        ) {
            updateFromFytService(title, artist, fytIsPlaying, art)
        }
    }

    private fun handleLegacyPlaybackState(state: Int) {
        // Estados de MediaPlayer: 0=error, 1=none, 2=paused, 3=playing,
        // 4=buffering, 5=stopped. Esto resuelve el play/pause real (el MCU
        // no nos da el código de pausa mapeado todavía).
        val playing = state == 3
        if (playing != fytIsPlaying) {
            fytIsPlaying = playing
            logMediaEvent("RemoteController legacy: estado=$state -> reproduciendo=$playing")
            pushFytState()
        }
    }

    private fun registerLegacyRemoteController() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val rc = RemoteController(this, legacyListener)
            if (!am.registerRemoteController(rc)) {
                logMediaEvent("RemoteController legacy: registro rechazado")
                return
            }
            legacyRemoteController = rc
            // Pedir carátula grande al cliente (el launcher original usa 960x960)
            rc.setArtworkConfiguration(960, 960)
            rc.setSynchronizationMode(1)
            logMediaEvent("RemoteController legacy registrado (artwork 960x960)")
            // Si el reproductor ya estaba activo, leer su metadata actual
            try {
                applyLegacyMetadata(rc.editMetadata())
            } catch (_: Exception) {}
        } catch (e: Exception) {
            logMediaEvent("RemoteController legacy: fallo al registrar", e)
        }
    }

    private fun unregisterLegacyRemoteController() {
        legacyRemoteController?.let { rc ->
            try {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterRemoteController(rc)
            } catch (_: Exception) {}
        }
        legacyRemoteController = null
    }

    override fun onListenerConnected() {
                    super.onListenerConnected()
                    instance = this
                    isConnected.value = true

        try {
                        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                        val component = ComponentName(this, MediaListenerService::class.java)
                        mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            logMediaEvent("Listener MediaSession conectado")
        } catch (e: Exception) {
            logMediaEvent("No se pudo registrar el listener de MediaSession", e)
        }

        registerLegacyRemoteController()

                    refreshNowPlaying()
                }

    override fun onListenerDisconnected() {
                    super.onListenerDisconnected()
                    cleanupMediaManager()
                    instance = null
                    isConnected.value = false
                    clearController()
                }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
                    refreshNowPlaying()
                }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
                    refreshNowPlaying()
                }

    override fun onDestroy() {
                    // Desvincular IPC de FYT
        try {
                        FytMcuManager.getInstance(this).unbindService()
        } catch (e: Exception) {
            logMediaEvent("No se pudo desvincular el IPC FYT", e)
        }

                    // Desregistrar Receiver
                    fytReceiver?.let {
                        try { unregisterReceiver(it) } catch (_: Exception) {}
                    }
                    cleanupMediaManager()
                    instance = null
                    clearController()
                    super.onDestroy()
                }

    private fun cleanupMediaManager() {
                    try {
                        if (::mediaSessionManager.isInitialized) {
                            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
                        }
        } catch (e: Exception) {
            logMediaEvent("No se pudo desregistrar el listener de MediaSession", e)
        }
        unregisterLegacyRemoteController()
    }

    fun clearController() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }

    fun clearActiveController() = clearController()

    private fun refreshNowPlaying() {
        val msm = if (::mediaSessionManager.isInitialized) mediaSessionManager
        else (getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return)

        val sessions: List<MediaController> = try {
            msm.getActiveSessions(ComponentName(this, MediaListenerService::class.java))
        } catch (e: Exception) {
            logMediaEvent("No se pudieron consultar las sesiones activas", e)
            emptyList()
        }

        // Diagnóstico: qué sesiones ve Android en este momento (paquete|estado)
        logActiveSessions(sessions)

        // Detectar si el reproductor nativo FYT (com.syu.music / com.syu.media)
        // está sonando, aunque no tenga metadata en su MediaSession (es su
        // comportamiento normal).
        val syuMusicPlaying = sessions.any { ctrl ->
            ctrl.packageName.startsWith("com.syu") &&
            ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        // Recordar el paquete real del reproductor FYT (p. ej. com.syu.media)
        // para que el tap en el widget lo abra directamente.
        sessions.firstOrNull { it.packageName.startsWith("com.syu") }?.let {
            if (it.packageName != lastMediaPackage) lastMediaPackage = it.packageName
        }

        // Sesiones externas válidas (Spotify, YouTube Music, etc.) con metadata real
        val validSessions = sessions.filter { controller ->
            if (controller.packageName in ignoredPackages) return@filter false
            // Excluir paquetes com.syu.* del pool de controladores externos —
            // sus metadatos llegan por IPC (FytMcuManager), no por MediaMetadata.
            if (controller.packageName.startsWith("com.syu")) return@filter false
            true
        }

        val activePlaying = validSessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val fytHasValidData = fytTitle.isNotBlank() && !fytTitle.contains("Unknown", ignoreCase = true)

        // CASO 1: Un reproductor externo real (Spotify, etc.) está sonando activamente
        if (activePlaying != null) {
            isFytActiveSource = false
            if (activePlaying.sessionToken != activeController?.sessionToken) {
                clearActiveController()
                activeController = activePlaying
                activePlaying.registerCallback(controllerCallback, mainHandler)
            }
            updateFromController(activeController)
            return
        }

        // CASO 2: El reproductor nativo FYT está sonando O FYT tiene datos válidos del IPC
        // Los metadatos FYT se conservan como respaldo, pero no pueden eclipsar
        // una MediaSession después de que el MCU haya comunicado otra fuente.
        if (syuMusicPlaying || (isFytActiveSource && fytHasValidData)) {
            clearActiveController()
            isFytActiveSource = true
            // Si el IPC no nos dio carátula, intentar leerla de la
            // MediaMetadata de la sesión com.syu.* (puede estar presente
            // aunque la sesión no tenga estado de reproducción).
            if (fytAlbumArt == null) {
                sessions.firstOrNull { it.packageName.startsWith("com.syu") }?.let { c ->
                    try {
                        val m = c.metadata
                        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                        if (art != null) {
                            fytAlbumArt = art
                            logMediaEvent("Carátula obtenida de la MediaSession ${c.packageName}")
                        }
                    } catch (_: Exception) {}
                }
            }
            if (fytHasValidData) {
                // Tenemos datos reales del IPC
                pushFytState()
            } else {
                // El reproductor nativo está sonando pero el IPC aún no nos dio el título.
                // Emitimos un estado provisional para que no aparezca "TAP TO PLAY MUSIC".
                postNowPlaying(
                    NowPlayingState(
                        title = "Música",
                        artist = "Reproductor local",
                        albumArt = null,
                        artUri = null,
                        isPlaying = true,
                        controller = null
                    )
                )
            }
            logSelectedSource("FYT", null, fytIsPlaying)
            return
        }

        // CASO 3: Ningún reproductor activo — mostrar sesión en pausa si existe
        val active = validSessions.firstOrNull()
        if (active != null) {
            isFytActiveSource = false
            if (active.sessionToken != activeController?.sessionToken) {
                clearActiveController()
                activeController = active
                active.registerCallback(controllerCallback, mainHandler)
            }
            updateFromController(activeController)
        } else {
            clearActiveController()
            logSelectedSource("none", null, false)
            postNowPlaying(null)
        }
    }

    private fun updateFromController(controller: MediaController?) {
        val fytHasValidData = fytTitle.isNotBlank() && !fytTitle.contains("Unknown", ignoreCase = true)

        if (controller == null) {
            if (fytHasValidData) {
                pushFytState()
            }
            return
        }

        lastMediaPackage = controller.packageName
        val meta = controller.metadata

        val rawTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""

        val rawArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val artUriString = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)

        if (rawTitle.isEmpty() || rawTitle.equals("Desconocido", ignoreCase = true) || rawTitle.contains("Unknown", ignoreCase = true)) {
            if (fytHasValidData) {
                clearActiveController()
                pushFytState()
                return
            }
        }

        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        logSelectedSource("MediaSession", controller.packageName, isPlaying)

        postNowPlaying(
            NowPlayingState(
                title = if (rawTitle.isBlank()) "Desconocido" else rawTitle,
                artist = rawArtist,
                albumArt = art,
                artUri = artUriString,
                isPlaying = isPlaying,
                controller = controller
            )
        )
    }

    private fun logActiveSessions(sessions: List<MediaController>) {
        var summary = if (sessions.isEmpty()) "sin sesiones activas"
        else sessions.joinToString(", ") { c -> "${c.packageName}|st=${c.playbackState?.state ?: -1}" }
        // Diagnóstico: la sesión com.syu.* expone metadata (título/carátula)?
        sessions.firstOrNull { it.packageName.startsWith("com.syu") }?.let { c ->
            try {
                val m = c.metadata
                val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                summary += " | syuMeta: '${m?.getString(MediaMetadata.METADATA_KEY_TITLE)}' art=${if (art != null) "${art.width}x${art.height}" else "none"}"
            } catch (_: Exception) {}
        }
        if (summary == lastLoggedSessions) return
        lastLoggedSessions = summary
        logMediaEvent("Sesiones activas: $summary")
    }

    private fun logSelectedSource(source: String, packageName: String?, isPlaying: Boolean) {
        val selection = "$source|${packageName.orEmpty()}|$isPlaying"
        if (selection == lastLoggedSelection) return
        lastLoggedSelection = selection
        logMediaEvent("Fuente seleccionada: $source, paquete=${packageName ?: "—"}, reproduciendo=$isPlaying")
    }

    private fun logMediaEvent(message: String, error: Throwable? = null) {
        if (error == null) Log.d(TAG, message) else Log.e(TAG, message, error)
        // Se registra sólo en transiciones y fallos; los callbacks normales no
        // generan una entrada repetida en el archivo de diagnóstico.
        FileLogger.log(this, "MediaSession: $message${error?.let { " (${it.javaClass.simpleName}: ${it.message})" }.orEmpty()}")
    }

    companion object {
        private const val TAG = "MediaListener"
        const val ACTION_FORCE_SYNC = "com.openlauncher.app.FORCE_SYNC"

        private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
        val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying
        val isConnected = MutableStateFlow(false)

        var lastMediaPackage: String = ""

        private var fytTitle: String = ""
        private var fytArtist: String = ""
        private var fytIsPlaying: Boolean = true
        private var fytAlbumArt: Bitmap? = null
        private var fytPositionMs: Long = 0L
        private var fytDurationMs: Long = 0L
        private var isFytActiveSource: Boolean = false

        @Volatile
        private var instance: MediaListenerService? = null

        fun requestRefresh() {
            instance?.refreshNowPlaying()
        }

        fun notifyFytSourceChanged(isFytActive: Boolean) {
            isFytActiveSource = isFytActive
            if (isFytActive && fytTitle.isNotBlank()) {
                instance?.clearActiveController()
                pushFytState()
            }
            // Al pasar a una fuente externa, volver a evaluar inmediatamente la
            // MediaSession en vez de dejar visibles metadatos FYT almacenados.
            instance?.refreshNowPlaying()
        }

        /** Estado real de play/pause desde el binder del reproductor FYT. */
        fun updateFytPlaybackState(playing: Boolean) {
            // Solo si la fuente activa es FYT: no eclipsar a un reproductor
            // externo (Spotify, etc.) con el estado del reproductor chino.
            if (!isFytActiveSource) return
            if (fytIsPlaying != playing) {
                fytIsPlaying = playing
                if (fytTitle.isNotBlank()) pushFytState()
            }
        }

        /**
         * Progreso real del reproductor FYT (IPlayer.getProgress → ms).
         * Solo con fuente FYT activa y solo si cambió algo (el ticker llama
         * cada segundo; no vale la pena recomponer si nada movió la barra).
         */
        fun updateFytProgress(positionMs: Long, durationMs: Long) {
            if (!isFytActiveSource || positionMs < 0) return
            val changed = positionMs != fytPositionMs || (durationMs > 0 && durationMs != fytDurationMs)
            if (!changed) return
            fytPositionMs = positionMs
            if (durationMs > 0) fytDurationMs = durationMs
            if (fytTitle.isNotBlank()) pushFytState()
        }

        fun updateFytAlbumArt(albumArt: Bitmap?) {
            if (albumArt != null) {
                fytAlbumArt = albumArt
                if (fytTitle.isNotBlank()) {
                    pushFytState()
                }
            }
        }

        fun updateFromFytBroadcast(title: String, artist: String, albumArt: Bitmap?) {
            val t = title.ifEmpty { fytTitle }
            val a = artist.ifEmpty { fytArtist }
            val art = albumArt ?: fytAlbumArt
            if (t.isNotBlank()) {
                updateFromFytService(t, a, isPlaying = true, albumArt = art)
            } else if (art != null) {
                updateFytAlbumArt(art)
            }
        }

        fun updateFromFytService(
            title: String,
            artist: String,
            isPlaying: Boolean,
            albumArt: Bitmap?
        ) {
            val cleanTitle = title.trim()
            val cleanArtist = artist.trim()

            if (cleanTitle.isEmpty() || cleanTitle.equals("Unknown", ignoreCase = true)) return

            if (fytTitle != cleanTitle) {
                fytTitle = cleanTitle
                fytAlbumArt = albumArt
                fytPositionMs = 0L
            } else if (albumArt != null) {
                fytAlbumArt = albumArt
            }

            fytArtist = cleanArtist
            fytIsPlaying = isPlaying

            if (lastMediaPackage.isEmpty()) lastMediaPackage = "com.syu.music"
            isFytActiveSource = true

            // Si había un controlador externo registrado, desvincularlo para dar prioridad al reproductor chino
            instance?.let { inst ->
                if (inst.activeController != null && !inst.activeController!!.packageName.startsWith("com.syu")) {
                    inst.clearActiveController()
                }
            }

            pushFytState()
        }

        private fun pushFytState() {
            postNowPlaying(
                NowPlayingState(
                    title = fytTitle,
                    artist = fytArtist,
                    albumArt = fytAlbumArt,
                    artUri = null,
                    isPlaying = fytIsPlaying,
                    controller = null,
                    positionMs = fytPositionMs,
                    durationMs = fytDurationMs
                )
            )
        }

        private fun postNowPlaying(state: NowPlayingState?) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                _nowPlaying.value = state
            } else {
                Handler(Looper.getMainLooper()).post {
                    _nowPlaying.value = state
                }
            }
        }

        // CONTROL DE REPRODUCCIÓN (Compatibilidad con Android y Radio China FYT)

        fun sendMediaKey(context: Context, keyCode: Int) {
            val currentState = _nowPlaying.value
            val controller = currentState?.controller

            // 1. Si hay un controlador Android estándar (Spotify, YouTube Music, etc.)
            if (controller != null) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (currentState.isPlaying) {
                            controller.transportControls.pause()
                        } else {
                            controller.transportControls.play()
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> controller.transportControls.play()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> controller.transportControls.pause()
                    KeyEvent.KEYCODE_MEDIA_NEXT -> controller.transportControls.skipToNext()
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> controller.transportControls.skipToPrevious()
                }
                return
            }

            // 2. Si es el reproductor o radio china de FYT (controller == null)
            FileLogger.log(context, "MediaSession: comando FYT keyCode=$keyCode")

            // a) Inyectar un evento de tecla real (mismo pipeline que los botones
            // físicos del carro). Requiere INJECT_EVENTS (lo tiene, uid.system).
            val injected = injectHardwareKey(context, keyCode)

            // b) Broadcast de tecla externa nativa de FYT (funciona en otros firmwares)
            val fytKeyCode = when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> 85
                KeyEvent.KEYCODE_MEDIA_PLAY -> 126
                KeyEvent.KEYCODE_MEDIA_PAUSE -> 127
                KeyEvent.KEYCODE_MEDIA_NEXT -> 87
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 88
                else -> keyCode
            }

            sendFytMediaCommand(context, fytKeyCode)

            // c) Broadcasts auxiliares de FYT para cambio de pista
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    context.sendBroadcast(Intent("com.syu.music.playpause"))
                    context.sendBroadcast(Intent("syu.widget.music.playpause"))
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    context.sendBroadcast(Intent("com.syu.music.next"))
                    context.sendBroadcast(Intent("syu.widget.music.next"))
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    context.sendBroadcast(Intent("com.syu.music.prev"))
                    context.sendBroadcast(Intent("syu.widget.music.prev"))
                }
            }

            // d) Si no se pudo inyectar, despachar como media key estándar
            if (!injected) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                    audioManager.dispatchMediaKeyEvent(eventDown)
                    audioManager.dispatchMediaKeyEvent(eventUp)
                }
            }
        }

        private fun injectHardwareKey(context: Context, keyCode: Int): Boolean {
            return try {
                val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
                // injectInputEvent está oculto de los stubs públicos (@SystemApi);
                // por reflección funciona en runtime (la app tiene INJECT_EVENTS).
                val method = InputManager::class.java.getMethod(
                    "injectInputEvent",
                    InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )
                // INJECT_INPUT_EVENT_MODE_ASYNC = 1
                val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, 0, InputDevice.SOURCE_DPAD)
                val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0, InputDevice.SOURCE_DPAD)
                method.invoke(inputManager, down, 1)
                method.invoke(inputManager, up, 1)
                true
            } catch (e: Exception) {
                FileLogger.log(context, "MediaSession: fallo al inyectar key $keyCode: ${e.message}")
                false
            }
        }

        fun sendFytMediaCommand(context: Context, keyCode: Int) {
            try {
                val intent = Intent("com.syu.ms.action.EXTERNAL_KEY").apply {
                    putExtra("keyCode", keyCode)
                }
                context.sendBroadcast(intent)
            } catch (_: Exception) {}
        }

        fun playPause(context: Context) {
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }

        fun next(context: Context) {
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        }

        fun previous(context: Context) {
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }
}
