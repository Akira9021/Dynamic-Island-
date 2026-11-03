package com.akira.island.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File

/**
 * Debug-only logger for the charge time estimator.
 *
 * Writes to Logcat (tag [TAG]) and to `getExternalFilesDir(null)/chargeeta.log`.
 * The file keeps at most [MAX_LINES] lines: when it grows, the oldest ones are dropped.
 * Everything is a no-op in release builds.
 */
object ChargeEtaLog {
    const val TAG = "ChargeEta"

    private const val FILE_NAME = "chargeeta.log"
    private const val MAX_LINES = 500
    private val lock = Any()

    /** Debug builds only: the application is debuggable. */
    fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun log(context: Context, message: String) {
        if (!isDebug(context)) return
        Log.d(TAG, message)
        val dir = context.getExternalFilesDir(null) ?: return
        runCatching {
            val file = File(dir, FILE_NAME)
            synchronized(lock) {
                file.appendText(message + "\n")
                val lines = file.readLines()
                if (lines.size > MAX_LINES) {
                    file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
                }
            }
        }
    }
}
