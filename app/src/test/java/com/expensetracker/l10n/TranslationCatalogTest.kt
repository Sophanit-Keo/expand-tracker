package com.expensetracker.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every English string has a Khmer translation with the same placeholders, and nothing extra. */
class TranslationCatalogTest {
    private class Entry(val texts: Map<String, String>, val translatable: Boolean)

    private fun read(path: String): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val out = linkedMapOf<String, Entry>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            out[e.getAttribute("name")] = Entry(mapOf("" to e.textContent), e.getAttribute("translatable") != "false")
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val items = e.getElementsByTagName("item")
            out[e.getAttribute("name")] = Entry(
                (0 until items.length).associate { j -> (items.item(j) as Element).let { it.getAttribute("quantity") to it.textContent } },
                translatable = true,
            )
        }
        return out
    }

    private val english = read("src/main/res/values/strings.xml")
    private val khmer = read("src/main/res/values-km/strings.xml")

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.toSortedSet()

    @Test
    fun everyStringIsTranslated() {
        val missing = english.filterValues { it.translatable }.keys - khmer.keys
        assertTrue("Missing in values-km: $missing", missing.isEmpty())
        val extra = khmer.keys - english.keys
        assertTrue("Only in values-km: $extra", extra.isEmpty())
    }

    @Test
    fun placeholdersMatch() {
        khmer.forEach { (name, km) ->
            val en = english.getValue(name)
            val expected = en.texts.values.map(::placeholders).reduce { a, b -> (a + b).toSortedSet() }
            km.texts.values.forEach { text -> assertEquals("Placeholders in $name: $text", expected, placeholders(text)) }
        }
    }

    @Test
    fun khmerPluralsUseOther() {
        // Khmer has no singular form: Android only ever asks for "other".
        khmer.filterValues { it.texts.keys != setOf("") }.forEach { (name, km) ->
            assertEquals("Plural forms of $name", setOf("other"), km.texts.keys)
        }
    }

    @Test
    fun khmerTextIsKhmer() {
        // A string that is still English in values-km was probably copied by mistake.
        val allowed = setOf("chart_point", "with_year", "pattern_day", "pattern_day_year", "pattern_short_day", "pattern_month_year",
            "pattern_week_start_same_month", "pattern_week_end_same_month", "pattern_date_year", "pending_both")
        val notKhmer = khmer.filter { (name, km) -> name !in allowed && km.texts.values.none { t -> t.any { it in 'ក'..'៿' } } }.keys
        assertTrue("Not translated: $notKhmer", notKhmer.isEmpty())
    }
}
