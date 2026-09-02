package com.budgetpace.app.di

import android.content.Context
import com.budgetpace.app.data.local.dao.*
import com.budgetpace.app.data.local.db.BudgetDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBudgetDatabase(@ApplicationContext context: Context): BudgetDatabase {
        return BudgetDatabase.getInstance(context)
    }

    @Provides
    fun provideBudgetMonthDao(db: BudgetDatabase): BudgetMonthDao = db.budgetMonthDao()

    @Provides
    fun provideCategoryDao(db: BudgetDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: BudgetDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCarryForwardDao(db: BudgetDatabase): CarryForwardDao = db.carryForwardDao()
}
