package wirecli.importing

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.feature.backup.RestoreBackupResult
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import wirecli.auth.AuthSession
import wirecli.auth.toQualifiedIdOrNull
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

sealed interface WireBackupRestoreResult {
    data object Success : WireBackupRestoreResult

    data class Failure(val message: String) : WireBackupRestoreResult
}

interface WireBackupRuntime {
    fun restore(
        session: AuthSession,
        input: Path,
        password: String?,
    ): WireBackupRestoreResult
}

class WireBackupImporter(
    private val runtime: WireBackupRuntime,
) : Importer {
    override val source: ImportSource = ImportSource.WIRE_BACKUP

    override fun import(
        session: AuthSession,
        input: Path,
        password: String?,
    ): ImportResult {
        if (!input.exists()) return ImportResult.Failure("backup file not found: $input")

        return when (val result = runtime.restore(session, input, password)) {
            WireBackupRestoreResult.Success -> ImportResult.Success
            is WireBackupRestoreResult.Failure -> ImportResult.Failure(result.message)
        }
    }
}

internal class SdkWireBackupRuntime(
    environment: Map<String, String>,
    cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : WireBackupRuntime {
    private val coreLogic =
        CoreLogic(
            rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
            kaliumConfigs = kaliumCliConfigs(cliMode),
            userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
        )

    override fun restore(
        session: AuthSession,
        input: Path,
        password: String?,
    ): WireBackupRestoreResult {
        val userId =
            session.userId.toQualifiedIdOrNull()
                ?: return WireBackupRestoreResult.Failure("invalid active session user ID")

        return runCatching {
            runBlocking {
                coreLogic.sessionScope(userId) {
                    multiPlatformBackup.restore(input.absolutePathString().toPath(), password) {}
                }
            }
        }.fold(
            onSuccess = ::mapRestoreResult,
            onFailure = { error ->
                WireBackupRestoreResult.Failure("failed to restore backup: ${error.message ?: error::class.simpleName}")
            },
        )
    }

    private fun mapRestoreResult(result: RestoreBackupResult): WireBackupRestoreResult =
        when (result) {
            RestoreBackupResult.Success -> WireBackupRestoreResult.Success
            is RestoreBackupResult.Failure -> WireBackupRestoreResult.Failure(result.failure.cause)
        }

    private fun resolveHomeDirectory(environment: Map<String, String>): String =
        environment["HOME"] ?: System.getProperty("user.home")
            ?: Paths.get("").toAbsolutePath().toString()
}
