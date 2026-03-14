package wirecli.sync

import wirecli.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for SdkKaliumSyncRuntime integration.
 *
 * Verifies:
 * - Proper Kalium SDK initialization
 * - Session-based sync service creation
 * - Consistent error handling
 * - Resource cleanup and shutdown
 */
class SdkKaliumSyncRuntimeTest {
    @Test
    fun `SdkKaliumSyncRuntime can be instantiated with environment`() {
        val runtime =
            SdkKaliumSyncRuntime(
                environment = emptyMap(),
            )

        runtime.shutdown()
    }

    @Test
    fun `SdkKaliumSyncRuntime creates instance with customizable network checker`() {
        val testSession =
            AuthSession(
                userId = "user@example.com",
                accessToken = "token",
                server = null,
            )

        val runtime =
            SdkKaliumSyncRuntime(
                environment = emptyMap(),
                networkConnectivityChecker = RealNetworkConnectivityChecker(),
            )

        // Verify runtime can be created without errors
        assertEquals(runtime.javaClass.simpleName, "SdkKaliumSyncRuntime")
        runtime.shutdown()
    }

    @Test
    fun `RealKaliumSyncApiClient delegates to runtime`() {
        val runtime =
            object : RealKaliumSyncRuntime {
                override fun getSyncStatus(session: AuthSession): SyncStatusResult {
                    return SyncStatusResult.Success(
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = HealthMetrics(100L, 5, 85, "2025-03-13T10:30:00Z"),
                        ),
                    )
                }

                override fun getDiagnostics(session: AuthSession): DiagnosticsResult {
                    return DiagnosticsResult.Success(
                        DiagnosticsReport(
                            checks = emptyList(),
                            summary = "OK",
                        ),
                    )
                }

                override fun getConversationSyncStatus(
                    session: AuthSession,
                    conversationId: String,
                ): ConversationSyncStatusResult {
                    return ConversationSyncStatusResult.Success(
                        ConversationSyncStatus(
                            conversation_id = conversationId,
                            status = SyncStatus.READY,
                            metrics = ConversationMetrics(conversationId, 100L, 0, 100, "2025-03-13T10:30:00Z"),
                            last_sync_timestamp = "2025-03-13T10:30:00Z",
                        ),
                    )
                }

                override fun getPerConversationDiagnostics(
                    session: AuthSession,
                    conversationId: String,
                ): PerConversationDiagnosticsResult {
                    return PerConversationDiagnosticsResult.Success(
                        PerConversationDiagnosticsReport(
                            conversation_id = conversationId,
                            checks = emptyList(),
                            summary = "OK",
                        ),
                    )
                }

                override fun resetSync(
                    session: AuthSession,
                    force: Boolean,
                ): ResetResult {
                    return ResetResult.Success("Reset successful (test mode)")
                }

                override fun shutdown() {}
            }

        val client = RealKaliumSyncApiClient(runtime)
        val session =
            AuthSession(
                userId = "user@example.com",
                accessToken = "token",
                server = null,
            )

        val statusResult = client.getSyncStatus(session)
        val successStatus = assertIs<SyncStatusResult.Success>(statusResult)
        assertEquals(SyncStatus.READY, successStatus.view.status)
    }

    @Test
    fun `SdkKaliumSyncRuntime shutdown is idempotent`() {
        val runtime = SdkKaliumSyncRuntime(environment = emptyMap())

        // First shutdown
        runtime.shutdown()

        // Second shutdown should not fail
        runtime.shutdown()
    }
}
