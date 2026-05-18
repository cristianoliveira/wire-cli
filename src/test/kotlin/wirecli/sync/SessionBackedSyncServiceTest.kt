package wirecli.sync

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedSyncServiceTest {
    @Test
    fun `delegates forceSyncAndWait to backend for persisted session`() {
        val expected: SyncStatusResult =
            SyncStatusResult.Success(
                SyncStatusView(
                    status = SyncStatus.READY,
                    metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                ),
            )
        val service =
            SessionBackedSyncService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeSyncApiClient(forceSyncResult = expected),
            )

        val result = service.forceSyncAndWait()

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.READY, success.view.status)
    }

    @Test
    fun `returns unauthorized when no session is persisted for getCurrentSyncStatus`() {
        val service =
            SessionBackedSyncService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeSyncApiClient(
                        statusResult =
                            SyncStatusResult.Success(
                                SyncStatusView(
                                    status = SyncStatus.READY,
                                    metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                                ),
                            ),
                    ),
            )

        val result = service.getCurrentSyncStatus()

        val failure = assertIs<SyncStatusResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns backend sync status result for persisted session`() {
        val expected: SyncStatusResult =
            SyncStatusResult.Success(
                SyncStatusView(
                    status = SyncStatus.READY,
                    metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                ),
            )
        val service =
            SessionBackedSyncService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeSyncApiClient(statusResult = expected),
            )

        val result = service.getCurrentSyncStatus()

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.READY, success.view.status)
        assertEquals(100L, success.view.metrics.lagMs)
    }

    @Test
    fun `returns unauthorized when no session is persisted for getDiagnosticsReport`() {
        val service =
            SessionBackedSyncService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeSyncApiClient(
                        diagnosticsResult =
                            DiagnosticsResult.Success(
                                DiagnosticsReport(
                                    checks = emptyList(),
                                    summary = "OK",
                                ),
                            ),
                    ),
            )

        val result = service.getDiagnosticsReport()

        val failure = assertIs<DiagnosticsResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates getDiagnosticsReport to backend for persisted session`() {
        val expected: DiagnosticsResult =
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
        val apiClient = FakeSyncApiClient(diagnosticsResult = expected)
        val service =
            SessionBackedSyncService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = apiClient,
            )

        val result = service.getDiagnosticsReport()

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(1, success.report.checks.size)
        assertEquals("All systems operational", success.report.summary)
    }

    @Test
    fun `returns backend failure result on api client failure for getCurrentSyncStatus`() {
        val expected: SyncStatusResult =
            SyncStatusResult.Failure(
                message = SyncMessages.NETWORK_FAILURE,
                exitCode = ExitCodes.NETWORK_ERROR,
            )
        val service =
            SessionBackedSyncService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeSyncApiClient(statusResult = expected),
            )

        val result = service.getCurrentSyncStatus()

        val failure = assertIs<SyncStatusResult.Failure>(result)
        assertEquals(SyncMessages.NETWORK_FAILURE, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `returns backend failure result on api client failure for getDiagnosticsReport`() {
        val expected: DiagnosticsResult =
            DiagnosticsResult.Failure(
                message = SyncMessages.DIAGNOSTICS_SERVER_FAILURE,
                exitCode = SyncExitCodes.SERVER_ERROR,
            )
        val service =
            SessionBackedSyncService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeSyncApiClient(diagnosticsResult = expected),
            )

        val result = service.getDiagnosticsReport()

        val failure = assertIs<DiagnosticsResult.Failure>(result)
        assertEquals(SyncMessages.DIAGNOSTICS_SERVER_FAILURE, failure.message)
        assertEquals(SyncExitCodes.SERVER_ERROR, failure.exitCode)
    }

    private class FakeSessionStore(private val activeSession: AuthSession?) : SessionProvider {
        override fun readActiveSession(): AuthSession? = activeSession
    }

    private class FakeSyncApiClient(
        private val statusResult: SyncStatusResult? = null,
        private val diagnosticsResult: DiagnosticsResult? = null,
        private val forceSyncResult: SyncStatusResult? = null,
    ) : SyncApiClient {
        override fun forceSyncAndWait(session: AuthSession): SyncStatusResult =
            forceSyncResult
                ?: SyncStatusResult.Success(
                    SyncStatusView(
                        status = SyncStatus.READY,
                        metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                    ),
                )

        override fun getSyncStatus(session: AuthSession): SyncStatusResult =
            statusResult
                ?: SyncStatusResult.Success(
                    SyncStatusView(
                        status = SyncStatus.READY,
                        metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                    ),
                )

        override fun getDiagnostics(session: AuthSession): DiagnosticsResult =
            diagnosticsResult
                ?: DiagnosticsResult.Success(
                    DiagnosticsReport(
                        checks = emptyList(),
                        summary = "All systems operational",
                    ),
                )

        override fun resetSync(
            session: AuthSession,
            force: Boolean,
        ): ResetResult {
            return ResetResult.Success("Reset successful (test mode)")
        }

        override fun getConversationSyncStatus(
            session: AuthSession,
            conversationId: String,
        ): ConversationSyncStatusResult {
            return ConversationSyncStatusResult.Success(
                ConversationSyncStatus(
                    conversationId = conversationId,
                    status = SyncStatus.READY,
                    metrics = ConversationMetrics(conversationId, 100L, 0, 100, "2025-03-13T10:30:00Z"),
                    lastSyncTimestamp = "2025-03-13T10:30:00Z",
                ),
            )
        }

        override fun getPerConversationDiagnostics(
            session: AuthSession,
            conversationId: String,
        ): PerConversationDiagnosticsResult {
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
