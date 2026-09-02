package com.budgetpace.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.budgetpace.app.core.model.*
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.*
import com.budgetpace.app.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Database(
    entities = [
        BudgetMonthEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        CarryForwardEntity::class,
    ],
    version = 1,
    exportSchema = true,
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
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed deterministic sample data for Phase 1 dashboard
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                seedSampleData(database)
                            }
                        }
                    }
                })
                .build()

        /** Deterministic sample data for Phase 1 dashboard testing (spec §78) */
        private suspend fun seedSampleData(db: BudgetDatabase) {
            val now = Instant.now()
            val today = LocalDate.now()
            val ym = YearMonth.now()

            val monthId = UUID.randomUUID()
            db.budgetMonthDao().insert(
                BudgetMonthEntity(
                    id         = monthId.toString(),
                    year       = ym.year,
                    month      = ym.monthValue,
                    status     = MonthStatus.ACTIVE.name,
                    createdAt  = now.toEpochMilli(),
                    archivedAt = null,
                )
            )

            // Categories matching the spec example
            val categories = listOf(
                category(monthId, "Rent",                  Money.rupeesToPaise(9000), false, "home",   0, now),
                category(monthId, "Protein + Curd",        Money.rupeesToPaise(3000), true,  "egg",    1, now),
                category(monthId, "Fruits",                Money.rupeesToPaise(1000), true,  "apple",  2, now),
                category(monthId, "Extra Food + Misc",     Money.rupeesToPaise(1500), true,  "misc",   3, now),
            )
            db.categoryDao().insertAll(categories)

            // Sample transactions spread across periods
            val fruitsId = categories[2].id
            val proteinId = categories[1].id
            val miscId = categories[3].id

            val txns = listOf(
                tx(monthId, fruitsId,   35300,  Bank.KOTAK, "X7970", today.minusDays(2), now),
                tx(monthId, fruitsId,   18000,  Bank.KOTAK, "X7970", today.minusDays(5), now),
                tx(monthId, fruitsId,   12000,  Bank.SBI,   "X5326", today.minusDays(8), now),
                tx(monthId, proteinId,  27200,  Bank.SBI,   "X5326", today.minusDays(1), now),
                tx(monthId, proteinId,  45000,  Bank.KOTAK, "X7970", today.minusDays(3), now),
                tx(monthId, proteinId,  38000,  Bank.KOTAK, "X7970", today.minusDays(6), now),
                tx(monthId, miscId,     12000,  Bank.SBI,   "X5326", today.minusDays(2), now),
                tx(monthId, miscId,     8500,   Bank.KOTAK, "X7970", today.minusDays(4), now),
            )
            txns.forEach { db.transactionDao().insert(it) }
        }

        private fun category(
            monthId: UUID, name: String, budgetMinor: Long,
            pacing: Boolean, icon: String, sort: Int, now: Instant
        ) = CategoryEntity(
            id                  = UUID.randomUUID().toString(),
            monthId             = monthId.toString(),
            name                = name,
            monthlyBudgetMinor  = budgetMinor,
            weeklyPacingEnabled = pacing,
            iconKey             = icon,
            sortOrder           = sort,
            active              = true,
            createdAt           = now.toEpochMilli(),
            updatedAt           = now.toEpochMilli(),
        )

        private fun tx(
            monthId: UUID, categoryId: String, amountMinor: Long,
            bank: Bank, account: String, date: LocalDate, now: Instant
        ) = TransactionEntity(
            id                     = UUID.randomUUID().toString(),
            monthId                = monthId.toString(),
            amountMinor            = amountMinor,
            currency               = "INR",
            direction              = TransactionDirection.DEBIT.name,
            categoryId             = categoryId,
            transactionDateTime    = null,
            transactionDate        = date.toString(),
            notificationReceivedAt = now.toEpochMilli(),
            bank                   = bank.name,
            accountSuffix          = account,
            recipient              = null,
            sender                 = null,
            referenceNumber        = null,
            sourcePackage          = null,
            sourceSender           = null,
            sourceMessageHash      = null,
            duplicateKey           = null,
            recordDecision         = RecordDecision.RECORDED.name,
            syncState              = SyncState.PENDING.name,
            parserVersion          = null,
            createdAt              = now.toEpochMilli(),
            updatedAt              = now.toEpochMilli(),
        )
    }
}
