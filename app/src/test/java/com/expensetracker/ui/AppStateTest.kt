package com.expensetracker.ui

import com.expensetracker.data.AppCurrency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppStateTest {
    @Test
    fun currencyIsGuessedFromThePhonesRegion() {
        assertEquals(AppCurrency.KHR, AppStateViewModel.guessCurrency(Locale.Builder().setLanguage("km").setRegion("KH").build()))
        assertEquals(AppCurrency.THB, AppStateViewModel.guessCurrency(Locale.Builder().setLanguage("th").setRegion("TH").build()))
        assertEquals(AppCurrency.EUR, AppStateViewModel.guessCurrency(Locale.FRANCE))
        assertEquals(AppCurrency.USD, AppStateViewModel.guessCurrency(Locale.US))
        assertEquals(AppCurrency.USD, AppStateViewModel.guessCurrency(Locale.Builder().setLanguage("de").setRegion("CH").build())) // CHF unsupported
        assertEquals(AppCurrency.USD, AppStateViewModel.guessCurrency(Locale.ENGLISH)) // no region
    }
}
