package com.akira.island.island

import androidx.compose.ui.geometry.Rect

/** The visible window-space contour, independent of the unchanged hit-testing bounds. */
data class BackdropGeometry(
    val area: Rect,
    val bottomRadius: Float,
    val topRadius: Float,
    val usable: Boolean,
    val card: Boolean,
)
