package com.expensetracker.importer

import com.expensetracker.data.Category
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToLong

/** Turns raw bank descriptions into readable notes, merchant keys and category guesses. */
object CategoryGuesser {
    private val rules: List<Pair<Category, List<String>>> = listOf(
        Category.TRAVEL to listOf("hotel", "airbnb", "booking.com", "agoda", "expedia", "airline", "airways", "air asia", "airasia", "flight", "travel", "resort", "hostel", "trip.com"),
        Category.HOUSING to listOf("rent", "landlord", "mortgage", "apartment", "condo", "property", "housing"),
        Category.BILLS to listOf("electric", "electricity", "water supply", "utility", "utilities", "internet", "broadband", "wifi", "mobile top", "top up", "topup", "telecom", "cellcard", "smart axiata", "metfone", "phone bill", "insurance", "bill payment", "edc"),
        Category.ENTERTAINMENT to listOf("netflix", "spotify", "youtube", "disney", "cinema", "movie", "steam", "playstation", "xbox", "nintendo", "concert", "ticket", "game", "apple music", "hbo"),
        Category.GROCERIES to listOf("supermarket", "grocery", "groceries", "mart", "aeon", "lucky", "makro", "tesco", "walmart", "lotus", "7-eleven", "7 eleven", "circle k", "family mart", "familymart", "costco", "aldi", "lidl", "market"),
        Category.FOOD to listOf("restaurant", "cafe", "café", "coffee", "starbucks", "kfc", "mcdonald", "burger", "pizza", "domino", "bakery", "food", "foodpanda", "grabfood", "grab food", "nham24", "eats", "tea", "dining", "bbq", "noodle", "sushi", "kitchen", "bar ", "pub"),
        Category.TRANSPORT to listOf("grab", "uber", "taxi", "tuk", "passapp", "bolt", "lyft", "fuel", "petrol", "gasoline", "gas station", "caltex", "total energies", "ptt", "tela", "shell", "parking", "toll", "bus", "train", "metro", "transport"),
        Category.HEALTH to listOf("pharmacy", "hospital", "clinic", "doctor", "dental", "dentist", "medical", "health", "gym", "fitness", "optical"),
        Category.EDUCATION to listOf("school", "university", "college", "tuition", "course", "udemy", "coursera", "academy", "bookstore", "books", "education", "training"),
        Category.SHOPPING to listOf("amazon", "shopee", "lazada", "aliexpress", "ebay", "shop", "store", "mall", "zara", "uniqlo", "h&m", "ikea", "apple store", "electronics", "fashion", "boutique"),
        Category.GIFTS to listOf("gift", "donation", "charity", "flower", "florist", "wedding"),
    )

    private val noiseWords = setOf(
        "pos", "purchase", "payment", "pmt", "paid", "card", "debit", "credit", "visa", "mastercard", "mc", "at", "to", "from",
        "ref", "trx", "txn", "trf", "transfer", "online", "ecom", "e-com", "contactless", "the", "via", "khqr", "qr", "aba", "pay",
    )

    private val boilerplatePrefix = Regex(
        """^(?i)(debit card purchase|card purchase|pos purchase|online purchase|e-?com(merce)? purchase|purchase at|purchase|pos|payment to|paid to)\b[\s:-]*""",
    )

    private val incomeRules: List<Pair<Category, List<String>>> = listOf(
        Category.SALARY to listOf("salary", "payroll", "wage", "wages", "pay slip", "payslip", "allowance", "bonus"),
        Category.INVESTMENT to listOf("interest", "dividend", "investment", "coupon", "yield"),
        Category.REFUND to listOf("refund", "reversal", "cashback", "cash back", "chargeback", "return"),
        Category.BUSINESS to listOf("invoice", "freelance", "client", "sales", "upwork", "fiverr", "payout"),
        Category.GIFT_RECEIVED to listOf("gift", "birthday", "wedding"),
        Category.TRANSFER_IN to listOf("transfer", "trf", "received from", "from", "deposit", "cash in", "khqr", "top up"),
    )

    /** Category for money coming in; falls back to "Other income". */
    fun guessIncome(description: String): Category {
        val text = " " + description.lowercase(Locale.ROOT) + " "
        return incomeRules.firstOrNull { (_, words) -> words.any { w -> matches(text, w) } }?.first ?: Category.OTHER_INCOME
    }

    fun guess(description: String): Category? {
        val text = " " + description.lowercase(Locale.ROOT) + " "
        return rules.firstOrNull { (_, words) -> words.any { w -> matches(text, w) } }?.first
    }

    private fun matches(text: String, keyword: String): Boolean {
        val idx = text.indexOf(keyword)
        if (idx < 0) return false
        // Short keywords must be whole words ("bus" shouldn't match "business").
        if (keyword.length > 5) return true
        val before = text.getOrNull(idx - 1) ?: ' '
        val after = text.getOrNull(idx + keyword.length) ?: ' '
        return !before.isLetter() && !after.isLetter()
    }

    /** "POS PURCHASE STARBUCKS #1234 XXXX1111" → "Starbucks". */
    fun cleanDescription(raw: String): String {
        var s = raw.replace(Regex("""\s+"""), " ").trim()
        // Card-terminal boilerplate in front of the merchant name.
        repeat(2) { s = s.replace(boilerplatePrefix, "") }
        s = s.replace(Regex("""(?i)\b[x*]{2,}\d{2,}\b"""), " ") // masked card numbers
        s = s.replace(Regex("""(?i)\b(ref|trx|txn|id|no)[:.#]?\s*[a-z0-9-]{6,}\b"""), " ") // reference ids
        s = s.replace(Regex("""\b\d{6,}\b"""), " ") // long numbers
        s = s.replace(Regex("""#\s?\d+"""), " ")
        s = s.replace(Regex("""\s+"""), " ").trim(' ', '-', ',', '.', '/', '*')
        val letters = s.filter(Char::isLetter)
        if (letters.isNotEmpty() && letters.count(Char::isUpperCase) > letters.length * 0.8) {
            s = s.split(' ').joinToString(" ") { word ->
                if (word.length <= 3 && word.all { it.isLetter() }) word // keep ATM, KFC, POS…
                else word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }
        return s.take(80)
    }

    /** Stable key for "the same merchant": used to remember categories you choose. */
    fun merchantKey(description: String): String {
        val words = description.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\s&.]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim('.') }
            .filter { it.length > 1 && it !in noiseWords }
        return words.take(3).joinToString(" ")
    }

    /** Identity of a statement line; identical lines on the same day get an occurrence number. */
    fun importKey(date: LocalDate, amount: Double, description: String, occurrence: Int): String =
        "$date|${(amount * 100).roundToLong()}|${merchantKey(description)}|$occurrence"
}
