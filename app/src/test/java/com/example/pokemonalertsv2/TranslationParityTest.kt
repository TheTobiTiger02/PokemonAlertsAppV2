package com.example.pokemonalertsv2

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every string exists in English and German, with the same format arguments, so
 * switching the app's language never falls back to English mid-screen or
 * crashes on a missing argument.
 */
class TranslationParityTest {
    private val entry = Regex("""<(string|plurals) name="([^"]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
    private val argument = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc]""")

    private fun resources(folder: String): Map<String, String> {
        val dir = listOf("src/main/res/$folder", "app/src/main/res/$folder").map(::File).first { it.isDirectory }
        return dir.listFiles { file -> file.extension == "xml" }.orEmpty()
            .flatMap { file -> entry.findAll(file.readText()).map { it.groupValues[2] to it.groupValues[3] }.toList() }
            .toMap()
    }

    private fun arguments(value: String): List<String> =
        argument.findAll(value).map { it.value.replace(Regex("""[-#+ 0,(]*\d*(\.\d+)?(?=[sdfxXc])"""), "") }.sorted().toList()

    @Test
    fun everyStringIsTranslatedWithTheSameArguments() {
        val english = resources("values")
        val german = resources("values-de")
        val missing = english.keys - german.keys
        val extra = german.keys - english.keys
        assertTrue("Missing in values-de: ${missing.sorted()}", missing.isEmpty())
        assertTrue("Only in values-de: ${extra.sorted()}", extra.isEmpty())
        val mismatched = english.keys.filter { key -> arguments(english.getValue(key)) != arguments(german.getValue(key)) }
        assertTrue("Format arguments differ: $mismatched", mismatched.isEmpty())
    }
}
