package com.expensetracker.importer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.data.AppDatabase
import com.expensetracker.data.Category
import com.expensetracker.data.CategoryRule
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ImporterIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val importer get() = StatementImporter(context)

    /** A statement table drawn at fixed x positions, like a bank's PDF. */
    private fun statementPdf(password: String? = null): ByteArray {
        PDFBoxResourceLoader.init(context)
        val rows = listOf(
            listOf("Date", "Description", "Money In", "Money Out", "Balance"),
            listOf("01/11/2026", "Opening balance", "", "", "500.00"),
            listOf("03/11/2026", "KFC AEON MALL", "", "12.50", "487.50"),
            listOf("05/11/2026", "SALARY NOVEMBER", "1,000.00", "", "1,487.50"),
            listOf("09/11/2026", "LUCKY SUPERMARKET", "", "64.20", "1,423.30"),
        )
        val columnsX = listOf(40f, 120f, 300f, 380f, 470f)
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.setFont(PDType1Font.HELVETICA, 10f)
                rows.forEachIndexed { r, cells ->
                    cells.forEachIndexed { c, text ->
                        if (text.isEmpty()) return@forEachIndexed
                        cs.beginText()
                        cs.newLineAtOffset(columnsX[c], 700f - r * 18f)
                        cs.showText(text)
                        cs.endText()
                    }
                }
            }
            if (password != null) {
                val policy = StandardProtectionPolicy("owner-$password", password, AccessPermission())
                policy.encryptionKeyLength = 128
                doc.protect(policy)
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    @Test
    fun readsTransactionsFromARealPdf() = runBlocking {
        val content = importer.load(StatementImporter.PickedFile("statement.pdf", statementPdf()))
        val result = (content as StatementContent.Pdf).result
        assertTrue(result.usedColumnHeader)
        assertEquals(500.0, result.summary.openingBalance!!, 0.0)
        assertEquals(1423.3, result.summary.closingBalance!!, 1e-9)
        assertEquals(
            listOf(
                ParsedTransaction(LocalDate.of(2026, 11, 3), "KFC AEON MALL", 12.5, Direction.OUT, 487.5),
                ParsedTransaction(LocalDate.of(2026, 11, 5), "SALARY NOVEMBER", 1000.0, Direction.IN, 1487.5),
                ParsedTransaction(LocalDate.of(2026, 11, 9), "LUCKY SUPERMARKET", 64.2, Direction.OUT, 1423.3),
            ),
            result.transactions,
        )
    }

    @Test
    fun passwordProtectedPdfAsksForPassword() = runBlocking {
        val file = StatementImporter.PickedFile("statement.pdf", statementPdf(password = "0101"))
        assertEquals(StatementContent.PasswordRequired, importer.load(file))
        assertEquals(StatementContent.PasswordRequired, importer.load(file, "wrong"))
        val unlocked = importer.load(file, "0101") as StatementContent.Pdf
        assertEquals(3, unlocked.result.transactions.size)
    }

    private fun xlsx(): ByteArray {
        fun ZipOutputStream.put(name: String, xml: String) {
            putNextEntry(ZipEntry(name)); write(xml.toByteArray()); closeEntry()
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.put("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Statement" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.put("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""")
            zip.put("xl/sharedStrings.xml", """<sst><si><t>Transaction Date</t></si><si><t>Description</t></si><si><t>Debit</t></si><si><t>Credit</t></si><si><r><t>Grab </t></r><r><t>ride</t></r></si><si><t>Cash deposit</t></si></sst>""")
            zip.put("xl/styles.xml", """<styleSheet><numFmts><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/></numFmts><cellXfs><xf numFmtId="0"/><xf numFmtId="164"/></cellXfs></styleSheet>""")
            zip.put(
                "xl/worksheets/sheet1.xml",
                """<worksheet><sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>
                    <row r="2"><c r="A2" s="1"><v>46352</v></c><c r="B2" t="s"><v>4</v></c><c r="C2"><v>3.2</v></c></row>
                    <row r="3"><c r="A3" s="1"><v>46353</v></c><c r="B3" t="s"><v>5</v></c><c r="D3"><v>50</v></c></row>
                    <row r="4"><c r="A4" s="1"><v>46354</v></c><c r="B4" t="inlineStr"><is><t>Netflix</t></is></c><c r="C4"><v>15.99</v></c></row>
                </sheetData></worksheet>""",
            )
        }
        return out.toByteArray()
    }

    @Test
    fun readsAnExcelFile() = runBlocking {
        val content = importer.load(StatementImporter.PickedFile("statement.xlsx", xlsx())) as StatementContent.Table
        assertEquals(StatementKind.EXCEL, content.kind)
        val mapping = TableMapper.detect(content.rows)!!
        val tx = TableMapper.apply(content.rows, mapping)
        assertEquals(
            listOf(
                ParsedTransaction(LocalDate.of(2026, 11, 26), "Grab ride", 3.2, Direction.OUT),
                ParsedTransaction(LocalDate.of(2026, 11, 27), "Cash deposit", 50.0, Direction.IN),
                ParsedTransaction(LocalDate.of(2026, 11, 28), "Netflix", 15.99, Direction.OUT),
            ),
            tx,
        )
    }

    @Test
    fun oldXlsFilesGetAHelpfulMessage() = runBlocking {
        val bytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0)
        val content = importer.load(StatementImporter.PickedFile("statement.xls", bytes))
        assertEquals(ImportProblem.OLD_EXCEL, (content as StatementContent.Unsupported).problem)
    }

    /** v4 (1.3–1.5) upgrades to v5: entries can keep an amount in another currency; old ones have none. */
    @Test
    fun upgradeFromVersion4AddsOriginalCurrency() = runBlocking {
        val name = "upgrade-v4-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `importKey` TEXT, `type` TEXT NOT NULL DEFAULT 'EXPENSE', `statementId` INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `category_rules` (`merchant` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`merchant`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `statements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `periodStart` INTEGER NOT NULL, `periodEnd` INTEGER NOT NULL, `totalIn` REAL NOT NULL, `totalOut` REAL NOT NULL, `transactionCount` INTEGER NOT NULL, `openingBalance` REAL, `closingBalance` REAL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO expenses (amount, category, note, date, createdAt, type) VALUES (4.5, 'FOOD', 'Coffee', ${LocalDate.of(2026, 11, 25).toEpochDay()}, 1, 'EXPENSE')")
            db.version = 4
        }
        val database = AppDatabase.build(context, name)
        try {
            val old = database.expenseDao().getAll().single()
            assertEquals(4.5, old.amount, 0.0)
            assertNull(old.originalAmount)
            assertNull(old.originalCurrency)
            database.expenseDao().insert(
                com.expensetracker.data.Expense(
                    amount = 3.0, category = Category.FOOD, note = "Noodles", date = LocalDate.of(2026, 11, 26),
                    originalAmount = 12_000.0, originalCurrency = com.expensetracker.data.AppCurrency.KHR,
                ),
            )
            val riel = database.expenseDao().getAll().first { it.note == "Noodles" }
            assertEquals(12_000.0 to com.expensetracker.data.AppCurrency.KHR, riel.foreign)
            assertEquals(1 / 4000.0, riel.exchangeRate!!, 1e-12)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    /** v3 (cash flow release) upgrades to v4: statements table, and entries keep their data. */
    @Test
    fun upgradeFromVersion3AddsStatements() = runBlocking {
        val name = "upgrade-v3-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `importKey` TEXT, `type` TEXT NOT NULL DEFAULT 'EXPENSE')")
            db.execSQL("CREATE TABLE IF NOT EXISTS `category_rules` (`merchant` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`merchant`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO expenses (amount, category, note, date, createdAt, importKey, type) VALUES (1500, 'SALARY', 'Salary', ${LocalDate.of(2026, 11, 25).toEpochDay()}, 1, '2026-11-25|150000|salary|0|in', 'INCOME')")
            db.version = 3
        }
        val database = AppDatabase.build(context, name)
        try {
            val entry = database.expenseDao().getAll().single()
            assertEquals(com.expensetracker.data.EntryType.INCOME, entry.type)
            assertNull(entry.statementId)
            val id = database.statementDao().insert(
                com.expensetracker.data.BankStatement(
                    fileName = "nov.csv", periodStart = LocalDate.of(2026, 11, 1), periodEnd = LocalDate.of(2026, 11, 30),
                    totalIn = 1500.0, totalOut = 0.0, transactionCount = 1, openingBalance = 0.0, closingBalance = 1500.0,
                ),
            )
            database.expenseDao().linkToStatement(listOf("2026-11-25|150000|salary|0|in"), id)
            assertEquals(id, database.expenseDao().getAll().single().statementId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    /** The version-2 database (statement import release) upgrades to v3 with every entry an expense. */
    @Test
    fun upgradeFromVersion2AddsEntryType() = runBlocking {
        val name = "upgrade-v2-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `importKey` TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `category_rules` (`merchant` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`merchant`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a308b27493658d451218a5dcaf7264be')")
            db.execSQL("INSERT INTO expenses (amount, category, note, date, createdAt, importKey) VALUES (5.75, 'FOOD', 'Starbucks', ${LocalDate.of(2026, 11, 3).toEpochDay()}, 1, '2026-11-03|575|starbucks|0')")
            db.execSQL("INSERT INTO category_rules (merchant, category) VALUES ('starbucks', 'FOOD')")
            db.version = 2
        }
        val database = AppDatabase.build(context, name)
        try {
            val entry = database.expenseDao().getAll().single()
            assertEquals(com.expensetracker.data.EntryType.EXPENSE, entry.type)
            assertEquals("2026-11-03|575|starbucks|0", entry.importKey)
            assertEquals(1, database.categoryRuleDao().getAll().size)
            database.expenseDao().upsert(com.expensetracker.data.Expense(amount = 1500.0, category = Category.SALARY, date = LocalDate.of(2026, 11, 25)))
            assertEquals(com.expensetracker.data.EntryType.INCOME, database.expenseDao().getAll().first { it.amount == 1500.0 }.type)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    /** Recreates the exact version-1 database the first release shipped, then upgrades it. */
    @Test
    fun upgradeFromVersion1KeepsExpenses() = runBlocking {
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '61b3b8007d470f516510c00ba44c4641')")
            db.execSQL("INSERT INTO expenses (amount, category, note, date, createdAt) VALUES (12.5, 'FOOD', 'Lunch', ${LocalDate.of(2026, 11, 20).toEpochDay()}, 1)")
            db.version = 1
        }

        val database = AppDatabase.build(context, name)
        try {
            val all = database.expenseDao().getAll()
            assertEquals(1, all.size)
            assertEquals("Lunch", all.single().note)
            assertEquals(Category.FOOD, all.single().category)
            assertNull(all.single().importKey)
            assertEquals(com.expensetracker.data.EntryType.EXPENSE, all.single().type)
            database.categoryRuleDao().upsert(listOf(CategoryRule("starbucks", Category.FOOD)))
            assertEquals(Category.FOOD, database.categoryRuleDao().getAll().single().category)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
