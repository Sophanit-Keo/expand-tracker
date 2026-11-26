package com.expensetracker.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object CsvReader {
    /** Decodes bank exports: UTF-8 (with or without BOM), UTF-16 with BOM, else Windows-1252. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        val utf8 = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return utf8.removePrefix("﻿")
    }

    fun read(text: String): List<List<String>> = parse(text, detectDelimiter(text))

    /** The candidate that splits the first lines into the most columns, most consistently. */
    fun detectDelimiter(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(30).toList()
        val candidates = listOf(',', ';', '\t', '|')
        return candidates.maxByOrNull { delimiter ->
            val counts = lines.map { line -> countOutsideQuotes(line, delimiter) }
            val common = counts.groupingBy { it }.eachCount().filterKeys { it > 0 }.maxByOrNull { it.value }
            // Score: how many lines share the most common non-zero count, weighted by columns.
            if (common == null) 0 else common.value * 100 + common.key
        } ?: ','
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (c in line) {
            if (c == '"') inQuotes = !inQuotes else if (c == delimiter && !inQuotes) count++
        }
        return count
    }

    /** RFC 4180 parsing: quoted fields may contain delimiters, doubled quotes and line breaks. */
    fun parse(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endField() {
            row += field.toString().trim()
            field.clear()
        }
        fun endRow() {
            endField()
            if (row.any { it.isNotEmpty() }) rows += row.toList()
            row.clear()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' && (inQuotes || field.isBlank()) -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
