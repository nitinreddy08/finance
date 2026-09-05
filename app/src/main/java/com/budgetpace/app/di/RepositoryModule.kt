package com.budgetpace.app.di

import com.budgetpace.app.core.time.LocalDateTicker
import com.budgetpace.app.data.local.dao.*
import com.budgetpace.app.data.repository.BudgetRepositoryImpl
import com.budgetpace.app.data.repository.TransactionRepositoryImpl
import com.budgetpace.app.domain.categorization.CategorizationPrompts
import com.budgetpace.app.domain.repository.BudgetRepository
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideBudgetRepository(
        monthDao: BudgetMonthDao,
        categoryDao: CategoryDao,
        transactionDao: TransactionDao,
        carryForwardDao: CarryForwardDao
    ): BudgetRepository {
        return BudgetRepositoryImpl(
            monthDao,
            categoryDao,
            transactionDao,
            carryForwardDao,
            LocalDateTicker().dates(),
        )
    }

    @Provides
    @Singleton
    fun provideTransactionRepository(
        transactionDao: TransactionDao,
        deletedTransactionDao: DeletedTransactionDao,
        prompts: CategorizationPrompts,
    ): TransactionRepository {
        return TransactionRepositoryImpl(transactionDao, deletedTransactionDao, prompts)
    }
}
