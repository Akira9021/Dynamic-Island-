@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.akira.island.island

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.akira.island.core.AlarmInfo
import com.akira.island.core.BatteryInfo
import com.akira.island.core.IslandBus
import com.akira.island.core.Lang
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.core.trf
import com.akira.island.data.BriefClockStyle
import com.akira.island.data.ExtraSettings
import com.akira.island.data.IslandSettings
import com.akira.island.service.StepRepo
import com.akira.island.service.WeatherRepo
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

/** A bounded horizontal pager; the expanded island remains the only vertical scroll container. */
@Composable
internal fun BriefPanel(
    s: IslandSettings,
    extras: ExtraSettings,
    battery: BatteryInfo,
    now: Long,
    time: String,
    date: String,
    alarm: AlarmInfo?,
) {
    val locked by IslandBus.locked.collectAsState()
    val steps by StepRepo.count.collectAsState()
    val weather by WeatherRepo.state.collectAsState()
    val details = briefDetails(extras, locked, steps, weather, now)
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    fun turnPage(delta: Int) {
        scope.launch {
            val target = (pager.currentPage + delta).coerceIn(0, 2)
            if (s.reduceEffects) pager.scrollToPage(target) else pager.animateScrollToPage(target)
        }
    }
    val pageTitles = listOf(tr("Дата и время", "Date and time"), tr("Шаги сегодня", "Steps today"), tr("Погода", "Weather"))
    val pageDescription = trf("Страница {0} из {1}: {2}", "Page {0} of {1}: {2}", pager.currentPage + 1, 3, pageTitles[pager.currentPage])
    // Text can grow with the system font setting without an unbounded pager measurement.
    val pageHeight = (240f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    // Keep line height relative when FitText reduces a clock or grows the step count.
    ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = 1.2.em)) {
        Column(Modifier.fillMaxWidth().testTag("brief-panel")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { turnPage(-1) },
                    enabled = pager.currentPage > 0,
                    modifier = Modifier.size(48.dp).testTag("brief-previous"),
                ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Предыдущая страница", "Previous page"), tint = Color.White.copy(alpha = if (pager.currentPage > 0) 0.8f else 0.2f)) }
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { stateDescription = pageDescription }, horizontalAlignment = Alignment.CenterHorizontally) {
                    FitText(tr("Сводка", "Brief"), Color.White, 14.sp, 8.sp, FontWeight.SemiBold.island)
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(3) { page ->
                            Box(Modifier.size(5.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = if (pager.currentPage == page) 0.9f else 0.25f)))
                        }
                    }
                }
                IconButton(
                    onClick = { turnPage(1) },
                    enabled = pager.currentPage < 2,
                    modifier = Modifier.size(48.dp).testTag("brief-next"),
                ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr("Следующая страница", "Next page"), tint = Color.White.copy(alpha = if (pager.currentPage < 2) 0.8f else 0.2f)) }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(pageHeight).testTag("brief-pages"),
                key = { it },
            ) { page ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))
                        .padding(10.dp).testTag("brief-page-$page"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FitText(pageTitles[page], Tints.Lavender, 13.sp, 9.sp, FontWeight.Medium.island, Modifier.fillMaxWidth())
                    when (page) {
                        0 -> BriefClockPage(s, extras.briefClockStyle, battery, now, time, date, alarm, locked)
                        1 -> BriefStepsPage(details)
                        2 -> BriefWeatherPage(details)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.BriefClockPage(
    s: IslandSettings,
    style: BriefClockStyle,
    battery: BatteryInfo,
    now: Long,
    time: String,
    date: String,
    alarm: AlarmInfo?,
    locked: Boolean,
) {
    val context = LocalContext.current
    when (style) {
        BriefClockStyle.DIGITAL -> FitText(time, Color.White, 38.sp, 10.sp, FontWeight.Normal.island,
            Modifier.fillMaxWidth().testTag("brief-clock"), Digits)
        BriefClockStyle.SPLIT -> {
            val hour = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH" else "h", Lang.locale).format(Date(now))
            val minute = SimpleDateFormat("mm", Lang.locale).format(Date(now))
            Row(Modifier.fillMaxWidth().testTag("brief-clock").clearAndSetSemantics { contentDescription = time },
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(hour, minute).forEach { part ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Tints.Lavender.copy(alpha = 0.15f)).padding(6.dp), contentAlignment = Alignment.Center) {
                        FitText(part, Color.White, 32.sp, 10.sp, FontWeight.Medium.island, style = Digits)
                    }
                }
            }
            if (!DateFormat.is24HourFormat(context)) {
                FitText(SimpleDateFormat("a", Lang.locale).format(Date(now)), Tints.Lavender, 12.sp, 9.sp, FontWeight.Normal.island, Modifier.fillMaxWidth())
            }
        }
        BriefClockStyle.ANALOG -> {
            AnalogClock(now, Modifier.fillMaxWidth().height(64.dp))
            FitText(time, Color.White, 20.sp, 10.sp, FontWeight.Normal.island,
                Modifier.fillMaxWidth().testTag("brief-clock"), Digits)
        }
    }
    Text(date, color = Color.LightGray, fontSize = 13.sp, lineHeight = 17.sp,
        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.weight(1f))
    if (battery.level >= 0) Row(Modifier.testTag("brief-battery"), verticalAlignment = Alignment.CenterVertically) {
        if (!s.batteryPercentOnly) {
            BatteryGlyph(battery.level, when {
                battery.charging -> Tints.Green
                battery.level <= 15 -> Tints.Red
                else -> Color.White
            }, Modifier.size(26.dp, 13.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text("${battery.level}%", color = Color.White, fontSize = 14.sp, style = Digits, maxLines = 1, softWrap = false)
    }
    alarm?.takeIf { it.triggerAt - now in 1..86_400_000L }?.let { next ->
        val alarmTime = wakeTime(context, next.triggerAt)
        val remaining = durationWords(next.triggerAt - now)
        val description = trf("{0} · через {1}", "{0} · in {1}", alarmTime, remaining)
        Row(Modifier.fillMaxWidth().testTag("brief-alarm")
            .then(if (!locked) Modifier.clickable { openAlarm(context, next) } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Ios.alarm, null, tint = AlarmTint, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                FitText(alarmTime, AlarmTint, 14.sp, 9.sp, FontWeight.Medium.island, Modifier.fillMaxWidth(), Digits)
                FitText(remaining, AlarmTint.copy(alpha = 0.8f), 11.sp, 8.sp, FontWeight.Normal.island, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ColumnScope.BriefStepsPage(details: BriefDetails) {
    when (details.stepsStatus) {
        BriefDataStatus.HIDDEN -> BriefMessage(tr("Разблокируйте телефон для просмотра", "Unlock your phone to view"))
        BriefDataStatus.DISABLED -> BriefMessage(tr("Включите шагомер в дополнительной панели", "Enable the step counter in Extra controls"))
        BriefDataStatus.UNAVAILABLE -> BriefMessage(tr("Ожидание данных датчика шагов", "Waiting for step sensor data"))
        BriefDataStatus.READY -> {
            Spacer(Modifier.weight(1f))
            FitText(NumberFormat.getIntegerInstance(Lang.locale).format(details.steps), Color.White, 44.sp, 12.sp,
                FontWeight.Medium.island, Modifier.fillMaxWidth().testTag("brief-step-count"), Digits)
            Text(tr("Шаги за день с момента включения", "Daily steps since enabling"), color = Color.LightGray,
                fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColumnScope.BriefWeatherPage(details: BriefDetails) {
    when (details.weatherStatus) {
        BriefDataStatus.HIDDEN -> BriefMessage(tr("Разблокируйте телефон для просмотра", "Unlock your phone to view"))
        BriefDataStatus.DISABLED -> BriefMessage(tr("Выберите город и включите погоду в дополнительной панели", "Choose a city and enable weather in Extra controls"))
        BriefDataStatus.UNAVAILABLE -> BriefMessage(tr("Погода пока недоступна", "Weather is not available yet"))
        BriefDataStatus.READY -> details.weather?.let { weather ->
            Text(weather.city, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("brief-weather-city"))
            FitText(String.format(Lang.locale, "%.0f°C", weather.temperature), Color.White, 38.sp, 12.sp,
                FontWeight.Normal.island, Modifier.fillMaxWidth(), Digits)
            Text(weather.summary(), color = Color.LightGray, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            FitText(tr("Обновлено", "Updated") + " " + DateFormat.getTimeFormat(LocalContext.current).format(Date(weather.updatedAt)),
                Color.LightGray, 11.sp, 8.sp, FontWeight.Normal.island, Modifier.fillMaxWidth())
            FitText("Open-Meteo · CC BY 4.0", Color.Gray, 10.sp, 8.sp, FontWeight.Normal.island, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ColumnScope.BriefMessage(message: String) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(message, color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp,
            maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
}
