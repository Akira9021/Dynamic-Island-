package com.akira.island.service

/** Ephemeral device identity never enters Notice, preferences or diagnostics. */
internal class HeadsetBatteryTracker {
    private data class Device(var level: Int? = null, var noticeId: Long? = null)
    private val devices = LinkedHashMap<String, Device>()
    private val bindings = LinkedHashMap<Long, String?>()
    private fun device(key: String): Device = devices.getOrPut(key) {
        if (devices.size >= 24) disconnect(devices.keys.first())
        Device()
    }
    fun battery(key: String, value: Int) { device(key).level = value.takeIf { it in 0..100 } }
    fun connect(key: String, id: Long): Int? = device(key).let {
        it.noticeId?.let { old -> bindings[old] = null }
        it.noticeId = id
        if (bindings.size >= 96) bindings.remove(bindings.keys.first())
        bindings[id] = key
        it.level
    }
    fun disconnect(key: String) {
        devices.remove(key)
        bindings.entries.forEach { if (it.value == key) it.setValue(null) }
    }
    fun levelForNotice(id: Long): Pair<Boolean, Int?> {
        return bindings.containsKey(id) to bindings[id]?.let { devices[it]?.level }
    }
    fun invalidateAll() { devices.clear(); bindings.entries.forEach { it.setValue(null) } }
    fun clear() { devices.clear(); bindings.clear() }
    companion object {
        // The existing renderer truncates ring*100; avoid showing 52% for an exact 53% reading.
        fun ring(level: Int?): Float? = level?.takeIf { it in 0..100 }?.let {
            if (it in 1..99) java.lang.Math.nextUp(it / 100f) else it / 100f
        }
    }
}
