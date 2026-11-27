package com.expensetracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RateStatus { IDLE, UPDATING, FAILED }

/**
 * Keeps saved exchange rates current: downloads today's rates for the currencies you use, at most
 * twice a day when the app opens, or right away when you add a currency or tap "Update now".
 */
class RatesUpdater(
    private val settings: SettingsRepository,
    private val source: () -> RateSource,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(RateStatus.IDLE)
    val status: StateFlow<RateStatus> = _status.asStateFlow()

    /** Updates the saved rates, plus [extra] currencies that have none yet. Returns false if it failed. */
    suspend fun update(extra: Set<AppCurrency> = emptySet()): Boolean = mutex.withLock {
        val prefs = settings.settings.first()
        val wanted = prefs.rates.keys + extra - prefs.currency
        if (wanted.isEmpty()) return true
        _status.value = RateStatus.UPDATING
        try {
            val market = source().latest(prefs.currency)
            if (market.base != prefs.currency) throw IllegalStateException("Wrong base")
            // Market rates say how much of each currency one unit of the main currency buys; saved
            // rates are the other way round: main-currency units per one unit of each currency.
            val rates = wanted.mapNotNull { c -> market.perBase[c]?.let { c to 1 / it } }.toMap()
            settings.saveOnlineRates(prefs.currency, rates, now(), market.provider, market.providerUrl)
            _status.value = RateStatus.IDLE
            true
        } catch (e: CancellationException) {
            _status.value = RateStatus.IDLE
            throw e
        } catch (e: Exception) {
            _status.value = RateStatus.FAILED
            false
        }
    }

    /** Updates when automatic updates are on, some rates are saved, and they're older than [MAX_AGE_MS]. */
    suspend fun updateIfDue(): Boolean {
        val prefs = settings.settings.first()
        if (!prefs.autoRates || prefs.rates.isEmpty()) return true
        if (now() - prefs.ratesUpdatedAt < MAX_AGE_MS) return true
        return update()
    }

    companion object {
        const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
    }
}
