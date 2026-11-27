package com.expensetracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.ui.theme.LocalDarkTheme
import com.expensetracker.ui.theme.card
import com.expensetracker.ui.theme.heroBrush
import com.expensetracker.ui.theme.onHero
import com.expensetracker.util.sanitizeAmountInput

/** First launch: pick a currency (pre-selected from the phone's region) and an optional budget. */
@Composable
fun OnboardingScreen(onFinish: (AppCurrency, Double) -> Unit) {
    var currency by rememberSaveable { mutableStateOf(AppStateViewModel.guessCurrency()) }
    var budget by rememberSaveable { mutableStateOf("") }
    val dark = LocalDarkTheme.current
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.background, modifier = Modifier.fillMaxSize().testTag("onboarding")) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .testTag("onboarding_scroll")
                .padding(horizontal = 20.dp),
        ) {
            LanguageToggle(Modifier.align(Alignment.End).padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(colors.heroBrush(dark))
                    .padding(24.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.onHero(dark).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Wallet, contentDescription = null, tint = colors.onHero(dark), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall, color = colors.onHero(dark))
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.welcome_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHero(dark).copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Feature(Icons.Rounded.UploadFile, stringResource(R.string.welcome_feature_add))
            Feature(Icons.Rounded.Insights, stringResource(R.string.welcome_feature_insights))
            Feature(Icons.Rounded.Lock, stringResource(R.string.welcome_feature_private))

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.your_currency), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.currency_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCurrency.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c -> CurrencyChip(c, c == currency, { currency = c }, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.monthly_budget_optional), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = budget,
                onValueChange = { budget = sanitizeAmountInput(it, currency.decimals) },
                singleLine = true,
                placeholder = { Text(if (currency.decimals == 0) "2000000" else "1500") },
                prefix = if (currency.symbolFirst) ({ Text(currency.symbol) }) else null,
                suffix = if (!currency.symbolFirst) ({ Text(currency.symbol) }) else null,
                supportingText = { Text(stringResource(R.string.budget_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_budget"),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onFinish(currency, budget.toDoubleOrNull() ?: 0.0) },
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("onboarding_start"),
            ) { Text(stringResource(R.string.get_started), style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CurrencyChip(currency: AppCurrency, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primaryContainer else colors.card, label = "chip")
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag("currency_${currency.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(currency.symbol, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onPrimaryContainer else colors.onSurface)
        Text(
            currency.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}


/** English | ខ្មែរ, shown as two pills; switching recreates the screen in the new language. */
@Composable
private fun LanguageToggle(modifier: Modifier = Modifier) {
    val khmer = AppLocale.isKhmer(appLocale())
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        listOf(AppLanguage.ENGLISH to "English", AppLanguage.KHMER to "ខ្មែរ").forEach { (language, name) ->
            val selected = (language == AppLanguage.KHMER) == khmer
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { if (!selected) AppLocale.select(language) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("language_${language.name.lowercase()}"),
            )
        }
    }
}
