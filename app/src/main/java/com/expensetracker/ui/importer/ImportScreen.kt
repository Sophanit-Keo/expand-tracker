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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.domain.StatementStatus
import com.expensetracker.importer.AmountMode
import com.expensetracker.importer.ColumnMapping
import com.expensetracker.importer.DateParser
import com.expensetracker.importer.Direction
import com.expensetracker.importer.ParsedTransaction
import com.expensetracker.importer.StatementKind
import com.expensetracker.importer.TableMapper
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.asString
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.color
import com.expensetracker.ui.components.icon
import com.expensetracker.ui.dateFormatter
import com.expensetracker.ui.shortDateText
import com.expensetracker.ui.shortTitle
import com.expensetracker.ui.theme.Decrease
import com.expensetracker.ui.theme.card
import com.expensetracker.ui.title
import com.expensetracker.util.formatMoney
import java.time.LocalDate
import java.time.YearMonth

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
    onViewCashFlow: (YearMonth) -> Unit,
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
                        Text(stringResource(if (state.step == ImportStep.MAP) R.string.match_columns else R.string.import_statement))
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
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    if (state.step == ImportStep.REVIEW && state.kind != StatementKind.PDF) {
                        TextButton(onClick = viewModel::editColumns) {
                            Icon(Icons.Rounded.ViewColumn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.columns))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            when (state.step) {
                ImportStep.REVIEW -> BottomAction(
                    text = if (state.selectedRows.isEmpty()) stringResource(R.string.save_statement_nothing_new)
                    else pluralStringResource(R.plurals.import_n, state.selectedRows.size, state.selectedRows.size),
                    enabled = state.rows.isNotEmpty(),
                    onClick = viewModel::import,
                    tag = "import_confirm",
                )
                ImportStep.MAP -> BottomAction(
                    text = stringResource(R.string.continue_action),
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
                ImportStep.READING, ImportStep.IMPORTING -> Busy(stringResource(if (state.step == ImportStep.READING) R.string.reading_statement else R.string.importing))
                ImportStep.PASSWORD -> PasswordStep(state.wrongPassword, viewModel::submitPassword)
                ImportStep.MAP -> MapStep(state, viewModel::updateMapping)
                ImportStep.REVIEW -> ReviewStep(
                    state = state,
                    onToggle = viewModel::toggleRow,
                    onSelectAll = viewModel::selectAllVisible,
                    onMoneyInSelected = viewModel::setMoneyInSelected,
                    onPickCategory = { categoryRowId = it },
                    onFlip = viewModel::flipDirection,
                )
                ImportStep.DONE -> DoneStep(state, onViewHistory, onViewCashFlow, onClose, viewModel::removeSampleData)
                ImportStep.ERROR -> ErrorStep(state.errorMessage?.asString().orEmpty(), pickFile)
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
        Text(stringResource(R.string.import_from_bank), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.import_from_bank_message),
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
            Text(stringResource(R.string.choose_statement_file), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        SectionCard(title = stringResource(R.string.how_it_works)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HowStep(1, stringResource(R.string.how_1_title), stringResource(R.string.how_1))
                HowStep(2, stringResource(R.string.how_2_title), stringResource(R.string.how_2))
                HowStep(3, stringResource(R.string.how_3_title), stringResource(R.string.how_3))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.file_stays_local),
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
        Text(stringResource(R.string.pdf_protected), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pdf_protected_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.pdf_password)) },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) ({ Text(stringResource(R.string.wrong_password)) }) else null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onSubmit(password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.unlock))
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
        Text(stringResource(R.string.import_failed), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.choose_another)) }
    }
}

@Composable
private fun DoneStep(
    state: ImportUiState,
    onViewHistory: () -> Unit,
    onViewCashFlow: (YearMonth) -> Unit,
    onClose: () -> Unit,
    onRemoveSampleData: () -> Unit,
) {
    val status = state.statementStatus
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        IconBubble(Icons.Rounded.CheckCircle, size = 88, tint = Color.White, background = Decrease)
        Spacer(Modifier.height(20.dp))
        Text(
            if (state.importedCount == 0) stringResource(R.string.statement_saved)
            else pluralStringResource(R.plurals.imported_n, state.importedCount, state.importedCount),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.importedCount == 0) stringResource(R.string.all_already_in)
            else stringResource(R.string.amounts_added, joinAnd(buildList {
                if (state.importedOut > 0) add(stringResource(R.string.spending_amount, formatMoney(state.importedOut, state.currency)))
                if (state.importedIn > 0) add(stringResource(R.string.income_amount, formatMoney(state.importedIn, state.currency)))
            })),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (status != null) {
            Spacer(Modifier.height(20.dp))
            CashFlowVsStatement(status, state.currency, onRemoveSampleData)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onViewCashFlow(YearMonth.from(status?.statement?.periodEnd ?: LocalDate.now())) },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("view_cash_flow"),
        ) { Text(stringResource(R.string.view_cash_flow)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onViewHistory, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.view_in_history)) }
        TextButton(onClick = onClose, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.done)) }
        Spacer(Modifier.height(16.dp))
    }
}

/** After importing: does the app's cash flow for the statement's dates equal the statement? */
@Composable
private fun CashFlowVsStatement(status: StatementStatus, currency: AppCurrency, onRemoveSampleData: () -> Unit) {
    val st = status.statement
    SectionCard(
        title = stringResource(R.string.cf_vs_statement),
        subtitle = dateRangeText(st.periodStart, st.periodEnd),
        action = { CheckBadge(if (status.matches) CheckState.OK else CheckState.PROBLEM, okText = stringResource(R.string.matches), problemText = stringResource(R.string.differs)) },
    ) {
        CheckLine(stringResource(R.string.statement), "+" + formatMoney(st.totalIn, currency), "−" + formatMoney(st.totalOut, currency))
        CheckLine(stringResource(R.string.cash_flow), "+" + formatMoney(status.appIn, currency), "−" + formatMoney(status.appOut, currency))
        if (st.openingBalance != null && st.closingBalance != null) {
            CheckLine(stringResource(R.string.bank_balance), formatMoney(st.openingBalance, currency) + " →", formatMoney(st.closingBalance, currency))
        }
        if (!status.matches) {
            Spacer(Modifier.height(8.dp))
            if (status.otherCount > 0) {
                val why = if (status.sampleCount > 0) stringResource(R.string.sample_note, status.sampleCount) else stringResource(R.string.by_hand_note)
                Note(pluralStringResource(R.plurals.other_entries_fall, status.otherCount, status.otherCount, " $why"))
            }
            if (status.missingIn > 0.005 || status.missingOut > 0.005) {
                Note(stringResource(R.string.rows_not_imported))
            }
            if (status.sampleCount > 0) {
                TextButton(onClick = onRemoveSampleData, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.remove_sample)) }
            }
        } else {
            Note(stringResource(R.string.cf_equals_statement))
        }
    }
}

private enum class CheckState { OK, PROBLEM, NONE }

@Composable
private fun CheckBadge(state: CheckState, okText: String, problemText: String, noneText: String = stringResource(R.string.nothing_to_check)) {
    val tint = when (state) {
        CheckState.OK -> Decrease
        CheckState.PROBLEM -> MaterialTheme.colorScheme.error
        CheckState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (state) {
                CheckState.OK -> Icons.Rounded.CheckCircle
                CheckState.PROBLEM -> Icons.Rounded.ErrorOutline
                CheckState.NONE -> Icons.Rounded.Info
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            when (state) {
                CheckState.OK -> okText
                CheckState.PROBLEM -> problemText
                CheckState.NONE -> noneText
            },
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

@Composable
private fun CheckLine(label: String, first: String, second: String, ok: Boolean? = null, detail: String? = null) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
            Text(first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(second, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (ok != null) {
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = stringResource(if (ok) R.string.matches else R.string.doesnt_match),
                    tint = if (ok) Decrease else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 96.dp))
        }
    }
}

@Composable
private fun Note(text: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Before importing: what was read from the file vs the statement's own totals and balances. */
@Composable
private fun StatementCheckCard(check: ImportCheck, currency: AppCurrency) {
    val state = when {
        check.hasProblem || check.unsure > 0 -> CheckState.PROBLEM
        check.allMatch -> CheckState.OK
        else -> CheckState.NONE
    }
    SectionCard(
        title = stringResource(R.string.statement_check),
        subtitle = dateRangeText(check.periodStart, check.periodEnd),
        action = { CheckBadge(state, okText = stringResource(R.string.adds_up), problemText = stringResource(R.string.check_rows)) },
        modifier = Modifier.testTag("statement_check"),
    ) {
        val s = check.summary
        CheckLine(
            stringResource(R.string.money_in), "+" + formatMoney(check.foundIn, currency), pluralStringResource(R.plurals.rows_count, check.countIn, check.countIn),
            ok = check.inMatches,
            detail = s.printedIn?.takeIf { check.inMatches == false }?.let { stringResource(R.string.statement_total_in, formatMoney(it, currency)) },
        )
        CheckLine(
            stringResource(R.string.money_out), "−" + formatMoney(check.foundOut, currency), pluralStringResource(R.plurals.rows_count, check.countOut, check.countOut),
            ok = check.outMatches,
            detail = s.printedOut?.takeIf { check.outMatches == false }?.let { stringResource(R.string.statement_total_out, formatMoney(it, currency)) },
        )
        if (s.openingBalance != null && s.closingBalance != null) {
            CheckLine(
                stringResource(R.string.balance), formatMoney(s.openingBalance, currency) + " →", formatMoney(s.closingBalance, currency),
                ok = check.balanceAddsUp,
                detail = check.balanceDifference?.takeIf { check.balanceAddsUp == false }?.let { stringResource(R.string.off_by, formatMoney(kotlin.math.abs(it), currency)) },
            )
        }
        Spacer(Modifier.height(6.dp))
        when {
            check.allMatch -> Note(stringResource(R.string.check_all_match))
            !check.hasChecks -> Note(stringResource(R.string.check_none))
        }
        if (s.fixedByBalance > 0) {
            Note(pluralStringResource(R.plurals.fixed_by_balance, s.fixedByBalance, s.fixedByBalance))
        }
        if (s.balanceBreaks > 0) {
            Note(pluralStringResource(R.plurals.balance_breaks, s.balanceBreaks, s.balanceBreaks))
        }
        if (check.unsure > 0) {
            Note(pluralStringResource(R.plurals.unsure_rows, check.unsure, check.unsure))
        }
        if (check.hasProblem) {
            Note(stringResource(R.string.check_flip_hint))
        }
    }
}

// --- Column mapping ---------------------------------------------------------------------------------

@Composable
private fun MapStep(state: ImportUiState, onChange: (ColumnMapping) -> Unit) {
    val mapping = state.mapping ?: return
    val columnFormat = stringResource(R.string.column_letter)
    val labels = state.columnLabels.mapIndexed { i, label -> label.ifBlank { columnFormat.format(TableMapper.columnLetter(i)) } }
    val noneLabel = stringResource(R.string.none)
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
                    stringResource(if (state.mappingAutoDetected) R.string.mapping_auto else R.string.mapping_manual),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.columns)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownField(stringResource(R.string.date), labels.getOrElse(mapping.dateColumn) { noneLabel }, labels) {
                        onChange(mapping.copy(dateColumn = it, dateFormat = DateParser.detectFormat(columnValues(state, mapping.headerRow, it)) ?: mapping.dateFormat))
                    }
                    DropdownField(stringResource(R.string.description), labels.getOrElse(mapping.descriptionColumn) { noneLabel }, listOf(noneLabel) + labels) {
                        onChange(mapping.copy(descriptionColumn = it - 1))
                    }
                    Text(stringResource(R.string.amounts), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(AmountMode.SINGLE to stringResource(R.string.one_column), AmountMode.DEBIT_CREDIT to stringResource(R.string.debit_credit)).forEachIndexed { i, (mode, label) ->
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
                        DropdownField(stringResource(R.string.amount), labels.getOrElse(mapping.amountColumn) { noneLabel }, labels) { onChange(mapping.copy(amountColumn = it)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.spending_positive), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.spending_positive_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = mapping.expensesArePositive, onCheckedChange = { onChange(mapping.copy(expensesArePositive = it)) })
                        }
                    } else {
                        DropdownField(stringResource(R.string.money_out_debit), labels.getOrElse(mapping.debitColumn) { noneLabel }, labels) { onChange(mapping.copy(debitColumn = it)) }
                        DropdownField(stringResource(R.string.money_in_credit), labels.getOrElse(mapping.creditColumn) { noneLabel }, labels) { onChange(mapping.copy(creditColumn = it)) }
                    }
                    DropdownField(
                        stringResource(R.string.date_format),
                        DateParser.example(mapping.dateFormat),
                        DateParser.supportedFormats.map { "${it.example}   (${it.id})" },
                    ) { onChange(mapping.copy(dateFormat = DateParser.supportedFormats[it].id)) }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.preview), subtitle = stringResource(R.string.preview_hint)) {
                if (state.mappingPreview.isEmpty()) {
                    Text(
                        stringResource(R.string.preview_empty),
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
        Text(shortDateText(tx.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
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
    onFlip: (Int) -> Unit,
) {
    val visible = state.visibleRows
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        state.check?.let { check ->
            item {
                StatementCheckCard(check, state.currency)
                Spacer(Modifier.height(12.dp))
            }
        }
        item {
            SummaryCard(state)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.moneyInCount > 0) {
                    FilterChip(
                        selected = state.moneyInSelected,
                        onClick = { onMoneyInSelected(!state.moneyInSelected) },
                        label = { Text(stringResource(R.string.include_money_in, state.moneyInCount)) },
                        modifier = Modifier.testTag("include_money_in"),
                        shape = CircleShape,
                    )
                }
                Spacer(Modifier.weight(1f))
                val allSelected = visible.filter { it.flag != RowFlag.ALREADY_IMPORTED }.all { it.selected }
                TextButton(onClick = { onSelectAll(!allSelected) }) { Text(stringResource(if (allSelected) R.string.select_none else R.string.select_all)) }
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
                onFlip = { onFlip(row.id) },
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
                Text(pluralStringResource(R.plurals.transactions_found, state.rows.size, state.rows.size), style = MaterialTheme.typography.titleMedium)
                if (dates.isNotEmpty()) {
                    Text(
                        "${dateRangeText(dates.min(), dates.max())} · ${state.kind?.label.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val notes = buildList {
            if (state.selectedOut > 0 || state.selectedIn > 0) {
                add(
                    stringResource(
                        R.string.selected_summary,
                        buildList {
                            if (state.selectedOut > 0) add(stringResource(R.string.spending_amount, formatMoney(state.selectedOut, state.currency)))
                            if (state.selectedIn > 0) add(stringResource(R.string.income_amount, formatMoney(state.selectedIn, state.currency)))
                        }.joinToString(", "),
                    ),
                )
            }
            state.moneyInCount.takeIf { it > 0 }?.let { n ->
                add(pluralStringResource(R.plurals.money_in_note, n, n))
            }
            state.skippedDuplicates.takeIf { it > 0 }?.let { n ->
                add(pluralStringResource(R.plurals.duplicates_note, n, n))
            }
            state.uncertainCount.takeIf { it > 0 }?.let { n ->
                add(pluralStringResource(R.plurals.uncertain_note, n, n))
            }
            add(stringResource(R.string.tap_category_note))
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
private fun ReviewRow(row: ImportRow, currency: AppCurrency, shape: Shape, onToggle: () -> Unit, onPickCategory: () -> Unit, onFlip: () -> Unit) {
    val disabled = row.flag == RowFlag.ALREADY_IMPORTED
    Row(
        Modifier
            .padding(vertical = 1.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.card)
            .clickable(enabled = !disabled, onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                Text(shortDateText(row.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                CategoryPill(row.category, onPickCategory)
            }
            row.flag?.let { flag ->
                Text(
                    stringResource(flag.label),
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
        AmountText(row.amount, if (row.type == EntryType.INCOME) Direction.IN else Direction.OUT, currency)
        IconButton(onClick = onFlip, enabled = !disabled, modifier = Modifier.size(40.dp).testTag("flip_${row.id}")) {
            Icon(
                Icons.Rounded.SwapVert,
                contentDescription = stringResource(R.string.switch_direction),
                tint = if (row.flag == RowFlag.CHECK_DIRECTION) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
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
        Text(category.title(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.change_category), modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(categories: List<Category>, description: String, selected: Category, similar: Int, onSelect: (Category) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.category_for, description), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (similar > 0) pluralStringResource(R.plurals.applies_similar, similar, similar)
                else stringResource(R.string.remembered_merchant),
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
                                Text(category.shortTitle(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** "Nov 1 – Nov 30, 2026" in the app's language. */
@Composable
private fun dateRangeText(start: LocalDate, end: LocalDate): String =
    "${shortDateText(start)} – ${dateFormatter(R.string.pattern_date_year).format(end)}"

/** "a and b", or just "a". */
@Composable
private fun joinAnd(parts: List<String>): String {
    val both = stringResource(R.string.pending_both, parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
    return if (parts.size >= 2) both else parts.firstOrNull().orEmpty()
}
