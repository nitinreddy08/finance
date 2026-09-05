package com.budgetpace.app.ingestion

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.budgetpace.app.BuildConfig
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.categorization.CategorizationPrompts
import com.budgetpace.app.domain.duplicate.DuplicateDetector
import com.budgetpace.app.domain.ingestion.IngestionChannel
import com.budgetpace.app.domain.ingestion.IngestionOutcome
import com.budgetpace.app.domain.ingestion.IngestionPolicy
import com.budgetpace.app.domain.ingestion.PreInsertDecision
import com.budgetpace.app.domain.parser.NotificationInput
import com.budgetpace.app.domain.parser.ParsedTransaction
import com.budgetpace.app.domain.parser.ParserCoordinator
import com.budgetpace.app.domain.usecase.EnsureActiveMonthUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one pipeline both capture channels run through: parse, de-duplicate, attribute to a month,
 * insert, prompt. Every decision worth arguing about lives in the pure [IngestionPolicy]; this
 * class only does the I/O.
 *
 * The scope is application-lifetime on purpose. Ingestion is started from a BroadcastReceiver and
 * from a NotificationListenerService, both of which the system can tear down mid-write; a
 * service-owned scope would cancel a coroutine between `insert()` and the prompt, leaving an
 * expense in the database that the owner is never asked about. The [Mutex] serialises the two
 * channels so the same message delivered milliseconds apart over both paths cannot race past the
 * duplicate check.
 */
@Singleton
class TransactionIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parserCoordinator: ParserCoordinator,
    private val transactionDao: TransactionDao,
    private val ensureActiveMonth: EnsureActiveMonthUseCase,
    private val prompts: CategorizationPrompts,
    private val diagnostics: DetectionDiagnostics,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Starts ingestion and hands back its [Job] so a receiver can wait for it inside `goAsync()`.
     * The job must be joined, never cancelled: cancelling between the insert and the prompt is
     * exactly the failure this class exists to prevent.
     */
    fun submit(input: NotificationInput, channel: IngestionChannel): Job =
        scope.launch { ingest(input, channel) }

    suspend fun ingest(input: NotificationInput, channel: IngestionChannel): IngestionOutcome {
        diagnostics.recordEvent(channel, input.title)
        return try {
            mutex.withLock { ingestLocked(input, channel) }
        } catch (cancellation: CancellationException) {
            // Never swallowed by the generic catch below: a cancelled coroutine that keeps running
            // would go on writing to the database after its caller has given up on it.
            throw cancellation
        } catch (error: Throwable) {
            Log.e(TAG, "Ingestion failed on $channel", error)
            diagnostics.recordOutcome(channel, IngestionOutcome.ERROR, error.javaClass.simpleName)
            IngestionOutcome.ERROR
        }
    }

    private suspend fun ingestLocked(
        input: NotificationInput,
        channel: IngestionChannel,
    ): IngestionOutcome {
        val decision = IngestionPolicy.preInsert(input.text) { parserCoordinator.parse(input) }
        if (decision is PreInsertDecision.Stop) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "$channel stopped at ${decision.outcome}: ${input.text}")
            }
            diagnostics.recordOutcome(channel, decision.outcome)
            return decision.outcome
        }

        val parsed = (decision as PreInsertDecision.Proceed).parsed
        val duplicateKey = DuplicateDetector.getDuplicateKey(parsed)

        // Fast path only. The unique indexes plus the insert's return value are what actually
        // decide; this just avoids the month lookup and the row build for an obvious redelivery.
        val alreadyRecorded = parsed.referenceNumber
            ?.let { transactionDao.findByBankRef(parsed.bank.name, it) }
            ?: transactionDao.findByDuplicateKey(duplicateKey)
        if (alreadyRecorded != null) {
            diagnostics.recordOutcome(channel, IngestionOutcome.DUPLICATE)
            return IngestionOutcome.DUPLICATE
        }

        val zone = ZoneId.systemDefault()
        val resolution = IngestionPolicy.resolveTransactionDate(parsed.transactionDate, input.receivedAt, zone)
        if (resolution.anomaly && BuildConfig.DEBUG) {
            Log.w(TAG, "Message claimed a future date; using the arrival date instead")
        }
        val month = ensureActiveMonth.forTransactionDate(resolution.date, LocalDate.now(zone))

        val now = Instant.now()
        val transaction = buildTransaction(input, parsed, resolution.date, month.id, duplicateKey, now)

        val rowId = transactionDao.insert(transaction.toEntity())
        val post = IngestionPolicy.postInsert(rowId, parsed.direction)
        diagnostics.recordOutcome(channel, post.outcome)
        Log.i(TAG, "$channel -> ${post.outcome} (${parsed.bank.name})")

        if (post.showPrompt) {
            showPrompt(transaction, channel)
        }
        return post.outcome
    }

    private suspend fun showPrompt(transaction: Transaction, channel: IngestionChannel) {
        // The expense is already saved; a prompt that cannot be posted must not undo that, so the
        // owner is told about it in Detection health instead.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            diagnostics.recordPromptSuppressed()
            Log.w(TAG, "Recorded from $channel but notifications are off, no prompt shown")
            return
        }
        prompts.show(transaction.id.toString())
    }

    private fun buildTransaction(
        input: NotificationInput,
        parsed: ParsedTransaction,
        transactionDate: LocalDate,
        monthId: UUID,
        duplicateKey: String,
        now: Instant,
    ): Transaction = Transaction(
        id = UUID.randomUUID(),
        monthId = monthId,
        amountMinor = parsed.amountMinor,
        currency = "INR",
        direction = parsed.direction,
        categoryId = null,
        transactionDateTime = parsed.transactionDateTime,
        transactionDate = transactionDate,
        notificationReceivedAt = input.receivedAt,
        bank = parsed.bank,
        accountSuffix = parsed.accountSuffix,
        recipient = parsed.recipient,
        sender = parsed.sender,
        referenceNumber = parsed.referenceNumber,
        sourcePackage = input.packageName,
        sourceSender = input.title,
        sourceMessageHash = input.text?.hashCode()?.toString(),
        duplicateKey = duplicateKey,
        recordDecision = RecordDecision.RECORDED,
        syncState = SyncState.PENDING,
        parserVersion = PARSER_VERSION,
        createdAt = now,
        updatedAt = now,
    )

    private companion object {
        const val TAG = "SmsIngest"
        const val PARSER_VERSION = "1.0"
    }
}
