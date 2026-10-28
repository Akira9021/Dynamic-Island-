package com.akira.island.island

import com.akira.island.core.AlarmInfo
import com.akira.island.core.InboxItem
import com.akira.island.core.BatteryInfo
import com.akira.island.core.CallInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.LiveInfo
import com.akira.island.core.MediaInfo
import com.akira.island.core.Notice
import com.akira.island.core.PayReady
import com.akira.island.core.ProgressInfo
import com.akira.island.core.StopwatchState
import com.akira.island.core.TimerState
import com.akira.island.core.TorchState

/** То, что сейчас может показывать остров. Короткие (transient) не раскрываются и не уходят в пузырь. */
sealed class Act(val id: String, val transient: Boolean = false) {
    open val pkg: String? get() = null

    data object Idle : Act("idle")
    data class NoticeA(val n: Notice) : Act("notice", transient = true)
    data class TimerA(val t: TimerState) : Act("timer")
    data class StopwatchA(val w: StopwatchState) : Act("stopwatch")
    data class TorchA(val t: TorchState) : Act("torch")
    data class ChronoA(val c: ChronoInfo) : Act("chrono") {
        override val pkg: String get() = c.pkg
    }
    data class MediaA(val m: MediaInfo) : Act("media") {
        override val pkg: String get() = m.pkg
    }
    data class ProgressA(val p: ProgressInfo) : Act("progress") {
        override val pkg: String get() = p.pkg
    }
    data class CallA(val c: CallInfo) : Act("call") {
        override val pkg: String get() = c.pkg
    }
    data class LiveA(val l: LiveInfo) : Act("live") {
        override val pkg: String get() = l.pkg
    }
    data class ChargingA(val b: BatteryInfo) : Act("charging")
    /** Оплата телефоном: ждём терминал. Не раскрывается и не уходит в пузырь */
    data class PayA(val p: PayReady) : Act("pay", transient = true) {
        override val pkg: String get() = p.pkg
    }
    data class AlarmA(val a: AlarmInfo) : Act("alarm") {
        override val pkg: String? get() = a.pkg
    }
    /** «История уведомлений»: прочитать позже — значки приложений в капсуле, список при раскрытии */
    data class InboxA(val items: List<InboxItem>) : Act("inbox")
}
