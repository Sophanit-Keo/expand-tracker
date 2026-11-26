package com.expensetracker.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.BankStatement
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.domain.StatementCheck
import com.expensetracker.domain.StatementStatus
import com.expensetracker.importer.AmountMode
import com.expensetracker.importer.CategoryGuesser
import com.expensetracker.importer.ColumnMapping
import com.expensetracker.importer.DateParser
import com.expensetracker.importer.Direction
import com.expensetracker.importer.ParsedTransaction
import com.expensetracker.importer.StatementContent
import com.expensetracker.importer.StatementImporter
import com.expensetracker.importer.StatementKind
import com.expensetracker.importer.StatementRead
import com.expensetracker.importer.StatementSummary
import com.expensetracker.importer.TableMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

enum class ImportStep { PICK, READING, PASSWORD, MAP, REVIEW, IMPORTING, DONE, ERROR }

enum class RowFlag(val label: String) {
    ALREADY_IMPORTED("Already imported"),
    POSSIBLE_DUPLICATE("Possible duplicate of an expense you added"),
    MONEY_IN("Money in · added as income"),
    CHECK_DIRECTION("Money in or out? Tap ⇅ to switch"),
}

data class ImportCheck(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val foundIn: Double,
    val foundOut: Double,
    val countIn: Int,
    val countOut: Int,
    val unsure: Int,
    val summary: StatementSummary,
) {
    private fun same(a: Double, b: Double) = abs(a - b) < 0.01

    val inMatches: Boolean? get() = summary.printedIn?.let { same(it, foundIn) }
    val outMatches: Boolean? get() = summary.printedOut?.let { same(it, foundOut) }

    /** Opening balance + money in − money out should equal the closing balance. */
    val balanceDifference: Double?
        get() {
            val opening = summary.openingBalance ?: return null
            val closing = summary.closingBalance ?: return null
            return opening + foundIn - foundOut - closing
        }
    val balanceAddsUp: Boolean? get() = balanceDifference?.let { abs(it) < 0.01 }

    private val checks get() = listOfNotNull(inMatches, outMatches, balanceAddsUp)
    val hasChecks: Boolean get() = checks.isNotEmpty()
    val allMatch: Boolean get() = hasChecks && checks.all { it } && unsure == 0
    val hasProblem: Boolean get() = checks.any { !it }
}

data class ImportRow(
    val id: Int,
    val date: LocalDate,
    val description: String,
    val amount: Double,
    val direction: Direction,
    val type: EntryType,
    val category: Category,
    val selected: Boolean,
    val flag: RowFlag?,
    val merchantKey: String,
    val importKey: String,
)

data class ImportUiState(
    val step: ImportStep = ImportStep.PICK,
    val fileName: String? = null,
    val kind: StatementKind? = null,
    val errorMessage: String? = null,
    val wrongPassword: Boolean = false,
    val currency: AppCurrency = AppCurrency.USD,
    // Spreadsheet column mapping.
    val table: List<List<String>> = emptyList(),
    val mapping: ColumnMapping? = null,
    val columnLabels: List<String> = emptyList(),
    val mappingPreview: List<ParsedTransaction> = emptyList(),
    val mappingAutoDetected: Boolean = false,
    // Review.
    val rows: List<ImportRow> = emptyList(),
    val summary: StatementSummary = StatementSummary(),
    val pdfUsedColumns: Boolean = false,
    /** How the saved statement compares with the app's cash flow (after importing). */
    val statementStatus: StatementStatus? = null,
    // Result.
    val importedCount: Int = 0,
    val importedOut: Double = 0.0,
    val importedIn: Double = 0.0,
) {
    val visibleRows: List<ImportRow> get() = rows
    val selectedRows: List<ImportRow> get() = rows.filter { it.selected }
    val selectedOut: Double get() = selectedRows.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount }
    val selectedIn: Double get() = selectedRows.filter { it.type == EntryType.INCOME }.sumOf { it.amount }
    val moneyInCount: Int get() = rows.count { it.type == EntryType.INCOME }
    private val selectableIncome get() = rows.filter { it.type == EntryType.INCOME && it.flag != RowFlag.ALREADY_IMPORTED }
    val moneyInSelected: Boolean get() = selectableIncome.isNotEmpty() && selectableIncome.all { it.selected }

    /** Everything read from the file (imported or not) compared with the statement's own figures. */
    val check: ImportCheck?
        get() {
            if (rows.isEmpty()) return null
            val income = rows.filter { it.type == EntryType.INCOME }
            val spending = rows.filter { it.type == EntryType.EXPENSE }
            return ImportCheck(
                periodStart = rows.minOf { it.date },
                periodEnd = rows.maxOf { it.date },
                foundIn = income.sumOf { it.amount },
                foundOut = spending.sumOf { it.amount },
                countIn = income.size,
                countOut = spending.size,
                unsure = rows.count { it.flag == RowFlag.CHECK_DIRECTION },
                summary = summary,
            )
        }
    val skippedDuplicates: Int get() = rows.count { it.flag == RowFlag.ALREADY_IMPORTED || it.flag == RowFlag.POSSIBLE_DUPLICATE }
    val uncertainCount: Int get() = rows.count { it.flag == RowFlag.CHECK_DIRECTION }
}

class ImportViewModel(
    private val importer: StatementImporter,
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var file: StatementImporter.PickedFile? = null

    /** Categories chosen by the user in this import, remembered for next time. */
    private val learned = mutableMapOf<String, Category>()

    init {
        viewModelScope.launch { _state.update { it.copy(currency = settings.settings.first().currency) } }
    }

    fun onFilePicked(uri: Uri) {
        _state.update { ImportUiState(step = ImportStep.READING, currency = it.currency) }
        learned.clear()
        viewModelScope.launch {
            val picked = runCatching { importer.read(uri) }.getOrElse { e ->
                fail(e.message ?: "The file couldn't be opened.")
                return@launch
            }
            file = picked
            _state.update { it.copy(fileName = picked.name, kind = StatementImporter.detectKind(picked)) }
            load(null)
        }
    }

    fun submitPassword(password: String) {
        _state.update { it.copy(step = ImportStep.READING) }
        viewModelScope.launch { load(password) }
    }

    private suspend fun load(password: String?) {
        val picked = file ?: return
        val content = runCatching { importer.load(picked, password) }.getOrElse { e ->
            fail("This file couldn't be read (${e.javaClass.simpleName}).")
            return
        }
        when (content) {
            is StatementContent.PasswordRequired -> _state.update { it.copy(step = ImportStep.PASSWORD, wrongPassword = password != null) }
            is StatementContent.Unsupported -> fail(content.message)
            is StatementContent.Pdf -> {
                if (content.result.transactions.isEmpty()) {
                    fail(
                        "No transactions were found in this PDF. If your bank offers a CSV or Excel download, try that instead; it's more reliable.",
                    )
                } else {
                    _state.update { it.copy(kind = StatementKind.PDF, pdfUsedColumns = content.result.usedColumnHeader) }
                    showReview(content.result.read)
                }
            }
            is StatementContent.Table -> {
                val detected = withContext(Dispatchers.Default) { TableMapper.detect(content.rows) }
                val mapping = detected ?: fallbackMapping(content.rows)
                val parsed = withContext(Dispatchers.Default) { TableMapper.read(content.rows, mapping) }
                _state.update {
                    it.copy(
                        kind = content.kind,
                        table = content.rows,
                        mapping = mapping,
                        columnLabels = TableMapper.columnLabels(content.rows, mapping.headerRow),
                        mappingPreview = parsed.transactions.take(PREVIEW_ROWS),
                        mappingAutoDetected = detected != null,
                    )
                }
                if (detected != null && parsed.transactions.isNotEmpty()) showReview(parsed) else _state.update { it.copy(step = ImportStep.MAP) }
            }
        }
    }

    private fun fallbackMapping(rows: List<List<String>>): ColumnMapping {
        val header = TableMapper.findHeaderRow(rows)
        return ColumnMapping(
            headerRow = header,
            dateColumn = 0,
            descriptionColumn = 1,
            amountMode = AmountMode.SINGLE,
            amountColumn = 2,
            dateFormat = DateParser.detectFormat(rows.drop(header + 1).map { it.getOrElse(0) { "" } }) ?: "d/M/yyyy",
        )
    }

    private fun fail(message: String) = _state.update { it.copy(step = ImportStep.ERROR, errorMessage = message) }

    // --- Column mapping -------------------------------------------------------------------------

    fun updateMapping(mapping: ColumnMapping) {
        val table = _state.value.table
        _state.update {
            it.copy(
                mapping = mapping,
                columnLabels = TableMapper.columnLabels(table, mapping.headerRow),
                mappingPreview = TableMapper.read(table, mapping).transactions.take(PREVIEW_ROWS),
            )
        }
    }

    fun confirmMapping() {
        val s = _state.value
        val mapping = s.mapping ?: return
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) { TableMapper.read(s.table, mapping) }
            if (parsed.transactions.isEmpty()) return@launch
            showReview(parsed)
        }
    }

    fun editColumns() = _state.update { it.copy(step = ImportStep.MAP) }

    // --- Review ---------------------------------------------------------------------------------

    private suspend fun showReview(read: StatementRead) {
        val transactions = read.transactions
        val existingKeys = expenses.importKeys()
        val rules = expenses.categoryRules() + learned
        val manual = expenses.getAll().filter { it.importKey == null }
            .groupBy { Triple(it.type, it.date, (it.amount * 100).roundToLong()) }
            .mapValues { it.value.size }
            .toMutableMap()

        val rows = withContext(Dispatchers.Default) {
            val occurrences = mutableMapOf<String, Int>()
            transactions.mapIndexed { index, tx ->
                val description = CategoryGuesser.cleanDescription(tx.description).ifBlank { "Bank transaction" }
                val merchant = CategoryGuesser.merchantKey(tx.description)
                val base = CategoryGuesser.importKey(tx.date, tx.amount, tx.description, 0)
                val type = if (tx.direction == Direction.IN) EntryType.INCOME else EntryType.EXPENSE
                // Money in gets its own key suffix; money-out keys stay as in earlier versions.
                val suffix = if (type == EntryType.INCOME) "|in" else ""
                val occurrence = occurrences.merge(base + suffix, 1, Int::plus)!! - 1
                val importKey = CategoryGuesser.importKey(tx.date, tx.amount, tx.description, occurrence) + suffix

                val manualKey = Triple(type, tx.date, (tx.amount * 100).roundToLong())
                val flag = when {
                    importKey in existingKeys -> RowFlag.ALREADY_IMPORTED
                    (manual[manualKey] ?: 0) > 0 -> {
                        manual[manualKey] = manual.getValue(manualKey) - 1
                        RowFlag.POSSIBLE_DUPLICATE
                    }
                    tx.direction == Direction.IN -> RowFlag.MONEY_IN
                    tx.direction == Direction.UNKNOWN -> RowFlag.CHECK_DIRECTION
                    else -> null
                }
                ImportRow(
                    id = index,
                    date = tx.date,
                    description = description,
                    amount = abs(tx.amount),
                    direction = tx.direction,
                    type = type,
                    category = rules[merchant]?.takeIf { merchant.isNotEmpty() && it.type == type }
                        ?: if (type == EntryType.INCOME) CategoryGuesser.guessIncome(tx.description)
                        else CategoryGuesser.guess(tx.description) ?: Category.OTHER,
                    selected = flag != RowFlag.ALREADY_IMPORTED && flag != RowFlag.POSSIBLE_DUPLICATE,
                    flag = flag,
                    merchantKey = merchant,
                    importKey = importKey,
                )
            }.sortedByDescending { it.date }
        }
        _state.update { it.copy(step = ImportStep.REVIEW, rows = rows, summary = read.summary) }
    }

    fun toggleRow(id: Int) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.id == id) it.copy(selected = !it.selected) else it })
    }

    fun selectAllVisible(selected: Boolean) = _state.update { s ->
        val visible = s.visibleRows.map { it.id }.toSet()
        s.copy(rows = s.rows.map { if (it.id in visible && it.flag != RowFlag.ALREADY_IMPORTED) it.copy(selected = selected) else it })
    }

    /** Switch a row between money in and money out (when the statement was read the wrong way). */
    fun flipDirection(rowId: Int) {
        viewModelScope.launch {
            val rules = expenses.categoryRules() + learned
            _state.update { s ->
                s.copy(
                    rows = s.rows.map { row ->
                        if (row.id != rowId) return@map row
                        val type = if (row.type == EntryType.INCOME) EntryType.EXPENSE else EntryType.INCOME
                        val keyBase = row.importKey.removeSuffix("|in")
                        row.copy(
                            type = type,
                            direction = if (type == EntryType.INCOME) Direction.IN else Direction.OUT,
                            category = rules[row.merchantKey]?.takeIf { it.type == type }
                                ?: if (type == EntryType.INCOME) CategoryGuesser.guessIncome(row.description)
                                else CategoryGuesser.guess(row.description) ?: Category.OTHER,
                            flag = when (row.flag) {
                                RowFlag.ALREADY_IMPORTED, RowFlag.POSSIBLE_DUPLICATE -> row.flag
                                else -> if (type == EntryType.INCOME) RowFlag.MONEY_IN else null
                            },
                            importKey = if (type == EntryType.INCOME) "$keyBase|in" else keyBase,
                        )
                    },
                )
            }
        }
    }

    /** Tick or untick every money-in row (imported as income). */
    fun setMoneyInSelected(selected: Boolean) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.type == EntryType.INCOME && it.flag != RowFlag.ALREADY_IMPORTED) it.copy(selected = selected) else it })
    }

    /** Sets the category for this row and every row from the same merchant, and remembers it. */
    fun setCategory(rowId: Int, category: Category) = _state.update { s ->
        val row = s.rows.firstOrNull { it.id == rowId }
        val merchant = row?.merchantKey.orEmpty()
        if (merchant.isNotEmpty()) learned[merchant] = category
        s.copy(
            rows = s.rows.map {
                val sameMerchant = merchant.isNotEmpty() && it.merchantKey == merchant && it.type == row?.type
                if (it.id == rowId || sameMerchant) it.copy(category = category) else it
            },
        )
    }

    fun similarCount(rowId: Int): Int {
        val rows = _state.value.rows
        val row = rows.firstOrNull { it.id == rowId } ?: return 0
        return if (row.merchantKey.isEmpty()) 0 else rows.count { it.merchantKey == row.merchantKey && it.type == row.type && it.id != rowId }
    }

    fun import() {
        val current = _state.value
        val selected = current.selectedRows
        val check = current.check ?: return
        _state.update { it.copy(step = ImportStep.IMPORTING) }
        viewModelScope.launch {
            var createdAt = System.currentTimeMillis()
            val newExpenses = selected.sortedBy { it.date }.map { row ->
                Expense(
                    amount = row.amount,
                    category = row.category,
                    type = row.category.type,
                    note = row.description,
                    date = row.date,
                    createdAt = createdAt++,
                    importKey = row.importKey,
                )
            }
            expenses.addAll(newExpenses)
            if (learned.isNotEmpty()) expenses.saveCategoryRules(learned.toMap())

            // Remember the statement and link its entries (new and previously imported) to it.
            val statement = BankStatement(
                fileName = current.fileName ?: "Statement",
                periodStart = check.periodStart,
                periodEnd = check.periodEnd,
                totalIn = check.foundIn,
                totalOut = check.foundOut,
                transactionCount = current.rows.size,
                openingBalance = current.summary.openingBalance,
                closingBalance = current.summary.closingBalance,
            )
            val linkKeys = current.rows.filter { it.selected || it.flag == RowFlag.ALREADY_IMPORTED }.map { it.importKey }
            val id = expenses.saveStatement(statement, linkKeys)
            val status = StatementCheck.status(statement.copy(id = id), expenses.getAll())
            _state.update {
                it.copy(
                    step = ImportStep.DONE,
                    importedCount = newExpenses.size,
                    importedOut = newExpenses.filterNot { e -> e.isIncome }.sumOf { e -> e.amount },
                    importedIn = newExpenses.filter { e -> e.isIncome }.sumOf { e -> e.amount },
                    statementStatus = status,
                )
            }
        }
    }

    fun removeSampleData() {
        viewModelScope.launch {
            expenses.removeSampleData()
            val status = _state.value.statementStatus ?: return@launch
            _state.update { it.copy(statementStatus = StatementCheck.status(status.statement, expenses.getAll())) }
        }
    }

    fun startOver() {
        file = null
        learned.clear()
        _state.update { ImportUiState(currency = it.currency) }
    }

    companion object {
        private const val PREVIEW_ROWS = 6
    }
}
