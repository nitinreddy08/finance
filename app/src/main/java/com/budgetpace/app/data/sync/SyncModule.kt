package com.budgetpace.app.data.sync

import android.content.Context
import androidx.work.WorkManager
import com.budgetpace.app.domain.sync.SyncTrigger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * [WorkManager.getInstance] must not be called before [com.budgetpace.app.BudgetPaceApp.onCreate]
 * has run `WorkManager.initialize(...)` — the manifest disables WorkManager's own auto-init so
 * Hilt can supply a [androidx.hilt.work.HiltWorkerFactory] first. Injecting a `Provider<WorkManager>`
 * instead of `WorkManager` itself defers that lookup to first use, by which point it has run.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncTrigger(impl: SyncTriggerImpl): SyncTrigger

    companion object {
        @Provides
        fun provideWorkManagerProvider(@ApplicationContext context: Context): Provider<WorkManager> =
            Provider { WorkManager.getInstance(context) }
    }
}
