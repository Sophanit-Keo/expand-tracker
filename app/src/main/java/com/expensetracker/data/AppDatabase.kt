package com.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter
    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()
}

/** v1 → v2: statement import (duplicate keys on expenses + learned merchant categories). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `importKey` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category_rules` " +
                "(`merchant` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`merchant`))",
        )
    }
}

/** v2 → v3: income. Every existing entry is an expense. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'EXPENSE'")
    }
}

/** v3 → v4: imported bank statements, and a link from each imported entry to its statement. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `statementId` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `statements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `periodStart` INTEGER NOT NULL, " +
                "`periodEnd` INTEGER NOT NULL, `totalIn` REAL NOT NULL, `totalOut` REAL NOT NULL, " +
                "`transactionCount` INTEGER NOT NULL, `openingBalance` REAL, `closingBalance` REAL)",
        )
    }
}

/** v4 → v5: entries in another currency keep their original amount and currency. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `originalAmount` REAL")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `originalCurrency` TEXT")
    }
}

@Database(entities = [Expense::class, CategoryRule::class, BankStatement::class], version = 5, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun statementDao(): StatementDao

    companion object {
        const val NAME = "expenses.db"

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
