package com.budgetpace.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.budgetpace.app.data.local.dao.*
import com.budgetpace.app.data.local.entity.*

@Database(
    entities = [
        BudgetMonthEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        CarryForwardEntity::class,
    ],
    version = 1,
    // No room.schemaLocation is configured (and nothing consumes exported schema files yet),
    // so exporting just produces the "schema export directory was not provided" build warning.
    exportSchema = false,
)
abstract class BudgetDatabase : RoomDatabase() {

    abstract fun budgetMonthDao(): BudgetMonthDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun carryForwardDao(): CarryForwardDao

    companion object {
        @Volatile private var INSTANCE: BudgetDatabase? = null

        fun getInstance(context: Context): BudgetDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context, BudgetDatabase::class.java, "budget_pace.db")
                // V1 has not shipped to any real install yet and ships no Migrations;
                // destructive fallback avoids crashing pre-release testers on schema
                // changes. Replace with real Migrations before the first real release.
                .fallbackToDestructiveMigration()
                .build()
    }
}
