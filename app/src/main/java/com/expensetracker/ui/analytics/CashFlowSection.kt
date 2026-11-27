package com.expensetracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.EntryType
import com.expensetracker.domain.CashFlowSummary
import com.expensetracker.domain.PeriodType
import com.expensetracker.domain.RecurringItem
import com.expensetracker.domain.StatementStatus
import com.expensetracker.ui.AppLocale
import com.expensetracker.ui.Prefill
import com.expensetracker.ui.appLocale
import com.expensetracker.ui.charts.CashFlowChart
import com.expensetracker.ui.charts.ChartLegend
import com.expensetracker.ui.charts.LineChart
import com.expensetracker.ui.components.CategoryIcon
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.RoundedProgressBar
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.animatedAmount
import com.expensetracker.ui.components.color
import com.expensetracker.ui.dateFormatter
import com.expensetracker.ui.shortDateText
import com.expensetracker.ui.theme.CashInDark
import com.expensetracker.ui.theme.CashInLight
import com.expensetracker.ui.theme.CashInText
import com.expensetracker.ui.theme.CashOutDark
import com.expensetracker.ui.theme.CashOutLight
import com.expensetracker.ui.theme.Decrease
import com.expensetracker.ui.theme.Increase
import com.expensetracker.ui.theme.LocalDarkTheme
import com.expensetracker.ui.title
import com.expensetracker.ui.word
import com.expensetracker.util.formatCompact
import com.expensetracker.util.formatMoney
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private val cashInColor: Color @Composable get() = if (LocalDarkTheme.current) CashInDark else CashInLight
private val cashOutColor: Color @Composable get() = if (LocalDarkTheme.current) CashOutDark else CashOutLight

private fun signed(value: Double, currency: AppCurrency) = (if (value < 0) "−" else "+") + formatMoney(abs(value), currency)

private fun percent(value: Double) = "${(value * 100).roundToInt()}%"

private fun ordinal(day: Int) = day.toString() + when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

/** The Cash flow tab: money in vs money out, with forward-looking advice for the current month. */
fun LazyListScope.cashFlowItems(
    state: AnalyticsUiState,
    onAddIncome: () -> Unit,
    onImport: () -> Unit,
    onRemoveSampleData: () -> Unit = {},
    onLogRecurring: (Prefill) -> Unit = {},
) {
    val summary = state.cashFlow ?: return
    val currency = state.currency


    // Bank statements first: they're the reference the cash flow should agree with.
    if (state.statements.isNotEmpty()) {
        item { StatementsCard(state, onRemoveSampleData) }
    }

    if (!summary.hasIncome) {
        item {
            SectionCard {
                EmptyState(
                    icon = Icons.Rounded.Insights,
                    title = stringResource(R.string.cf_add_income_title),
                    message = stringResource(R.string.cf_add_income_message),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = onAddIncome) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.add_income))
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onImport) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.import_bank_statement))
                            }
                        }
                    },
                )
            }
        }
        return
    }

    item { CashSummaryCard(summary, state) }
    item { SmartInsightsCard(summary, state) }

    item {
        SectionCard(
            title = stringResource(R.string.cash_in_vs_out),
            subtitle = stringResource(if (state.type == PeriodType.YEAR) R.string.per_month_tap else R.string.per_day_tap),
        ) {
            ChartLegend(listOf(stringResource(R.string.cash_in) to cashInColor, stringResource(R.string.cash_out) to cashOutColor))
            Spacer(Modifier.height(12.dp))
            CashFlowChart(
                points = summary.series,
                inColor = cashInColor,
                outColor = cashOutColor,
                valueFormatter = { formatMoney(it, currency) },
                axisFormatter = { formatCompact(it, currency) },
                tooltipLabel = seriesLabeler(state.type, summary.period.start),
            )
        }
    }

    if (summary.runningNet.size > 1) {
        item {
            val slotLabel = seriesLabeler(state.type, summary.period.start)
            val byEnd = stringResource(R.string.by_end_of)
            val byDate = stringResource(R.string.by_date)
            SectionCard(
                title = stringResource(R.string.running_net),
                subtitle = stringResource(R.string.running_net_subtitle, state.type.word()),
            ) {
                LineChart(
                    points = summary.runningNet,
                    valueFormatter = { signed(it, currency) },
                    axisFormatter = { formatCompact(it, currency) },
                    tooltipLabel = { index -> (if (state.type == PeriodType.YEAR) byEnd else byDate).format(slotLabel(index)) },
                )
            }
        }
    }

    if (summary.recurringIncome.isNotEmpty() || summary.recurringBills.isNotEmpty()) {
        item { RecurringCard(summary, state.today, currency, onLogRecurring) }
    }

    if (summary.incomeBySource.isNotEmpty()) {
        item {
            SectionCard(title = stringResource(R.string.money_comes_from), subtitle = stringResource(R.string.income_this_period, state.type.word())) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    summary.incomeBySource.forEach { source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(source.category, size = 40.dp, iconSize = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(source.category.title(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(formatMoney(source.total, currency), style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RoundedProgressBar(source.share, source.category.color, MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.weight(1f))
                                    Spacer(Modifier.width(10.dp))
                                    Text(percent(source.share.toDouble()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    item {
        val subtitle = when {
            summary.completedMonthsWithData == 0 -> stringResource(R.string.monthly_totals)
            summary.monthsOverspent == 0 -> stringResource(R.string.within_income_all)
            else -> stringResource(R.string.overspent_months, summary.monthsOverspent, summary.completedMonthsWithData)
        }
        SectionCard(title = stringResource(R.string.last_6_months), subtitle = subtitle) {
            ChartLegend(listOf(stringResource(R.string.cash_in) to cashInColor, stringResource(R.string.cash_out) to cashOutColor))
            Spacer(Modifier.height(12.dp))
            CashFlowChart(
                points = summary.monthly,
                inColor = cashInColor,
                outColor = cashOutColor,
                valueFormatter = { formatMoney(it, currency) },
                axisFormatter = { formatCompact(it, currency) },
            )
        }
    }
}

@Composable
private fun CashSummaryCard(summary: CashFlowSummary, state: AnalyticsUiState) {
    val currency = state.currency
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            Figure(stringResource(R.string.cash_in), "+" + formatMoney(animatedAmount(summary.cashIn), currency), cashInColor, Modifier.weight(1f))
            Figure(stringResource(R.string.cash_out), "−" + formatMoney(animatedAmount(summary.cashOut), currency), cashOutColor, Modifier.weight(1f))
        }
        if (summary.cashIn == 0.0) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.no_money_in_period, state.type.word()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(if (summary.net >= 0) R.string.left_over else R.string.short_by), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(abs(animatedAmount(summary.net)), currency),
            style = MaterialTheme.typography.displaySmall,
            color = if (summary.net >= 0) MaterialTheme.colorScheme.onSurface else Increase,
        )
        summary.savingsRate?.let { rate ->
            Spacer(Modifier.height(8.dp))
            val rateText = if (rate >= 0) stringResource(R.string.kept_rate, percent(rate)) else stringResource(R.string.overspent_rate, percent(-rate))
            Text(rateText, style = MaterialTheme.typography.bodyMedium)
            summary.previousSavingsRate?.let { prev ->
                val delta = ((rate - prev) * 100).roundToInt()
                val text = when {
                    delta > 0 -> stringResource(R.string.rate_up, delta, state.comparedToText(), percent(prev))
                    delta < 0 -> stringResource(R.string.rate_down, -delta, state.comparedToText(), percent(prev))
                    else -> stringResource(R.string.rate_same, state.comparedToText())
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (delta != 0) {
                        Icon(
                            if (delta > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                            contentDescription = null,
                            tint = if (delta > 0) Decrease else Increase,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, swatch: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(swatch, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmartInsightsCard(summary: CashFlowSummary, state: AnalyticsUiState) {
    val currency = state.currency
    val outlook = summary.outlook
    SectionCard(title = stringResource(R.string.smart_insights)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (outlook != null) {
                val parts = buildList {
                    if (outlook.expectedIncome > 0) add(stringResource(R.string.expected_income_pending, formatMoney(outlook.expectedIncome, currency)))
                    if (outlook.expectedBills > 0) add(stringResource(R.string.bills_pending, formatMoney(outlook.expectedBills, currency)))
                }
                val both = stringResource(R.string.pending_both, parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
                val pending = if (parts.size == 2) both else parts.firstOrNull().orEmpty()
                val days = pluralStringResource(R.plurals.days_count, outlook.remainingDays, outlook.remainingDays)
                if (outlook.safeToSpendPerDay >= 0) {
                    InsightRow(
                        Icons.Rounded.Savings, Decrease,
                        stringResource(R.string.safe_to_spend_day, formatMoney(outlook.safeToSpendPerDay, currency)),
                        if (pending.isNotEmpty()) stringResource(R.string.safe_detail_pending, days, pending) else stringResource(R.string.safe_detail, days),
                    )
                } else {
                    InsightRow(
                        Icons.Rounded.WarningAmber, Increase,
                        stringResource(R.string.short_month, formatMoney(-outlook.safeToSpendPerDay * outlook.remainingDays, currency)),
                        if (pending.isNotEmpty()) stringResource(R.string.short_detail_pending, days, pending) else stringResource(R.string.short_detail, days),
                    )
                }
                InsightRow(
                    Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.primary,
                    stringResource(R.string.projected, shortDateText(summary.period.end), signed(outlook.projectedNet, currency)),
                    stringResource(R.string.projected_detail, formatMoney(outlook.projectedIn, currency), formatMoney(outlook.projectedOut, currency)),
                )
            }
            if (summary.completedMonthsWithData >= 2) {
                if (summary.monthsOverspent == 0) {
                    InsightRow(Icons.AutoMirrored.Rounded.TrendingUp, Decrease, stringResource(R.string.within_every_month), stringResource(R.string.within_every_month_detail, summary.completedMonthsWithData))
                } else {
                    InsightRow(
                        Icons.AutoMirrored.Rounded.TrendingDown, Increase,
                        stringResource(R.string.overspent_in, summary.monthsOverspent, summary.completedMonthsWithData),
                        stringResource(R.string.overspent_in_detail),
                    )
                }
            }
            val income = summary.averageMonthlyIncome
            if (summary.recurringBillsPerMonth > 0 && income != null && income > 0) {
                InsightRow(
                    Icons.Rounded.EventRepeat, MaterialTheme.colorScheme.tertiary,
                    stringResource(R.string.bills_share, percent(summary.recurringBillsPerMonth / income)),
                    pluralStringResource(R.plurals.bills_share_detail, summary.recurringBills.size, formatMoney(summary.recurringBillsPerMonth, currency), summary.recurringBills.size),
                )
            }
            summary.incomeBySource.firstOrNull()?.takeIf { summary.incomeBySource.size > 1 }?.let { top ->
                InsightRow(
                    Icons.Rounded.Insights, MaterialTheme.colorScheme.secondary,
                    stringResource(R.string.top_source, top.category.title(), percent(top.share.toDouble())),
                    pluralStringResource(R.plurals.other_sources, summary.incomeBySource.size - 1, summary.incomeBySource.size - 1, state.type.word()),
                )
            }
        }
    }
}

@Composable
private fun RecurringCard(summary: CashFlowSummary, today: LocalDate, currency: AppCurrency, onLog: (Prefill) -> Unit) {
    val items = (summary.recurringIncome + summary.recurringBills).sortedBy { it.nextDate }
    SectionCard(title = stringResource(R.string.regular_title), subtitle = stringResource(R.string.regular_subtitle)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { item ->
                RecurringRow(item, today, currency) { onLog(Prefill(item.typicalAmount, item.category, item.name.takeIf { it != item.category.label }.orEmpty())) }
            }
        }
    }
}

@Composable
private fun RecurringRow(item: RecurringItem, today: LocalDate, currency: AppCurrency, onLog: () -> Unit) {
    val income = item.type == EntryType.INCOME
    val days = ChronoUnit.DAYS.between(today, item.nextDate)
    val next = shortDateText(item.nextDate)
    val whenText = when {
        days <= 0L -> stringResource(R.string.expected_today)
        days == 1L -> stringResource(R.string.tomorrow)
        days < 14 -> stringResource(R.string.in_days, days.toInt(), next)
        else -> stringResource(R.string.next_on, next)
    }
    // A recurring item with no note is named after its category: show that in the app's language.
    val name = if (item.name == item.category.label) item.category.title() else item.name
    val dayText = if (AppLocale.isKhmer(appLocale())) item.dayOfMonth.toString() else ordinal(item.dayOfMonth)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onLog)
            .padding(vertical = 6.dp)
            .testTag("recurring_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(item.category, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.around_day, dayText, whenText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (income) "+" else "−") + formatMoney(item.typicalAmount, currency),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (income) CashInText else MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            Icons.Rounded.AddCircleOutline,
            contentDescription = stringResource(R.string.log_now, name),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp).size(20.dp),
        )
    }
}

@Composable
internal fun InsightRow(icon: ImageVector, tint: Color, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatementsCard(state: AnalyticsUiState, onRemoveSampleData: () -> Unit) {
    val currency = state.currency
    val balance = state.bankBalance
    SectionCard(
        title = pluralStringResource(R.plurals.statements_title, state.statements.size),
        subtitle = balance?.let { stringResource(R.string.bank_balance_on, formatMoney(it.closingBalance ?: 0.0, currency), dateFormatter(R.string.pattern_date_year).format(it.periodEnd)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            state.statements.forEach { StatementBlock(it, currency, onRemoveSampleData) }
        }
    }
}

@Composable
private fun StatementBlock(status: StatementStatus, currency: AppCurrency, onRemoveSampleData: () -> Unit) {
    val st = status.statement
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(st.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${shortDateText(st.periodStart)} – ${dateFormatter(R.string.pattern_date_year).format(st.periodEnd)} · " +
                        pluralStringResource(R.plurals.transactions_count, st.transactionCount, st.transactionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MatchBadge(status.matches)
        }
        Spacer(Modifier.height(12.dp))
        StatementLine(stringResource(R.string.on_statement), "+" + formatMoney(st.totalIn, currency), "−" + formatMoney(st.totalOut, currency))
        StatementLine(stringResource(R.string.in_cash_flow), "+" + formatMoney(status.appIn, currency), "−" + formatMoney(status.appOut, currency))
        if (st.openingBalance != null && st.closingBalance != null) {
            Row(Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                Text(
                    "${formatMoney(st.openingBalance, currency)} → ${formatMoney(st.closingBalance, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!status.matches) {
            val reasons = buildList {
                if (status.otherCount > 0) {
                    val sample = if (status.sampleCount > 0) " " + stringResource(R.string.sample_note, status.sampleCount) else ""
                    add(pluralStringResource(R.plurals.other_entries, status.otherCount, status.otherCount, sample, moneyPair(status.otherIn, status.otherOut, currency)))
                }
                if (status.missingIn > 0.005 || status.missingOut > 0.005) {
                    add(stringResource(R.string.not_imported, moneyPair(status.missingIn, status.missingOut, currency)))
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.why_different), style = MaterialTheme.typography.labelLarge)
                reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (status.sampleCount > 0) {
                    TextButton(onClick = onRemoveSampleData, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.remove_sample)) }
                }
            }
        }
    }
}

@Composable
private fun moneyPair(inValue: Double, outValue: Double, currency: AppCurrency): String {
    val inText = stringResource(R.string.history_in, formatMoney(inValue, currency))
    val outText = stringResource(R.string.history_out, formatMoney(outValue, currency))
    return buildList {
        if (inValue > 0.005) add(inText)
        if (outValue > 0.005) add(outText)
    }.joinToString(", ").ifEmpty { formatMoney(0.0, currency) }
}

@Composable
private fun StatementLine(label: String, inText: String, outText: String) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(inText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = CashInText, modifier = Modifier.weight(1f))
        Text(outText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun MatchBadge(matches: Boolean) {
    val tint = if (matches) Decrease else MaterialTheme.colorScheme.tertiary
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (matches) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(if (matches) R.string.matches else R.string.differs), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
