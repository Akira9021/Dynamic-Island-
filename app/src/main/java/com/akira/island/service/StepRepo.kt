package com.akira.island.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.akira.island.data.ExtrasRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Hardware counter deltas; never presents the boot total as today's step count. */
internal class DailySteps(var day: String = "", var previous: Long = -1, var count: Long = 0) {
    fun sample(today: String, total: Long): Long {
        if (today != day) { day = today; count = 0; previous = -1 }
        if (previous >= 0 && total >= previous) count += total - previous
        previous = total
        return count
    }
}
object StepRepo {
    val count = MutableStateFlow<Long?>(null)
    fun start(context: Context, scope: CoroutineScope) {
        scope.launch {
            ExtrasRepo.state.map { it.steps }.distinctUntilChanged().collectLatest { enabled ->
                count.value = null
                if (!enabled || Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return@collectLatest
                val manager = context.getSystemService(SensorManager::class.java) ?: return@collectLatest
                val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return@collectLatest
                val prefs = context.getSharedPreferences("island_steps", Context.MODE_PRIVATE)
                val tracker = DailySteps(prefs.getString("day", "").orEmpty(), prefs.getLong("last", -1), prefs.getLong("count", 0))
                val listener = object : SensorEventListener {
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    override fun onSensorChanged(event: SensorEvent) {
                        val total = event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0 }?.toLong() ?: return
                        count.value = tracker.sample(LocalDate.now().toString(), total)
                        prefs.edit().putString("day", tracker.day).putLong("last", tracker.previous).putLong("count", tracker.count).apply()
                    }
                }
                try {
                    if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) awaitCancellation()
                } catch (_: SecurityException) {
                    // Access can be revoked after the permission check; keep the rest of the island alive.
                    count.value = null
                } catch (_: IllegalStateException) {
                    count.value = null
                } finally { manager.unregisterListener(listener) }
            }
        }
    }
}
