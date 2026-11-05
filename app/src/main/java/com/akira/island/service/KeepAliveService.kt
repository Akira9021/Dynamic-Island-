package com.akira.island.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import android.provider.Settings
import com.akira.island.core.IslandBus
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Perms

/**
 * «Не выгружать остров»: служба на переднем плане с тихим уведомлением. Процесс с такой службой Android
 * не закрывает вместе с приложением, когда его смахивают из недавних, — остров остаётся на экране.
 * Поддерживает foreground-уведомление и проверяет подключение слушателя только при включённом экране.
 */
class KeepAliveService : Service() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var maintenance: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SettingsRepo.init(this)
        // Запущенная через startForegroundService служба обязана выйти на передний план, даже если её уже выключили:
        // иначе Android закрывает приложение с ошибкой. Поэтому сначала выходим, потом при необходимости уходим
        val shown = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification())
            }
        }.isSuccess
        if (!shown || !SettingsRepo.current.keepAlive || !SettingsRepo.current.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        maintenance?.cancel()
        maintenance = scope.launch {
            IslandBus.screenOn.collectLatest { screenOn ->
                if (screenOn) while (SettingsRepo.current.keepAlive && SettingsRepo.current.enabled) {
                    if (Perms.notifications(this@KeepAliveService) && !NotifListener.isConnected) {
                        NotifListener.requestReconnect(this@KeepAliveService)
                    }
                    delay(60_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Работа острова", "Island running"), NotificationManager.IMPORTANCE_MIN).apply {
                description = tr("Не даёт Android закрыть остров, когда его смахивают из недавних",
                    "Keeps Android from closing the island when it's swiped away from recent apps")
                setShowBadge(false)
            },
        )
        // Нажатие открывает настройки острова
        val open = packageManager.getLaunchIntentForPackage(packageName)
            ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val restore = PendingIntent.getActivity(this, 1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_island)
            .setContentTitle(tr("Остров работает", "The island is running"))
            .setContentText(tr("Уведомление не даёт системе закрыть остров", "This notification keeps the system from closing the island"))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null,
                tr("Восстановить остров", "Restore island"), restore).build())
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val ID = 7
        private const val CHANNEL = "keep_alive"

        /**
         * Запустить или остановить по настройке. Служба нужна, только пока включён сам остров (спец. возможности).
         * Из фона Android 12+ может её не запустить — тогда она запустится, когда откроют приложение.
         */
        fun sync(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (SettingsRepo.current.keepAlive && SettingsRepo.current.enabled && Perms.accessibility(context)) {
                runCatching { context.startForegroundService(intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
