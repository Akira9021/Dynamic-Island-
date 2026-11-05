package com.akira.island.core

import com.akira.island.data.AppLanguage
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Translation tables are data: validate their contract without starting Android. */
class TranslationAssetsTest {
    private val assets = File("src/main/assets/i18n")
    private val placeholders = Regex("\\{\\d+\\}|%(?:\\d+\\$)?(?:\\.\\d+)?[sdf]")

    @Test
    fun frenchAndSpanishCoverExistingStringsAndPreserveFormatting() {
        val baseline = Json.parseToJsonElement(File(assets, "pt.json").readText()).jsonObject.keys
        for (language in listOf("fr", "es")) {
            val table = Json.parseToJsonElement(File(assets, "$language.json").readText()).jsonObject
            assertTrue("$language is missing existing strings", table.keys.containsAll(baseline))
            for ((key, value) in table) {
                val text = value.jsonPrimitive.content
                assertTrue("$language has an empty translation for $key", text.isNotBlank())
                assertEquals("$language changed placeholders for $key",
                    placeholders.findAll(key).map { it.value }.sorted().toList(),
                    placeholders.findAll(text).map { it.value }.sorted().toList())
            }
        }
    }

    @Test
    fun bothLanguagesAreSelectable() {
        assertEquals("fr", AppLanguage.FR.code)
        assertEquals("es", AppLanguage.ES.code)
    }
}
