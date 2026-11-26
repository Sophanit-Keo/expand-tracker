package com.expensetracker.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Regression tests for statements that used to import wrong amounts or directions. */
class StatementAccuracyTest {
    private fun lines(text: String): List<PdfLine> = text.trimIndent().lines().mapIndexed { index, line ->
        val words = Regex("""\S+""").findAll(line).map { m -> PdfWord(m.value, m.range.first * 5f, (m.range.last + 1) * 5f) }.toList()
        PdfLine(0, index * 14f, words)
    }

    @Test
    fun timeAfterDateDoesNotSwallowTheAmount() {
        // Used to read the balance (487.50) as the amount and lose the description.
        val result = PdfStatementParser.parse(
            lines(
                """
                Opening balance 500.00
                15/11/2026 10:32 Coffee 12.50 487.50
                16/11/2026 09:01 AM Salary 1,000.00 1,487.50
                17/11/2026 18:45:10 Grab ride 3.20 1,484.30
                """,
            ),
        )
        assertEquals(
            listOf(
                ParsedTransaction(LocalDate.of(2026, 11, 15), "Coffee", 12.5, Direction.OUT, 487.5),
                ParsedTransaction(LocalDate.of(2026, 11, 16), "Salary", 1000.0, Direction.IN, 1487.5),
                ParsedTransaction(LocalDate.of(2026, 11, 17), "Grab ride", 3.2, Direction.OUT, 1484.3),
            ),
            result.transactions,
        )
        assertEquals(500.0, result.summary.openingBalance!!, 0.0)
        assertEquals(1484.3, result.summary.closingBalance!!, 1e-9)
    }

    @Test
    fun dotThousandsAreNotDecimals() {
        // Used to read -40.000 as 40.
        val rows = CsvReader.read("Date,Description,Amount\n01/11/2026,Market,-40.000\n02/11/2026,Salary,\"1.500.000\"\n03/11/2026,Tuk tuk,-12.000\n")
        val tx = TableMapper.read(rows, TableMapper.detect(rows)!!).transactions
        assertEquals(listOf(40_000.0, 1_500_000.0, 12_000.0), tx.map { it.amount })
        assertEquals(listOf(Direction.OUT, Direction.IN, Direction.OUT), tx.map { it.direction })
    }

    @Test
    fun dotThousandsInPdf() {
        val tx = PdfStatementParser.parse(
            lines(
                """
                Opening balance 100.000
                01/11/2026 Market 40.000 60.000
                02/11/2026 Salary 1.500.000 1.560.000
                """,
            ),
        ).transactions
        assertEquals(listOf(40_000.0, 1_500_000.0), tx.map { it.amount })
        assertEquals(listOf(Direction.OUT, Direction.IN), tx.map { it.direction })
    }

    @Test
    fun runningBalanceDecidesDirectionWhenAllAmountsArePositive() {
        // Used to count the salary as spending.
        val rows = CsvReader.read(
            "Date,Description,Amount,Balance\n01/11/2026,Opening,0,500.00\n02/11/2026,Coffee,4.50,495.50\n" +
                "03/11/2026,Salary,1000.00,1495.50\n04/11/2026,Rent,300.00,1195.50\n",
        )
        val read = TableMapper.read(rows, TableMapper.detect(rows)!!)
        assertEquals(listOf(Direction.OUT, Direction.IN, Direction.OUT), read.transactions.map { it.direction })
        assertEquals(500.0, read.summary.openingBalance!!, 0.0)
        assertEquals(1195.5, read.summary.closingBalance!!, 0.0)
        assertEquals(1, read.summary.fixedByBalance)
        assertEquals(0, read.summary.balanceBreaks)
    }

    @Test
    fun untrustworthyBalanceColumnChangesNothing() {
        // "Balance" here doesn't follow the amounts at all, so the file's own signs are kept.
        val rows = CsvReader.read(
            "Date,Description,Amount,Balance\n02/11/2026,Coffee,-4.50,10\n03/11/2026,Salary,1000.00,10\n04/11/2026,Rent,-300.00,10\n",
        )
        val read = TableMapper.read(rows, TableMapper.detect(rows)!!)
        assertEquals(listOf(Direction.OUT, Direction.IN, Direction.OUT), read.transactions.map { it.direction })
        assertEquals(0, read.summary.fixedByBalance)
    }

    @Test
    fun printedTotalsAndBalancesInPdf() {
        val summary = PdfStatementParser.parse(
            lines(
                """
                Date          Description              Money In     Money Out       Balance
                01/11/2026    Opening balance                                         500.00
                03/11/2026    KFC                                      12.50          487.50
                05/11/2026    Salary                   1,000.00                     1,487.50
                Total                                  1,000.00        12.50
                Closing balance 1,487.50
                """,
            ),
        ).summary
        assertEquals(1000.0, summary.printedIn!!, 0.0)
        assertEquals(12.5, summary.printedOut!!, 0.0)
        assertEquals(500.0, summary.openingBalance!!, 0.0)
        assertEquals(1487.5, summary.closingBalance!!, 0.0)
    }

    @Test
    fun printedTotalsOnOneSummaryLine() {
        val summary = PdfStatementParser.parse(
            lines(
                """
                Total Money In 2,500.00   Total Money Out 1,032.75
                03/11/2026 KFC 12.50 DR
                """,
            ),
        ).summary
        assertEquals(2500.0, summary.printedIn!!, 0.0)
        assertEquals(1032.75, summary.printedOut!!, 0.0)
        assertNull(summary.openingBalance)
    }

    @Test
    fun csvSummaryRowsAreReadNotImported() {
        val rows = CsvReader.read(
            "Opening balance,,,,500.00\nDate,Details,Debit,Credit,Balance\n" +
                "03/11/2026,KFC,12.50,,487.50\n05/11/2026,Salary,,1000.00,1487.50\n" +
                "Total,,12.50,1000.00,\nClosing balance,,,,1487.50\n",
        )
        val read = TableMapper.read(rows, TableMapper.detect(rows)!!)
        assertEquals(2, read.transactions.size)
        assertEquals(StatementSummary(printedIn = 1000.0, printedOut = 12.5, openingBalance = 500.0, closingBalance = 1487.5), read.summary)
    }
}
