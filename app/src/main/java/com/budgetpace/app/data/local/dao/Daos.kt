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

    /**
     * Newest first. Used to seed a newly created month from the most recent earlier month's
     * category configuration — including when a kill mid-rollover left no ACTIVE month at all.
     */
    @Query("SELECT * FROM budget_months ORDER BY year DESC, month DESC")
    suspend fun getAll(): List<BudgetMonthEntity>

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

    /**
     * Every category of the month, active or not, as a snapshot. The sync's Categories tab lists
     * inactive ones with Active=false so a deactivated category does not simply vanish from the
     * owner's sheet.
     */
    @Query("SELECT * FROM categories WHERE monthId = :monthId ORDER BY sortOrder ASC")
    fun observeAllByMonth(monthId: String): Flow<List<CategoryEntity>>
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

    @Query("""
        SELECT * FROM transactions
        WHERE categoryId = :categoryId AND recordDecision = 'RECORDED'
        ORDER BY transactionDate DESC
    """)
    suspend fun getByCategory(categoryId: String): List<TransactionEntity>

    @Query("""
        UPDATE transactions
        SET categoryId = :toCategoryId, syncState = 'PENDING', updatedAt = :now
        WHERE categoryId = :fromCategoryId
    """)
    suspend fun moveAllFromCategory(fromCategoryId: String, toCategoryId: String, now: Long)

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

    @Query("SELECT COUNT(*) FROM transactions WHERE syncState = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE syncState = 'SYNCED'")
    fun observeSyncedCount(): Flow<Int>

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

    // ── Conditional writes ────────────────────────────────────────────────────
    // Each returns the number of rows it changed, which is the only reliable way for a background
    // receiver to know whether it won a race with the in-app chooser. A read-then-write check
    // would happily overwrite a category the owner had just picked by hand.

    /** Assigns a category only while the expense still has none. Returns 1 when it applied. */
    @Query("""
        UPDATE transactions
        SET categoryId = :categoryId, syncState = 'PENDING', updatedAt = :now
        WHERE id = :id AND categoryId IS NULL AND recordDecision = 'RECORDED'
    """)
    suspend fun assignCategoryIfUnset(id: String, categoryId: String, now: Long): Int

    /** "Don't record", only while the expense is still recorded. Returns 1 when it applied. */
    @Query("""
        UPDATE transactions
        SET recordDecision = 'IGNORED', syncState = 'PENDING', updatedAt = :now
        WHERE id = :id AND recordDecision = 'RECORDED'
    """)
    suspend fun markIgnoredIfRecorded(id: String, now: Long): Int

    /** Undo of the above. Returns 1 when it applied. */
    @Query("""
        UPDATE transactions
        SET recordDecision = 'RECORDED', syncState = 'PENDING', updatedAt = :now
        WHERE id = :id AND recordDecision = 'IGNORED'
    """)
    suspend fun markRecordedIfIgnored(id: String, now: Long): Int

    /**
     * Marks a row synced only if it has not been edited since the upload read it. Deliberately
     * does not write updatedAt: doing so would look like a fresh edit to the next sync.
     */
    @Query("UPDATE transactions SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAtSeen")
    suspend fun markSyncedIfUnchanged(id: String, updatedAtSeen: Long): Int

    /** Reseeds the whole history for upload into a brand-new spreadsheet. */
    @Query("UPDATE transactions SET syncState = 'PENDING'")
    suspend fun markAllPending()

    /** A renamed category has to reach the sheet, and the name lives on the transaction rows. */
    @Query("UPDATE transactions SET syncState = 'PENDING' WHERE categoryId = :categoryId")
    suspend fun markPendingByCategory(categoryId: String)

    /** What "Backed up N expenses" counts: ignored rows are deliberately absent from the sheet. */
    @Query("SELECT COUNT(*) FROM transactions WHERE syncState = 'SYNCED' AND recordDecision = 'RECORDED'")
    fun observeSyncedRecordedCount(): Flow<Int>

    /** Drives the "N expenses need a category" row on Home and the Expenses tab badge. */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE monthId = :monthId AND recordDecision = 'RECORDED'
          AND direction = 'DEBIT' AND categoryId IS NULL
    """)
    fun observeUncategorizedCount(monthId: String): Flow<Int>

    /** Includes IGNORED rows, so "Show hidden" in Expenses can bring a mis-tap back. */
    @androidx.room.Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE monthId = :monthId
        ORDER BY transactionDate DESC, createdAt DESC
    """)
    fun observeAllWithCategoryByMonth(monthId: String): Flow<List<TransactionWithCategoryEntity>>

    // ── Quick-action ranking (spec section 21) ────────────────────────────────
    // Both filter to categorized debits: a credit or an uncategorized row says nothing about
    // which category the owner would pick.

    @Query("""
        SELECT categoryId AS categoryId, COUNT(*) AS count FROM transactions
        WHERE monthId = :monthId AND direction = 'DEBIT' AND categoryId IS NOT NULL
          AND recordDecision = 'RECORDED'
        GROUP BY categoryId
    """)
    suspend fun categoryUsageThisMonth(monthId: String): List<CategoryUsageRow>

    @Query("""
        SELECT categoryId AS categoryId, COUNT(*) AS count FROM transactions
        WHERE direction = 'DEBIT' AND categoryId IS NOT NULL AND recordDecision = 'RECORDED'
          AND recipient IS NOT NULL AND recipient = :payee
        GROUP BY categoryId
    """)
    suspend fun categoryUsageForPayee(payee: String): List<CategoryUsageRow>
}

/** Projection for the two usage queries above; maps 1:1 onto the pure `CategoryUsage`. */
data class CategoryUsageRow(val categoryId: String, val count: Int)

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

    /**
     * Changing a category's period count renumbers its periods, so every carry-forward it holds
     * now points at a period that means something else. Dropping them is the only honest option.
     */
    @Query("DELETE FROM carry_forwards WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: String)
}

// ─── DeletedTransactionDao (sync tombstones) ───────────────────────────────────
@Dao
interface DeletedTransactionDao {

    @Query("SELECT * FROM deleted_transactions")
    suspend fun getAll(): List<DeletedTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeletedTransactionEntity)

    @Query("DELETE FROM deleted_transactions WHERE transactionId = :transactionId")
    suspend fun deleteById(transactionId: String)
}
