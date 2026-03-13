package wirecli.sync

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class StubSyncApiClient(
    private val environment: Map<String, String>,
) : SyncApiClient {
    private val defaultHealthMetrics =
        HealthMetrics(
            lag_ms = 100L,
            pending_messages = 5,
            mls_pct = 85,
            timestamp = "2025-03-13T10:30:00Z",
        )

    private val defaultDegradedMetrics =
        HealthMetrics(
            lag_ms = 5000L,
            pending_messages = 250,
            mls_pct = 45,
            timestamp = "2025-03-13T10:35:00Z",
        )

    private val defaultErrorMetrics =
        HealthMetrics(
            lag_ms = 30000L,
            pending_messages = 1000,
            mls_pct = 10,
            timestamp = "2025-03-13T10:40:00Z",
        )

    override fun getSyncStatus(session: AuthSession): SyncStatusResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "status_ready" ->
                SyncStatusResult.Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = defaultHealthMetrics,
                        ),
                )

            "status_initializing" ->
                SyncStatusResult.Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.INITIALIZING,
                            metrics =
                                HealthMetrics(
                                    lag_ms = 2000L,
                                    pending_messages = 100,
                                    mls_pct = 20,
                                    timestamp = "2025-03-13T10:32:00Z",
                                ),
                        ),
                )

            "status_degraded" ->
                SyncStatusResult.Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.DEGRADED,
                            metrics = defaultDegradedMetrics,
                        ),
                )

            "status_error" ->
                SyncStatusResult.Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.ERROR,
                            metrics = defaultErrorMetrics,
                        ),
                )

            "network_error" ->
                SyncStatusResult.Failure(
                    message = SyncMessages.NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "server_error" ->
                SyncStatusResult.Failure(
                    message = SyncMessages.SERVER_FAILURE,
                    exitCode = SyncExitCodes.SERVER_ERROR,
                )

            "unauthorized" ->
                SyncStatusResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                SyncStatusResult.Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = defaultHealthMetrics,
                        ),
                )
        }
    }

    override fun getDiagnostics(session: AuthSession): DiagnosticsResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "status_ready", "diagnostics_healthy" ->
                DiagnosticsResult.Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "healthy",
                                        details = "Sync engine is running and responsive",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "healthy",
                                        details = "Event queue is processing normally (lag: 100ms)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "healthy",
                                        details = "Key packages available: 42 remaining",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "healthy",
                                        details = "Network connection is stable and responsive",
                                    ),
                                ),
                            summary = "All systems operational",
                        ),
                )

            "status_initializing", "diagnostics_initializing" ->
                DiagnosticsResult.Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "initializing",
                                        details = "Sync engine is initializing (2/5 steps complete)",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "initializing",
                                        details = "Event queue is being initialized",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "initializing",
                                        details = "Key packages are being loaded",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "healthy",
                                        details = "Network connection is stable",
                                    ),
                                ),
                            summary = "System is initializing",
                        ),
                )

            "status_degraded", "diagnostics_degraded" ->
                DiagnosticsResult.Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "degraded",
                                        details = "Sync engine is running but experiencing delays",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "degraded",
                                        details = "Event queue is backlogged (lag: 5000ms, pending: 250)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "degraded",
                                        details = "Key packages running low: 3 remaining",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "degraded",
                                        details = "Network latency detected (200ms avg)",
                                    ),
                                ),
                            summary = "System is degraded, operation continues but performance is reduced",
                        ),
                )

            "status_error", "diagnostics_error" ->
                DiagnosticsResult.Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "error",
                                        details = "Sync engine encountered a critical error and stopped",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "error",
                                        details = "Event queue has stopped processing (lag: 30000ms, pending: 1000)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "error",
                                        details = "Key package retrieval failed",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "error",
                                        details = "Network connection is unstable or unavailable",
                                    ),
                                ),
                            summary = "System encountered critical errors, immediate action required",
                        ),
                )

            "diagnostics_network_error" ->
                DiagnosticsResult.Failure(
                    message = SyncMessages.DIAGNOSTICS_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "diagnostics_server_error" ->
                DiagnosticsResult.Failure(
                    message = SyncMessages.DIAGNOSTICS_SERVER_FAILURE,
                    exitCode = SyncExitCodes.SERVER_ERROR,
                )

            "diagnostics_unauthorized" ->
                DiagnosticsResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                DiagnosticsResult.Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "healthy",
                                        details = "Sync engine is running and responsive",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "healthy",
                                        details = "Event queue is processing normally (lag: 100ms)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "healthy",
                                        details = "Key packages available: 42 remaining",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "healthy",
                                        details = "Network connection is stable and responsive",
                                    ),
                                ),
                            summary = "All systems operational",
                        ),
                )
        }
    }
}
