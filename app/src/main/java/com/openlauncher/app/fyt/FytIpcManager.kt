package com.openlauncher.app.fyt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.syu.ipc.IModuleCallback
import com.syu.ipc.IRemoteModule
import com.syu.ipc.IRemoteToolkit

class FytIpcManager(
    private val context: Context,
        private val onMediaInfoUpdated: (title: String, artist: String, source: String) -> Unit
) {

    private var remoteToolkit: IRemoteToolkit? = null
        private var isBound = false

        // Módulos conocidos en FYT UIS7870
        companion object {
            private const val TAG = "FYT_IPC_MANAGER"
            private const val MODULE_MAIN = 0    // Sistema / Global
            private const val MODULE_RADIO = 1   // Radio FM/AM
            private const val MODULE_BT = 2      // Bluetooth Audio
            private const val MODULE_MUSIC = 3   // Reproductor USB/Local
        }

        // Callback para recibir eventos del módulo
        private val mcuCallback = object : IModuleCallback.Stub() {
            override fun update(updateCode: Int, ints: IntArray?, floats: FloatArray?, strings: Array<out String>?) {
                // Log para inspeccionar los códigos de actualización que envía el chip
                Log.d(TAG, "UpdateCode: $updateCode | Strings: ${strings?.contentToString()} | Ints: ${ints?.contentToString()}")

                strings?.let { strArray ->
                    if (strArray.isNotEmpty() && strArray[0].isNotBlank()) {
                        val title = strArray.getOrNull(0) ?: ""
                        val artist = strArray.getOrNull(1) ?: ""

                        // Notificar a OpenLauncher con los datos procesados
                        onMediaInfoUpdated(title, artist, "FYT_HARDWARE")
                    }
                }
            }
        }

        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.i(TAG, "Conexión exitosa con el servicio com.syu.ms")
                remoteToolkit = IRemoteToolkit.Stub.asInterface(service)

                try {
                    val toolkit = remoteToolkit ?: return

                    // Registrar oyentes en los módulos de música/radio
                    registerModule(toolkit, MODULE_MUSIC, 30) // Música Local
                    registerModule(toolkit, MODULE_BT, 30)    // Bluetooth
                    registerModule(toolkit, MODULE_RADIO, 31) // Radio FM

                } catch (e: RemoteException) {
                    Log.e(TAG, "Error al suscribirse a módulos FYT", e)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "Servicio FYT desconectado")
                remoteToolkit = null
                isBound = false
            }
        }

        private fun registerModule(toolkit: IRemoteToolkit, moduleId: Int, maxCodes: Int) {
            val module: IRemoteModule? = toolkit.getRemoteModule(moduleId)
            module?.let {
                for (code in 0 until maxCodes) {
                    try {
                        // Flag 1 = Pedir el valor actual inmediatamente
                        it.register(mcuCallback, code, 1)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error registrando código $code en módulo $moduleId", e)
                    }
                }
                Log.d(TAG, "Módulo $moduleId registrado ($maxCodes eventos)")
            }
        }

        fun connect() {
            if (isBound) return

                val intent = Intent("com.syu.ms.toolkit").apply {
                    setPackage("com.syu.ms")
                }

                try {
                    isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                    if (!isBound) {
                        Log.e(TAG, "No se pudo hacer bind al servicio com.syu.ms")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción intentando conectar a com.syu.ms", e)
                }
        }

        fun disconnect() {
            if (isBound) {
                try {
                    context.unbindService(serviceConnection)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al desconectar servicio", e)
                }
                isBound = false
            }
        }
}
