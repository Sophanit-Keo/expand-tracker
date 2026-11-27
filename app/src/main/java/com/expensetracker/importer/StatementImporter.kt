package com.expensetracker.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** What was found in a statement file. */
sealed interface StatementContent {
    /** A spreadsheet (CSV or Excel): rows of cells that still need a column mapping. */
    data class Table(val kind: StatementKind, val rows: List<List<String>>) : StatementContent

    /** A PDF: transactions were found directly in the text. */
    data class Pdf(val result: PdfParseResult) : StatementContent

    data object PasswordRequired : StatementContent

    data class Unsupported(val problem: ImportProblem) : StatementContent
}

/** Reads a statement chosen by the user. Everything happens on the device. */
class StatementImporter(private val context: Context) {
    private var pdfBoxReady = false

    data class PickedFile(val name: String, val bytes: ByteArray)

    suspend fun read(uri: Uri): PickedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "statement"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = input.readBytes()
            if (buffer.size > MAX_BYTES) throw ImportException(ImportProblem.TOO_LARGE)
            buffer
        } ?: throw ImportException(ImportProblem.CANT_OPEN)
        PickedFile(name, bytes)
    }

    suspend fun load(file: PickedFile, password: String? = null): StatementContent = withContext(Dispatchers.Default) {
        when (detectKind(file)) {
            StatementKind.PDF -> loadPdf(file.bytes, password)
            StatementKind.EXCEL -> runCatching { StatementContent.Table(StatementKind.EXCEL, XlsxReader.read(file.bytes)) }
                .getOrElse { StatementContent.Unsupported((it as? XlsxReader.FormatException)?.problem ?: ImportProblem.EXCEL_UNREADABLE) }
            StatementKind.CSV -> {
                if (isLegacyExcel(file.bytes)) {
                    StatementContent.Unsupported(ImportProblem.OLD_EXCEL)
                } else {
                    val rows = CsvReader.read(CsvReader.decode(file.bytes))
                    if (rows.size < 2) StatementContent.Unsupported(ImportProblem.NO_ROWS)
                    else StatementContent.Table(StatementKind.CSV, rows)
                }
            }
        }
    }

    private fun loadPdf(bytes: ByteArray, password: String?): StatementContent {
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
        val document = try {
            PDDocument.load(bytes, password.orEmpty())
        } catch (e: InvalidPasswordException) {
            return StatementContent.PasswordRequired
        } catch (e: IOException) {
            return StatementContent.Unsupported(ImportProblem.PDF_DAMAGED)
        }
        return document.use { doc ->
            val lines = PdfTextExtractor().extract(doc)
            if (lines.sumOf { it.words.size } < 10) {
                StatementContent.Unsupported(ImportProblem.PDF_NO_TEXT)
            } else {
                StatementContent.Pdf(PdfStatementParser.parse(lines))
            }
        }
    }

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024

        fun detectKind(file: PickedFile): StatementKind {
            val b = file.bytes
            return when {
                b.size >= 4 && b[0] == '%'.code.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'D'.code.toByte() && b[3] == 'F'.code.toByte() -> StatementKind.PDF
                b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2] == 3.toByte() && b[3] == 4.toByte() -> StatementKind.EXCEL
                file.name.endsWith(".pdf", ignoreCase = true) -> StatementKind.PDF
                else -> StatementKind.CSV
            }
        }

        /** Old binary .xls files start with the OLE2 signature D0 CF 11 E0. */
        private fun isLegacyExcel(b: ByteArray) =
            b.size >= 4 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() && b[2] == 0x11.toByte() && b[3] == 0xE0.toByte()
    }
}
