package com.expensetracker.data

/** Which way the money went. */
enum class EntryType { EXPENSE, INCOME }

/**
 * Categories for money out (expenses) and money in (income). Stored in the database by [name], so
 * never rename an existing constant; add new ones instead. Icons and colors live in the UI layer
 * (see `CategoryStyle.kt`).
 */
enum class Category(val label: String, val type: EntryType = EntryType.EXPENSE) {
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

    SALARY("Salary", EntryType.INCOME),
    BUSINESS("Business", EntryType.INCOME),
    INVESTMENT("Interest & investments", EntryType.INCOME),
    REFUND("Refunds", EntryType.INCOME),
    GIFT_RECEIVED("Gifts received", EntryType.INCOME),
    TRANSFER_IN("Transfers in", EntryType.INCOME),
    OTHER_INCOME("Other income", EntryType.INCOME),
    ;

    companion object {
        fun of(type: EntryType): List<Category> = entries.filter { it.type == type }

        fun default(type: EntryType): Category = if (type == EntryType.EXPENSE) FOOD else SALARY
    }
}
