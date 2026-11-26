package com.expensetracker.data

/**
 * Spending categories. Stored in the database by [name], so never rename an existing constant;
 * add new ones instead. Icons and colors live in the UI layer (see `CategoryStyle.kt`).
 */
enum class Category(val label: String) {
    FOOD("Food & Dining"),
    GROCERIES("Groceries"),
    TRANSPORT("Transport"),
    SHOPPING("Shopping"),
    BILLS("Bills"),
    HOUSING("Housing"),
    ENTERTAINMENT("Entertainment"),
    HEALTH("Health"),
    EDUCATION("Education"),
    TRAVEL("Travel"),
    GIFTS("Gifts"),
    OTHER("Other"),
}
