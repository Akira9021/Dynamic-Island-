package com.akira.island.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.tr
import com.akira.island.service.glassBlurRadiusDp

/** Local sample only: the effect touches the background, never the foreground text. */
@Composable
internal fun BlurStrengthPreview(strength: Int, enabled: Boolean) {
    val radius = with(LocalDensity.current) { glassBlurRadiusDp(strength).dp.toPx() }
    Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF242532))
        .testTag("glass-blur-preview"), contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize().graphicsLayer {
            renderEffect = if(Build.VERSION.SDK_INT>=31 && enabled && radius>0f)
                BlurEffect(radius,radius,TileMode.Clamp) else null
        }) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF6861DB),Color(0xFF2D9AA9),Color(0xFF463C7B))))
            for(i in 0..5) drawCircle(if(i%2==0) Color(0xFFF2C681) else Color(0xFFBDEBE5),
                size.height*.26f, Offset(size.width*(i+.5f)/6f, size.height*(if(i%2==0) .25f else .8f)))
        }
        Text(tr("Чёткий текст", "Sharp text"), color=Color.White, fontSize=16.sp,
            modifier=Modifier.background(Color.Black.copy(alpha=.4f),RoundedCornerShape(12.dp)).padding(horizontal=14.dp,vertical=8.dp))
    }
}
