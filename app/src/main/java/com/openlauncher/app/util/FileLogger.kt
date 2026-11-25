package com.openlauncher.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object FileLogger {
    private const val TAG = "OpenLauncher"
    private const val FILE_NAME = "launcher_logs.txt"

    fun log(context: Context, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message\n"
        
        Log.d(TAG, message)

        try {
            val logFile = File(context.getExternalFilesDir(null), FILE_NAME)
            FileOutputStream(logFile, true).use {
                it.write(logEntry.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    fun getLogFilePath(context: Context): String {
        return File(context.getExternalFilesDir(null), FILE_NAME).absolutePath
    }
}
