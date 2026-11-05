package com.akira.island.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ContactShortcut(val name: String, val uri: String)

@Serializable
data class ExtraSettings(
    val controls: Boolean = false,
    val steps: Boolean = false,
    val analogClock: Boolean = false,
    val apps: List<String> = emptyList(),
    val contacts: List<ContactShortcut> = emptyList(),
    val weatherEnabled: Boolean = false,
    val weatherCity: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

/** Optional panel preferences are separate from the island layout migration. */
object ExtrasRepo {
    val state = MutableStateFlow(ExtraSettings())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var prefs: android.content.SharedPreferences? = null
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("island_extras", Context.MODE_PRIVATE)
        prefs?.getString("settings", null)?.let { raw ->
            runCatching { json.decodeFromString<ExtraSettings>(raw) }.onSuccess { state.value = it }
        }
    }
    fun update(block: ExtraSettings.() -> ExtraSettings) {
        val next = state.value.block()
        prefs?.edit()?.putString("settings", json.encodeToString(ExtraSettings.serializer(), next))?.apply()
        state.value = next
    }
}
