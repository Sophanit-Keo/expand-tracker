package com.expensetracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.R
import com.expensetracker.domain.CashFlowSummary
import com.expensetracker.domain.CategoryTotal
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.appLocale
import com.expensetracker.ui.charts.BarChart
import com.expensetracker.ui.components.CategoryIcon
import com.expensetracker.ui.components.ChangeChip
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.ExpenseRow
import com.expensetracker.ui.components.RoundedProgressBar
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.StatTile
import com.expensetracker.ui.components.animatedAmount
import com.expensetracker.ui.components.color
import com.expensetracker.ui.dateFormatter
import com.expensetracker.ui.friendlyDateText
import com.expensetracker.ui.theme.CashInDark
import com.expensetracker.ui.theme.CashInLight
import com.expensetracker.ui.theme.CashOutDark
import com.expensetracker.ui.theme.CashOutLight
import com.expensetracker.ui.theme.LocalDarkTheme
import com.expensetracker.ui.theme.heroBrush
import com.expensetracker.ui.theme.onHero
import com.expensetracker.ui.title
import com.expensetracker.util.formatCompact
import com.expensetracker.util.formatMoney
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenCashFlow: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, onAddExpense, onOpenExpense, onSeeAll, onOpenAnalytics, onOpenSettings, onImport, viewModel::loadSampleData, onAddIncome, onOpenCashFlow)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onLoadSample: () -> Unit,
    onAddIncome: () -> Unit = {},
    onOpenCashFlow: () -> Unit = {},
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = statusBar.calculateTopPadding() + 12.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(state) }

        if (state.loading) return@LazyColumn

        if (state.isEmpty) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Wallet,
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = onAddExpense) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_add_expense))
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onImport) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.import_bank_statement))
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onLoadSample) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.explore_sample))
                            }
                        }
                    },
                )
            }
            return@LazyColumn
        }

        item { HeroCard(state, onOpenSettings) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = stringResource(R.string.today),
                    value = formatMoney(state.todayTotal, state.currency),
                    icon = Icons.Rounded.Today,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onSeeAll,
                )
                StatTile(
                    label = stringResource(R.string.this_week),
                    value = formatMoney(state.weekTotal, state.currency),
                    icon = Icons.Rounded.CalendarMonth,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = onSeeAll,
                )
            }
        }

        item {
            val cashFlow = state.cashFlow
            if (cashFlow != null) CashFlowCard(cashFlow, state, onOpenCashFlow) else IncomePrompt(onAddIncome)
        }

        item {
            val todayLabel = stringResource(R.string.today)
            SectionCard(
                title = stringResource(R.string.last_7_days),
                subtitle = stringResource(R.string.last_7_days_subtitle, formatMoney(state.last7Days.sumOf { it.value }, state.currency)),
            ) {
                BarChart(
                    points = state.last7Days.mapIndexed { i, p -> if (i == state.last7Days.lastIndex) p.copy(label = todayLabel) else p },
                    valueFormatter = { formatMoney(it, state.currency) },
                    axisFormatter = { formatCompact(it, state.currency) },
                    height = 190.dp,
                    highlightIndex = state.last7Days.lastIndex,
                )
            }
        }

        if (state.topCategories.isNotEmpty()) {
            item {
                SectionCard(
                    title = stringResource(R.string.top_categories),
                    subtitle = stringResource(R.string.this_month),
                    action = {
                        TextButton(onClick = onOpenAnalytics) {
                            Text(stringResource(R.string.nav_analytics))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.topCategories.forEach { TopCategoryRow(it, state) }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.recent_transactions),
                action = { TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) } },
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
            ) {
                state.recent.forEach { expense ->
                    ExpenseRow(
                        expense = expense,
                        currency = state.currency,
                        supportingText = expense.category.title() + " · " + friendlyDateText(expense.date, state.today),
                        onClick = { onOpenExpense(expense.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..16 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_evening)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.your_spending), style = MaterialTheme.typography.headlineMedium)
        }
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                dateFormatter(R.string.pattern_day).format(state.today),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HeroCard(state: HomeUiState, onOpenSettings: () -> Unit) {
    val dark = LocalDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val onHero = colors.onHero(dark)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.heroBrush(dark))
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.spent_in, monthName(state)), style = MaterialTheme.typography.titleSmall, color = onHero.copy(alpha = 0.85f))
        Spacer(Modifier.height(6.dp))
        Text(formatMoney(animatedAmount(state.monthTotal), state.currency), style = MaterialTheme.typography.displaySmall, color = onHero)
        Spacer(Modifier.height(10.dp))
        ChangeChip(state.monthChangePercent, comparedTo = stringResource(R.string.compared_last_month), onDark = true)

        Spacer(Modifier.height(20.dp))
        if (state.budget > 0) {
            val progress = (state.monthTotal / state.budget).toFloat()
            val remaining = state.budget - state.monthTotal
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.monthly_budget), style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.budget_progress, (progress * 100).roundToInt(), formatMoney(state.budget, state.currency)),
                    style = MaterialTheme.typography.labelLarge,
                    color = onHero,
                )
            }
            Spacer(Modifier.height(8.dp))
            RoundedProgressBar(
                progress = progress,
                color = if (progress > 1f) Color(0xFFFFC2C4) else onHero,
                trackColor = onHero.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(10.dp))
            val daysText = when (state.daysLeftInMonth) {
                0 -> stringResource(R.string.last_day_of_month)
                else -> pluralStringResource(R.plurals.days_left, state.daysLeftInMonth, state.daysLeftInMonth)
            }
            Text(
                if (remaining >= 0) stringResource(R.string.left_to_spend, formatMoney(remaining, state.currency), daysText)
                else stringResource(R.string.over_budget, formatMoney(-remaining, state.currency), daysText),
                style = MaterialTheme.typography.bodyMedium,
                color = onHero,
                fontWeight = if (remaining < 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        } else {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(onHero.copy(alpha = 0.16f))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Savings, contentDescription = null, tint = onHero, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.set_monthly_budget), style = MaterialTheme.typography.labelLarge, color = onHero)
            }
        }
    }
}

@Composable
private fun CashFlowCard(summary: CashFlowSummary, state: HomeUiState, onOpen: () -> Unit) {
    val dark = LocalDarkTheme.current
    val inColor = if (dark) CashInDark else CashInLight
    val outColor = if (dark) CashOutDark else CashOutLight
    SectionCard(
        title = stringResource(R.string.cash_flow),
        subtitle = monthName(state),
        action = {
            TextButton(onClick = onOpen, modifier = Modifier.testTag("open_cash_flow")) {
                Text(stringResource(R.string.analysis))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
    ) {
        Row {
            CashFigure(stringResource(R.string.cash_in_short),  "+" + formatMoney(animatedAmount(summary.cashIn), state.currency), inColor, Modifier.weight(1f))
            CashFigure(stringResource(R.string.cash_out_short),  "−" + formatMoney(animatedAmount(summary.cashOut), state.currency), outColor, Modifier.weight(1f))
            CashFigure(
                if (summary.net >= 0) stringResource(R.string.left_short) else stringResource(R.string.short_short),
                formatMoney(kotlin.math.abs(animatedAmount(summary.net)), state.currency),
                null,
                Modifier.weight(1f),
            )
        }
        if (summary.cashIn == 0.0) {
            // Imported last month's statement? Its income is there, just not in this month's numbers.
            val lastMonth = summary.monthly.getOrNull(summary.monthly.size - 2)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.no_income_this_month) +
                (lastMonth?.takeIf { it.cashIn > 0 }?.let { " " + stringResource(R.string.no_income_last_month_hint, it.label, formatMoney(it.cashIn, state.currency)) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.cashIn > 0) {
            val used = (summary.cashOut / summary.cashIn).toFloat()
            Spacer(Modifier.height(14.dp))
            RoundedProgressBar(progress = used, color = outColor, trackColor = inColor.copy(alpha = 0.25f))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.income_spent, (used * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        summary.outlook?.let { outlook ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (outlook.safeToSpendPerDay >= 0) {
                        pluralStringResource(R.plurals.safe_to_spend_home, outlook.remainingDays, formatMoney(outlook.safeToSpendPerDay, state.currency), outlook.remainingDays)
                    } else {
                        stringResource(R.string.short_this_month, formatMoney(-outlook.safeToSpendPerDay * outlook.remainingDays, state.currency))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CashFigure(label: String, value: String, swatch: Color?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                Box(Modifier.size(8.dp).background(swatch, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IncomePrompt(onAddIncome: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.track_income_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.track_income_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAddIncome) { Text(stringResource(R.string.add)) }
        }
    }
}

@Composable
private fun TopCategoryRow(item: CategoryTotal, state: HomeUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(item.category, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(item.category.title(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(formatMoney(item.total, state.currency), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundedProgressBar(
                    progress = item.share,
                    color = item.category.color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(item.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** This month's name in the app language ("November" / "វិច្ឆិកា"). */
@Composable
private fun monthName(state: HomeUiState): String =
    state.today.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, appLocale())
