package com.akira.island.face

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.akira.island.core.IslandBus
import com.akira.island.data.IslandSettings
import kotlin.math.roundToInt

/** Own short animation, no continuous frame loop when inactive or effects are reduced. */
@Composable
fun FaceGlyph(phase: FacePhase, reduced: Boolean, modifier: Modifier = Modifier) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(phase, reduced) {
        sweep.snapTo(0f)
        if (!reduced && phase != FacePhase.HIDDEN) sweep.animateTo(1f, tween(if (phase == FacePhase.SCANNING) 1_400 else 260))
    }
    Canvas(modifier) {
        if (phase == FacePhase.HIDDEN) return@Canvas
        val tint = when (phase) {
            FacePhase.SUCCESS -> Color(0xFF56DB91)
            FacePhase.FAILURE -> Color(0xFFD0C7F5)
            else -> Color.White
        }
        val u = size.minDimension / 40f
        val stroke = Stroke(2.1f * u, cap = StrokeCap.Round)
        drawRoundRect(Color.Black.copy(alpha = .82f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(11*u))
        val l=7*u; val r=size.width-l; val t=7*u; val b=size.height-t; val c=6*u
        val corners = Path().apply {
            moveTo(l,t+c); lineTo(l,t+2*u); quadraticBezierTo(l,t,l+2*u,t); lineTo(l+c,t)
            moveTo(r-c,t); lineTo(r-2*u,t); quadraticBezierTo(r,t,r,t+2*u); lineTo(r,t+c)
            moveTo(l,b-c); lineTo(l,b-2*u); quadraticBezierTo(l,b,l+2*u,b); lineTo(l+c,b)
            moveTo(r-c,b); lineTo(r-2*u,b); quadraticBezierTo(r,b,r,b-2*u); lineTo(r,b-c)
        }
        drawPath(corners,tint,style=stroke)
        if (phase == FacePhase.SUCCESS) {
            val check=Path().apply { moveTo(13*u,20*u); lineTo(18*u,25*u); lineTo(28*u,14*u) }
            drawPath(check,tint.copy(alpha=if(reduced) 1f else sweep.value.coerceAtLeast(.15f)),style=stroke)
        } else {
            drawLine(tint,Offset(14*u,15*u),Offset(14*u,18*u),2*u,StrokeCap.Round)
            drawLine(tint,Offset(26*u,15*u),Offset(26*u,18*u),2*u,StrokeCap.Round)
            val face=Path().apply {
                moveTo(20*u,17*u); lineTo(20*u,23*u); lineTo(18*u,23*u)
                moveTo(14*u,27*u); quadraticBezierTo(20*u,31*u,26*u,27*u)
            }
            drawPath(face,tint,style=stroke)
            if (!reduced && phase == FacePhase.SCANNING) {
                val y=(10+20*sweep.value)*u
                drawLine(tint.copy(alpha=.35f),Offset(10*u,y),Offset(30*u,y),2*u,StrokeCap.Round)
            }
        }
    }
}

/** Insets identify the physical camera. With no trustworthy anchor, do not invent a position. */
@Composable
fun FaceUnlockLayer(settings: IslandSettings, allowed: Boolean) {
    val phase by FaceUnlockState.phase.collectAsState()
    val screenOn by IslandBus.screenOn.collectAsState()
    val offSchedule by IslandBus.offSchedule.collectAsState()
    // The disabled/idle feature contributes no layout, insets queries or animation work.
    if (!settings.faceUnlockOnly || !settings.enabled || !screenOn || offSchedule || !allowed || phase == FacePhase.HIDDEN) return
    val view = LocalView.current
    val density = LocalDensity.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var anchor by remember { mutableStateOf<Offset?>(null) }
    val side = with(density) { 36.dp.toPx() }
    fun locate(c: LayoutCoordinates?) {
        anchor = if (Build.VERSION.SDK_INT >= 28 && c?.isAttached == true) runCatching {
            val cutout = view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull { !it.isEmpty }
                ?: return@runCatching null
            val corners = listOf(Offset(cutout.left.toFloat(),cutout.top.toFloat()),
                Offset(cutout.right.toFloat(),cutout.top.toFloat()),Offset(cutout.left.toFloat(),cutout.bottom.toFloat()),
                Offset(cutout.right.toFloat(),cutout.bottom.toFloat())).map(c::windowToLocal)
            val left=corners.minOf { it.x }; val rightEdge=corners.maxOf { it.x }
            val centerY=(corners.minOf { it.y }+corners.maxOf { it.y })/2f
            // Leave the physical hole uncovered; place the scanner just alongside it.
            val right = rightEdge + side*.12f
            val x = if (right+side <= c.size.width) right else left-side*1.12f
            val y = (centerY-side/2).coerceAtLeast(0f)
            Offset(x,y).takeIf { it.x >= 0 && it.x+side <= c.size.width && it.y+side <= c.size.height }
        }.getOrNull() else null
    }
    LaunchedEffect(phase, allowed) { if (phase != FacePhase.HIDDEN && allowed) locate(coordinates) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates=it; locate(it) }) {
        val at=anchor
        if (at != null) {
            FaceGlyph(phase,settings.reduceEffects,Modifier.offset { IntOffset(at.x.roundToInt(),at.y.roundToInt()) }.size(36.dp))
        }
    }
}
