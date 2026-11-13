package com.akira.island.face

import androidx.annotation.Keep
import rikka.shizuku.ShizukuProvider

/** This optional feature supports the explicitly approved ADB backend, not automatic Sui/root. */
@Keep
class FaceShizukuProvider : ShizukuProvider() {
    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        return super.onCreate()
    }
}
