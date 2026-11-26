package com.expensetracker.importer

import com.expensetracker.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AmountParserTest {
    @Test
    fun parsesCommonBankFormats() {
        assertEquals(1234.56, AmountParser.parse("1,234.56")!!, 0.0)
        assertEquals(1234.56, AmountParser.parse("1.234,56")!!, 0.0)
        assertEquals(-12.0, AmountParser.parse("(12.00)")!!, 0.0)
        assertEquals(-12.0, AmountParser.parse("12.00-")!!, 0.0)
        assertEquals(-5.0, AmountParser.parse("-$5")!!, 0.0)
        assertEquals(-5.0, AmountParser.parse("−5.00")!!, 0.0)
        assertEquals(40000.0, AmountParser.parse("USD 40,000")!!, 0.0)
        assertEquals(40000.0, AmountParser.parse("40,000៛")!!, 0.0)
        assertEquals(40000.0, AmountParser.parse("40,000USD")!!, 0.0)
        assertEquals(-12.5, AmountParser.parse("12.50 DR")!!, 0.0)
        assertEquals(12.5, AmountParser.parse("12.50 CR")!!, 0.0)
        assertEquals(12.5, AmountParser.parse("12,5")!!, 0.0)
        assertEquals(1234567.0, AmountParser.parse("1.234.567")!!, 0.0)
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("Coffee"))
        assertNull(AmountParser.parse("12/11/2026"))
    }

    @Test
    fun moneyShapeIgnoresReferencesAndDates() {
        assertTrue(AmountParser.looksLikeMoney("1,234.56"))
        assertTrue(AmountParser.looksLikeMoney("12.00"))
        assertTrue(AmountParser.looksLikeMoney("40,000"))
        assertTrue(AmountParser.looksLikeMoney("-5.25"))
        assertTrue(AmountParser.looksLikeMoney("$12.00"))
        assertFalse(AmountParser.looksLikeMoney("123456"))
        assertFalse(AmountParser.looksLikeMoney("2026"))
        assertFalse(AmountParser.looksLikeMoney("26/11/2026"))
        assertFalse(AmountParser.looksLikeMoney("KFC"))
    }
}

class DateParserTest {
    @Test
    fun parsesManyLayouts() {
        val d = LocalDate.of(2026, 11, 26)
        assertEquals(d, DateParser.parseAny("2026-11-26"))
        assertEquals(d, DateParser.parseAny("26/11/2026"))
        assertEquals(d, DateParser.parseAny("26 Nov 2026"))
        assertEquals(d, DateParser.parseAny("26-NOV-2026"))
        assertEquals(d, DateParser.parseAny("Nov 26, 2026"))
        assertEquals(d, DateParser.parseAny("26.11.2026"))
        assertEquals(d, DateParser.parseAny("26/11/2026 14:03:22"))
        assertEquals(d, DateParser.parseAny("2026-11-26T09:15:00"))
        assertEquals(d, DateParser.fromExcelSerial("46352"))
        assertNull(DateParser.parseAny("Coffee"))
    }

    @Test
    fun detectsDayFirstVersusMonthFirst() {
        // Unambiguous: day 26 > 12.
        assertEquals("d/M/yyyy", DateParser.detectFormat(listOf("03/11/2026", "26/11/2026")))
        assertEquals("M/d/yyyy", DateParser.detectFormat(listOf("11/03/2026", "11/26/2026")))
        // Ambiguous: pick the reading with the shortest span (3–5 Nov, not Mar–May).
        assertEquals("M/d/yyyy", DateParser.detectFormat(listOf("11/03/2026", "11/04/2026", "11/05/2026")))
        assertEquals("d/M/yyyy", DateParser.detectFormat(listOf("03/11/2026", "04/11/2026", "05/11/2026")))
    }

    @Test
    fun yearlessDatesUseTheStatementYear() {
        val reference = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 1, 3), DateParser.parseYearless("03 Jan", reference))
        assertEquals(LocalDate.of(2025, 12, 28), DateParser.parseYearless("28 Dec", reference))
    }
}

class CsvAndTableTest {
    @Test
    fun readsQuotedFieldsAndDetectsDelimiter() {
        val rows = CsvReader.read("a;b;c\n\"x;1\";\"say \"\"hi\"\"\";3\r\n")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("x;1", "say \"hi\"", "3")), rows)
    }

    @Test
    fun decodesBomAndLatin1() {
        assertEquals("Date", CsvReader.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Date".toByteArray()))
        assertEquals("Café", CsvReader.decode(byteArrayOf(0x43, 0x61, 0x66, 0xE9.toByte())))
    }

    @Test
    fun singleSignedAmountColumn() {
        val csv = """
            Account: 001-234-567,,,
            Statement period: Nov 2026,,,
            Date,Description,Amount,Balance
            11/03/2026,STARBUCKS STORE 1234,-5.75,1000.25
            11/04/2026,PAYROLL DEPOSIT,"2,500.00",3500.25
            11/05/2026,"AMAZON.COM*AB12CD, SEATTLE",-42.10,3458.15
            ,Total,,3458.15
        """.trimIndent()
        val rows = CsvReader.read(csv)
        val mapping = TableMapper.detect(rows)!!
        assertEquals(2, mapping.headerRow)
        assertEquals(AmountMode.SINGLE, mapping.amountMode)
        assertEquals("M/d/yyyy", mapping.dateFormat)
        assertFalse(mapping.expensesArePositive)

        val tx = TableMapper.apply(rows, mapping)
        assertEquals(3, tx.size)
        assertEquals(ParsedTransaction(LocalDate.of(2026, 11, 3), "STARBUCKS STORE 1234", 5.75, Direction.OUT, 1000.25), tx[0])
        assertEquals(Direction.IN, tx[1].direction)
        assertEquals(2500.0, tx[1].amount, 0.0)
        assertEquals("AMAZON.COM*AB12CD, SEATTLE", tx[2].description)
    }

    @Test
    fun debitAndCreditColumnsWithEuropeanNumbers() {
        val csv = "Transaction Date;Details;Debit;Credit;Balance\n" +
            "26.11.2026;Grocery Store;1.234,56;;5.000,00\n" +
            "27.11.2026;Salary;;3.000,00;8.000,00\n"
        val rows = CsvReader.read(csv)
        val mapping = TableMapper.detect(rows)!!
        assertEquals(AmountMode.DEBIT_CREDIT, mapping.amountMode)
        assertEquals(1, mapping.descriptionColumn)
        val tx = TableMapper.apply(rows, mapping)
        assertEquals(ParsedTransaction(LocalDate.of(2026, 11, 26), "Grocery Store", 1234.56, Direction.OUT, 5000.0), tx[0])
        assertEquals(ParsedTransaction(LocalDate.of(2026, 11, 27), "Salary", 3000.0, Direction.IN, 8000.0), tx[1])
    }

    @Test
    fun moneyInMoneyOutHeaders() {
        val csv = "Date,Transaction Details,Money In,Money Out,Balance\n" +
            "2026-11-20,Coffee Shop,,4.50,95.50\n" +
            "2026-11-21,Refund,10.00,,105.50\n"
        val rows = CsvReader.read(csv)
        val tx = TableMapper.apply(rows, TableMapper.detect(rows)!!)
        assertEquals(Direction.OUT, tx[0].direction)
        assertEquals(4.5, tx[0].amount, 0.0)
        assertEquals(Direction.IN, tx[1].direction)
    }

    @Test
    fun creditCardWithPositiveCharges() {
        val csv = "Posting Date,Merchant,Amount\n2026-11-02,Netflix,15.99\n2026-11-03,Uber,8.40\n"
        val rows = CsvReader.read(csv)
        val mapping = TableMapper.detect(rows)!!
        assertTrue(mapping.expensesArePositive)
        assertTrue(TableMapper.apply(rows, mapping).all { it.direction == Direction.OUT })
    }

    @Test
    fun typeColumnDecidesDirection() {
        val csv = "Date,Description,Amount,Dr/Cr\n01/11/2026,ATM withdrawal,100.00,DR\n02/11/2026,Interest,1.20,CR\n"
        val rows = CsvReader.read(csv)
        val tx = TableMapper.apply(rows, TableMapper.detect(rows)!!)
        assertEquals(listOf(Direction.OUT, Direction.IN), tx.map { it.direction })
    }

    @Test
    fun fileWithoutHeaderIsDetectedFromContent() {
        val csv = "26/11/2026,Lunch at the market,-12.00\n27/11/2026,Bus fare,-1.50\n28/11/2026,Transfer received,50.00\n"
        val rows = CsvReader.read(csv)
        val mapping = TableMapper.detect(rows)!!
        assertEquals(-1, mapping.headerRow)
        assertEquals(0, mapping.dateColumn)
        assertEquals(1, mapping.descriptionColumn)
        assertEquals(2, mapping.amountColumn)
        assertEquals(3, TableMapper.apply(rows, mapping).size)
    }
}

class PdfStatementParserTest {
    /** Builds PDF-like lines from fixed-width text: each character is 5pt wide, lines 14pt apart. */
    private fun lines(text: String): List<PdfLine> = text.trimIndent().lines().mapIndexed { index, line ->
        val words = Regex("""\S+""").findAll(line).map { m -> PdfWord(m.value, m.range.first * 5f, (m.range.last + 1) * 5f) }.toList()
        PdfLine(0, index * 14f, words)
    }

    @Test
    fun moneyInOutBalanceColumns() {
        val result = PdfStatementParser.parse(
            lines(
                """
                ACCOUNT STATEMENT                         Period: 01/11/2026 - 30/11/2026
                Date          Description                    Money In     Money Out       Balance
                01 Nov 2026   Opening balance                                              500.00
                03 Nov 2026   KFC AEON MALL                                   12.50        487.50
                05 Nov 2026   Salary November                1,000.00                    1,487.50
                06 Nov 2026   Transfer to Dara                                 40,000      1,447.50
                              ref 99887766
                """,
            ),
        )
        assertTrue(result.usedColumnHeader)
        val tx = result.transactions
        assertEquals(3, tx.size)
        assertEquals(ParsedTransaction(LocalDate.of(2026, 11, 3), "KFC AEON MALL", 12.5, Direction.OUT, 487.5), tx[0])
        assertEquals(Direction.IN, tx[1].direction)
        assertEquals(1000.0, tx[1].amount, 0.0)
        // Plain-looking number under the Money Out column is still read, and the wrapped line is joined.
        assertEquals(Direction.OUT, tx[2].direction)
        assertEquals("Transfer to Dara ref 99887766", tx[2].description)
    }

    @Test
    fun noHeaderUsesRunningBalance() {
        val tx = PdfStatementParser.parse(
            lines(
                """
                Balance brought forward 200.00
                12/11/2026 Grab ride 3.20 196.80
                13/11/2026 Cash deposit 50.00 246.80
                14/11/2026 Pharmacy 9.80 237.00
                """,
            ),
        ).transactions
        assertEquals(listOf(Direction.OUT, Direction.IN, Direction.OUT), tx.map { it.direction })
        assertEquals(listOf(3.2, 50.0, 9.8), tx.map { it.amount })
        assertEquals("Grab ride", tx[0].description)
    }

    @Test
    fun drCrMarkersAndYearlessDates() {
        val tx = PdfStatementParser.parse(
            lines(
                """
                Statement date 30 Nov 2026
                26 Nov  POS PURCHASE STARBUCKS        5.75 DR
                27 Nov  INTEREST CREDIT               0.42 CR
                """,
            ),
        ).transactions
        assertEquals(2, tx.size)
        assertEquals(LocalDate.of(2026, 11, 26), tx[0].date)
        assertEquals(Direction.OUT, tx[0].direction)
        assertEquals(Direction.IN, tx[1].direction)
    }

    @Test
    fun newestFirstStatementStillUsesBalances() {
        val tx = PdfStatementParser.parse(
            lines(
                """
                20/11/2026 Supermarket 30.00 170.00
                19/11/2026 Transfer in 100.00 200.00
                18/11/2026 Coffee 5.00 100.00
                Opening balance 105.00
                """,
            ),
        ).transactions
        assertEquals(listOf(Direction.OUT, Direction.IN, Direction.OUT), tx.map { it.direction })
    }
}

class CategoryGuesserTest {
    @Test
    fun guessesCommonMerchants() {
        assertEquals(Category.FOOD, CategoryGuesser.guess("POS PURCHASE STARBUCKS #1234"))
        assertEquals(Category.FOOD, CategoryGuesser.guess("GRABFOOD ORDER"))
        assertEquals(Category.TRANSPORT, CategoryGuesser.guess("Grab ride"))
        assertEquals(Category.GROCERIES, CategoryGuesser.guess("LUCKY SUPERMARKET"))
        assertEquals(Category.ENTERTAINMENT, CategoryGuesser.guess("NETFLIX.COM"))
        assertEquals(Category.HOUSING, CategoryGuesser.guess("Monthly rent"))
        assertNull(CategoryGuesser.guess("Current account fee")) // "rent" must be a whole word
        assertNull(CategoryGuesser.guess("Business lunch transfer").takeIf { it == Category.TRANSPORT })
    }

    @Test
    fun cleansDescriptions() {
        assertEquals("Starbucks Phnom Penh", CategoryGuesser.cleanDescription("POS PURCHASE STARBUCKS PHNOM PENH XXXX1234 REF 99887766"))
        assertEquals("ATM Withdrawal", CategoryGuesser.cleanDescription("ATM WITHDRAWAL XXXX1234"))
        assertEquals("Purchased Goods Ltd", CategoryGuesser.cleanDescription("Purchased Goods Ltd")) // whole word only
        assertEquals("KFC Aeon Mall", CategoryGuesser.cleanDescription("KFC AEON MALL #0042"))
    }

    @Test
    fun merchantKeyIgnoresNoise() {
        assertEquals(CategoryGuesser.merchantKey("POS PURCHASE STARBUCKS 12/11"), CategoryGuesser.merchantKey("Starbucks"))
    }
}
