package com.budgetpace.app.domain.sync

/** The single action a sync problem offers; the backup screen renders at most one button. */
enum class SyncAction { TRY_AGAIN, RECONNECT, OPEN_ACCOUNT_SETTINGS, START_NEW_SHEET, NONE }

/**
 * Everything the app knows about a failed sync, in a form that can be shown to the owner.
 *
 * Copy lives here rather than in the store because only [code] and [detail] are persisted: a later
 * build must never show text written by an older one, and the raw exception (spec section 61) must
 * never reach the screen at all.
 */
sealed class SyncProblem {

    abstract val code: String

    /** An HTTP status, a GMS status code or a class name — diagnostic, never rendered as prose. */
    abstract val detail: String?

    abstract val title: String

    abstract val message: String

    abstract val action: SyncAction

    /**
     * Whether the WorkManager job should ask for a retry. Spec section 54: do not retry forever
     * when the problem needs the owner (consent, a missing sheet, a misconfigured build) — those
     * fail the job instead and wait for a deliberate tap.
     */
    abstract val isRetryableInBackground: Boolean

    /** Backing out of a Google sheet is not a failure; the UI shows nothing for a silent problem. */
    open val isSilent: Boolean get() = false

    data class Offline(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_OFFLINE
        override val title: String = "No internet connection"
        override val message: String =
            "Budget Pace will back up your expenses as soon as you're online again. " +
                "Your local data is safe."
        override val action: SyncAction = SyncAction.TRY_AGAIN
        override val isRetryableInBackground: Boolean = true
    }

    data class NeedsReconnect(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_NEEDS_RECONNECT
        override val title: String = "Reconnect Google Sheets"
        override val message: String =
            "Google needs you to approve Budget Pace again before it can update your sheet. " +
                "Your local data is safe."
        override val action: SyncAction = SyncAction.RECONNECT
        override val isRetryableInBackground: Boolean = false
    }

    data class RateLimited(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_RATE_LIMITED
        override val title: String = "Google asked us to slow down"
        override val message: String =
            "Too many changes went up at once. Budget Pace will finish the backup shortly."
        override val action: SyncAction = SyncAction.TRY_AGAIN
        override val isRetryableInBackground: Boolean = true
    }

    data class GoogleUnavailable(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_GOOGLE_UNAVAILABLE
        override val title: String = "Google Sheets isn't responding"
        override val message: String =
            "The problem is on Google's side, not with your data. Budget Pace will try again later."
        override val action: SyncAction = SyncAction.TRY_AGAIN
        override val isRetryableInBackground: Boolean = true
    }

    data class SheetUnavailable(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_SHEET_UNAVAILABLE
        override val title: String = "Can't find your backup sheet"
        override val message: String =
            "The sheet was moved, renamed or is no longer shared with this account. " +
                "You can start a new sheet — nothing on this phone is lost."
        override val action: SyncAction = SyncAction.START_NEW_SHEET
        override val isRetryableInBackground: Boolean = false
    }

    data class AccountChanged(val previousEmail: String?) : SyncProblem() {
        override val code: String = CODE_ACCOUNT_CHANGED
        override val detail: String? = previousEmail
        override val title: String = "Different Google account"
        override val message: String =
            if (previousEmail.isNullOrBlank()) {
                "Your backup sheet belongs to a different Google account. Start a new sheet for " +
                    "the account you're signed in with now — nothing on this phone is lost."
            } else {
                "Your backup sheet belongs to $previousEmail. Start a new sheet for the account " +
                    "you're signed in with now — nothing on this phone is lost."
            }
        override val action: SyncAction = SyncAction.START_NEW_SHEET
        override val isRetryableInBackground: Boolean = false
    }

    data class ApiDisabled(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_API_DISABLED
        override val title: String = "Google Sheets access is switched off"
        override val message: String =
            "Sheets backup isn't switched on for this copy of Budget Pace yet. " +
                "Turn it on in your Google project, then try again."
        override val action: SyncAction = SyncAction.TRY_AGAIN
        override val isRetryableInBackground: Boolean = false
    }

    data class DeveloperMisconfig(override val detail: String?) : SyncProblem() {
        override val code: String = CODE_DEVELOPER_MISCONFIG
        override val title: String = "Backup isn't set up correctly"
        override val message: String =
            "Google turned down this build of Budget Pace. This needs a fix in the app's setup, " +
                "not on your phone. Your local data is safe."
        override val action: SyncAction = SyncAction.NONE
        override val isRetryableInBackground: Boolean = false
    }

    data class NoGoogleAccount(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_NO_GOOGLE_ACCOUNT
        override val title: String = "No Google account"
        override val message: String =
            "Couldn't find a Google account to use. Add or check your Google account in Settings " +
                "and try again."
        override val action: SyncAction = SyncAction.OPEN_ACCOUNT_SETTINGS
        override val isRetryableInBackground: Boolean = false
    }

    /** Never shown: the owner closing a Google sheet is a decision, not an error. */
    data class Cancelled(override val detail: String? = null) : SyncProblem() {
        override val code: String = CODE_CANCELLED
        override val title: String = "Backup cancelled"
        override val message: String = "Nothing was changed."
        override val action: SyncAction = SyncAction.NONE
        override val isRetryableInBackground: Boolean = false
        override val isSilent: Boolean get() = true
    }

    data class Unknown(override val detail: String?) : SyncProblem() {
        override val code: String = CODE_UNKNOWN
        override val title: String = "Couldn't sync with Google Sheets"
        override val message: String = "Your local data is safe. Try again in a moment."
        override val action: SyncAction = SyncAction.TRY_AGAIN
        override val isRetryableInBackground: Boolean = true
    }

    companion object {
        const val CODE_OFFLINE: String = "offline"
        const val CODE_NEEDS_RECONNECT: String = "reconnect"
        const val CODE_RATE_LIMITED: String = "rate_limited"
        const val CODE_GOOGLE_UNAVAILABLE: String = "google_unavailable"
        const val CODE_SHEET_UNAVAILABLE: String = "sheet_unavailable"
        const val CODE_ACCOUNT_CHANGED: String = "account_changed"
        const val CODE_API_DISABLED: String = "api_disabled"
        const val CODE_DEVELOPER_MISCONFIG: String = "developer_misconfig"
        const val CODE_NO_GOOGLE_ACCOUNT: String = "no_google_account"
        const val CODE_CANCELLED: String = "cancelled"
        const val CODE_UNKNOWN: String = "unknown"

        /**
         * Rebuilds a problem from what was persisted. An unrecognised code (written by a build
         * that knew a case this one does not) degrades to [Unknown] rather than crashing.
         */
        fun fromCode(code: String, detail: String?): SyncProblem = when (code) {
            CODE_OFFLINE -> Offline(detail)
            CODE_NEEDS_RECONNECT -> NeedsReconnect(detail)
            CODE_RATE_LIMITED -> RateLimited(detail)
            CODE_GOOGLE_UNAVAILABLE -> GoogleUnavailable(detail)
            CODE_SHEET_UNAVAILABLE -> SheetUnavailable(detail)
            CODE_ACCOUNT_CHANGED -> AccountChanged(detail)
            CODE_API_DISABLED -> ApiDisabled(detail)
            CODE_DEVELOPER_MISCONFIG -> DeveloperMisconfig(detail)
            CODE_NO_GOOGLE_ACCOUNT -> NoGoogleAccount(detail)
            CODE_CANCELLED -> Cancelled(detail)
            else -> Unknown(detail)
        }
    }
}

/**
 * The flattened facts of a failure: the Android layer walks the cause chain
 * (`GoogleJsonResponseException`, `ApiException`, `GetCredentialException`) and fills this in, so
 * the decision itself stays pure and testable.
 */
data class FailureFacts(
    val exceptionClassNames: List<String> = emptyList(),
    val httpStatus: Int? = null,
    val apiReason: String? = null,
    val gmsStatusCode: Int? = null,
    val message: String? = null,
)

// com.google.android.gms.common.api.CommonStatusCodes, mirrored here so this file stays pure.
private const val GMS_SIGN_IN_REQUIRED = 4
private const val GMS_NETWORK_ERROR = 7
private const val GMS_DEVELOPER_ERROR = 10
private const val GMS_TIMEOUT = 15
private const val GMS_CANCELED = 16

private val OFFLINE_EXCEPTION_NAMES = setOf(
    "UnknownHostException",
    "SocketTimeoutException",
    "ConnectException",
    "NoRouteToHostException",
    "SSLHandshakeException",
)

/**
 * Maps a failure onto the one problem the owner can act on.
 *
 * Deliberately takes no "client id is a placeholder" flag: the Authorization API validates the
 * package name and signing SHA-1, not the web client id, so a placeholder id cannot be the cause
 * of a sync failure and checking it here would mask the real one. That check belongs to sign-in.
 */
fun classifySyncFailure(f: FailureFacts): SyncProblem {
    val simpleNames = f.exceptionClassNames.map { it.simpleClassName() }

    // Cancellation outranks everything: a cancelled sync carries whatever HTTP status the
    // in-flight call happened to have, and none of it is worth showing.
    if (f.gmsStatusCode == GMS_CANCELED || simpleNames.any { it.endsWith("CancellationException") }) {
        return SyncProblem.Cancelled()
    }
    if (f.gmsStatusCode == GMS_DEVELOPER_ERROR) {
        return SyncProblem.DeveloperMisconfig("DEVELOPER_ERROR")
    }
    if (simpleNames.any { it == "NoCredentialException" }) {
        return SyncProblem.NoGoogleAccount()
    }
    if (simpleNames.any { it in OFFLINE_EXCEPTION_NAMES }) {
        return SyncProblem.Offline(simpleNames.first { it in OFFLINE_EXCEPTION_NAMES })
    }
    if (f.gmsStatusCode == GMS_NETWORK_ERROR || f.gmsStatusCode == GMS_TIMEOUT) {
        return SyncProblem.Offline("GMS ${f.gmsStatusCode}")
    }
    if (f.gmsStatusCode == GMS_SIGN_IN_REQUIRED) {
        return SyncProblem.NeedsReconnect("GMS $GMS_SIGN_IN_REQUIRED")
    }

    val status = f.httpStatus
    if (status != null) {
        val detail = status.toString()
        return when {
            // A renamed or deleted Expenses tab makes every range call fail with
            // "Unable to parse range" — permanent until the owner acts, so never retried.
            status == 400 -> SyncProblem.SheetUnavailable(detail)
            status == 401 -> SyncProblem.NeedsReconnect(detail)
            status == 403 -> classifyForbidden(f.apiReason, detail)
            status == 404 -> SyncProblem.SheetUnavailable(detail)
            status == 429 -> SyncProblem.RateLimited(detail)
            status in 500..599 -> SyncProblem.GoogleUnavailable(detail)
            else -> SyncProblem.Unknown(f.exceptionClassNames.firstOrNull() ?: detail)
        }
    }

    return SyncProblem.Unknown(f.exceptionClassNames.firstOrNull())
}

private fun classifyForbidden(apiReason: String?, detail: String): SyncProblem =
    when (apiReason?.trim()?.lowercase()) {
        "accessnotconfigured" -> SyncProblem.ApiDisabled(detail)
        "insufficientpermissions" -> SyncProblem.NeedsReconnect(detail)
        "ratelimitexceeded", "userratelimitexceeded", "quotaexceeded" -> SyncProblem.RateLimited(detail)
        // A 403 on the file itself (not shared with this account any more) reads the same to the
        // owner as a missing sheet, and offers the same way out.
        else -> SyncProblem.SheetUnavailable(detail)
    }

private fun String.simpleClassName(): String = substringAfterLast('.').substringAfterLast('$')
