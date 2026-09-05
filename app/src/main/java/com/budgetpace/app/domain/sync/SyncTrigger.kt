package com.budgetpace.app.domain.sync

/**
 * The seam between "local data changed" and "get it into the owner's sheet".
 *
 * Callers (ingestion, the categorization receiver, the repositories) must not know about
 * WorkManager. Implementations enqueue unique work and return immediately, and must never throw:
 * a backup that cannot be scheduled is never a reason to fail the write that triggered it.
 */
interface SyncTrigger {

    /**
     * Requests a backup in the near future, coalescing repeated calls into one run. A no-op when
     * the owner has not connected a Google account.
     */
    fun requestSyncSoon()
}
