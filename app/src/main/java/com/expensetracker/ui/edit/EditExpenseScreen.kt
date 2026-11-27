package com.expensetracker.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.domain.Exchange
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.Prefill
import com.expensetracker.ui.components.color
import com.expensetracker.ui.components.icon
import com.expensetracker.ui.friendlyDateText
import com.expensetracker.ui.shortTitle
import com.expensetracker.ui.theme.card
import com.expensetracker.ui.title
import com.expensetracker.util.formatMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    onDone: () -> Unit,
    onDuplicate: (Prefill) -> Unit = {},
    viewModel: EditExpenseViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val amountFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val rateFocus = remember { FocusRequester() }

    LaunchedEffect(state.done) { if (state.done) onDone() }
    LaunchedEffect(state.loading) {
        if (!state.loading && !state.isEditing) amountFocus.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val income = state.type == EntryType.INCOME
                    Text(
                        stringResource(
                            when {
                                state.isEditing && income -> R.string.edit_title_edit_income
                                state.isEditing -> R.string.edit_title_edit_expense
                                income -> R.string.edit_title_new_income
                                else -> R.string.edit_title_new_expense
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    if (state.isEditing && !state.loading) {
                        IconButton(onClick = { viewModel.duplicate()?.let(onDuplicate) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.duplicate_entry))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = {
                        if (state.amount != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        else haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        viewModel.save()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp),
                    enabled = !state.loading,
                ) {
                    Text(
                        when {
                            state.isEditing -> stringResource(R.string.save_changes)
                            state.type == EntryType.INCOME -> stringResource(R.string.save_income)
                            else -> stringResource(R.string.save_expense)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                listOf(EntryType.EXPENSE to stringResource(R.string.type_expense), EntryType.INCOME to stringResource(R.string.type_income)).forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.testTag("type_${type.name.lowercase()}"),
                    ) { Text(label) }
                }
            }
            AmountInput(
                text = state.amountText,
                currency = state.entryCurrency,
                isError = state.showAmountError,
                onChange = viewModel::onAmountChange,
                onPickCurrency = { pickCurrency = true },
                modifier = Modifier.focusRequester(amountFocus),
                onNext = { if (state.isForeign && state.rateText.isEmpty()) rateFocus.requestFocus() else noteFocus.requestFocus() },
            )
            // Paid in another currency: the rate converts it to the main currency.
            AnimatedVisibility(visible = state.isForeign) {
                RateCard(state, viewModel::onRateChange, Modifier.focusRequester(rateFocus), onDone = { noteFocus.requestFocus() })
            }

            // Things you enter often: one tap fills everything in.
            AnimatedVisibility(visible = !state.isEditing && state.suggestions.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.quick_fill), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.suggestions, key = { it.note + it.category }) { fill ->
                            QuickFillChip(fill, fill.currency ?: state.currency) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.applySuggestion(fill)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth().focusRequester(noteFocus),
                label = { Text(stringResource(if (state.type == EntryType.INCOME) R.string.note_label_income else R.string.note_label_expense)) },
                placeholder = { Text(stringResource(if (state.type == EntryType.INCOME) R.string.note_hint_income else R.string.note_hint_expense)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AnimatedVisibility(visible = state.categoryAuto, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.category_from_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            CategoryGrid(categories = Category.of(state.type), selected = state.category, onSelect = {
                focusManager.clearFocus()
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                viewModel.onCategoryChange(it)
            })

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.date), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            DateRow(
                date = state.date,
                onPick = { showDatePicker = true },
                onQuickPick = viewModel::onDateChange,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickCurrency) {
        CurrencyPickerDialog(
            main = state.currency,
            selected = state.entryCurrency,
            rates = state.rates,
            onSelect = {
                pickCurrency = false
                viewModel.onCurrencyChange(it)
            },
            onDismiss = { pickCurrency = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AmountInput(
    text: String,
    currency: AppCurrency,
    isError: Boolean,
    onChange: (String) -> Unit,
    onPickCurrency: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: () -> Unit = {},
) {
    val symbol = currency.symbol
    val symbolFirst = currency.symbolFirst
    val decimals = currency.decimals
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(if (isError) colors.error else Color.Transparent, label = "amountBorder")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.primaryContainer.copy(alpha = 0.55f))
            .border(BorderStroke(2.dp, border), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.amount), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
            Spacer(Modifier.width(10.dp))
            // Tap to type the amount in another currency.
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.7f))
                    .clickable(onClickLabel = stringResource(R.string.change_currency), onClick = onPickCurrency)
                    .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                    .testTag("entry_currency"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currency.name, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val amountStyle = MaterialTheme.typography.displayMedium.copy(color = colors.onPrimaryContainer, textAlign = TextAlign.Start)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (symbolFirst) Text(symbol, style = amountStyle.copy(color = colors.onPrimaryContainer.copy(alpha = 0.6f)))
            BasicTextField(
                value = text,
                onValueChange = onChange,
                modifier = modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 48.dp, max = 260.dp),
                textStyle = amountStyle,
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimals == 0) KeyboardType.Number else KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                if (decimals == 0) "0" else "0." + "0".repeat(decimals),
                                style = amountStyle.copy(color = colors.onPrimaryContainer.copy(alpha = 0.3f)),
                            )
                        }
                        inner()
                    }
                },
            )
            if (!symbolFirst) Text(symbol, style = amountStyle.copy(color = colors.onPrimaryContainer.copy(alpha = 0.6f)))
        }
        if (isError) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.amount_error), style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<Category>, selected: Category, onSelect: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    CategoryTile(category, category == selected, { onSelect(category) }, Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tint = category.color
    val background by animateColorAsState(
        if (selected) tint.copy(alpha = 0.16f) else colors.card,
        label = "tileBackground",
    )
    val borderColor by animateColorAsState(if (selected) tint else colors.outlineVariant.copy(alpha = 0.5f), label = "tileBorder")
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) tint else tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = if (selected) Color.White else tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        val label = category.shortTitle()
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.let { if (label.length > 10) it.copy(fontSize = 10.sp, letterSpacing = 0.sp) else it },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DateRow(date: LocalDate, onPick: () -> Unit, onQuickPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.card)
                .clickable(onClick = onPick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(friendlyDateText(date, today), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = date == today, onClick = { onQuickPick(today) }, label = { Text(stringResource(R.string.today)) }, shape = CircleShape)
            FilterChip(
                selected = date == today.minusDays(1),
                onClick = { onQuickPick(today.minusDays(1)) },
                label = { Text(stringResource(R.string.yesterday)) },
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun QuickFillChip(fill: QuickFill, currency: AppCurrency, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(fill.category.color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            .testTag("quick_fill"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(fill.category.icon, contentDescription = null, tint = fill.category.color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(fill.note, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(
            " · " + formatMoney(fill.amount, currency),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** "1 USD = [4100] ៛" with what the amount counts as in the main currency. */
@Composable
private fun RateCard(state: EditUiState, onRateChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val example = Exchange.quote(state.entryCurrency, state.currency, Exchange.approximate(state.entryCurrency, state.currency)).value
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colors.card)
            .border(BorderStroke(1.dp, if (state.showRateError) colors.error else colors.outlineVariant.copy(alpha = 0.6f)), MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.exchange_rate), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            state.mainAmount?.let {
                Text(
                    stringResource(R.string.counts_as, formatMoney(it, state.currency)),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.testTag("converted_amount"),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1 ${state.quoteOne.name} =", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = state.rateText,
                onValueChange = onRateChange,
                modifier = modifier.weight(1f).testTag("rate_input"),
                singleLine = true,
                isError = state.showRateError,
                placeholder = { Text(Exchange.formatRate(example)) },
                textStyle = MaterialTheme.typography.titleMedium,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onDone() }),
            )
            Spacer(Modifier.width(10.dp))
            Text(state.quoteOther.name, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (state.showRateError) R.string.rate_error else R.string.rate_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.showRateError) colors.error else colors.onSurfaceVariant,
        )
    }
}

/** Main currency first, then currencies with a saved rate, then the rest. */
@Composable
private fun CurrencyPickerDialog(
    main: AppCurrency,
    selected: AppCurrency,
    rates: Map<AppCurrency, Double>,
    onSelect: (AppCurrency) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = listOf(main) + AppCurrency.entries.filter { it != main && it in rates } + AppCurrency.entries.filter { it != main && it !in rates }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_currency)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(ordered) { currency ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = currency == selected, onClick = { onSelect(currency) }, role = Role.RadioButton)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("pick_currency_${currency.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currency == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${currency.title()} (${currency.symbol})", style = MaterialTheme.typography.bodyLarge)
                            val detail = when {
                                currency == main -> stringResource(R.string.main_currency)
                                else -> rates[currency]?.let { Exchange.describe(currency, main, it) }
                            }
                            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
