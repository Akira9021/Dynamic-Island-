package com.akira.island.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService
import com.akira.island.service.IslandTile
import com.akira.island.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Only the launcher alias is optional. MainActivity and the system settings routes stay enabled.
 * Recovery must have been opened from an added tile before the alias can be hidden.
 */
object LauncherVisibility {
    private const val ALIAS = "com.akira.island.Launcher"
    private const val PREFS = "launcher_visibility"
    private const val TILE_ADDED = "tile_added"
    private const val RECOVERY_TESTED = "recovery_tested"

    data class State(
        val hidden: Boolean = false,
        val tileAdded: Boolean = false,
        val recoveryTested: Boolean = false,
        val recoveryAvailable: Boolean = false,
    ) {
        val canHide: Boolean get() = tileAdded && recoveryTested && recoveryAvailable
    }

    private val lock = Any()
    private var recoveryTestedThisProcess = false
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    fun launcherComponent(context: Context) = ComponentName(context, ALIAS)

    /** Explicit activity intent: it also works while there is no launch intent for the package. */
    fun settingsIntent(context: Context) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun refresh(context: Context): State = synchronized(lock) {
        var current = read(context)
        // Lost/cleared proof must fail open. PackageManager owns the alias state across app updates.
        if (current.hidden && (!current.tileAdded || !current.recoveryAvailable ||
                !prefs(context).getBoolean(RECOVERY_TESTED, false))) {
            setAliasEnabled(context, true)
            current = read(context)
        }
        mutableState.value = current
        current
    }

    /** Called only by TileService.onTileAdded or a successful system add-tile acknowledgement. */
    fun onTileAdded(context: Context) = synchronized(lock) {
        prefs(context).edit().putBoolean(TILE_ADDED, true).apply()
        refresh(context)
    }

    /** Removing the recovery route restores the icon without requiring a notification permission. */
    fun onTileRemoved(context: Context) = synchronized(lock) {
        setAliasEnabled(context, true)
        recoveryTestedThisProcess = false
        prefs(context).edit().remove(TILE_ADDED).remove(RECOVERY_TESTED).apply()
        refresh(context)
    }

    /**
     * Ordinary app launches do not count. This is a UX recovery check, not sender authentication:
     * another app can send the exported preferences action, but hiding still needs an explicit tap.
     */
    fun onSettingsOpened(context: Context, intent: Intent) = synchronized(lock) {
        if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES &&
            tileComponent(intent) == ComponentName(context, IslandTile::class.java) &&
            prefs(context).getBoolean(TILE_ADDED, false)
        ) {
            recoveryTestedThisProcess = prefs(context).edit().putBoolean(RECOVERY_TESTED, true).commit()
        }
        refresh(context)
    }

    /** Returns false if the system recovery route has not been tested or PackageManager refused. */
    fun setHidden(context: Context, hidden: Boolean): Boolean = synchronized(lock) {
        if (hidden && !refresh(context).canHide) return@synchronized false
        val changed = setAliasEnabled(context, !hidden)
        if (changed && !hidden) recoveryTestedThisProcess = false
        mutableState.value = read(context)
        changed && mutableState.value.hidden == hidden
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun tileComponent(intent: Intent): ComponentName? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_COMPONENT_NAME) as? ComponentName
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun read(context: Context): State {
        val pm = context.packageManager
        val recovery = runCatching {
            val main = ComponentName(context, MainActivity::class.java)
            pm.getActivityInfo(main, 0).enabled &&
                pm.resolveActivity(Intent(TileService.ACTION_QS_TILE_PREFERENCES).setPackage(context.packageName), 0)
                    ?.activityInfo?.name == main.className
        }.getOrDefault(false)
        return State(
            hidden = pm.getComponentEnabledSetting(launcherComponent(context)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            tileAdded = prefs(context).getBoolean(TILE_ADDED, false),
            recoveryTested = recoveryTestedThisProcess && prefs(context).getBoolean(RECOVERY_TESTED, false),
            recoveryAvailable = recovery,
        )
    }

    private fun setAliasEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        context.packageManager.setComponentEnabledSetting(
            launcherComponent(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }.isSuccess
}
