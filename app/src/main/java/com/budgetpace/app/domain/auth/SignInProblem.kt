package com.budgetpace.app.domain.auth

/**
 * What the sign-in button shows under itself when Google turns the owner away.
 *
 * Sign-in failures are never persisted (unlike [com.budgetpace.app.domain.sync.SyncProblem]), so
 * this only carries the copy plus [detail] for logcat; spec section 61 keeps the raw exception off
 * the screen.
 */
sealed class SignInProblem {

    abstract val code: String

    /** An exception class name or a Google status number — diagnostic, never rendered as prose. */
    abstract val detail: String?

    abstract val title: String

    abstract val message: String

    /** Backing out of the account chooser is a decision, not an error: the UI shows nothing. */
    open val isSilent: Boolean get() = false

    data class Cancelled(override val detail: String? = null) : SignInProblem() {
        override val code: String = CODE_CANCELLED
        override val title: String = "Sign-in cancelled"
        override val message: String = "Nothing was changed."
        override val isSilent: Boolean get() = true
    }

    data class Misconfigured(override val detail: String? = null) : SignInProblem() {
        override val code: String = CODE_MISCONFIGURED
        override val title: String = "Sign-in isn't set up correctly"
        override val message: String =
            "This build of Budget Pace isn't registered with Google. This needs a fix in the " +
                "app's setup, not on your phone."
    }

    /** Soft copy on purpose: this flow can also add an account, so it is not a dead end. */
    data class NoGoogleAccount(override val detail: String? = null) : SignInProblem() {
        override val code: String = CODE_NO_GOOGLE_ACCOUNT
        override val title: String = "No Google account"
        override val message: String =
            "Couldn't find a Google account to use. Add or check your Google account in Settings " +
                "and try again."
    }

    data class Offline(override val detail: String? = null) : SignInProblem() {
        override val code: String = CODE_OFFLINE
        override val title: String = "No internet connection"
        override val message: String =
            "Budget Pace couldn't reach Google. Check your connection and try again."
    }

    data class Failed(override val detail: String? = null) : SignInProblem() {
        override val code: String = CODE_FAILED
        override val title: String = "Couldn't sign in"
        override val message: String =
            "Something went wrong signing in to Google. Your local data is safe — try again " +
                "in a moment."
    }

    companion object {
        const val CODE_CANCELLED: String = "cancelled"
        const val CODE_MISCONFIGURED: String = "misconfigured"
        const val CODE_NO_GOOGLE_ACCOUNT: String = "no_google_account"
        const val CODE_OFFLINE: String = "offline"
        const val CODE_FAILED: String = "failed"

        /** The value `app/build.gradle.kts` falls back to when `GOOGLE_CLIENT_ID` is missing. */
        const val PLACEHOLDER_CLIENT_ID: String = "YOUR_WEB_CLIENT_ID_HERE"

        /** True when the build carries no real web client id, quoted or not. */
        fun isPlaceholderClientId(clientId: String?): Boolean =
            clientId == null || clientId.trim().trim('"').isEmpty() ||
                clientId.trim().trim('"') == PLACEHOLDER_CLIENT_ID
    }
}

private val OFFLINE_EXCEPTION_NAMES = setOf(
    "UnknownHostException",
    "SocketTimeoutException",
    "ConnectException",
    "NoRouteToHostException",
    "SSLHandshakeException",
)

/**
 * Maps a Credential Manager failure onto the one thing the owner can do about it.
 *
 * @param exceptionClassNames the cause chain flattened by the Android layer, outermost first.
 * @param clientIdIsPlaceholder unlike sync, sign-in *does* check the web client id: an unset id is
 *   the single likeliest cause of a rejected sign-in on a fresh clone, and Google's own message
 *   for it ("Developer console is not set up correctly", 28444) is unreadable to the owner.
 */
fun classifySignInFailure(
    exceptionClassNames: List<String>,
    message: String?,
    clientIdIsPlaceholder: Boolean,
): SignInProblem {
    val simpleNames: List<String> = exceptionClassNames.map { it.simpleClassName() }

    // Cancellation outranks everything: the owner backed out, so whatever else was wrong with the
    // request is not worth a word on screen.
    if (simpleNames.any { it.endsWith("CancellationException") }) {
        return SignInProblem.Cancelled(simpleNames.firstOrNull())
    }

    val lowerMessage: String = message.orEmpty().lowercase()
    if (clientIdIsPlaceholder) {
        return SignInProblem.Misconfigured("placeholder_client_id")
    }
    if (lowerMessage.contains("developer console") || lowerMessage.contains("28444")) {
        return SignInProblem.Misconfigured("28444")
    }
    if (simpleNames.any { it == "NoCredentialException" }) {
        return SignInProblem.NoGoogleAccount(simpleNames.first { it == "NoCredentialException" })
    }
    val offlineName: String? = simpleNames.firstOrNull { it in OFFLINE_EXCEPTION_NAMES }
    if (offlineName != null) {
        return SignInProblem.Offline(offlineName)
    }
    return SignInProblem.Failed(exceptionClassNames.firstOrNull())
}

private fun String.simpleClassName(): String = substringAfterLast('.').substringAfterLast('$')
