package wirecli.exporting

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.feature.backup.CreateBackupResult
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.auth.toQualifiedIdOrNull
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs
import java.nio.file.Paths

internal class SdkLocalCacheBackupRuntime(
    environment: Map<String, String>,
    cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : LocalCacheBackupRuntime {
    private val coreLogic =
        CoreLogic(
            rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
            kaliumConfigs = kaliumCliConfigs(cliMode),
            userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
        )

    override fun create(
        session: AuthSession,
        password: String?,
    ): LocalCacheBackupResult {
        val userId =
            session.userId.toQualifiedIdOrNull()
                ?: return LocalCacheBackupResult.Failure("invalid active session user ID")

        return runCatching {
            runBlocking {
                coreLogic.sessionScope(userId) {
                    multiPlatformBackup.create(password = password.orEmpty(), onProgress = {})
                }
            }
        }.fold(
            onSuccess = ::mapResult,
            onFailure = { error ->
                LocalCacheBackupResult.Failure(
                    "failed to create local cache backup: ${error.message ?: error::class.simpleName}",
                )
            },
        )
    }

    private fun mapResult(result: CreateBackupResult): LocalCacheBackupResult =
        when (result) {
            is CreateBackupResult.Success -> LocalCacheBackupResult.Success(Paths.get(result.backupFilePath.toString()))
            is CreateBackupResult.Failure -> LocalCacheBackupResult.Failure("failed to create local cache backup: ${result.coreFailure}")
        }

    private fun resolveHomeDirectory(environment: Map<String, String>): String =
        environment["HOME"] ?: System.getProperty("user.home")
            ?: Paths.get("").toAbsolutePath().toString()
}
