package wirecli.sync

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedSyncService(
    private val authSessionService: AuthSessionService,
    private val delegate: SyncService,
) : SyncService {
    override fun getCurrentSyncStatus(): SyncStatusResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getCurrentSyncStatus()
            is AuthResult.Failure ->
                SyncStatusResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getDiagnosticsReport(): DiagnosticsResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getDiagnosticsReport()
            is AuthResult.Failure ->
                DiagnosticsResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}
