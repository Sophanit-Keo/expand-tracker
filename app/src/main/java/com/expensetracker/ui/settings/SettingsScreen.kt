package com.expensetracker.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.BuildConfig
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.ThemeMode
import com.expensetracker.domain.Exchange
import com.expensetracker.ui.AppLanguage
import com.expensetracker.ui.AppLocale
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.theme.card
import com.expensetracker.ui.theme.supportsDynamicColor
import com.expensetracker.ui.title
import com.expensetracker.util.amountToInput
import com.expensetracker.util.formatMoney
import com.expensetracker.util.sanitizeAmountInput
import kotlinx.coroutines.launch

private enum class SettingsDialog { CURRENCY, BUDGET, THEME, LANGUAGE, CLEAR, CLEAR_IMPORTED, LICENSES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onImport: () -> Unit,
    onOpenRates: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                SettingsGroup(stringResource(R.string.group_money)) {
                    SettingsItem(
                        icon = Icons.Rounded.Payments,
                        title = stringResource(R.string.main_currency),
                        subtitle = "${settings.currency.title()} (${settings.currency.symbol})",
                        onClick = { dialog = SettingsDialog.CURRENCY },
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.CurrencyExchange,
                        title = stringResource(R.string.exchange_rates),
                        subtitle = settings.rates.entries.sortedBy { it.key.ordinal }.take(2)
                            .joinToString(" · ") { (c, r) -> Exchange.describe(c, settings.currency, r) }
                            .let { if (settings.rates.size > 2) "$it …" else it }
                            .ifEmpty { stringResource(R.string.rates_none_hint) },
                        onClick = onOpenRates,
                        modifier = Modifier.testTag("settings_rates"),
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.Savings,
                        title = stringResource(R.string.monthly_budget),
                        subtitle = if (settings.monthlyBudget > 0) formatMoney(settings.monthlyBudget, settings.currency) else stringResource(R.string.not_set),
                        onClick = { dialog = SettingsDialog.BUDGET },
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.group_appearance)) {
                    SettingsItem(
                        icon = Icons.Rounded.Translate,
                        title = stringResource(R.string.language),
                        subtitle = AppLocale.selected().title(),
                        onClick = { dialog = SettingsDialog.LANGUAGE },
                        modifier = Modifier.testTag("settings_language"),
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.theme),
                        subtitle = settings.themeMode.title(),
                        onClick = { dialog = SettingsDialog.THEME },
                    )
                    if (supportsDynamicColor) {
                        GroupDivider()
                        SettingsItem(
                            icon = Icons.Rounded.ColorLens,
                            title = stringResource(R.string.dynamic_color),
                            subtitle = stringResource(R.string.dynamic_color_hint),
                            onClick = { viewModel.setDynamicColor(!settings.dynamicColor) },
                            trailing = { Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor) },
                        )
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.group_data)) {
                    SettingsItem(
                        icon = Icons.Rounded.UploadFile,
                        title = stringResource(R.string.import_bank_statement),
                        subtitle = stringResource(R.string.import_hint),
                        onClick = onImport,
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.FileDownload,
                        title = stringResource(R.string.export_csv),
                        subtitle = pluralStringResource(R.plurals.export_hint, state.expenseCount, state.expenseCount),
                        onClick = {
                            scope.launch {
                                if (state.expenseCount == 0) {
                                    snackbar.showSnackbar(context.getString(R.string.nothing_to_export))
                                    return@launch
                                }
                                val csv = viewModel.exportCsv()
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                }
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.export_chooser)))
                            }
                        },
                    )
                    GroupDivider()
                    if (state.sampleCount > 0) {
                        SettingsItem(
                            icon = Icons.Rounded.AutoAwesome,
                            title = stringResource(R.string.remove_sample),
                            subtitle = pluralStringResource(R.plurals.remove_sample_hint, state.sampleCount, state.sampleCount),
                            onClick = {
                                viewModel.removeSampleData()
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.sample_removed)) }
                            },
                        )
                        GroupDivider()
                    }
                    if (state.importedCount > 0) {
                        SettingsItem(
                            icon = Icons.Rounded.DeleteSweep,
                            title = stringResource(R.string.remove_imported),
                            subtitle = stringResource(R.string.remove_imported_hint, state.importedCount),
                            onClick = { dialog = SettingsDialog.CLEAR_IMPORTED },
                            destructive = true,
                        )
                        GroupDivider()
                    }
                    SettingsItem(
                        icon = Icons.Rounded.DeleteForever,
                        title = stringResource(R.string.delete_all),
                        subtitle = stringResource(R.string.delete_all_hint),
                        onClick = { dialog = SettingsDialog.CLEAR },
                        destructive = true,
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.group_about)) {
                    SettingsItem(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.version_hint, BuildConfig.VERSION_NAME),
                        onClick = null,
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.licenses),
                        subtitle = stringResource(R.string.licenses_hint),
                        onClick = { dialog = SettingsDialog.LICENSES },
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.CURRENCY -> ChoiceDialog(
            title = stringResource(R.string.main_currency),
            options = AppCurrency.entries,
            selected = settings.currency,
            label = { "${it.title()}  ${it.symbol}" },
            onSelect = { viewModel.setCurrency(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.title() },
            onSelect = { viewModel.setTheme(it); dialog = null },
            onDismiss = { dialog = null },
            )
            SettingsDialog.LANGUAGE -> ChoiceDialog(
                title = stringResource(R.string.language),
                options = AppLanguage.entries,
                selected = AppLocale.selected(),
                label = { it.title() },
                onSelect = {
                    dialog = null
                    // Recreates the screen in the new language; the choice is kept across restarts.
                    AppLocale.select(it)
                },
                onDismiss = { dialog = null },
            )
        SettingsDialog.BUDGET -> BudgetDialog(
            current = settings.monthlyBudget,
            currency = settings.currency,
            onSave = { viewModel.setBudget(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLEAR -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_message, state.expenseCount)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); dialog = null }) {
                    Text(stringResource(R.string.delete_all_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        SettingsDialog.CLEAR_IMPORTED -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.remove_imported_title)) },
            text = {
                Text(stringResource(R.string.remove_imported_message, state.importedCount))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeImported()
                    dialog = null
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.imported_removed)) }
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        SettingsDialog.LICENSES -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.licenses)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(openSourceLibraries) { (name, notice) ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) } },
        )
        null -> Unit
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
    )
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    ) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun BudgetDialog(current: Double, currency: AppCurrency, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) amountToInput(current) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Savings, contentDescription = null) },
        title = { Text(stringResource(R.string.monthly_budget)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.budget_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitizeAmountInput(it, currency.decimals) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.amount)) },
                    prefix = if (currency.symbolFirst) ({ Text(currency.symbol) }) else null,
                    suffix = if (!currency.symbolFirst) ({ Text(currency.symbol) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.toDoubleOrNull() ?: 0.0) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val APACHE_2 = "Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0"

/** Third-party software shipped in the app, with the notices their licenses ask for. */
private val openSourceLibraries = listOf(
    "PdfBox-Android" to "Copyright © Tom Roush and contributors. A port of Apache PDFBox, which includes " +
        "software developed at The Apache Software Foundation (https://www.apache.org/). $APACHE_2",
    "Android Jetpack (AndroidX): Compose, Material 3, Room, Navigation, Lifecycle, DataStore" to "Copyright © The Android Open Source Project. $APACHE_2",
    "Kotlin and kotlinx.coroutines" to "Copyright © JetBrains s.r.o. and Kotlin Programming Language contributors. $APACHE_2",
    "Material Symbols icons" to "Copyright © Google LLC. $APACHE_2",
    "Inter and Noto Sans Khmer (logo)" to "The \$ and ៛ signs in the app icon are drawn from Inter (Copyright © The Inter Project Authors) " +
        "and Noto Sans Khmer (Copyright © Google LLC). Licensed under the SIL Open Font License 1.1: https://openfontlicense.org",
    "Exchange rates" to "Rates by ExchangeRate-API (https://www.exchangerate-api.com), with currency-api by Fawaz Ahmed " +
        "(https://github.com/fawazahmed0/exchange-api) as a fallback.",
)
