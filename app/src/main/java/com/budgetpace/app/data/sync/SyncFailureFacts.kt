package com.budgetpace.app.data.sync

import com.budgetpace.app.domain.sync.FailureFacts
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.json.GoogleJsonResponseException

/**
 * Flattens a caught [Throwable]'s cause chain into the shape
 * [com.budgetpace.app.domain.sync.classifySyncFailure] expects, so the decision itself
 * (`domain/sync/SyncProblem.kt`) stays pure and never has to know about Google's exception types.
 */
fun Throwable.toFailureFacts(): FailureFacts {
    val chain = causeChain(this)
    val jsonError = chain.filterIsInstance<GoogleJsonResponseException>().firstOrNull()
    val apiException = chain.filterIsInstance<ApiException>().firstOrNull()
    return FailureFacts(
        exceptionClassNames = chain.map { it.javaClass.name },
        httpStatus = jsonError?.statusCode,
        apiReason = jsonError?.details?.errors?.firstOrNull()?.reason,
        gmsStatusCode = apiException?.statusCode,
        message = message,
    )
}

/** Outermost first; guards against a (should-never-happen) self-referential cause cycle. */
private fun causeChain(t: Throwable, maxDepth: Int = 12): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = t
    while (current != null && chain.size < maxDepth && chain.none { it === current }) {
        chain += current
        current = current.cause
    }
    return chain
}
