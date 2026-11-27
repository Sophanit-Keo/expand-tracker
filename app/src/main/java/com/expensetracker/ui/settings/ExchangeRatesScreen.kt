package com.expensetracker.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.domain.Exchange
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.theme.card
import com.expensetracker.ui.title
import com.expensetracker.util.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RatesUiState(
    val loading: Boolean = true,
    val main: AppCurrency = AppCurrency.USD,
    /** Main-currency units per one unit of each currency. */
    val rates: Map<AppCurrency, Double> = emptyMap(),
    /** How many entries were made in each currency. */
    val usage: Map<AppCurrency, Int> = emptyMap(),
    /** A currency just added, waiting for its rate to be typed. */
    val pending: AppCurrency? = null,
)

class ExchangeRatesViewModel(
    expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val pending = MutableStateFlow<AppCurrency?>(null)

    val state: StateFlow<RatesUiState> = combine(settings.settings, expenses.expenses, pending) { prefs, all, added ->
        RatesUiState(
            loading = false,
            main = prefs.currency,
            rates = prefs.rates,
            usage = all.mapNotNull { it.originalCurrency }.groupingBy { it }.eachCount(),
            pending = added?.takeIf { it != prefs.currency && it !in prefs.rates },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatesUiState())

    /** Shows a row for [currency] to type its rate into; it's saved once the rate is valid. */
    fun add(currency: AppCurrency) {
        pending.value = currency
    }

    /** [value] is the number in "1 X = [value] Y", written the usual way round. */
    fun setRate(currency: AppCurrency, value: Double) {
        val rate = Exchange.rateFromQuote(currency, state.value.main, value) ?: return
        viewModelScope.launch { settings.setRate(currency, rate) }
    }

    fun remove(currency: AppCurrency) {
        if (pending.value == currency) pending.value = null
        viewModelScope.launch { settings.removeRate(currency) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRatesScreen(
    onBack: () -> Unit,
    viewModel: ExchangeRatesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val available = AppCurrency.entries.filter { it != state.main && it !in state.rates && it != state.pending }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exchange_rates)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (available.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_rate)) },
                    modifier = Modifier.testTag("add_rate"),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.main_currency) + ": ${state.main.title()} (${state.main.symbol})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.rates_intro, state.main.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            state.pending?.let { currency ->
                item(key = "pending-${currency.name}") {
                    RateRow(
                        currency = currency,
                        main = state.main,
                        rate = null,
                        used = state.usage[currency] ?: 0,
                        onChange = { viewModel.setRate(currency, it) },
                        onRemove = { viewModel.remove(currency) },
                    )
                }
            }
            if (!state.loading && state.rates.isEmpty() && state.pending == null) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.CurrencyExchange,
                        title = stringResource(R.string.exchange_rates),
                        message = stringResource(R.string.no_rates),
                    )
                }
            }
            items(state.rates.keys.sortedBy { it.ordinal }, key = { it.name }) { currency ->
                RateRow(
                    currency = currency,
                    main = state.main,
                    rate = state.rates.getValue(currency),
                    used = state.usage[currency] ?: 0,
                    onChange = { viewModel.setRate(currency, it) },
                    onRemove = { viewModel.remove(currency) },
                )
            }
            if (state.rates.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.rate_existing_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    if (adding) {
        CurrencyListDialog(
            options = available,
            onSelect = {
                adding = false
                viewModel.add(it)
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun RateRow(currency: AppCurrency, main: AppCurrency, rate: Double?, used: Int, onChange: (Double) -> Unit, onRemove: () -> Unit) {
    val foreignFirst = Exchange.foreignFirst(currency, main)
    val one = if (foreignFirst) currency else main
    val other = if (foreignFirst) main else currency
    // Typed text is kept as typed; only a valid number is saved.
    var text by remember(currency, main) { mutableStateOf(rate?.let { Exchange.rateToInput(Exchange.quote(currency, main, it).value) }.orEmpty()) }
    val focus = remember { FocusRequester() }
    if (rate == null) LaunchedEffect(currency) { focus.requestFocus() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.card)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 16.dp)
            .testTag("rate_${currency.name}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CurrencyBadge(currency)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${currency.title()} (${currency.name})", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (used > 0) pluralStringResource(R.plurals.rate_used_by, used, used) else stringResource(R.string.rate_not_used),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.remove_rate))
            }
        }
        Spacer(Modifier.height(8.dp))
        QuoteField(
            one = one,
            other = other,
            text = text,
            placeholder = Exchange.formatRate(Exchange.quote(currency, main, Exchange.approximate(currency, main)).value),
            onTextChange = {
                text = sanitizeAmountInput(it, 6, maxLength = 14)
                text.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let(onChange)
            },
            modifier = Modifier.padding(end = 12.dp),
            fieldModifier = Modifier.focusRequester(focus),
        )
        if (rate == null) {
            Text(
                stringResource(R.string.rate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun QuoteField(
    one: AppCurrency,
    other: AppCurrency,
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("1 ${one.name} =", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = fieldModifier.weight(1f).testTag("quote_input"),
            singleLine = true,
            placeholder = { Text(placeholder) },
            textStyle = MaterialTheme.typography.titleMedium,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.width(10.dp))
        Text(other.name, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CurrencyBadge(currency: AppCurrency) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(currency.symbol, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
    }
}

/** The currencies without a rate yet. */
@Composable
private fun CurrencyListDialog(options: List<AppCurrency>, onSelect: (AppCurrency) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_rate)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { currency ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(currency) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .testTag("add_rate_${currency.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CurrencyBadge(currency)
                        Spacer(Modifier.width(12.dp))
                        Text("${currency.title()} (${currency.name})", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
