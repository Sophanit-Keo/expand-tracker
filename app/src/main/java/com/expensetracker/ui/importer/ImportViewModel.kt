package com.expensetracker.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.importer.AmountMode
import com.expensetracker.importer.CategoryGuesser
import com.expensetracker.importer.ColumnMapping
import com.expensetracker.importer.DateParser
import com.expensetracker.importer.Direction
import com.expensetracker.importer.ParsedTransaction
import com.expensetracker.importer.StatementContent
import com.expensetracker.importer.StatementImporter
import com.expensetracker.importer.StatementKind
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
    MONEY_IN("Money in"),
    CHECK_DIRECTION("Check: money in or out?"),
}

data class ImportRow(
    val id: Int,
    val date: LocalDate,
    val description: String,
    val amount: Double,
    val direction: Direction,
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
    val showMoneyIn: Boolean = false,
    val pdfUsedColumns: Boolean = false,
    // Result.
    val importedCount: Int = 0,
    val importedTotal: Double = 0.0,
) {
    val visibleRows: List<ImportRow> get() = if (showMoneyIn) rows else rows.filter { it.direction != Direction.IN }
    val selectedRows: List<ImportRow> get() = rows.filter { it.selected }
    val selectedTotal: Double get() = selectedRows.sumOf { it.amount }
    val moneyInCount: Int get() = rows.count { it.direction == Direction.IN }
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
                    showReview(content.result.transactions)
                }
            }
            is StatementContent.Table -> {
                val detected = withContext(Dispatchers.Default) { TableMapper.detect(content.rows) }
                val mapping = detected ?: fallbackMapping(content.rows)
                val parsed = withContext(Dispatchers.Default) { TableMapper.apply(content.rows, mapping) }
                _state.update {
                    it.copy(
                        kind = content.kind,
                        table = content.rows,
                        mapping = mapping,
                        columnLabels = TableMapper.columnLabels(content.rows, mapping.headerRow),
                        mappingPreview = parsed.take(PREVIEW_ROWS),
                        mappingAutoDetected = detected != null,
                    )
                }
                if (detected != null && parsed.isNotEmpty()) showReview(parsed) else _state.update { it.copy(step = ImportStep.MAP) }
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
                mappingPreview = TableMapper.apply(table, mapping).take(PREVIEW_ROWS),
            )
        }
    }

    fun confirmMapping() {
        val s = _state.value
        val mapping = s.mapping ?: return
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) { TableMapper.apply(s.table, mapping) }
            if (parsed.isEmpty()) return@launch
            showReview(parsed)
        }
    }

    fun editColumns() = _state.update { it.copy(step = ImportStep.MAP) }

    // --- Review ---------------------------------------------------------------------------------

    private suspend fun showReview(transactions: List<ParsedTransaction>) {
        val existingKeys = expenses.importKeys()
        val rules = expenses.categoryRules() + learned
        val manual = expenses.getAll().filter { it.importKey == null }
            .groupBy { it.date to (it.amount * 100).roundToLong() }
            .mapValues { it.value.size }
            .toMutableMap()

        val rows = withContext(Dispatchers.Default) {
            val occurrences = mutableMapOf<String, Int>()
            transactions.mapIndexed { index, tx ->
                val description = CategoryGuesser.cleanDescription(tx.description).ifBlank { "Bank transaction" }
                val merchant = CategoryGuesser.merchantKey(tx.description)
                val base = CategoryGuesser.importKey(tx.date, tx.amount, tx.description, 0)
                val occurrence = occurrences.merge(base, 1, Int::plus)!! - 1
                val importKey = CategoryGuesser.importKey(tx.date, tx.amount, tx.description, occurrence)

                val manualKey = tx.date to (tx.amount * 100).roundToLong()
                val flag = when {
                    importKey in existingKeys -> RowFlag.ALREADY_IMPORTED
                    tx.direction == Direction.IN -> RowFlag.MONEY_IN
                    (manual[manualKey] ?: 0) > 0 -> {
                        manual[manualKey] = manual.getValue(manualKey) - 1
                        RowFlag.POSSIBLE_DUPLICATE
                    }
                    tx.direction == Direction.UNKNOWN -> RowFlag.CHECK_DIRECTION
                    else -> null
                }
                ImportRow(
                    id = index,
                    date = tx.date,
                    description = description,
                    amount = abs(tx.amount),
                    direction = tx.direction,
                    category = rules[merchant]?.takeIf { merchant.isNotEmpty() }
                        ?: CategoryGuesser.guess(tx.description)
                        ?: Category.OTHER,
                    selected = tx.direction != Direction.IN && flag != RowFlag.ALREADY_IMPORTED && flag != RowFlag.POSSIBLE_DUPLICATE,
                    flag = flag,
                    merchantKey = merchant,
                    importKey = importKey,
                )
            }.sortedByDescending { it.date }
        }
        _state.update { it.copy(step = ImportStep.REVIEW, rows = rows, showMoneyIn = false) }
    }

    fun toggleRow(id: Int) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.id == id) it.copy(selected = !it.selected) else it })
    }

    fun selectAllVisible(selected: Boolean) = _state.update { s ->
        val visible = s.visibleRows.map { it.id }.toSet()
        s.copy(rows = s.rows.map { if (it.id in visible && it.flag != RowFlag.ALREADY_IMPORTED) it.copy(selected = selected) else it })
    }

    fun setShowMoneyIn(show: Boolean) = _state.update { it.copy(showMoneyIn = show) }

    /** Sets the category for this row and every row from the same merchant, and remembers it. */
    fun setCategory(rowId: Int, category: Category) = _state.update { s ->
        val merchant = s.rows.firstOrNull { it.id == rowId }?.merchantKey.orEmpty()
        if (merchant.isNotEmpty()) learned[merchant] = category
        s.copy(
            rows = s.rows.map {
                if (it.id == rowId || (merchant.isNotEmpty() && it.merchantKey == merchant)) it.copy(category = category) else it
            },
        )
    }

    fun similarCount(rowId: Int): Int {
        val rows = _state.value.rows
        val merchant = rows.firstOrNull { it.id == rowId }?.merchantKey.orEmpty()
        return if (merchant.isEmpty()) 0 else rows.count { it.merchantKey == merchant && it.id != rowId }
    }

    fun import() {
        val selected = _state.value.selectedRows
        if (selected.isEmpty()) return
        _state.update { it.copy(step = ImportStep.IMPORTING) }
        viewModelScope.launch {
            var createdAt = System.currentTimeMillis()
            val newExpenses = selected.sortedBy { it.date }.map { row ->
                Expense(
                    amount = row.amount,
                    category = row.category,
                    note = row.description,
                    date = row.date,
                    createdAt = createdAt++,
                    importKey = row.importKey,
                )
            }
            expenses.addAll(newExpenses)
            if (learned.isNotEmpty()) expenses.saveCategoryRules(learned.toMap())
            _state.update {
                it.copy(step = ImportStep.DONE, importedCount = newExpenses.size, importedTotal = newExpenses.sumOf { e -> e.amount })
            }
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
