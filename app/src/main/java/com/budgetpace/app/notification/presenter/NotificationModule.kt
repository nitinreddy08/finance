package com.budgetpace.app.notification.presenter

import com.budgetpace.app.domain.categorization.CategorizationPrompts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the categorization seam (spec §21) to its real notification-backed implementation.
 *
 * [com.budgetpace.app.ingestion.TransactionIngestor] and
 * [com.budgetpace.app.data.repository.TransactionRepositoryImpl] both constructor-inject
 * [CategorizationPrompts] without knowing this binding exists — without this module the whole
 * app fails to build.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindCategorizationPrompts(
        impl: CategorizationNotificationManager
    ): CategorizationPrompts
}
