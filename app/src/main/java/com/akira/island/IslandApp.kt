package com.akira.island

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import com.akira.island.core.IslandBus
import com.akira.island.core.Lang
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.SetTimerActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class IslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepo.init(this)
        Lang.init(this)
        // «Таймер от помощника»: выключено — остров не предлагается в списке, когда просят поставить таймер
        IslandBus.scope.launch {
            SettingsRepo.state.map { it.timerFromAssistant }.distinctUntilChanged().collect { on ->
                runCatching {
                    packageManager.setComponentEnabledSetting(
                        ComponentName(this@IslandApp, SetTimerActivity::class.java),
                        if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }
}
