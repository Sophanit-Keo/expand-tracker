package com.expensetracker.ui.analytics

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.EntryType
import com.expensetracker.domain.CashFlowSummary
import com.expensetracker.domain.PeriodType
import com.expensetracker.domain.RecurringItem
import com.expensetracker.domain.StatementStatus
import com.expensetracker.ui.charts.CashFlowChart
import com.expensetracker.ui.charts.ChartLegend
import com.expensetracker.ui.charts.LineChart
import com.expensetracker.ui.components.CategoryIcon
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.RoundedProgressBar
import com.expensetracker.ui.components.SectionCard
import com.expensetracker.ui.components.color
import com.expensetracker.ui.theme.CashInDark
import com.expensetracker.ui.theme.CashInLight
import com.expensetracker.ui.theme.CashInText
import com.expensetracker.ui.theme.CashOutDark
import com.expensetracker.ui.theme.CashOutLight
import com.expensetracker.ui.theme.Decrease
import com.expensetracker.ui.theme.Increase
import com.expensetracker.ui.theme.LocalDarkTheme
import com.expensetracker.util.formatCompact
import com.expensetracker.util.formatMoney
import com.expensetracker.util.shortDate
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
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
) {
    val summary = state.cashFlow ?: return
    val currency = state.currency
    val periodWord = state.type.label.lowercase()

    // Bank statements first: they're the reference the cash flow should agree with.
    if (state.statements.isNotEmpty()) {
        item { StatementsCard(state, onRemoveSampleData) }
    }

    if (!summary.hasIncome) {
        item {
            SectionCard {
                EmptyState(
                    icon = Icons.Rounded.Insights,
                    title = "Add your income to see cash flow",
                    message = "Record your salary and other money coming in (or import it from a bank statement) to see how much you keep, what's safe to spend, and which bills come back every month.",
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = onAddIncome) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add income")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onImport) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import bank statement")
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
            title = "Cash in vs cash out",
            subtitle = if (state.type == PeriodType.YEAR) "Per month · tap a column" else "Per day · tap a column",
        ) {
            ChartLegend(listOf("Cash in" to cashInColor, "Cash out" to cashOutColor))
            Spacer(Modifier.height(12.dp))
            CashFlowChart(
                points = summary.series,
                inColor = cashInColor,
                outColor = cashOutColor,
                valueFormatter = { formatMoney(it, currency) },
                axisFormatter = { formatCompact(it, currency) },
                tooltipLabel = { index ->
                    when (state.type) {
                        PeriodType.YEAR -> summary.period.start.plusMonths(index.toLong()).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        else -> shortDate(summary.period.start.plusDays(index.toLong()))
                    }
                },
            )
        }
    }

    if (summary.runningNet.size > 1) {
        item {
            SectionCard(
                title = "Running net",
                subtitle = "How far ahead (above 0) or behind you are as the $periodWord goes on",
            ) {
                LineChart(
                    points = summary.runningNet,
                    valueFormatter = { signed(it, currency) },
                    axisFormatter = { formatCompact(it, currency) },
                    tooltipLabel = { index ->
                        when (state.type) {
                            PeriodType.YEAR -> "By end of " + summary.period.start.plusMonths(index.toLong()).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            else -> "By " + shortDate(summary.period.start.plusDays(index.toLong()))
                        }
                    },
                )
            }
        }
    }

    if (summary.recurringIncome.isNotEmpty() || summary.recurringBills.isNotEmpty()) {
        item { RecurringCard(summary, state.today, currency) }
    }

    if (summary.incomeBySource.isNotEmpty()) {
        item {
            SectionCard(title = "Where your money comes from", subtitle = "Income this $periodWord") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    summary.incomeBySource.forEach { source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(source.category, size = 40.dp, iconSize = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(source.category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
            summary.completedMonthsWithData == 0 -> "Monthly totals"
            summary.monthsOverspent == 0 -> "You stayed within your income in every completed month"
            else -> "Spent more than came in during ${summary.monthsOverspent} of ${summary.completedMonthsWithData} completed months"
        }
        SectionCard(title = "Last 6 months", subtitle = subtitle) {
            ChartLegend(listOf("Cash in" to cashInColor, "Cash out" to cashOutColor))
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
            Figure("Cash in", "+" + formatMoney(summary.cashIn, currency), cashInColor, Modifier.weight(1f))
            Figure("Cash out", "−" + formatMoney(summary.cashOut, currency), cashOutColor, Modifier.weight(1f))
        }
        if (summary.cashIn == 0.0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No money in recorded for this ${state.type.label.lowercase()}. Use ‹ to see earlier periods, or add income.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(if (summary.net >= 0) "Left over" else "Short by", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(abs(summary.net), currency),
            style = MaterialTheme.typography.displaySmall,
            color = if (summary.net >= 0) MaterialTheme.colorScheme.onSurface else Increase,
        )
        summary.savingsRate?.let { rate ->
            Spacer(Modifier.height(8.dp))
            val rateText = if (rate >= 0) "You kept ${percent(rate)} of what came in" else "You spent ${percent(-rate)} more than came in"
            Text(rateText, style = MaterialTheme.typography.bodyMedium)
            summary.previousSavingsRate?.let { prev ->
                val delta = ((rate - prev) * 100).roundToInt()
                val text = when {
                    delta > 0 -> "Up $delta points from the ${state.comparedTo} (${percent(prev)})"
                    delta < 0 -> "Down ${-delta} points from the ${state.comparedTo} (${percent(prev)})"
                    else -> "Same as the ${state.comparedTo}"
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
    SectionCard(title = "Smart insights") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (outlook != null) {
                val pending = buildList {
                    if (outlook.expectedIncome > 0) add("${formatMoney(outlook.expectedIncome, currency)} income still expected")
                    if (outlook.expectedBills > 0) add("${formatMoney(outlook.expectedBills, currency)} in bills still due")
                }.joinToString(" and ")
                val days = "${outlook.remainingDays} ${if (outlook.remainingDays == 1) "day" else "days"}"
                if (outlook.safeToSpendPerDay >= 0) {
                    InsightRow(
                        Icons.Rounded.Savings, Decrease,
                        "Safe to spend: ${formatMoney(outlook.safeToSpendPerDay, currency)} a day",
                        "For the next $days to end the month even" + if (pending.isNotEmpty()) ", counting $pending." else ".",
                    )
                } else {
                    InsightRow(
                        Icons.Rounded.WarningAmber, Increase,
                        "Short by ${formatMoney(-outlook.safeToSpendPerDay * outlook.remainingDays, currency)} this month",
                        "More has gone out than will come in" + (if (pending.isNotEmpty()) ", even with $pending" else "") + ". Keep spending low for the next $days.",
                    )
                }
                InsightRow(
                    Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.primary,
                    "By ${shortDate(summary.period.end)}: ${signed(outlook.projectedNet, currency)} expected",
                    "About ${formatMoney(outlook.projectedIn, currency)} in and ${formatMoney(outlook.projectedOut, currency)} out at your current pace.",
                )
            }
            if (summary.completedMonthsWithData >= 2) {
                if (summary.monthsOverspent == 0) {
                    InsightRow(Icons.AutoMirrored.Rounded.TrendingUp, Decrease, "Within your income every month", "None of the last ${summary.completedMonthsWithData} completed months spent more than came in.")
                } else {
                    InsightRow(
                        Icons.AutoMirrored.Rounded.TrendingDown, Increase,
                        "Overspent in ${summary.monthsOverspent} of the last ${summary.completedMonthsWithData} months",
                        "Those months spent more than came in. Check them in the 6-month chart below.",
                    )
                }
            }
            val income = summary.averageMonthlyIncome
            if (summary.recurringBillsPerMonth > 0 && income != null && income > 0) {
                InsightRow(
                    Icons.Rounded.EventRepeat, MaterialTheme.colorScheme.tertiary,
                    "Regular bills take ${percent(summary.recurringBillsPerMonth / income)} of your income",
                    "${formatMoney(summary.recurringBillsPerMonth, currency)} a month across ${summary.recurringBills.size} recurring ${if (summary.recurringBills.size == 1) "payment" else "payments"}.",
                )
            }
            summary.incomeBySource.firstOrNull()?.takeIf { summary.incomeBySource.size > 1 }?.let { top ->
                InsightRow(
                    Icons.Rounded.Insights, MaterialTheme.colorScheme.secondary,
                    "${top.category.label} brings in ${percent(top.share.toDouble())} of your income",
                    "The rest comes from ${summary.incomeBySource.size - 1} other ${if (summary.incomeBySource.size == 2) "source" else "sources"} this ${state.type.label.lowercase()}.",
                )
            }
        }
    }
}

@Composable
private fun RecurringCard(summary: CashFlowSummary, today: LocalDate, currency: AppCurrency) {
    val items = (summary.recurringIncome + summary.recurringBills).sortedBy { it.nextDate }
    SectionCard(title = "Regular income & bills", subtitle = "Spotted from the last 4 months · sorted by next date") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { RecurringRow(it, today, currency) }
        }
    }
}

@Composable
private fun RecurringRow(item: RecurringItem, today: LocalDate, currency: AppCurrency) {
    val income = item.type == EntryType.INCOME
    val days = ChronoUnit.DAYS.between(today, item.nextDate)
    val whenText = when {
        days <= 0L -> "Expected today"
        days == 1L -> "Tomorrow"
        days < 14 -> "In $days days · ${shortDate(item.nextDate)}"
        else -> "Next on ${shortDate(item.nextDate)}"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(item.category, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Around the ${ordinal(item.dayOfMonth)} · $whenText",
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
        title = if (state.statements.size == 1) "Bank statement" else "Bank statements",
        subtitle = balance?.let { "Bank balance ${formatMoney(it.closingBalance ?: 0.0, currency)} on ${shortDate(it.periodEnd)}, ${it.periodEnd.year}" },
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
                    "${shortDate(st.periodStart)} – ${shortDate(st.periodEnd)}, ${st.periodEnd.year} · ${st.transactionCount} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MatchBadge(status.matches)
        }
        Spacer(Modifier.height(12.dp))
        StatementLine("On the statement", "+" + formatMoney(st.totalIn, currency), "−" + formatMoney(st.totalOut, currency))
        StatementLine("In your cash flow", "+" + formatMoney(status.appIn, currency), "−" + formatMoney(status.appOut, currency))
        if (st.openingBalance != null && st.closingBalance != null) {
            Row(Modifier.padding(top = 6.dp)) {
                Text("Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
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
                    val sample = if (status.sampleCount > 0) " (${status.sampleCount} are sample data)" else ""
                    add("${status.otherCount} other ${if (status.otherCount == 1) "entry is" else "entries are"} in these dates$sample: " + moneyPair(status.otherIn, status.otherOut, currency) + ".")
                }
                if (status.missingIn > 0.005 || status.missingOut > 0.005) {
                    add("Not imported from the statement (unticked or deleted): " + moneyPair(status.missingIn, status.missingOut, currency) + ".")
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
                Text("Why it's different", style = MaterialTheme.typography.labelLarge)
                reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (status.sampleCount > 0) {
                    TextButton(onClick = onRemoveSampleData, contentPadding = PaddingValues(0.dp)) { Text("Remove sample data") }
                }
            }
        }
    }
}

private fun moneyPair(inValue: Double, outValue: Double, currency: AppCurrency) = buildList {
    if (inValue > 0.005) add("+" + formatMoney(inValue, currency) + " in")
    if (outValue > 0.005) add("−" + formatMoney(outValue, currency) + " out")
}.joinToString(", ").ifEmpty { formatMoney(0.0, currency) }

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
        Text(if (matches) "Matches" else "Differs", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
