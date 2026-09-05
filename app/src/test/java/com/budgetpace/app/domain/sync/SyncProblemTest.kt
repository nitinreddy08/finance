package com.budgetpace.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProblemTest {

    private data class Case(
        val name: String,
        val facts: FailureFacts,
        val expected: SyncProblem,
    )

    private fun assertClassifies(cases: List<Case>) {
        for (case in cases) {
            assertEquals(case.name, case.expected, classifySyncFailure(case.facts))
        }
    }

    // ─── Precedence ───────────────────────────────────────────────────────────

    @Test
    fun testCancellationOutranksEverythingElse() {
        assertClassifies(
            listOf(
                Case(
                    "coroutine cancellation with an in-flight 500",
                    FailureFacts(
                        exceptionClassNames = listOf("kotlinx.coroutines.JobCancellationException"),
                        httpStatus = 500,
                    ),
                    SyncProblem.Cancelled(),
                ),
                Case(
                    "credential cancellation with a 429",
                    FailureFacts(
                        exceptionClassNames =
                            listOf("androidx.credentials.exceptions.GetCredentialCancellationException"),
                        httpStatus = 429,
                    ),
                    SyncProblem.Cancelled(),
                ),
                Case(
                    "GMS 16 with a 401",
                    FailureFacts(httpStatus = 401, gmsStatusCode = 16),
                    SyncProblem.Cancelled(),
                ),
                Case(
                    "cancellation beats a developer error",
                    FailureFacts(
                        exceptionClassNames = listOf("java.util.concurrent.CancellationException"),
                        gmsStatusCode = 10,
                    ),
                    SyncProblem.Cancelled(),
                ),
            )
        )
    }

    @Test
    fun testMisconfigurationOutranksMissingAccountAndOffline() {
        assertClassifies(
            listOf(
                Case(
                    "GMS 10 with a missing credential",
                    FailureFacts(
                        exceptionClassNames =
                            listOf("androidx.credentials.exceptions.NoCredentialException"),
                        gmsStatusCode = 10,
                    ),
                    SyncProblem.DeveloperMisconfig("DEVELOPER_ERROR"),
                ),
                Case(
                    "missing credential beats an offline cause",
                    FailureFacts(
                        exceptionClassNames = listOf(
                            "androidx.credentials.exceptions.NoCredentialException",
                            "java.net.UnknownHostException",
                        ),
                    ),
                    SyncProblem.NoGoogleAccount(),
                ),
                Case(
                    "offline beats an HTTP status and GMS sign-in-required",
                    FailureFacts(
                        exceptionClassNames = listOf("java.net.UnknownHostException"),
                        httpStatus = 401,
                        gmsStatusCode = 4,
                    ),
                    SyncProblem.Offline("UnknownHostException"),
                ),
                Case(
                    "GMS network error beats an HTTP status",
                    FailureFacts(httpStatus = 404, gmsStatusCode = 7),
                    SyncProblem.Offline("GMS 7"),
                ),
            )
        )
    }

    // ─── Exception names ──────────────────────────────────────────────────────

    @Test
    fun testOfflineExceptionNames() {
        assertClassifies(
            listOf(
                Case(
                    "UnknownHostException",
                    FailureFacts(exceptionClassNames = listOf("java.net.UnknownHostException")),
                    SyncProblem.Offline("UnknownHostException"),
                ),
                Case(
                    "SocketTimeoutException",
                    FailureFacts(exceptionClassNames = listOf("java.net.SocketTimeoutException")),
                    SyncProblem.Offline("SocketTimeoutException"),
                ),
                Case(
                    "ConnectException",
                    FailureFacts(exceptionClassNames = listOf("java.net.ConnectException")),
                    SyncProblem.Offline("ConnectException"),
                ),
                Case(
                    "NoRouteToHostException",
                    FailureFacts(exceptionClassNames = listOf("java.net.NoRouteToHostException")),
                    SyncProblem.Offline("NoRouteToHostException"),
                ),
                Case(
                    "SSLHandshakeException",
                    FailureFacts(exceptionClassNames = listOf("javax.net.ssl.SSLHandshakeException")),
                    SyncProblem.Offline("SSLHandshakeException"),
                ),
            )
        )
    }

    @Test
    fun testNestedClassNamesAreReducedToTheirSimpleName() {
        assertEquals(
            SyncProblem.Cancelled(),
            classifySyncFailure(
                FailureFacts(exceptionClassNames = listOf("com.example.Outer\$InnerCancellationException")),
            ),
        )
        assertEquals(
            SyncProblem.NoGoogleAccount(),
            classifySyncFailure(
                FailureFacts(exceptionClassNames = listOf("com.example.Wrapper\$NoCredentialException")),
            ),
        )
    }

    // ─── GMS status codes ─────────────────────────────────────────────────────

    @Test
    fun testGmsStatusCodes() {
        assertClassifies(
            listOf(
                Case(
                    "GMS 10 DEVELOPER_ERROR",
                    FailureFacts(gmsStatusCode = 10),
                    SyncProblem.DeveloperMisconfig("DEVELOPER_ERROR"),
                ),
                Case("GMS 7 NETWORK_ERROR", FailureFacts(gmsStatusCode = 7), SyncProblem.Offline("GMS 7")),
                Case("GMS 15 TIMEOUT", FailureFacts(gmsStatusCode = 15), SyncProblem.Offline("GMS 15")),
                Case(
                    "GMS 4 SIGN_IN_REQUIRED",
                    FailureFacts(gmsStatusCode = 4),
                    SyncProblem.NeedsReconnect("GMS 4"),
                ),
                Case(
                    "an unmapped GMS code with no status is unknown",
                    FailureFacts(exceptionClassNames = listOf("com.example.ApiException"), gmsStatusCode = 8),
                    SyncProblem.Unknown("com.example.ApiException"),
                ),
            )
        )
    }

    // ─── HTTP statuses ────────────────────────────────────────────────────────

    @Test
    fun testHttpStatuses() {
        assertClassifies(
            listOf(
                Case(
                    "400 bad range means the Expenses tab is gone",
                    FailureFacts(httpStatus = 400),
                    SyncProblem.SheetUnavailable("400"),
                ),
                Case("401", FailureFacts(httpStatus = 401), SyncProblem.NeedsReconnect("401")),
                Case("404", FailureFacts(httpStatus = 404), SyncProblem.SheetUnavailable("404")),
                Case("429", FailureFacts(httpStatus = 429), SyncProblem.RateLimited("429")),
                Case("500", FailureFacts(httpStatus = 500), SyncProblem.GoogleUnavailable("500")),
                Case("503", FailureFacts(httpStatus = 503), SyncProblem.GoogleUnavailable("503")),
                Case("599", FailureFacts(httpStatus = 599), SyncProblem.GoogleUnavailable("599")),
                Case(
                    "an unmapped status falls back to the class name",
                    FailureFacts(
                        exceptionClassNames = listOf("com.google.api.client.googleapis.json.GoogleJsonResponseException"),
                        httpStatus = 418,
                    ),
                    SyncProblem.Unknown("com.google.api.client.googleapis.json.GoogleJsonResponseException"),
                ),
                Case(
                    "an unmapped status with no class name keeps the status",
                    FailureFacts(httpStatus = 418),
                    SyncProblem.Unknown("418"),
                ),
                Case(
                    "a 300 is as unmapped as a 418",
                    FailureFacts(httpStatus = 302),
                    SyncProblem.Unknown("302"),
                ),
            )
        )
    }

    @Test
    fun testForbiddenIsSplitByApiReason() {
        assertClassifies(
            listOf(
                Case(
                    "accessNotConfigured",
                    FailureFacts(httpStatus = 403, apiReason = "accessNotConfigured"),
                    SyncProblem.ApiDisabled("403"),
                ),
                Case(
                    "insufficientPermissions",
                    FailureFacts(httpStatus = 403, apiReason = "insufficientPermissions"),
                    SyncProblem.NeedsReconnect("403"),
                ),
                Case(
                    "rateLimitExceeded",
                    FailureFacts(httpStatus = 403, apiReason = "rateLimitExceeded"),
                    SyncProblem.RateLimited("403"),
                ),
                Case(
                    "userRateLimitExceeded",
                    FailureFacts(httpStatus = 403, apiReason = "userRateLimitExceeded"),
                    SyncProblem.RateLimited("403"),
                ),
                Case(
                    "quotaExceeded",
                    FailureFacts(httpStatus = 403, apiReason = "quotaExceeded"),
                    SyncProblem.RateLimited("403"),
                ),
                Case(
                    "forbidden on the file itself",
                    FailureFacts(httpStatus = 403, apiReason = "forbidden"),
                    SyncProblem.SheetUnavailable("403"),
                ),
                Case(
                    "no reason at all",
                    FailureFacts(httpStatus = 403),
                    SyncProblem.SheetUnavailable("403"),
                ),
                Case(
                    "reason casing and padding are ignored",
                    FailureFacts(httpStatus = 403, apiReason = "  ACCESSNOTCONFIGURED  "),
                    SyncProblem.ApiDisabled("403"),
                ),
            )
        )
    }

    // ─── Nothing known ────────────────────────────────────────────────────────

    @Test
    fun testEmptyFactsAreUnknownWithNoDetail() {
        assertEquals(SyncProblem.Unknown(null), classifySyncFailure(FailureFacts()))
        assertEquals(
            SyncProblem.Unknown("java.lang.IllegalStateException"),
            classifySyncFailure(
                FailureFacts(exceptionClassNames = listOf("java.lang.IllegalStateException")),
            ),
        )
    }

    // ─── Retry policy and silence ─────────────────────────────────────────────

    private val everyProblem: List<SyncProblem> = listOf(
        SyncProblem.Offline("UnknownHostException"),
        SyncProblem.NeedsReconnect("401"),
        SyncProblem.RateLimited("429"),
        SyncProblem.GoogleUnavailable("503"),
        SyncProblem.SheetUnavailable("404"),
        SyncProblem.AccountChanged("owner@example.com"),
        SyncProblem.ApiDisabled("403"),
        SyncProblem.DeveloperMisconfig("DEVELOPER_ERROR"),
        SyncProblem.NoGoogleAccount(),
        SyncProblem.Cancelled(),
        SyncProblem.Unknown("418"),
    )

    @Test
    fun testOnlyProblemsThatDoNotNeedTheOwnerAreRetriedInBackground() {
        val retryable = everyProblem.filter { it.isRetryableInBackground }.map { it.code }
        assertEquals(
            listOf(
                SyncProblem.CODE_OFFLINE,
                SyncProblem.CODE_RATE_LIMITED,
                SyncProblem.CODE_GOOGLE_UNAVAILABLE,
                SyncProblem.CODE_UNKNOWN,
            ),
            retryable,
        )
    }

    @Test
    fun testOnlyCancellationIsSilent() {
        for (problem in everyProblem) {
            if (problem is SyncProblem.Cancelled) {
                assertTrue(problem.code, problem.isSilent)
            } else {
                assertFalse(problem.code, problem.isSilent)
            }
        }
    }

    @Test
    fun testEveryProblemHasCopyAndDistinctCode() {
        val codes = everyProblem.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
        for (problem in everyProblem) {
            assertTrue(problem.code, problem.title.isNotBlank())
            assertTrue(problem.code, problem.message.isNotBlank())
        }
    }

    @Test
    fun testAccountChangedNamesThePreviousOwnerWhenKnown() {
        assertTrue(
            SyncProblem.AccountChanged("owner@example.com").message.contains("owner@example.com"),
        )
        assertFalse(SyncProblem.AccountChanged(null).message.contains("null"))
        assertFalse(SyncProblem.AccountChanged("  ").message.contains("  "))
    }

    // ─── Persistence round trip ───────────────────────────────────────────────

    @Test
    fun testEveryProblemSurvivesCodeAndDetailRoundTrip() {
        for (problem in everyProblem) {
            assertEquals(problem, SyncProblem.fromCode(problem.code, problem.detail))
        }
    }

    @Test
    fun testDetailIsPreservedAcrossTheRoundTrip() {
        val restored = SyncProblem.fromCode(SyncProblem.CODE_NEEDS_RECONNECT, "401")
        assertEquals("401", restored.detail)
        assertEquals(SyncAction.RECONNECT, restored.action)
    }

    @Test
    fun testACodeFromANewerBuildDegradesToUnknown() {
        val restored = SyncProblem.fromCode("sheet_on_fire", "999")
        assertEquals(SyncProblem.Unknown("999"), restored)
        assertTrue(restored.isRetryableInBackground)
    }
}
