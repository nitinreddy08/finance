package com.budgetpace.app.data.local.dao

import androidx.room.*
import com.budgetpace.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// ─── BudgetMonthDao ───────────────────────────────────────────────────────────
@Dao
interface BudgetMonthDao {

    @Query("SELECT * FROM budget_months ORDER BY year DESC, month DESC")
    fun observeAll(): Flow<List<BudgetMonthEntity>>

    @Query("SELECT * FROM budget_months WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActiveMonth(): Flow<BudgetMonthEntity?>

    @Query("SELECT * FROM budget_months WHERE id = :id")
    fun observeById(id: String): Flow<BudgetMonthEntity?>

    @Query("SELECT * FROM budget_months WHERE year = :year AND month = :month LIMIT 1")
    suspend fun getByYearMonth(year: Int, month: Int): BudgetMonthEntity?

    @Query("SELECT * FROM budget_months WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveMonth(): BudgetMonthEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(month: BudgetMonthEntity)

    @Update
    suspend fun update(month: BudgetMonthEntity)

    @Delete
    suspend fun delete(month: BudgetMonthEntity)
}

// ─── CategoryDao ──────────────────────────────────────────────────────────────
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE monthId = :monthId AND active = 1 ORDER BY sortOrder ASC")
    fun observeByMonth(monthId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE monthId = :monthId ORDER BY sortOrder ASC")
    suspend fun getByMonth(monthId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("UPDATE categories SET active = 0, updatedAt = :now WHERE id = :id")
    suspend fun deactivate(id: String, now: Long)
}

// ─── TransactionDao ───────────────────────────────────────────────────────────
@Dao
interface TransactionDao {

    @androidx.room.Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE monthId = :monthId AND recordDecision = 'RECORDED'
        ORDER BY transactionDate DESC, createdAt DESC
    """)
    fun observeWithCategoryByMonth(monthId: String): Flow<List<TransactionWithCategoryEntity>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeWithCategoryById(id: String): Flow<TransactionWithCategoryEntity?>

    @Query("""
        SELECT * FROM transactions
        WHERE monthId = :monthId AND recordDecision = 'RECORDED'
        ORDER BY transactionDate DESC, createdAt DESC
    """)
    fun observeByMonth(monthId: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE monthId = :monthId AND categoryId = :categoryId AND recordDecision = 'RECORDED'
        ORDER BY transactionDate DESC
    """)
    fun observeByMonthAndCategory(monthId: String, categoryId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE monthId = :monthId AND recordDecision = 'RECORDED'
        ORDER BY transactionDate DESC, createdAt DESC
    """)
    suspend fun getRecordedByMonth(monthId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE syncState = 'PENDING'")
    suspend fun getPending(): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE bank = :bank AND referenceNumber = :referenceNumber
        LIMIT 1
    """)
    suspend fun findByBankRef(bank: String, referenceNumber: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions WHERE duplicateKey = :key LIMIT 1
    """)
    suspend fun findByDuplicateKey(key: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("""
        UPDATE transactions
        SET categoryId = :categoryId, syncState = 'PENDING', updatedAt = :now
        WHERE id = :id
    """)
    suspend fun updateCategory(id: String, categoryId: String?, now: Long)

    @Query("""
        UPDATE transactions
        SET recordDecision = :recordDecision, syncState = 'PENDING', updatedAt = :now
        WHERE id = :id
    """)
    suspend fun updateRecordDecision(id: String, recordDecision: String, now: Long)

    @Query("UPDATE transactions SET syncState = 'SYNCED', updatedAt = :now WHERE id = :id")
    suspend fun markSynced(id: String, now: Long)
}

// ─── CarryForwardDao ──────────────────────────────────────────────────────────
@Dao
interface CarryForwardDao {

    @Query("SELECT * FROM carry_forwards WHERE monthId = :monthId")
    fun observeByMonth(monthId: String): Flow<List<CarryForwardEntity>>

    @Query("SELECT * FROM carry_forwards WHERE monthId = :monthId AND categoryId = :categoryId")
    suspend fun getByMonthCategory(monthId: String, categoryId: String): List<CarryForwardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cf: CarryForwardEntity)

    @Delete
    suspend fun delete(cf: CarryForwardEntity)
}
