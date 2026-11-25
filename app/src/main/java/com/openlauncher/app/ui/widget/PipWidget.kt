package com.openlauncher.app.ui.widget

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
// Cambia esto por el package real donde definiste PipDpi
import com.openlauncher.app.data.PipDpi

private const val TAG = "PipWidget"
private const val WAZE_PACKAGE = "com.waze"

// Define las banderas en la parte superior del archivo
private const val VIRTUAL_DISPLAY_FLAG_PUBLIC = 1 shl 0
private const val VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = 1 shl 3
private const val VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
private const val VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 shl 10

private object SyuConstants {
    const val EMBEDDED_APP = "persist.launcher.embedded_pkg"
    const val EMBEDDED_WINDOW_STATE = "persist.launcher.embedded_window"
    const val EMBEDDED_WINDOW_NOTOUCH = "persist.launcher.embedded_notouch"
    const val EMBEDDED_APP_KEY = "embedded_app"
    const val EMBEDDED_CLAZZ_KEY = "embedded_class"
}

private object SystemPropertiesHelper {
    private val setMethod = runCatching {
        Class.forName("android.os.SystemProperties")
        .getMethod("set", String::class.java, String::class.java)
    }.getOrNull()

    fun set(key: String, value: String) {
        try {
            setMethod?.invoke(null, key, value)
            ?: Runtime.getRuntime().exec(arrayOf("setprop", key, value))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting prop $key: ${e.message}")
        }
    }
}

private object InputReflectionCache {
    val setDisplayIdMethod = runCatching {
        MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }.getOrNull()

    val injectInputEventMethod = runCatching {
        InputManager::class.java.getMethod(
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }.getOrNull()
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName) != null
    }.getOrDefault(false)
}

@Composable
fun PipWidget(
    packageName: String,
    pipDpiCustom: PipDpi,
    isOverlayOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val effectivePackage = remember(packageName) {
        if (packageName.isNotEmpty() && isPackageInstalled(context, packageName)) packageName else ""
    }

    var virtualDisplay by remember { mutableStateOf<VirtualDisplay?>(null) }
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }

    var surfaceToken by remember { mutableIntStateOf(0) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    val isWaze = effectivePackage == WAZE_PACKAGE
  //  val launcherViewModel: LauncherViewModel = viewModel()
  //  val settings by launcherViewModel.settings.collectAsState()

  //  val pipDpiCustom = settings.pipDpi.name
  val customDpi: Int = when (pipDpiCustom) {
      PipDpi.D160 -> 160
      PipDpi.D200 -> 200
      PipDpi.D240 -> 240
      PipDpi.D280 -> 280
      PipDpi.D320 -> 320
  }

    // Escuchar ciclo de vida
    DisposableEffect(lifecycleOwner, effectivePackage) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (effectivePackage.isNotEmpty() && !isOverlayOpen) {
                    SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_STATE, "1")
                    SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_NOTOUCH, "0")
                    if (!isWaze) {
                        surfaceToken++
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 1. Control de estado SYU
    LaunchedEffect(isOverlayOpen, effectivePackage) {
        if (isOverlayOpen) {
            SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_STATE, "0")
            SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_NOTOUCH, "1")
        } else if (effectivePackage.isNotEmpty()) {
            SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_STATE, "1")
            SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_NOTOUCH, "0")
        }
    }

    // 2. Lanzamiento de la App (Esperamos a que viewWidth > 0 y viewHeight > 0)
    LaunchedEffect(effectivePackage, virtualDisplay, isOverlayOpen, surfaceToken, viewWidth, viewHeight) {
        val vDisplay = virtualDisplay
        if (effectivePackage.isNotEmpty() && vDisplay != null && !isOverlayOpen && viewWidth > 0 && viewHeight > 0) {
            if (isWaze) {
                launchWazeWithBounds(context, vDisplay.display.displayId, viewWidth, viewHeight)
            } else {
                launchAppInVirtualDisplay(context, effectivePackage, vDisplay.display.displayId)
            }

            val keepAliveRunnable = object : Runnable {
                override fun run() {
                    if (virtualDisplay != null && !isOverlayOpen) {
                        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_APP, effectivePackage)
                        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_STATE, "1")
                        handler.postDelayed(this, 10000)
                    }
                }
            }
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(keepAliveRunnable, 10000)
        }
    }

    // 3. Limpieza de recursos
    DisposableEffect(effectivePackage) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            try {
                virtualDisplay?.release()
                virtualDisplay = null

                currentSurface?.release()
                currentSurface = null

                SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_STATE, "0")
                SystemPropertiesHelper.set(SyuConstants.EMBEDDED_APP, "")
                SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_NOTOUCH, "0")

                context.sendBroadcast(Intent("com.syu.pip.action.CLOSE"))
            } catch (e: Exception) {
                Log.e(TAG, "Error durante onDispose: ${e.message}")
            }
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (effectivePackage.isEmpty()) {
            Text(text = "Sin aplicación activa", color = Color.Gray)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                        virtualDisplay?.release()
                                        currentSurface?.release()

                                        viewWidth = width
                                        viewHeight = height

                                        val surface = Surface(st)
                                        currentSurface = surface

                                        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                                     //   val densityDpi = ctx.resources.displayMetrics.densityDpi
                                     // Asigna 160 DPI si es Google Maps, o 240 DPI para cualquier otra app
                                    // val densityDpi = if (effectivePackage == "com.google.android.apps.maps") 160 else ctx.resources.displayMetrics.densityDpi
                                       val densityDpi = customDpi

                                        // Usa PRESENTATION y SECURE en lugar de PUBLIC
                                        val flags = VIRTUAL_DISPLAY_FLAG_PUBLIC or
                                        VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                                        VIRTUAL_DISPLAY_FLAG_TRUSTED or
                                        VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL

                                        virtualDisplay = dm.createVirtualDisplay(
                                            "SyuEmbedTaskView",
                                            width.coerceAtLeast(100),
                                            height.coerceAtLeast(100),
                                            densityDpi,
                                            surface,
                                            flags
                                        )

                                        surfaceToken++
                                    }

                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                        viewWidth = width
                                        viewHeight = height
                                        virtualDisplay?.resize(
                                            width.coerceAtLeast(100),
                                                               height.coerceAtLeast(100),
                                                               ctx.resources.displayMetrics.densityDpi
                                        )
                                    }

                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        handler.removeCallbacksAndMessages(null)
                                        virtualDisplay?.release()
                                        virtualDisplay = null

                                        currentSurface?.release()
                                        currentSurface = null

                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        update = { textureView ->
                            @SuppressLint("ClickableViewAccessibility")
                            textureView.setOnTouchListener { v, event ->
                                if (isOverlayOpen) return@setOnTouchListener false
                                    val vDisp = virtualDisplay ?: return@setOnTouchListener false

                                    // Calcular la escala entre la vista (TextureView) y la pantalla virtual
                                    val scaleX = vDisp.display.width.toFloat() / v.width.coerceAtLeast(1).toFloat()
                                    val scaleY = vDisp.display.height.toFloat() / v.height.coerceAtLeast(1).toFloat()

                                    // Duplicar el evento para transformar sus coordenadas
                                    val scaledEvent = MotionEvent.obtain(event).apply {
                                        // Reescalar coordenadas x, y a la resolución interna del VirtualDisplay
                                        setLocation(event.x * scaleX, event.y * scaleY)
                                    }

                                    // Inyectar evento transformado en el Display de la app incrustada
                                    injectInputEventToDisplay(textureView.context, scaledEvent, vDisp.display.displayId)
                                    true
                            }
                        }
            )
        }
    }
}

/**
 * Método específico para Waze con restricción de límites previa y posterior.
 */
private fun launchWazeWithBounds(context: Context, displayId: Int, width: Int, height: Int) {
    runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(WAZE_PACKAGE)?.apply {
            // Forzar a re-crear/re-asentar el Task en el display destino
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_NO_ANIMATION
        } ?: return

        val className = launchIntent.component?.className ?: ""

        // Propiedades clave de SYU/FYT
        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_APP, WAZE_PACKAGE)
        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_STATE, "1")
        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_NOTOUCH, "0")
        SystemPropertiesHelper.set("persist.launcher.pip", WAZE_PACKAGE) // Propiedad nativa SYU PIP

        val options = ActivityOptions.makeBasic().apply {
            // Asignación explícita del Display ID
            val setLaunchDisplayIdMethod = ActivityOptions::class.java.getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
            setLaunchDisplayIdMethod.invoke(this, displayId)

            val setLaunchBoundsMethod = ActivityOptions::class.java.getMethod("setLaunchBounds", Rect::class.java)
            setLaunchBoundsMethod.invoke(this, Rect(0, 0, width, height))
        }

        val sendSyuBroadcast = {
            val syuBroadcast = Intent("com.syu.pip.action.LAUNCH").apply {
                putExtra(SyuConstants.EMBEDDED_APP_KEY, WAZE_PACKAGE)
                putExtra(SyuConstants.EMBEDDED_CLAZZ_KEY, className)
                putExtra("display_id", displayId)
                putExtra("package_name", WAZE_PACKAGE)
                putExtra("class_name", className)
            }
            context.sendBroadcast(syuBroadcast)
        }

        sendSyuBroadcast()
        context.startActivity(launchIntent, options.toBundle())

        // Múltiples pases de resincronización para Waze
        Handler(Looper.getMainLooper()).postDelayed({ sendSyuBroadcast() }, 150)
        Handler(Looper.getMainLooper()).postDelayed({ sendSyuBroadcast() }, 500)

    }.onFailure {
        Log.e(TAG, "Error lanzando Waze con Bounds: ${it.message}")
    }
}

/**
 * Método estándar para Maps, YouTube, Calculadora, etc.
 */
private fun launchAppInVirtualDisplay(context: Context, pkgName: String, displayId: Int) {
    runCatching {
        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_APP, pkgName)
        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_STATE, "1")
        SystemPropertiesHelper.set(SyuConstants.EMBEDDED_WINDOW_NOTOUCH, "0")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        } ?: return

        launchOnDisplay(context, launchIntent, displayId)

        val className = launchIntent.component?.className ?: ""
        val sendSyuBroadcast = {
            val syuBroadcast = Intent("com.syu.pip.action.LAUNCH").apply {
                putExtra(SyuConstants.EMBEDDED_APP_KEY, pkgName)
                putExtra(SyuConstants.EMBEDDED_CLAZZ_KEY, className)
                putExtra("display_id", displayId)
                putExtra("package_name", pkgName)
                putExtra("class_name", className)
            }
            context.sendBroadcast(syuBroadcast)
        }

        sendSyuBroadcast()
        Handler(Looper.getMainLooper()).postDelayed({ sendSyuBroadcast() }, 300)

    }.onFailure {
        Log.e(TAG, "Fallo al lanzar app en VirtualDisplay: ${it.message}")
    }
}

private fun launchOnDisplay(context: Context, intent: Intent, displayId: Int) {
    val options = ActivityOptions.makeBasic()
    runCatching {
        val method = ActivityOptions::class.java.getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
        method.invoke(options, displayId)
    }
    try {
        context.startActivity(intent, options.toBundle())
    } catch (e: Exception) {
        Log.e(TAG, "Error en launchOnDisplay: ${e.message}")
    }
}

private fun injectInputEventToDisplay(context: Context, event: MotionEvent, displayId: Int) {
    try {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

        // 1. Asignar el ID de la pantalla virtual al evento
        InputReflectionCache.setDisplayIdMethod?.invoke(event, displayId)

        // 2. Inyectar usando INJECT_INPUT_EVENT_MODE_ASYNC (0)
        InputReflectionCache.injectInputEventMethod?.invoke(
            inputManager,
            event,
            0 // INJECT_INPUT_EVENT_MODE_ASYNC
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error inyectando input en display $displayId: ${e.message}")
    } finally {
        // Importante reciclar el evento clonado
        runCatching { event.recycle() }
    }
}
