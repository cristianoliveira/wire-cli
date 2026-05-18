package wirecli.sync

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthGuardedSyncServiceTest {
    @Test
    fun `returns auth failure when getCurrentSyncStatus called without session`() {
        val service =
            AuthGuardedSyncService(
                authSessionService = FakeAuthSessionService(isAuthorized = false),
                delegate = FakeSyncService(),
            )

        val result = service.getCurrentSyncStatus()

        val failure = assertIs<SyncStatusResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates getCurrentSyncStatus when session is valid`() {
        val expectedResult: SyncStatusResult =
            SyncStatusResult.Success(
                SyncStatusView(
                    status = SyncStatus.READY,
                    metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                ),
            )
        val delegate = FakeSyncService(statusResult = expectedResult)
        val service =
            AuthGuardedSyncService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.getCurrentSyncStatus()

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.READY, success.view.status)
        assertEquals(100L, success.view.metrics.lagMs)
    }

    @Test
    fun `delegates forceSyncAndWait when session is valid`() {
        val expectedResult: SyncStatusResult =
            SyncStatusResult.Success(
                SyncStatusView(
                    status = SyncStatus.READY,
                    metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                ),
            )
        val delegate = FakeSyncService(statusResult = expectedResult)
        val service =
            AuthGuardedSyncService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.forceSyncAndWait()

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.READY, success.view.status)
    }

    @Test
    fun `returns auth failure when getDiagnosticsReport called without session`() {
        val service =
            AuthGuardedSyncService(
                authSessionService = FakeAuthSessionService(isAuthorized = false),
                delegate = FakeSyncService(),
            )

        val result = service.getDiagnosticsReport()

        val failure = assertIs<DiagnosticsResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates getDiagnosticsReport when session is valid`() {
        val expectedResult: DiagnosticsResult =
            DiagnosticsResult.Success(
                DiagnosticsReport(
                    checks =
                        listOf(
                            Check(
                                name = "Authentication",
                                status = "healthy",
                                details = "Session is valid",
                            ),
                        ),
                    summary = "All systems operational",
                ),
            )
        val delegate = FakeSyncService(diagnosticsResult = expectedResult)
        val service =
            AuthGuardedSyncService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.getDiagnosticsReport()

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(1, success.report.checks.size)
        assertEquals("All systems operational", success.report.summary)
    }

    @Test
    fun `returns auth failure message from authSessionService`() {
        val authMessage = "Your session has expired"
        val service =
            AuthGuardedSyncService(
                authSessionService = FakeAuthSessionService(isAuthorized = false, failureMessage = authMessage),
                delegate = FakeSyncService(),
            )

        val result = service.getCurrentSyncStatus()

        val failure = assertIs<SyncStatusResult.Failure>(result)
        assertEquals(authMessage, failure.message)
    }

    @Test
    fun `delegates multiple calls independently`() {
        val statusResult: SyncStatusResult =
            SyncStatusResult.Success(
                SyncStatusView(
                    status = SyncStatus.DEGRADED,
                    metrics = HealthMetrics(5000L, 250, 45, "2025-03-13T10:35:00Z"),
                ),
            )
        val diagnosticsResult: DiagnosticsResult =
            DiagnosticsResult.Success(
                DiagnosticsReport(
                    checks =
                        listOf(
                            Check("Sync Engine", "degraded", "Running with delays"),
                        ),
                    summary = "System is degraded",
                ),
            )
        val delegate = FakeSyncService(statusResult = statusResult, diagnosticsResult = diagnosticsResult)
        val service =
            AuthGuardedSyncService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val status = service.getCurrentSyncStatus()
        val diagnostics = service.getDiagnosticsReport()

        val statusSuccess = assertIs<SyncStatusResult.Success>(status)
        val diagnosticsSuccess = assertIs<DiagnosticsResult.Success>(diagnostics)

        assertEquals(SyncStatus.DEGRADED, statusSuccess.view.status)
        assertEquals("System is degraded", diagnosticsSuccess.report.summary)
    }

    private class FakeAuthSessionService(
        private val isAuthorized: Boolean,
        private val failureMessage: String = "No active session",
    ) : AuthSessionService {
        override fun login(input: wirecli.auth.LoginInput): AuthResult {
            throw NotImplementedError()
        }

        override fun logout(): AuthResult {
            throw NotImplementedError()
        }

        override fun requireActiveSession(): AuthResult {
            return if (isAuthorized) {
                AuthResult.Success("Session is valid")
            } else {
                AuthResult.Failure(failureMessage, ExitCodes.UNAUTHORIZED)
            }
        }
    }

    private class FakeSyncService(
        private val statusResult: SyncStatusResult =
            SyncStatusResult.Success(
                SyncStatusView(
                    status = SyncStatus.READY,
                    metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                ),
            ),
        private val diagnosticsResult: DiagnosticsResult =
            DiagnosticsResult.Success(
                DiagnosticsReport(
                    checks = emptyList(),
                    summary = "All systems operational",
                ),
            ),
    ) : SyncService {
        override fun forceSyncAndWait(): SyncStatusResult = statusResult

        override fun getCurrentSyncStatus(): SyncStatusResult = statusResult

        override fun getDiagnosticsReport(): DiagnosticsResult = diagnosticsResult

        override fun resetSync(force: Boolean): ResetResult {
            return ResetResult.Success("Reset successful (test mode)")
        }

        override fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult {
            return ConversationSyncStatusResult.Success(
                ConversationSyncStatus(
                    conversationId = conversationId,
                    status = SyncStatus.READY,
                    metrics = ConversationMetrics(conversationId, 100L, 0, 100, "2025-03-13T10:30:00Z"),
                    lastSyncTimestamp = "2025-03-13T10:30:00Z",
                ),
            )
        }

        override fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult {
            return PerConversationDiagnosticsResult.Success(
                PerConversationDiagnosticsReport(
                    conversationId = conversationId,
                    checks = emptyList(),
                    summary = "Conversation is healthy",
                ),
            )
        }
    }
}
