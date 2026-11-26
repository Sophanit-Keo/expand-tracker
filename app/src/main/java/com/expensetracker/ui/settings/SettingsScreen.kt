package com.expensetracker.ui.settings

import com.expensetracker.ui.theme.card
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
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.BuildConfig
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.ThemeMode
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.theme.supportsDynamicColor
import com.expensetracker.util.amountToInput
import com.expensetracker.util.formatMoney
import com.expensetracker.util.sanitizeAmountInput
import kotlinx.coroutines.launch

private enum class SettingsDialog { CURRENCY, BUDGET, THEME, CLEAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
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
                title = { Text("Settings") },
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
                SettingsGroup("Money") {
                    SettingsItem(
                        icon = Icons.Rounded.Payments,
                        title = "Currency",
                        subtitle = "${settings.currency.displayName} (${settings.currency.symbol})",
                        onClick = { dialog = SettingsDialog.CURRENCY },
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.Savings,
                        title = "Monthly budget",
                        subtitle = if (settings.monthlyBudget > 0) formatMoney(settings.monthlyBudget, settings.currency) else "Not set",
                        onClick = { dialog = SettingsDialog.BUDGET },
                    )
                }
            }
            item {
                SettingsGroup("Appearance") {
                    SettingsItem(
                        icon = Icons.Rounded.DarkMode,
                        title = "Theme",
                        subtitle = settings.themeMode.label,
                        onClick = { dialog = SettingsDialog.THEME },
                    )
                    if (supportsDynamicColor) {
                        GroupDivider()
                        SettingsItem(
                            icon = Icons.Rounded.ColorLens,
                            title = "Dynamic color",
                            subtitle = "Match your wallpaper colors",
                            onClick = { viewModel.setDynamicColor(!settings.dynamicColor) },
                            trailing = { Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor) },
                        )
                    }
                }
            }
            item {
                SettingsGroup("Data") {
                    SettingsItem(
                        icon = Icons.Rounded.FileDownload,
                        title = "Export as CSV",
                        subtitle = "${state.expenseCount} expenses · share to Drive, email or Sheets",
                        onClick = {
                            scope.launch {
                                if (state.expenseCount == 0) {
                                    snackbar.showSnackbar("Nothing to export yet")
                                    return@launch
                                }
                                val csv = viewModel.exportCsv()
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Expenses export")
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                }
                                context.startActivity(Intent.createChooser(send, "Export expenses"))
                            }
                        },
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Add sample data",
                        subtitle = "Fill the app with 4 months of example expenses",
                        onClick = {
                            viewModel.loadSampleData()
                            scope.launch { snackbar.showSnackbar("Sample expenses added") }
                        },
                    )
                    GroupDivider()
                    SettingsItem(
                        icon = Icons.Rounded.DeleteForever,
                        title = "Delete all expenses",
                        subtitle = "Permanently remove every expense",
                        onClick = { dialog = SettingsDialog.CLEAR },
                        destructive = true,
                    )
                }
            }
            item {
                SettingsGroup("About") {
                    SettingsItem(
                        icon = Icons.Rounded.Info,
                        title = "Expense Tracker",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} · your data stays on this device",
                        onClick = null,
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.CURRENCY -> ChoiceDialog(
            title = "Currency",
            options = AppCurrency.entries,
            selected = settings.currency,
            label = { "${it.displayName}  ${it.symbol}" },
            onSelect = { viewModel.setCurrency(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label },
            onSelect = { viewModel.setTheme(it); dialog = null },
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
            title = { Text("Delete all expenses?") },
            text = { Text("All ${state.expenseCount} expenses will be removed from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); dialog = null }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
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
    trailing: @Composable (() -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier
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
    label: (T) -> String,
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BudgetDialog(current: Double, currency: AppCurrency, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) amountToInput(current) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Savings, contentDescription = null) },
        title = { Text("Monthly budget") },
        text = {
            Column {
                Text(
                    "Set how much you plan to spend each month. Leave empty to turn the budget off.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitizeAmountInput(it, currency.decimals) },
                    singleLine = true,
                    label = { Text("Amount") },
                    prefix = if (currency.symbolFirst) ({ Text(currency.symbol) }) else null,
                    suffix = if (!currency.symbolFirst) ({ Text(currency.symbol) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.toDoubleOrNull() ?: 0.0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
