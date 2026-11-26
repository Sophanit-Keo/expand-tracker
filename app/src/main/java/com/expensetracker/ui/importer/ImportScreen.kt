package com.expensetracker.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.importer.AmountMode
import com.expensetracker.importer.ColumnMapping
import com.expensetracker.importer.DateParser
import com.expensetracker.importer.Direction
import com.expensetracker.importer.ParsedTransaction
import com.expensetracker.importer.StatementKind
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.color
import com.expensetracker.ui.components.icon
import com.expensetracker.ui.theme.Decrease
import com.expensetracker.ui.theme.card
import com.expensetracker.util.formatMoney
import com.expensetracker.util.shortDate

/** MIME types offered in the file picker; some file managers label CSV files as plain text or binary. */
private val statementMimeTypes = arrayOf(
    "text/csv", "text/comma-separated-values", "application/csv", "text/plain", "text/tab-separated-values",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
    "application/pdf", "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onClose: () -> Unit,
    onViewHistory: () -> Unit,
    viewModel: ImportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }
    val pickFile = { picker.launch(statementMimeTypes) }
    var categoryRowId by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.step == ImportStep.MAP) "Match columns" else "Import statement")
                        if (state.fileName != null && state.step in setOf(ImportStep.MAP, ImportStep.REVIEW)) {
                            Text(
                                state.fileName.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                },
                actions = {
                    if (state.step == ImportStep.REVIEW && state.kind != StatementKind.PDF) {
                        TextButton(onClick = viewModel::editColumns) {
                            Icon(Icons.Rounded.ViewColumn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Columns")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            when (state.step) {
                ImportStep.REVIEW -> BottomAction(
                    text = if (state.selectedRows.isEmpty()) "Select transactions to import"
                    else "Import ${state.selectedRows.size} ${if (state.selectedRows.size == 1) "transaction" else "transactions"}",
                    enabled = state.selectedRows.isNotEmpty(),
                    onClick = viewModel::import,
                    tag = "import_confirm",
                )
                ImportStep.MAP -> BottomAction(
                    text = "Continue",
                    enabled = state.mappingPreview.isNotEmpty(),
                    onClick = viewModel::confirmMapping,
                    tag = "mapping_continue",
                )
                else -> Unit
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.step) {
                ImportStep.PICK -> PickStep(pickFile)
                ImportStep.READING, ImportStep.IMPORTING -> Busy(if (state.step == ImportStep.READING) "Reading your statement…" else "Importing…")
                ImportStep.PASSWORD -> PasswordStep(state.wrongPassword, viewModel::submitPassword)
                ImportStep.MAP -> MapStep(state, viewModel::updateMapping)
                ImportStep.REVIEW -> ReviewStep(
                    state = state,
                    onToggle = viewModel::toggleRow,
                    onSelectAll = viewModel::selectAllVisible,
                    onMoneyInSelected = viewModel::setMoneyInSelected,
                    onPickCategory = { categoryRowId = it },
                )
                ImportStep.DONE -> DoneStep(state, onViewHistory, onClose)
                ImportStep.ERROR -> ErrorStep(state.errorMessage.orEmpty(), pickFile)
            }
        }
    }

    categoryRowId?.let { rowId ->
        val row = state.rows.firstOrNull { it.id == rowId }
        if (row != null) {
            CategorySheet(
                categories = Category.of(row.type),
                description = row.description,
                selected = row.category,
                similar = viewModel.similarCount(rowId),
                onSelect = {
                    viewModel.setCategory(rowId, it)
                    categoryRowId = null
                },
                onDismiss = { categoryRowId = null },
            )
        }
    }
}

@Composable
private fun BottomAction(text: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp)
                .testTag(tag),
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PickStep(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        IconBubble(Icons.Rounded.UploadFile, size = 96)
        Spacer(Modifier.height(20.dp))
        Text("Import from your bank", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Download a statement from your bank's app or website, then choose the file here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CSV", "Excel (.xlsx)", "PDF").forEach { FormatPill(it) }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("choose_file")) {
            Icon(Icons.Rounded.UploadFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose statement file", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        SectionCard(title = "How it works") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HowStep(1, "Choose your statement", "CSV and Excel files work best. PDFs work when they contain text (not a scan).")
                HowStep(2, "Review", "Spending and money coming in are selected for you, each with a suggested category.")
                HowStep(3, "Import", "Transactions you've already imported are skipped, so importing the same file twice is safe.")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Your file is read on this phone and never uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormatPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun HowStep(number: Int, title: String, text: String) {
    Row {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, size: Int = 88, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer, background: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.45f).dp))
    }
}

@Composable
private fun Busy(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PasswordStep(wrong: Boolean, onSubmit: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        IconBubble(Icons.Rounded.Lock)
        Spacer(Modifier.height(20.dp))
        Text("This PDF is password-protected", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Banks often protect statements with a password, such as your date of birth or part of your account number. Check the email or app the statement came from.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("PDF password") },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) ({ Text("That password didn't work. Try again.") }) else null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onSubmit(password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Unlock")
        }
    }
}

@Composable
private fun ErrorStep(message: String, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        IconBubble(Icons.Rounded.ErrorOutline, tint = MaterialTheme.colorScheme.onErrorContainer, background = MaterialTheme.colorScheme.errorContainer)
        Spacer(Modifier.height(20.dp))
        Text("Couldn't import this file", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Choose another file") }
    }
}

@Composable
private fun DoneStep(state: ImportUiState, onViewHistory: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        IconBubble(Icons.Rounded.CheckCircle, size = 104, tint = Color.White, background = Decrease)
        Spacer(Modifier.height(24.dp))
        Text(
            "Imported ${state.importedCount} ${if (state.importedCount == 1) "transaction" else "transactions"}",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildList {
                if (state.importedOut > 0) add("−${formatMoney(state.importedOut, state.currency)} spending")
                if (state.importedIn > 0) add("+${formatMoney(state.importedIn, state.currency)} income")
            }.joinToString(" and ") + " added. Your charts, budget and cash flow are up to date.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onViewHistory, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("View in History") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
    }
}

// --- Column mapping ---------------------------------------------------------------------------------

@Composable
private fun MapStep(state: ImportUiState, onChange: (ColumnMapping) -> Unit) {
    val mapping = state.mapping ?: return
    val labels = state.columnLabels
    val noneLabel = "None"
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(16.dp),
            ) {
                Icon(Icons.Rounded.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (state.mappingAutoDetected) "Check that each field points at the right column. The preview below updates as you change them."
                    else "Some columns couldn't be recognised automatically. Choose which column holds each field.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        item {
            SectionCard(title = "Columns") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownField("Date", labels.getOrElse(mapping.dateColumn) { noneLabel }, labels) {
                        onChange(mapping.copy(dateColumn = it, dateFormat = DateParser.detectFormat(columnValues(state, mapping.headerRow, it)) ?: mapping.dateFormat))
                    }
                    DropdownField("Description", labels.getOrElse(mapping.descriptionColumn) { noneLabel }, listOf(noneLabel) + labels) {
                        onChange(mapping.copy(descriptionColumn = it - 1))
                    }
                    Text("Amounts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(AmountMode.SINGLE to "One column", AmountMode.DEBIT_CREDIT to "Debit & credit").forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = mapping.amountMode == mode,
                                onClick = {
                                    val fallback = labels.indices.firstOrNull { c -> c != mapping.dateColumn && c != mapping.descriptionColumn } ?: 0
                                    onChange(
                                        if (mode == AmountMode.SINGLE) mapping.copy(amountMode = mode, amountColumn = mapping.amountColumn.takeIf { it >= 0 } ?: mapping.debitColumn.takeIf { it >= 0 } ?: fallback)
                                        else mapping.copy(
                                            amountMode = mode,
                                            debitColumn = mapping.debitColumn.takeIf { it >= 0 } ?: mapping.amountColumn.takeIf { it >= 0 } ?: fallback,
                                            creditColumn = mapping.creditColumn.takeIf { it >= 0 } ?: minOf(labels.lastIndex, fallback + 1),
                                        ),
                                    )
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(label) }
                        }
                    }
                    if (mapping.amountMode == AmountMode.SINGLE) {
                        DropdownField("Amount", labels.getOrElse(mapping.amountColumn) { noneLabel }, labels) { onChange(mapping.copy(amountColumn = it)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Spending is shown as positive", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Turn on if purchases aren't negative numbers (common on credit card statements).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = mapping.expensesArePositive, onCheckedChange = { onChange(mapping.copy(expensesArePositive = it)) })
                        }
                    } else {
                        DropdownField("Money out (debit)", labels.getOrElse(mapping.debitColumn) { noneLabel }, labels) { onChange(mapping.copy(debitColumn = it)) }
                        DropdownField("Money in (credit)", labels.getOrElse(mapping.creditColumn) { noneLabel }, labels) { onChange(mapping.copy(creditColumn = it)) }
                    }
                    DropdownField(
                        "Date format",
                        DateParser.example(mapping.dateFormat),
                        DateParser.supportedFormats.map { "${it.example}   (${it.id})" },
                    ) { onChange(mapping.copy(dateFormat = DateParser.supportedFormats[it].id)) }
                }
            }
        }
        item {
            SectionCard(title = "Preview", subtitle = "First rows read with these settings") {
                if (state.mappingPreview.isEmpty()) {
                    Text(
                        "No transactions could be read. Check the date column and date format.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.mappingPreview.forEach { PreviewRow(it, state.currency) }
                    }
                }
            }
        }
    }
}

private fun columnValues(state: ImportUiState, headerRow: Int, column: Int) =
    state.table.drop(headerRow + 1).map { it.getOrElse(column) { "" } }

@Composable
private fun PreviewRow(tx: ParsedTransaction, currency: AppCurrency) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(shortDate(tx.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        Text(tx.description.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        AmountText(tx.amount, tx.direction, currency)
    }
}

@Composable
private fun AmountText(amount: Double, direction: Direction, currency: AppCurrency) {
    val incoming = direction == Direction.IN
    Text(
        (if (incoming) "+" else "-") + formatMoney(amount, currency),
        style = MaterialTheme.typography.titleSmall,
        color = if (incoming) Decrease else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}

// --- Review -------------------------------------------------------------------------------------------

@Composable
private fun ReviewStep(
    state: ImportUiState,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onMoneyInSelected: (Boolean) -> Unit,
    onPickCategory: (Int) -> Unit,
) {
    val visible = state.visibleRows
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        item {
            SummaryCard(state)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.moneyInCount > 0) {
                    FilterChip(
                        selected = state.moneyInSelected,
                        onClick = { onMoneyInSelected(!state.moneyInSelected) },
                        label = { Text("Include money in (${state.moneyInCount})") },
                        modifier = Modifier.testTag("include_money_in"),
                        shape = CircleShape,
                    )
                }
                Spacer(Modifier.weight(1f))
                val allSelected = visible.filter { it.flag != RowFlag.ALREADY_IMPORTED }.all { it.selected }
                TextButton(onClick = { onSelectAll(!allSelected) }) { Text(if (allSelected) "Select none" else "Select all") }
            }
            Spacer(Modifier.height(4.dp))
        }
        itemsIndexed(visible, key = { _, row -> row.id }) { index, row ->
            ReviewRow(
                row = row,
                currency = state.currency,
                shape = rowShape(index, visible.size),
                onToggle = { onToggle(row.id) },
                onPickCategory = { onPickCategory(row.id) },
            )
        }
    }
}

private fun rowShape(index: Int, count: Int): Shape {
    val big = 20.dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
}

@Composable
private fun SummaryCard(state: ImportUiState) {
    val dates = state.rows.map { it.date }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.AutoMirrored.Rounded.ReceiptLong, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${state.rows.size} transactions found", style = MaterialTheme.typography.titleMedium)
                if (dates.isNotEmpty()) {
                    Text(
                        "${shortDate(dates.min())} – ${shortDate(dates.max())}, ${dates.max().year} · ${state.kind?.label.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val notes = buildList {
            if (state.selectedOut > 0 || state.selectedIn > 0) {
                add(
                    "Selected: " + buildList {
                        if (state.selectedOut > 0) add("−${formatMoney(state.selectedOut, state.currency)} spending")
                        if (state.selectedIn > 0) add("+${formatMoney(state.selectedIn, state.currency)} income")
                    }.joinToString(", ") + ".",
                )
            }
            state.moneyInCount.takeIf { it > 0 }?.let { n ->
                add(if (n == 1) "1 money-in transaction (salary, transfer in…) will be added as income for your cash flow." else "$n money-in transactions (salary, transfers in…) will be added as income for your cash flow.")
            }
            state.skippedDuplicates.takeIf { it > 0 }?.let { n ->
                add(if (n == 1) "1 looks like a duplicate and is unticked." else "$n look like duplicates and are unticked.")
            }
            state.uncertainCount.takeIf { it > 0 }?.let { n ->
                add(if (n == 1) "1 couldn't be marked as money in or out for sure; check it before importing." else "$n couldn't be marked as money in or out for sure; check them before importing.")
            }
            add("Tap a category to change it. The app remembers your choice for that merchant.")
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { note ->
                Row {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(row: ImportRow, currency: AppCurrency, shape: Shape, onToggle: () -> Unit, onPickCategory: () -> Unit) {
    val disabled = row.flag == RowFlag.ALREADY_IMPORTED
    Row(
        Modifier
            .padding(vertical = 1.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.card)
            .clickable(enabled = !disabled, onClick = onToggle)
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.selected, onCheckedChange = { onToggle() }, enabled = !disabled)
        Column(Modifier.weight(1f)) {
            Text(
                row.description,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shortDate(row.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                CategoryPill(row.category, onPickCategory)
            }
            row.flag?.let { flag ->
                Text(
                    flag.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (flag) {
                        RowFlag.MONEY_IN -> Decrease
                        RowFlag.CHECK_DIRECTION -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AmountText(row.amount, row.direction, currency)
    }
}

@Composable
private fun CategoryPill(category: Category, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(category.color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(category.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Change category", modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(categories: List<Category>, description: String, selected: Category, similar: Int, onSelect: (Category) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text("Category for “$description”", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (similar > 0) "Also applies to $similar similar ${if (similar == 1) "transaction" else "transactions"} and to future imports."
                else "Remembered for this merchant in future imports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.chunked(4).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { category ->
                            val isSelected = category == selected
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(if (isSelected) category.color.copy(alpha = 0.16f) else Color.Transparent)
                                    .clickable { onSelect(category) }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) category.color else category.color.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(category.icon, contentDescription = null, tint = if (isSelected) Color.White else category.color, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(category.label.substringBefore(" &"), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
