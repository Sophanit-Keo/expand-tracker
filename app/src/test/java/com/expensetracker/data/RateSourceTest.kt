package com.expensetracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/** Parses real responses (trimmed) from both rate services. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RateSourceTest {
    private fun fixture(name: String) = javaClass.getResource("/rates/$name")!!.readText()

    @Test
    fun readsExchangeRateApi() {
        val rates = OnlineRateSource.parseExchangeRateApi(fixture("er-api-usd.json"))
        assertEquals(AppCurrency.USD, rates.base)
        assertEquals(4067.555337, rates.perBase.getValue(AppCurrency.KHR), 1e-9)
        assertEquals(34.044181, rates.perBase.getValue(AppCurrency.THB), 1e-9)
        assertEquals(1.0, rates.perBase.getValue(AppCurrency.USD), 0.0)
        assertEquals(AppCurrency.entries.toSet(), rates.perBase.keys) // all 12, AED ignored
        assertEquals("ExchangeRate-API", rates.provider)
    }

    @Test
    fun readsCurrencyApi() {
        val rates = OnlineRateSource.parseCurrencyApi(fixture("currency-api-usd.json"), AppCurrency.USD)
        assertEquals(4069.01022354, rates.perBase.getValue(AppCurrency.KHR), 1e-9)
        assertEquals(25882.88475209, rates.perBase.getValue(AppCurrency.VND), 1e-9)
        assertEquals("currency-api", rates.provider)
    }

    @Test
    fun rejectsErrorsAndEmptyAnswers() {
        assertThrows(IOException::class.java) { OnlineRateSource.parseExchangeRateApi("""{"result":"error","error-type":"unsupported-code"}""") }
        assertThrows(IOException::class.java) { OnlineRateSource.parseExchangeRateApi("""{"result":"success","base_code":"USD","rates":{"USD":1}}""") }
        assertThrows(Exception::class.java) { OnlineRateSource.parseCurrencyApi("<html>", AppCurrency.USD) }
    }
}
