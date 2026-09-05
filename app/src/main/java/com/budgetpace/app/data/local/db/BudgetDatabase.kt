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
        DeletedTransactionEntity::class,
    ],
    version = 3,
    // Exported to app/schemas via the room.schemaLocation KSP argument. Room needs the previous
    // schema on disk to generate and verify a real Migration, so the next schema change cannot be
    // written safely until version 3 has been committed there.
    exportSchema = true,
)
abstract class BudgetDatabase : RoomDatabase() {

    abstract fun budgetMonthDao(): BudgetMonthDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun carryForwardDao(): CarryForwardDao
    abstract fun deletedTransactionDao(): DeletedTransactionDao

    companion object {
        @Volatile private var INSTANCE: BudgetDatabase? = null

        fun getInstance(context: Context): BudgetDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context, BudgetDatabase::class.java, "budget_pace.db")
                // Only from the two pre-release versions. A blanket fallbackToDestructiveMigration
                // would silently erase every expense the owner has recorded the first time a
                // future version ships without a Migration — on a phone that is the only copy of
                // that data. Failing the open is loud, recoverable, and the safer default.
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()
    }
}
