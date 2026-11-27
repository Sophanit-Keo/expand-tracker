package com.expensetracker.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.data.ThemeMode
import com.expensetracker.domain.PeriodType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/** Translated names for the app's built-in values. The enums' own labels stay English (CSV export). */
@get:StringRes
val Category.labelRes: Int
    get() = when (this) {
        Category.FOOD -> R.string.cat_food
        Category.GROCERIES -> R.string.cat_groceries
        Category.TRANSPORT -> R.string.cat_transport
        Category.SHOPPING -> R.string.cat_shopping
        Category.BILLS -> R.string.cat_bills
        Category.HOUSING -> R.string.cat_housing
        Category.ENTERTAINMENT -> R.string.cat_entertainment
        Category.HEALTH -> R.string.cat_health
        Category.EDUCATION -> R.string.cat_education
        Category.TRAVEL -> R.string.cat_travel
        Category.GIFTS -> R.string.cat_gifts
        Category.OTHER -> R.string.cat_other
        Category.SALARY -> R.string.cat_salary
        Category.BUSINESS -> R.string.cat_business
        Category.INVESTMENT -> R.string.cat_investment
        Category.REFUND -> R.string.cat_refund
        Category.GIFT_RECEIVED -> R.string.cat_gift_received
        Category.TRANSFER_IN -> R.string.cat_transfer_in
        Category.OTHER_INCOME -> R.string.cat_other_income
    }

/** Short form for small tiles ("Food" rather than "Food & Dining"). */
@get:StringRes
val Category.shortLabelRes: Int
    get() = when (this) {
        Category.FOOD -> R.string.cat_food_short
        Category.INVESTMENT -> R.string.cat_investment_short
        Category.GIFT_RECEIVED -> R.string.cat_gift_received_short
        Category.OTHER_INCOME -> R.string.cat_other_income_short
        else -> labelRes
    }

@Composable @ReadOnlyComposable
fun Category.title(): String = stringResource(labelRes)

@Composable @ReadOnlyComposable
fun Category.shortTitle(): String = stringResource(shortLabelRes)

/** The note, or the category name when there's no note. */
@Composable @ReadOnlyComposable
fun Expense.displayTitle(): String = note.ifBlank { category.title() }

fun Expense.titleText(): UiText = if (note.isNotBlank()) UiText.Plain(note) else UiText.Res(category.labelRes)

@Composable @ReadOnlyComposable
fun AppCurrency.title(): String = stringResource(
    when (this) {
        AppCurrency.USD -> R.string.cur_usd
        AppCurrency.EUR -> R.string.cur_eur
        AppCurrency.GBP -> R.string.cur_gbp
        AppCurrency.KHR -> R.string.cur_khr
        AppCurrency.THB -> R.string.cur_thb
        AppCurrency.VND -> R.string.cur_vnd
        AppCurrency.SGD -> R.string.cur_sgd
        AppCurrency.JPY -> R.string.cur_jpy
        AppCurrency.KRW -> R.string.cur_krw
        AppCurrency.CNY -> R.string.cur_cny
        AppCurrency.INR -> R.string.cur_inr
        AppCurrency.AUD -> R.string.cur_aud
    },
)

@Composable @ReadOnlyComposable
fun ThemeMode.title(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable @ReadOnlyComposable
fun AppLanguage.title(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.KHMER -> R.string.language_khmer
    },
)

/** "Week", "Month", "Year" (segmented buttons). */
@Composable @ReadOnlyComposable
fun PeriodType.title(): String = stringResource(
    when (this) {
        PeriodType.WEEK -> R.string.period_week
        PeriodType.MONTH -> R.string.period_month
        PeriodType.YEAR -> R.string.period_year
    },
)

/** "week", "month", "year" inside a sentence. */
@Composable @ReadOnlyComposable
fun PeriodType.word(): String = stringResource(
    when (this) {
        PeriodType.WEEK -> R.string.period_week_word
        PeriodType.MONTH -> R.string.period_month_word
        PeriodType.YEAR -> R.string.period_year_word
    },
)

/** The language the screen is shown in. */
@Composable @ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun dateFormatter(@StringRes pattern: Int): DateTimeFormatter {
    val p = stringResource(pattern)
    val locale = appLocale()
    return remember(p, locale) { DateTimeFormatter.ofPattern(p, locale) }
}

/** "Today", "Yesterday", "Mon, Nov 24" (or with the year when it isn't this year). */
@Composable
fun friendlyDateText(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val day = dateFormatter(R.string.pattern_day)
    val dayYear = dateFormatter(R.string.pattern_day_year)
    return when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> if (date.year == today.year) day.format(date) else dayYear.format(date)
    }
}

/** "Nov 24" / "24 វិច្ឆិកា". */
@Composable
fun shortDateText(date: LocalDate): String = dateFormatter(R.string.pattern_short_day).format(date)

/** "November 2026" / "វិច្ឆិកា 2026". */
@Composable
fun monthYearText(date: TemporalAccessor): String = dateFormatter(R.string.pattern_month_year).format(date)
