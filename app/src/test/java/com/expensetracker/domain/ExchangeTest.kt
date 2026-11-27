package com.expensetracker.domain

import com.expensetracker.data.AppCurrency.EUR
import com.expensetracker.data.AppCurrency.KHR
import com.expensetracker.data.AppCurrency.THB
import com.expensetracker.data.AppCurrency.USD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeTest {
    @Test
    fun ratesReadTheUsualWayRound() {
        // Main USD, paying in riel: "1 USD = 4,100 KHR", not "1 KHR = 0.000244 USD".
        assertFalse(Exchange.foreignFirst(KHR, USD))
        assertEquals(RateQuote(USD, KHR, 4100.0), Exchange.quote(KHR, USD, 1 / 4100.0).let { it.copy(value = Math.round(it.value).toDouble()) })
        assertEquals(1 / 4100.0, Exchange.rateFromQuote(KHR, USD, 4100.0)!!, 1e-15)
        // Main KHR, paying in dollars: also "1 USD = 4,100 KHR".
        assertTrue(Exchange.foreignFirst(USD, KHR))
        assertEquals(RateQuote(USD, KHR, 4100.0), Exchange.quote(USD, KHR, 4100.0))
        assertEquals(4100.0, Exchange.rateFromQuote(USD, KHR, 4100.0)!!, 0.0)
        // Euro vs dollar: the euro is worth more.
        assertEquals("1 EUR = 1.085 USD", Exchange.describe(EUR, USD, 1.085))
        assertEquals("1 USD = 4,100 KHR", Exchange.describe(KHR, USD, 1 / 4100.0))
        assertNull(Exchange.rateFromQuote(KHR, USD, 0.0))
    }

    @Test
    fun convertsToTheMainCurrency() {
        val rates = mapOf(KHR to 1 / 4000.0)
        assertEquals(3.0, Exchange.toMain(12_000.0, KHR, USD, rates)!!, 1e-12)
        assertEquals(5.0, Exchange.toMain(5.0, USD, USD, rates)!!, 0.0)
        assertNull(Exchange.toMain(100.0, THB, USD, rates))
    }

    @Test
    fun ratesFollowANewMainCurrency() {
        val rates = mapOf(KHR to 1 / 4000.0, THB to 1 / 36.0)
        val inRiel = Exchange.rebase(rates, USD, KHR)
        assertEquals(setOf(USD, THB), inRiel.keys)
        assertEquals(4000.0, inRiel.getValue(USD), 1e-9)
        assertEquals(4000.0 / 36.0, inRiel.getValue(THB), 1e-9)
        // And back again.
        val back = Exchange.rebase(inRiel, KHR, USD)
        assertEquals(1 / 4000.0, back.getValue(KHR), 1e-15)
        assertEquals(1 / 36.0, back.getValue(THB), 1e-15)
        // No rate between the old and the new main currency: nothing can be converted.
        assertTrue(Exchange.rebase(rates, USD, EUR).isEmpty())
    }

    @Test
    fun ratesAreStoredAndReadBack() {
        val rates = mapOf(KHR to 1 / 4100.0, EUR to 1.085)
        assertEquals(rates, Exchange.decode(Exchange.encode(rates)))
        assertTrue(Exchange.decode(null).isEmpty())
        assertEquals(mapOf(EUR to 1.1), Exchange.decode("EUR=1.1;XXX=2;KHR=-1;THB=abc"))
        assertEquals("4100", Exchange.rateToInput(4100.0))
        assertEquals("1.085", Exchange.rateToInput(1.085))
        assertEquals("4,100", Exchange.formatRate(4100.0))
    }
}
