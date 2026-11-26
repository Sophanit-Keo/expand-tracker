package com.expensetracker.data

import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.random.Random

/** Generates a few months of realistic-looking expenses so the charts have something to show. */
object SampleData {
    private data class Template(val category: Category, val notes: List<String>, val min: Double, val max: Double)

    private val daily = listOf(
        Template(Category.FOOD, listOf("Lunch", "Coffee", "Dinner out", "Breakfast", "Bubble tea"), 2.0, 18.0),
        Template(Category.TRANSPORT, listOf("Tuk-tuk", "Bus fare", "Taxi", "Fuel", "Parking"), 1.0, 12.0),
        Template(Category.GROCERIES, listOf("Supermarket", "Fresh market", "Fruit"), 5.0, 40.0),
        Template(Category.SHOPPING, listOf("Clothes", "Phone case", "Shoes", "Household items"), 6.0, 60.0),
        Template(Category.ENTERTAINMENT, listOf("Movie tickets", "Streaming", "Concert", "Games"), 4.0, 30.0),
        Template(Category.HEALTH, listOf("Pharmacy", "Gym", "Vitamins"), 3.0, 35.0),
        Template(Category.GIFTS, listOf("Birthday gift", "Flowers"), 8.0, 45.0),
        Template(Category.OTHER, listOf("Haircut", "Laundry", "Printing"), 2.0, 15.0),
    )

    // Weighted so food and transport show up more often, as they do in real life.
    private val weights = listOf(9, 6, 4, 2, 2, 1, 1, 2)

    fun generate(currency: AppCurrency, today: LocalDate = LocalDate.now(), days: Int = 120): List<Expense> {
        val random = Random(today.toEpochDay())
        val result = mutableListOf<Expense>()
        var createdAt = System.currentTimeMillis() - days * 86_400_000L

        fun add(category: Category, note: String, amountUsd: Double, date: LocalDate) {
            createdAt += 1_000
            result += Expense(
                amount = scale(amountUsd, currency),
                category = category,
                note = note,
                date = date,
                createdAt = createdAt,
            )
        }

        for (offset in days downTo 0) {
            val date = today.minusDays(offset.toLong())
            repeat(random.nextInt(0, 4)) {
                val t = pickWeighted(random)
                add(t.category, t.notes.random(random), random.nextDouble(t.min, t.max), date)
            }
            when (date.dayOfMonth) {
                1 -> add(Category.HOUSING, "Rent", 320.0, date)
                5 -> add(Category.BILLS, "Electricity", random.nextDouble(25.0, 45.0), date)
                8 -> add(Category.BILLS, "Internet", 20.0, date)
                15 -> add(Category.EDUCATION, "English course", 45.0, date)
            }
            if (date.dayOfMonth == 20 && date.monthValue % 2 == 0) {
                add(Category.TRAVEL, "Weekend trip", random.nextDouble(60.0, 140.0), date)
            }
        }
        return result
    }

    private fun pickWeighted(random: Random): Template {
        var roll = random.nextInt(weights.sum())
        weights.forEachIndexed { index, weight ->
            if (roll < weight) return daily[index]
            roll -= weight
        }
        return daily.last()
    }

    private fun scale(amountUsd: Double, currency: AppCurrency): Double {
        val value = amountUsd * currency.sampleScale
        return when {
            currency.decimals > 0 -> (value * 4).roundToLong() / 4.0 // nearest 0.25
            value >= 10_000 -> (value / 500).roundToLong() * 500.0
            value >= 1_000 -> (value / 100).roundToLong() * 100.0
            else -> value.roundToLong().toDouble()
        }
    }
}
