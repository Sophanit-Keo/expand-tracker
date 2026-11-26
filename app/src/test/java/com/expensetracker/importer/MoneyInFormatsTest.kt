package com.expensetracker.importer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Banks show money in many ways. Each of these used to drop the money-in row, use the balance as
 * its amount, or import it as spending, so cash in didn't add up.
 */
class MoneyInFormatsTest {
    private val expected = listOf("KFC" to Direction.OUT to 12.5, "Dara Sok" to Direction.IN to 1000.0)

    private fun lines(text: String): List<PdfLine> = text.trimIndent().lines().mapIndexed { index, line ->
        val words = Regex("""\S+""").findAll(line).map { m -> PdfWord(m.value, m.range.first * 5f, (m.range.last + 1) * 5f) }.toList()
        PdfLine(0, index * 14f, words)
    }

    private fun assertPdf(text: String) = assertEquals(
        expected,
        PdfStatementParser.parse(lines(text)).transactions.map { it.description to it.direction to it.amount },
    )

    private fun assertCsv(text: String) {
        val rows = CsvReader.read(text)
        assertEquals(expected, TableMapper.read(rows, TableMapper.detect(rows)!!).transactions.map { it.description to it.direction to it.amount })
    }

    @Test fun pdfPlusSign() = assertPdf("03/11/2026 KFC -12.50\n05/11/2026 Dara Sok +1,000.00")

    @Test fun pdfPlusSignWithBalance() = assertPdf("03/11/2026 KFC -12.50 487.50\n05/11/2026 Dara Sok +1,000.00 1,487.50")

    @Test fun pdfSeparateSignTokens() = assertPdf("03/11/2026 KFC - 12.50\n05/11/2026 Dara Sok + 1,000.00")

    @Test fun pdfMarkerBeforeAmount() = assertPdf("03/11/2026 KFC DR 12.50\n05/11/2026 Dara Sok CR 1,000.00")

    @Test fun pdfDebitAmountCreditAmountColumns() = assertPdf(
        """
        Date         Description          Debit Amount    Credit Amount
        03/11/2026   KFC                         12.50
        05/11/2026   Dara Sok                                1,000.00
        """,
    )

    @Test fun pdfSignedAmountColumn() = assertPdf(
        """
        Date         Description          Amount        Balance
        03/11/2026   KFC                  -12.50         487.50
        05/11/2026   Dara Sok           +1,000.00      1,487.50
        """,
    )

    @Test fun csvTypeMoneyInOut() = assertCsv("Date,Description,Amount,Type\n03/11/2026,KFC,12.50,Money Out\n05/11/2026,Dara Sok,1000.00,Money In\n")

    @Test fun csvTypeIncomeExpense() = assertCsv("Date,Description,Amount,Transaction Type\n03/11/2026,KFC,12.50,Expense\n05/11/2026,Dara Sok,1000.00,Income\n")

    @Test fun csvPlusMinus() = assertCsv("Date,Description,Amount\n03/11/2026,KFC,-12.50\n05/11/2026,Dara Sok,+1000.00\n")

    @Test fun csvCashInCashOutColumns() = assertCsv("Date,Remark,Cash Out,Cash In,Balance\n03/11/2026,KFC,12.50,,487.50\n05/11/2026,Dara Sok,,1000.00,1487.50\n")

    @Test fun csvPaidReceivedColumns() = assertCsv("Date,Details,Paid,Received\n03/11/2026,KFC,12.50,\n05/11/2026,Dara Sok,,1000.00\n")

    @Test
    fun typeWords() {
        assertEquals(Direction.OUT, TableMapper.directionFromType("Card purchase"))
        assertEquals(Direction.IN, TableMapper.directionFromType("Refund of purchase"))
        assertEquals(Direction.IN, TableMapper.directionFromType("CR"))
        assertEquals(Direction.OUT, TableMapper.directionFromType("Transfer Out"))
        assertEquals(null, TableMapper.directionFromType("Transfer"))
    }
}
