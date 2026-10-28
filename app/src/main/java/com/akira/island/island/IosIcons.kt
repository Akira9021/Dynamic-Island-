package com.akira.island.island

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.akira.island.R

/**
 * Иконки острова. Главные — те же простые фигуры, что в промо-ролике 1.6: молния, трубка, месяц, песочные
 * часы, колокольчик, фонарик, наушники, Wi-Fi, самолёт, навигатор, скачивание, стрелка ответа и кнопки
 * плеера. Остальные — набор Ionicons (MIT, https://ionic.io/ionicons). В приложении настроек — Material.
 */
object Ios {
    private val cache = HashMap<Int, ImageVector>()

    /** Для кода вне Compose: службы и события. */
    fun load(context: Context, @DrawableRes id: Int): ImageVector = synchronized(cache) {
        cache.getOrPut(id) { ImageVector.vectorResource(null, context.resources, id) }
    }

    val alarm @Composable get() = ImageVector.vectorResource(R.drawable.ios_alarm)
    val add @Composable get() = ImageVector.vectorResource(R.drawable.ios_add)
    val remove @Composable get() = ImageVector.vectorResource(R.drawable.ios_remove)
    val download @Composable get() = ImageVector.vectorResource(R.drawable.ios_download)
    val arrowUp @Composable get() = ImageVector.vectorResource(R.drawable.ios_arrow_up)
    val batteryCharging @Composable get() = ImageVector.vectorResource(R.drawable.ios_battery_charging)
    val batteryFull @Composable get() = ImageVector.vectorResource(R.drawable.ios_battery_full)
    val call @Composable get() = ImageVector.vectorResource(R.drawable.ios_call)
    val close @Composable get() = ImageVector.vectorResource(R.drawable.ios_close)
    val flash @Composable get() = ImageVector.vectorResource(R.drawable.ios_flash)
    val flashlight @Composable get() = ImageVector.vectorResource(R.drawable.ios_flashlight)
    val navigate @Composable get() = ImageVector.vectorResource(R.drawable.ios_navigate)
    val music @Composable get() = ImageVector.vectorResource(R.drawable.ios_musical_notes)
    val moon @Composable get() = ImageVector.vectorResource(R.drawable.ios_moon)
    val lock @Composable get() = ImageVector.vectorResource(R.drawable.ios_lock_closed)
    val bell @Composable get() = ImageVector.vectorResource(R.drawable.ios_notifications)
    val pause @Composable get() = ImageVector.vectorResource(R.drawable.ios_pause)
    val play @Composable get() = ImageVector.vectorResource(R.drawable.ios_play)
    val back @Composable get() = ImageVector.vectorResource(R.drawable.ios_play_back)
    val forward @Composable get() = ImageVector.vectorResource(R.drawable.ios_play_forward)
    val airplay @Composable get() = ImageVector.vectorResource(R.drawable.ios_radio)
    val refresh @Composable get() = ImageVector.vectorResource(R.drawable.ios_refresh)
    val stop @Composable get() = ImageVector.vectorResource(R.drawable.ios_stop)
    val stopwatch @Composable get() = ImageVector.vectorResource(R.drawable.ios_stopwatch)
    val timer @Composable get() = ImageVector.vectorResource(R.drawable.ios_hourglass)
    val chatbubble @Composable get() = ImageVector.vectorResource(R.drawable.ios_chatbubble_ellipses)
    val bellOff @Composable get() = ImageVector.vectorResource(R.drawable.ios_notifications_off)
    val vibrate @Composable get() = ImageVector.vectorResource(R.drawable.ios_phone_portrait)
}
