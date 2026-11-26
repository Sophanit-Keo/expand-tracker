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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.domain.CategoryTotal
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.charts.BarChart
import com.expensetracker.ui.components.CategoryIcon
import com.expensetracker.ui.components.ChangeChip
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.ExpenseRow
import com.expensetracker.ui.components.RoundedProgressBar
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.StatTile
import com.expensetracker.ui.components.color
import com.expensetracker.ui.theme.LocalDarkTheme
import com.expensetracker.ui.theme.heroBrush
import com.expensetracker.ui.theme.onHero
import com.expensetracker.util.formatCompact
import com.expensetracker.util.formatMoney
import com.expensetracker.util.friendlyDate
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
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, onAddExpense, onOpenExpense, onSeeAll, onOpenAnalytics, onOpenSettings, viewModel::loadSampleData)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoadSample: () -> Unit,
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
                    title = "Start tracking your spending",
                    message = "Add your first expense and your daily totals, budget and charts will appear here.",
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = onAddExpense) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add expense")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onLoadSample) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Explore with sample data")
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
                    label = "Today",
                    value = formatMoney(state.todayTotal, state.currency),
                    icon = Icons.Rounded.Today,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "This week",
                    value = formatMoney(state.weekTotal, state.currency),
                    icon = Icons.Rounded.CalendarMonth,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(
                title = "Last 7 days",
                subtitle = "${formatMoney(state.last7Days.sumOf { it.value }, state.currency)} total · tap a day for details",
            ) {
                BarChart(
                    points = state.last7Days,
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
                    title = "Top categories",
                    subtitle = "This month",
                    action = {
                        TextButton(onClick = onOpenAnalytics) {
                            Text("Analytics")
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
                title = "Recent transactions",
                action = { TextButton(onClick = onSeeAll) { Text("See all") } },
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
            ) {
                state.recent.forEach { expense ->
                    ExpenseRow(
                        expense = expense,
                        currency = state.currency,
                        supportingText = "${expense.category.label} · ${friendlyDate(expense.date, state.today)}",
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
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Your spending", style = MaterialTheme.typography.headlineMedium)
        }
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                state.today.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
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
        Text("Spent in ${state.monthName}", style = MaterialTheme.typography.titleSmall, color = onHero.copy(alpha = 0.85f))
        Spacer(Modifier.height(6.dp))
        Text(formatMoney(state.monthTotal, state.currency), style = MaterialTheme.typography.displaySmall, color = onHero)
        Spacer(Modifier.height(10.dp))
        ChangeChip(state.monthChangePercent, comparedTo = "last month", onDark = true)

        Spacer(Modifier.height(20.dp))
        if (state.budget > 0) {
            val progress = (state.monthTotal / state.budget).toFloat()
            val remaining = state.budget - state.monthTotal
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text(
                    "${(progress * 100).roundToInt()}% of ${formatMoney(state.budget, state.currency)}",
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
                0 -> "last day of the month"
                1 -> "1 day left"
                else -> "${state.daysLeftInMonth} days left"
            }
            Text(
                if (remaining >= 0) "${formatMoney(remaining, state.currency)} left to spend · $daysText"
                else "Over budget by ${formatMoney(-remaining, state.currency)} · $daysText",
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
                Text("Set a monthly budget", style = MaterialTheme.typography.labelLarge, color = onHero)
            }
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
                Text(item.category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
