package com.budgetpace.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInProblemTest {

    private fun classify(
        names: List<String> = emptyList(),
        message: String? = null,
        placeholder: Boolean = false,
    ): SignInProblem = classifySignInFailure(names, message, placeholder)

    // ─── Cancellation ─────────────────────────────────────────────────────────

    @Test
    fun testCancellationIsSilent() {
        val problem = classify(
            names = listOf("androidx.credentials.exceptions.GetCredentialCancellationException"),
        )
        assertEquals(SignInProblem.Cancelled("GetCredentialCancellationException"), problem)
        assertTrue(problem.isSilent)
    }

    @Test
    fun testAnyCancellationExceptionCounts() {
        assertEquals(
            SignInProblem.CODE_CANCELLED,
            classify(names = listOf("kotlinx.coroutines.JobCancellationException")).code,
        )
        assertEquals(
            SignInProblem.CODE_CANCELLED,
            classify(names = listOf("java.util.concurrent.CancellationException")).code,
        )
        assertEquals(
            SignInProblem.CODE_CANCELLED,
            classify(names = listOf("com.example.Outer\$InnerCancellationException")).code,
        )
    }

    @Test
    fun testCancellationOutranksEverythingElse() {
        val problem = classify(
            names = listOf(
                "androidx.credentials.exceptions.GetCredentialCancellationException",
                "androidx.credentials.exceptions.NoCredentialException",
                "java.net.UnknownHostException",
            ),
            message = "[28444] Developer console is not set up correctly.",
            placeholder = true,
        )
        assertEquals(SignInProblem.CODE_CANCELLED, problem.code)
    }

    // ─── Misconfiguration ─────────────────────────────────────────────────────

    @Test
    fun testPlaceholderClientIdIsMisconfiguration() {
        val problem = classify(
            names = listOf("androidx.credentials.exceptions.GetCredentialException"),
            placeholder = true,
        )
        assertEquals(SignInProblem.Misconfigured("placeholder_client_id"), problem)
        assertFalse(problem.isSilent)
    }

    @Test
    fun testGoogleDeveloperConsoleMessageIsMisconfiguration() {
        assertEquals(
            SignInProblem.Misconfigured("28444"),
            classify(
                names = listOf("com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException"),
                message = "[28444] Developer console is not set up correctly.",
            ),
        )
        assertEquals(
            SignInProblem.CODE_MISCONFIGURED,
            classify(message = "During begin sign in, failure response from one tap: 28444").code,
        )
        assertEquals(
            SignInProblem.CODE_MISCONFIGURED,
            classify(message = "developer console is not set up correctly").code,
        )
    }

    @Test
    fun testMisconfigurationOutranksMissingAccountAndOffline() {
        assertEquals(
            SignInProblem.CODE_MISCONFIGURED,
            classify(
                names = listOf(
                    "androidx.credentials.exceptions.NoCredentialException",
                    "java.net.UnknownHostException",
                ),
                placeholder = true,
            ).code,
        )
        assertEquals(
            SignInProblem.CODE_MISCONFIGURED,
            classify(
                names = listOf("java.net.UnknownHostException"),
                message = "[28444] Developer console is not set up correctly.",
            ).code,
        )
    }

    @Test
    fun testMisconfiguredCopyBlamesTheBuildNotThePhone() {
        val message = SignInProblem.Misconfigured().message
        assertTrue(message.contains("isn't registered with Google"))
        assertTrue(message.contains("app's setup"))
    }

    // ─── No Google account ────────────────────────────────────────────────────

    @Test
    fun testNoCredentialExceptionIsANoAccountProblemWithSoftCopy() {
        val problem = classify(
            names = listOf("androidx.credentials.exceptions.NoCredentialException"),
        )
        assertEquals(SignInProblem.NoGoogleAccount("NoCredentialException"), problem)
        assertEquals(
            "Couldn't find a Google account to use. Add or check your Google account in " +
                "Settings and try again.",
            problem.message,
        )
    }

    @Test
    fun testMissingAccountOutranksOffline() {
        assertEquals(
            SignInProblem.CODE_NO_GOOGLE_ACCOUNT,
            classify(
                names = listOf(
                    "java.net.SocketTimeoutException",
                    "androidx.credentials.exceptions.NoCredentialException",
                ),
            ).code,
        )
    }

    // ─── Offline ──────────────────────────────────────────────────────────────

    @Test
    fun testOfflineExceptionNames() {
        val expected = mapOf(
            "java.net.UnknownHostException" to "UnknownHostException",
            "java.net.SocketTimeoutException" to "SocketTimeoutException",
            "java.net.ConnectException" to "ConnectException",
            "java.net.NoRouteToHostException" to "NoRouteToHostException",
            "javax.net.ssl.SSLHandshakeException" to "SSLHandshakeException",
        )
        for ((fullName, simpleName) in expected) {
            assertEquals(fullName, SignInProblem.Offline(simpleName), classify(names = listOf(fullName)))
        }
    }

    @Test
    fun testOfflineIsFoundAnywhereInTheCauseChain() {
        assertEquals(
            SignInProblem.Offline("UnknownHostException"),
            classify(
                names = listOf(
                    "androidx.credentials.exceptions.GetCredentialUnknownException",
                    "java.io.IOException",
                    "java.net.UnknownHostException",
                ),
            ),
        )
    }

    // ─── Everything else ──────────────────────────────────────────────────────

    @Test
    fun testAnythingElseFailsGenerically() {
        val problem = classify(
            names = listOf("androidx.credentials.exceptions.GetCredentialUnknownException"),
            message = "Unexpected internal error",
        )
        assertEquals(
            SignInProblem.Failed("androidx.credentials.exceptions.GetCredentialUnknownException"),
            problem,
        )
        // Spec section 61: nothing technical reaches the screen.
        assertFalse(problem.message.contains("Exception"))
        assertFalse(problem.message.contains("Unexpected internal error"))
    }

    @Test
    fun testNothingKnownAtAllFailsGenerically() {
        assertEquals(SignInProblem.Failed(null), classify())
    }

    // ─── Shape ────────────────────────────────────────────────────────────────

    @Test
    fun testOnlyCancellationIsSilentAndCodesAreDistinct() {
        val every: List<SignInProblem> = listOf(
            SignInProblem.Cancelled(),
            SignInProblem.Misconfigured(),
            SignInProblem.NoGoogleAccount(),
            SignInProblem.Offline(),
            SignInProblem.Failed(),
        )
        assertEquals(every.size, every.map { it.code }.distinct().size)
        for (problem in every) {
            assertTrue(problem.code, problem.title.isNotBlank())
            assertTrue(problem.code, problem.message.isNotBlank())
            if (problem is SignInProblem.Cancelled) {
                assertTrue(problem.code, problem.isSilent)
            } else {
                assertFalse(problem.code, problem.isSilent)
            }
        }
    }

    @Test
    fun testPlaceholderClientIdIsRecognisedQuotedOrUnset() {
        assertTrue(SignInProblem.isPlaceholderClientId(SignInProblem.PLACEHOLDER_CLIENT_ID))
        assertTrue(SignInProblem.isPlaceholderClientId("\"YOUR_WEB_CLIENT_ID_HERE\""))
        assertTrue(SignInProblem.isPlaceholderClientId(null))
        assertTrue(SignInProblem.isPlaceholderClientId("   "))
        assertFalse(
            SignInProblem.isPlaceholderClientId("123456789012-abcdef.apps.googleusercontent.com"),
        )
    }
}
