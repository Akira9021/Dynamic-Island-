package com.akira.island.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.R
import com.akira.island.core.LangScope
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.screens.AnimScreen
import com.akira.island.ui.screens.AppInfoScreen
import com.akira.island.ui.screens.BannersScreen
import com.akira.island.ui.screens.BanksScreen
import com.akira.island.ui.screens.BehaviorScreen
import com.akira.island.ui.screens.CapsuleScreen
import com.akira.island.ui.screens.CardsScreen
import com.akira.island.ui.screens.ColorsScreen
import com.akira.island.ui.screens.DiagScreen
import com.akira.island.ui.screens.EffectsScreen
import com.akira.island.ui.screens.EventsScreen
import com.akira.island.ui.screens.FeaturesScreen
import com.akira.island.ui.screens.GesturesScreen
import com.akira.island.ui.screens.HomeScreen
import com.akira.island.ui.screens.IdeaScreen
import com.akira.island.ui.screens.InstructionScreen
import com.akira.island.ui.screens.KeepAliveScreen
import com.akira.island.ui.screens.LookScreen
import com.akira.island.ui.screens.LyricsScreen
import com.akira.island.ui.screens.NotificationsScreen
import com.akira.island.ui.screens.NotifAppsScreen
import com.akira.island.ui.screens.NotifDiagScreen
import com.akira.island.ui.screens.FontScreen
import com.akira.island.ui.screens.PayDiagScreen
import com.akira.island.ui.screens.PermissionsScreen
import com.akira.island.ui.screens.SizeScreen
import com.akira.island.ui.screens.TestsScreen
import com.akira.island.service.KeepAliveService
import com.akira.island.update.UpdateChecker

enum class Screen {
    CARDS, EVENTS, GESTURES, LYRICS, SIZE, COLORS, INSTRUCTION, ABOUT, DIAG, LOOK, ANIM, EFFECTS, BANNERS,
    PERMISSIONS, TESTS, NOTIFICATIONS, BEHAVIOR, PAYDIAG, KEEPALIVE, BANKS, NOTIF_APPS, NOTIF_DIAG, FONT,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepo.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { IslandTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        // «Не выгружать остров»: из фона Android может не запустить службу — запускаем, пока приложение открыто
        KeepAliveService.sync(this)
    }
}

@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    var forward by remember { mutableStateOf(true) }
    val open: (Screen) -> Unit = {
        forward = true
        stack = stack + it
    }
    val back: () -> Unit = {
        forward = false
        stack = stack.dropLast(1)
    }
    BackHandler(enabled = stack.isNotEmpty(), onBack = back)

    // Раз в 3 часа спрашиваем GitHub, не вышла ли новая версия; после обновления — «Что нового»
    val context = LocalContext.current
    LaunchedEffect(Unit) { UpdateChecker.check(context.applicationContext, force = false) }
    // Сменили язык — экраны рисуются заново на нём, а открытая вкладка и экран остаются
    LangScope {
        WhatsNewDialog()

        Box(Modifier.fillMaxSize().background(Ui.Bg)) {
            val key = stack.lastOrNull()?.name ?: "tab$tab"
            AnimatedContent(
                targetState = key,
                transitionSpec = {
                    if (targetState.startsWith("tab") && initialState.startsWith("tab")) {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                    } else if (forward) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                label = "nav",
            ) { k ->
                when (k) {
                    "tab0" -> HomeScreen(open)
                    "tab1" -> FeaturesScreen(open)
                    "tab2" -> CapsuleScreen(open)
                    "tab3" -> AppInfoScreen(open)
                    Screen.CARDS.name -> CardsScreen(back) { open(Screen.INSTRUCTION) }
                    Screen.EVENTS.name -> EventsScreen(back)
                    Screen.GESTURES.name -> GesturesScreen(back)
                    Screen.LYRICS.name -> LyricsScreen(back)
                    Screen.SIZE.name -> SizeScreen(back)
                    Screen.COLORS.name -> ColorsScreen(back)
                    Screen.INSTRUCTION.name -> InstructionScreen(back)
                    Screen.ABOUT.name -> IdeaScreen(back)
                    Screen.DIAG.name -> DiagScreen(back)
                    Screen.PAYDIAG.name -> PayDiagScreen(back)
                    Screen.KEEPALIVE.name -> KeepAliveScreen(back)
                    Screen.BANKS.name -> BanksScreen(back)
                    Screen.LOOK.name -> LookScreen(back)
                    Screen.ANIM.name -> AnimScreen(back)
                    Screen.EFFECTS.name -> EffectsScreen(back)
                    Screen.BANNERS.name -> BannersScreen(back)
                    Screen.PERMISSIONS.name -> PermissionsScreen(back, open)
                    Screen.TESTS.name -> TestsScreen(back)
                    Screen.NOTIFICATIONS.name -> NotificationsScreen(back, open)
                    Screen.NOTIF_APPS.name -> NotifAppsScreen(back)
                    Screen.NOTIF_DIAG.name -> NotifDiagScreen(back)
                    Screen.FONT.name -> FontScreen(back)
                    Screen.BEHAVIOR.name -> BehaviorScreen(back)
                }
            }

            AnimatedVisibility(
                visible = stack.isEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(tab) { tab = it }
            }
        }
    }
}

/** Вкладки: главная, что показывает остров, как он выглядит, и о приложении — отдельно от настроек. */
private fun tabTitles() = listOf(tr("Главная", "Home"), tr("Функции", "Features"), tr("Оформление", "Appearance"), tr("О приложении", "About"))

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(
        ImageVector.vectorResource(R.drawable.ic_tab_island),
        Icons.Rounded.Widgets,
        Icons.Rounded.Palette,
        Icons.Rounded.Info,
    )
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(Ui.Nav)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabTitles().forEachIndexed { i, title ->
            val on = i == selected
            val pill by animateColorAsState(if (on) Ui.Primary else Ui.Nav, tween(200), label = "tab")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = 56.dp, height = 32.dp).clip(CircleShape).background(pill), contentAlignment = Alignment.Center) {
                    Icon(icons[i], title, tint = if (on) Ui.OnPrimary else Ui.Text, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(4.dp))
                TabLabel(title, if (on) Ui.Text else Ui.Sub, on)
            }
        }
    }
}

/** Подпись вкладки: если не влезает (крупный шрифт Android), чуть уменьшается, а не обрезается. */
@Composable
private fun TabLabel(text: String, color: androidx.compose.ui.graphics.Color, bold: Boolean) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Medium
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        val room = constraints.maxWidth
        val size = remember(text, room, weight) {
            var sz = 12f
            while (sz > 8f && measurer.measure(text, base.copy(fontSize = sz.sp, fontWeight = weight), maxLines = 1).size.width > room) {
                sz -= 0.5f
            }
            sz.sp
        }
        Text(text, color = color, fontSize = size, fontWeight = weight, maxLines = 1, softWrap = false, style = base)
    }
}
