package com.expensetracker.importer

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Minimal reader for Excel .xlsx files (a zip of XML parts): returns the first non-empty worksheet as
 * rows of text. Date-formatted cells are returned as ISO dates (yyyy-MM-dd).
 */
object XlsxReader {
    private const val MAX_PART_BYTES = 30 * 1024 * 1024

    class FormatException(val problem: ImportProblem) : Exception(problem.name)

    fun read(bytes: ByteArray): List<List<String>> {
        val parts = unzip(bytes)
        val sharedStrings = parts["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val dateStyles = parts["xl/styles.xml"]?.let(::readDateStyles).orEmpty()
        val sheets = sheetOrder(parts).ifEmpty {
            parts.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted()
        }
        if (sheets.isEmpty()) throw FormatException(ImportProblem.EXCEL_NO_SHEETS)
        for (sheet in sheets) {
            val rows = readSheet(parts[sheet] ?: continue, sharedStrings, dateStyles)
            if (rows.count { row -> row.any { it.isNotBlank() } } >= 2) return rows
        }
        throw FormatException(ImportProblem.EXCEL_EMPTY)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("/")
                if (name.startsWith("xl/") && name.endsWith(".xml") || name.endsWith(".rels")) {
                    val data = zip.readBytes()
                    if (data.size > MAX_PART_BYTES) throw FormatException(ImportProblem.EXCEL_TOO_LARGE)
                    parts[name] = data
                }
            }
        }
        if (parts.isEmpty()) throw FormatException(ImportProblem.NOT_XLSX)
        return parts
    }

    private fun sax(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        // No external entities: spreadsheets never need them.
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    private fun Attributes.get(name: String): String? = getValue(name) ?: (0 until length).firstOrNull {
        getQName(it).substringAfter(':') == name || getLocalName(it) == name
    }?.let { getValue(it) }

    private fun local(qName: String, localName: String?) = localName?.takeIf { it.isNotEmpty() } ?: qName.substringAfter(':')

    /** Worksheet part names in the order the workbook lists them. */
    private fun sheetOrder(parts: Map<String, ByteArray>): List<String> {
        val workbook = parts["xl/workbook.xml"] ?: return emptyList()
        val rels = parts["xl/_rels/workbook.xml.rels"] ?: return emptyList()
        val ids = mutableListOf<String>()
        sax(workbook, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                if (local(qName, localName) == "sheet") attributes.get("id")?.let(ids::add)
            }
        })
        val targets = mutableMapOf<String, String>()
        sax(rels, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                if (local(qName, localName) == "Relationship") {
                    val id = attributes.get("Id") ?: return
                    val target = attributes.get("Target") ?: return
                    targets[id] = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                }
            }
        })
        return ids.mapNotNull { targets[it] }.filter { parts.containsKey(it) }
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        sax(bytes, object : DefaultHandler() {
            val current = StringBuilder()
            var inText = false
            var inPhonetic = false
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (local(qName, localName)) {
                    "si" -> current.clear()
                    "t" -> inText = !inPhonetic
                    "rPh" -> inPhonetic = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName, localName)) {
                    "si" -> strings += current.toString()
                    "t" -> inText = false
                    "rPh" -> inPhonetic = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) current.append(ch, start, length)
            }
        })
        return strings
    }

    /** Indexes of cell styles (cellXfs) that display a date. */
    private fun readDateStyles(bytes: ByteArray): Set<Int> {
        val builtInDateFormats = (14..22).toSet() + (27..36) + (45..47) + (50..58)
        val customDateFormats = mutableSetOf<Int>()
        val styleFormats = mutableListOf<Int>()
        sax(bytes, object : DefaultHandler() {
            var inCellXfs = false
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (local(qName, localName)) {
                    "numFmt" -> {
                        val id = attributes.get("numFmtId")?.toIntOrNull() ?: return
                        val code = attributes.get("formatCode").orEmpty()
                        val unquoted = code.replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "").lowercase()
                        if (unquoted.contains('d') || unquoted.contains('y')) customDateFormats += id
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) styleFormats += attributes.get("numFmtId")?.toIntOrNull() ?: 0
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (local(qName, localName) == "cellXfs") inCellXfs = false
            }
        })
        return styleFormats.withIndex()
            .filter { (_, fmt) -> fmt in builtInDateFormats || fmt in customDateFormats }
            .map { it.index }
            .toSet()
    }

    private fun columnIndex(ref: String): Int {
        var index = 0
        for (c in ref) {
            if (!c.isLetter()) break
            index = index * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>, dateStyles: Set<Int>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        sax(bytes, object : DefaultHandler() {
            var row = mutableMapOf<Int, String>()
            var nextColumn = 0
            var cellColumn = 0
            var cellType: String? = null
            var cellStyle = 0
            val value = StringBuilder()
            var capture = false

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (local(qName, localName)) {
                    "row" -> { row = mutableMapOf(); nextColumn = 0 }
                    "c" -> {
                        cellColumn = attributes.get("r")?.let(::columnIndex)?.takeIf { it >= 0 } ?: nextColumn
                        nextColumn = cellColumn + 1
                        cellType = attributes.get("t")
                        cellStyle = attributes.get("s")?.toIntOrNull() ?: 0
                        value.clear()
                    }
                    "v", "t" -> capture = true
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName, localName)) {
                    "v", "t" -> capture = false
                    "c" -> row[cellColumn] = cellText(value.toString())
                    "row" -> {
                        val width = (row.keys.maxOrNull() ?: -1) + 1
                        rows += List(width) { row[it].orEmpty() }
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capture) value.append(ch, start, length)
            }

            fun cellText(raw: String): String = when (cellType) {
                "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                "inlineStr", "str" -> raw
                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                "e" -> ""
                else -> {
                    val number = raw.trim()
                    when {
                        number.isEmpty() -> ""
                        cellStyle in dateStyles -> DateParser.fromExcelSerial(number)?.toString() ?: number
                        else -> runCatching { BigDecimal(number).stripTrailingZeros().toPlainString() }.getOrDefault(number)
                    }
                }
            }
        })
        return rows
    }
}
