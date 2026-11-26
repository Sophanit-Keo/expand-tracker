package com.expensetracker.importer

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs

/** Pulls every word out of a PDF together with its position, grouped into lines. */
class PdfTextExtractor(private val maxPages: Int = 60) {

    fun extract(document: PDDocument): List<PdfLine> {
        val stripper = PositionStripper()
        stripper.sortByPosition = true
        stripper.startPage = 1
        stripper.endPage = minOf(document.numberOfPages, maxPages)
        stripper.getText(document) // drives the callbacks; the plain text itself isn't needed
        return stripper.lines
    }

    private class PositionedWord(val word: PdfWord, val y: Float, val height: Float)

    private class PositionStripper : PDFTextStripper() {
        val lines = mutableListOf<PdfLine>()
        private val pageWords = mutableListOf<PositionedWord>()
        private var pageIndex = 0

        override fun startPage(page: PDPage?) {
            pageWords.clear()
            super.startPage(page)
        }

        override fun endPage(page: PDPage?) {
            lines += groupIntoLines(pageWords, pageIndex)
            pageIndex++
            super.endPage(page)
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val positions = textPositions ?: return
            val current = StringBuilder()
            var x0 = 0f
            var x1 = 0f
            var y = 0f
            var h = 0f
            fun flush() {
                if (current.isNotBlank()) pageWords += PositionedWord(PdfWord(current.toString(), x0, x1), y, h)
                current.clear()
            }
            for (tp in positions) {
                val ch = tp.unicode ?: continue
                val gap = tp.xDirAdj - x1
                val spaceWidth = tp.widthOfSpace.takeIf { it > 0f } ?: (tp.widthDirAdj * 0.5f)
                if (ch.isBlank() || (current.isNotEmpty() && gap > spaceWidth * 0.6f)) flush()
                if (ch.isBlank()) continue
                if (current.isEmpty()) {
                    x0 = tp.xDirAdj
                    y = tp.yDirAdj
                    h = tp.heightDir
                }
                current.append(ch)
                x1 = tp.xDirAdj + tp.widthDirAdj
            }
            flush()
        }

        private fun groupIntoLines(words: List<PositionedWord>, page: Int): List<PdfLine> {
            val sorted = words.sortedWith(compareBy({ it.y }, { it.word.x0 }))
            val result = mutableListOf<PdfLine>()
            var bucket = mutableListOf<PositionedWord>()
            var bucketY = Float.NaN
            for (w in sorted) {
                val tolerance = maxOf(2.5f, w.height * 0.45f)
                if (bucket.isNotEmpty() && abs(w.y - bucketY) > tolerance) {
                    result += PdfLine(page, bucketY, bucket.sortedBy { it.word.x0 }.map { it.word })
                    bucket = mutableListOf()
                }
                if (bucket.isEmpty()) bucketY = w.y
                bucket += w
            }
            if (bucket.isNotEmpty()) result += PdfLine(page, bucketY, bucket.sortedBy { it.word.x0 }.map { it.word })
            return result
        }
    }
}
