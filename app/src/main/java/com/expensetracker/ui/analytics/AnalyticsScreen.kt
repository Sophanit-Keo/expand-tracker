package com.expensetracker.ui.analytics

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.domain.CategoryTotal
import com.expensetracker.domain.PeriodSummary
import com.expensetracker.domain.PeriodType
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.Prefill
import com.expensetracker.ui.appLocale
import com.expensetracker.ui.charts.BarChart
import com.expensetracker.ui.charts.DonutChart
import com.expensetracker.ui.charts.DonutSlice
import com.expensetracker.ui.charts.LineChart
import com.expensetracker.ui.components.ChangeChip
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.OtherSliceColor
import com.expensetracker.ui.components.PeriodNavigator
import com.expensetracker.ui.components.RoundedProgressBar
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.animatedAmount
import com.expensetracker.ui.components.color
import com.expensetracker.ui.dateFormatter
import com.expensetracker.ui.displayTitle
import com.expensetracker.ui.monthYearText
import com.expensetracker.ui.shortDateText
import com.expensetracker.ui.theme.Decrease
import com.expensetracker.ui.theme.Increase
import com.expensetracker.ui.title
import com.expensetracker.ui.word
import com.expensetracker.util.formatCompact
import com.expensetracker.util.formatMoney
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onLogRecurring: (Prefill) -> Unit,
    onAddIncome: () -> Unit,
    onImport: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.analytics_title)) },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                PrimaryTabRow(
                    selectedTabIndex = state.tab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    AnalyticsTab.entries.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { viewModel.setTab(tab) },
                            text = { Text(stringResource(tab.label), style = MaterialTheme.typography.titleSmall) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnalyticsContent(
            state = state,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            onTypeChange = viewModel::setType,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onAddIncome = onAddIncome,
            onImport = onImport,
            onRemoveSampleData = viewModel::removeSampleData,
            onLogRecurring = onLogRecurring,
        )
    }
}

@Composable
fun AnalyticsContent(
    state: AnalyticsUiState,
    contentPadding: PaddingValues,
    onTypeChange: (PeriodType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAddIncome: () -> Unit = {},
    onImport: () -> Unit = {},
    onRemoveSampleData: () -> Unit = {},
    onLogRecurring: (Prefill) -> Unit = {},
) {
    val summary = state.summary
    val listState = rememberLazyListState()
    // A different tab is a different page: start it from the top.
    LaunchedEffect(state.tab) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Filters first: one row above everything they scope.
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PeriodType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, PeriodType.entries.size),
                    ) { Text(type.title()) }
                }
            }
            Spacer(Modifier.height(12.dp))
            PeriodNavigator(state.periodLabelText(), onPrevious, onNext, state.canGoNext)
        }

        if (summary == null) return@LazyColumn
        if (state.tab == AnalyticsTab.CASH_FLOW) {
            cashFlowItems(state, onAddIncome, onImport, onRemoveSampleData, onLogRecurring)
            return@LazyColumn
        }

        item { SummaryCard(summary, state) }

        if (summary.total <= 0.0) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Insights,
                    title = stringResource(R.string.no_spending_period),
                    message = stringResource(R.string.no_spending_period_message, state.type.word()),
                )
            }
        } else {
            item { CategoryBreakdownCard(summary.byCategory, summary.total, state.currency) }
            item {
                SectionCard(
                    title = stringResource(R.string.spending_over_time),
                    subtitle = stringResource(if (state.type == PeriodType.YEAR) R.string.monthly_totals_tap else R.string.daily_totals_tap),
                ) {
                    BarChart(
                        points = summary.series,
                        valueFormatter = { formatMoney(it, state.currency) },
                        axisFormatter = { formatCompact(it, state.currency) },
                        highlightIndex = null,
                        tooltipLabel = seriesLabeler(state.type, summary.period.start),
                    )
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.trend_6_month), subtitle = stringResource(R.string.trend_6_month_subtitle)) {
                LineChart(
                    points = state.trend,
                    valueFormatter = { formatMoney(it, state.currency) },
                    axisFormatter = { formatCompact(it, state.currency) },
                )
            }
        }

        if (summary.total > 0.0) {
            item { InsightsCard(summary, state) }
        }
    }
}

/** Labels a chart slot in the selected period: a month name for a year, a short date otherwise. */
@Composable
internal fun seriesLabeler(type: PeriodType, start: LocalDate): (Int) -> String {
    val locale = appLocale()
    val day = dateFormatter(R.string.pattern_short_day)
    return { index ->
        when (type) {
            PeriodType.YEAR -> start.plusMonths(index.toLong()).month.getDisplayName(TextStyle.FULL, locale)
            else -> day.format(start.plusDays(index.toLong()))
        }
    }
}

/** "Nov 3 – 9", "Oct 27 – Nov 2", "November 2026" or "2026", in the app's language. */
@Composable
internal fun AnalyticsUiState.periodLabelText(): String {
    val period = summary?.period ?: return ""
    val sameMonth = period.start.month == period.end.month
    val startFormat = dateFormatter(if (sameMonth) R.string.pattern_week_start_same_month else R.string.pattern_short_day)
    val endFormat = dateFormatter(if (sameMonth) R.string.pattern_week_end_same_month else R.string.pattern_short_day)
    val month = monthYearText(period.start)
    val range = "${startFormat.format(period.start)} – ${endFormat.format(period.end)}"
    val rangeWithYear = stringResource(R.string.with_year, range, period.end.year)
    return when (type) {
        PeriodType.WEEK -> if (period.end.year != today.year) rangeWithYear else range
        PeriodType.MONTH -> month
        PeriodType.YEAR -> period.start.year.toString()
    }
}

/** "previous week" etc., for "vs previous week" style comparisons. */
@Composable
internal fun AnalyticsUiState.comparedToText(): String = stringResource(
    when (type) {
        PeriodType.WEEK -> R.string.compared_previous_week
        PeriodType.MONTH -> R.string.compared_previous_month
        PeriodType.YEAR -> R.string.compared_previous_year
    },
)

@Composable
private fun SummaryCard(summary: PeriodSummary, state: AnalyticsUiState) {
    SectionCard {
        Text(stringResource(R.string.total_spent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(formatMoney(animatedAmount(summary.total), state.currency), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        ChangeChip(summary.changePercent, comparedTo = state.comparedToText())
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            MiniStat(stringResource(R.string.daily_average), formatMoney(summary.dailyAverage, state.currency), Modifier.weight(1f))
            MiniStat(stringResource(R.string.transactions), summary.count.toString(), Modifier.weight(1f))
            MiniStat(stringResource(R.string.largest), summary.largest?.let { formatMoney(it.amount, state.currency) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class LegendEntry(val label: String, val total: Double, val share: Float, val color: Color, val detail: String)

@Composable
private fun CategoryBreakdownCard(categories: List<CategoryTotal>, total: Double, currency: AppCurrency) {
    // Part-to-whole reads best with ≤ 6 slices: keep the top five, fold the tail.
    val maxSlices = 6
    val shown = if (categories.size <= maxSlices) categories else categories.take(maxSlices - 1)
    val rest = categories.drop(shown.size)
    val entries = buildList {
        shown.forEach {
            add(LegendEntry(it.category.title(), it.total, it.share, it.category.color, pluralStringResource(R.plurals.expenses_count, it.count, it.count)))
        }
        if (rest.isNotEmpty()) {
            val sum = rest.sumOf { it.total }
            add(LegendEntry(pluralStringResource(R.plurals.more_categories, rest.size, rest.size), sum, (sum / total).toFloat(), OtherSliceColor, rest.map { it.category.title() }.joinToString()))
        }
    }
    var selected by remember(categories) { mutableStateOf<Int?>(null) }

    SectionCard(title = stringResource(R.string.by_category), subtitle = stringResource(R.string.by_category_hint)) {
        DonutChart(
            slices = entries.map { DonutSlice(it.total.toFloat(), it.color) },
            selectedIndex = selected,
            onSliceClick = { selected = it },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(210.dp),
        ) {
            val focus = selected?.let { entries.getOrNull(it) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(130.dp)) {
                Text(
                    focus?.let { "${(it.share * 100).roundToInt()}%" } ?: stringResource(R.string.total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatMoney(focus?.total ?: total, currency),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    focus?.label ?: pluralStringResource(R.plurals.categories_count, categories.size, categories.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        // The legend doubles as the table view: every slice's name, share and amount.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEachIndexed { index, entry ->
                val isSelected = selected == index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                        .clickable { selected = if (isSelected) null else index }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(entry.color),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(entry.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatMoney(entry.total, currency), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundedProgressBar(
                                progress = entry.share,
                                color = entry.color,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${(entry.share * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                        Text(
                            entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(summary: PeriodSummary, state: AnalyticsUiState) {
    val currency = state.currency
    SectionCard(title = stringResource(R.string.insights)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            summary.byCategory.firstOrNull()?.let { top ->
                InsightRow(
                    Icons.Rounded.EmojiEvents,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.insight_top_category, top.category.title()),
                    stringResource(R.string.insight_top_category_detail, (top.share * 100).roundToInt(), formatMoney(top.total, currency)),
                )
            }
            summary.largest?.let { largest ->
                InsightRow(
                    Icons.Rounded.LocalFireDepartment,
                    largest.category.color,
                    stringResource(R.string.insight_largest, formatMoney(largest.amount, currency)),
                    "${largest.displayTitle()} · ${shortDateText(largest.date)}",
                )
            }
            if (state.type != PeriodType.WEEK) {
                summary.topWeekday?.let { day ->
                    InsightRow(
                        Icons.Rounded.CalendarToday,
                        MaterialTheme.colorScheme.tertiary,
                        stringResource(R.string.insight_weekday, day.getDisplayName(TextStyle.FULL, appLocale())),
                        pluralStringResource(R.plurals.insight_weekday_detail, summary.count, summary.count, state.type.word()),
                    )
                }
            }
            InsightRow(
                Icons.Rounded.Speed,
                MaterialTheme.colorScheme.secondary,
                stringResource(R.string.insight_per_day, formatMoney(summary.dailyAverage, currency)),
                stringResource(R.string.insight_per_day_detail, state.type.word()),
            )
            summary.changePercent?.let { change ->
                val up = change > 0
                InsightRow(
                    if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                    if (up) Increase else Decrease,
                    stringResource(if (up) R.string.insight_up else R.string.insight_down, abs(change).roundToInt()),
                    stringResource(R.string.insight_compared, state.comparedToText(), formatMoney(summary.previousTotal, currency)),
                )
            } ?: InsightRow(
                Icons.Rounded.BarChart,
                MaterialTheme.colorScheme.outline,
                stringResource(R.string.insight_no_data, state.comparedToText()),
                stringResource(R.string.insight_keep_tracking),
            )
        }
    }
}
