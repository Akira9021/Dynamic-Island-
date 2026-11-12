package com.akira.island.service

import java.util.Locale

/** Original parser of public notification text. Callers must verify the system source first. */
internal object HotspotNotificationParser {
    private val topic = Regex("\\bhotspot\\b|точк[а-я]* доступ[а-я]*|titik akses|ponto de acesso|punto de acceso|point d.accès|кіру нүктесі|қатынау нүктесі")
    private val on = Regex("(?:${topic.pattern})\\s*(?:is\\s*)?[:：·-]?\\s*(?:enabled|turned on|on\\b|active\\b|включен|активн|diaktifkan|aktif|ativado|activado|activé|қосулы)")
    private val offState = "disabled|turned off|(?:is )?off|inactive|not active|выключен[а-я]*|отключен[а-я]*|не включен[а-я]*|dinonaktifkan|desativado|desactivado|désactivé|өшірулі"
    private val off = Regex("(?:${topic.pattern})\\s*(?:(?:wi-fi|is|was|была?)\\s*)?[:：·-]?\\s*(?:$offState)")
    private val standaloneOff = Regex("^(?:$offState)[.!]?$")
    private val counts = listOf(
        Regex("(?<![\\d.,])([0-9]{1,3})\\s+(?:devices?|clients?)\\s+(?:are\\s+)?connected\\b"),
        Regex("\\b([0-9]{1,3})\\s+connected\\s+(?:devices?|clients?)\\b"),
        Regex("\\bconnected\\s+(?:devices?|clients?)\\s*[:：]\\s*([0-9]{1,3})(?![\\d.,])"),
        Regex("подключено\\s+(?:устройств[а-я]*\\s*[:：]?\\s*)?([0-9]{1,3})(?![\\d.,])"),
        Regex("(?<![\\d.,])([0-9]{1,3})\\s+(?:подключ[её]нн[а-я]*\\s+устройств[а-я]*|устройств[а-я]*\\s+подключ[её]н[а-я]*)"),
        Regex("(?<![\\d.,])([0-9]{1,3})\\s+(?:perangkat\\s+terhubung|dispositivos?\\s+(?:conectados?|ligados?)|appareils?\\s+connectés?|құрылғы\\s+қосылған)"),
        Regex("(?:terhubung|қосылған құрылғылар)\\s*[:：]\\s*([0-9]{1,3})(?![\\d.,])"),
    )
    private val noClients = Regex("no (?:devices?|clients?) connected|нет подключ[её]нных устройств|устройства не подключены|tidak ada perangkat terhubung|nenhum dispositivo conectado|ningún dispositivo conectado|aucun appareil connecté")

    fun parse(trustedSource: Boolean, ongoing: Boolean, texts: List<String>): HotspotEvidence? {
        if (!trustedSource) return null
        val text = texts.take(12).joinToString("\n") { it.take(1_000) }.lowercase(Locale.ROOT).replace('ё', 'е')
        // Android's VPN notification may contain the name of the unrelated Hotspot Shield app.
        if ("hotspot shield" in text) return null
        if (!topic.containsMatchIn(text)) return null
        if (off.containsMatchIn(text) || text.lines().any { standaloneOff.matches(it.trim()) }) return HotspotEvidence(false)
        val found = counts.flatMap { pattern -> pattern.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList() }.toMutableSet()
        if (noClients.containsMatchIn(text)) found += 0
        val clients = found.singleOrNull()
        return if (ongoing || clients != null || on.containsMatchIn(text)) HotspotEvidence(true, clients) else null
    }
}
