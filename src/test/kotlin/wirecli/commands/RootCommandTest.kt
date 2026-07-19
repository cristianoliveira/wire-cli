package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService
import wirecli.profile.ProfileUpdate
import wirecli.profile.ProfileView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RootCommandTest {
    private val authenticatedHandle =
        ProfileView(
            name = "Alice",
            handle = "alice",
            email = "alice@example.com",
        )

    @Test
    fun `no-argument live state shows authenticated handle`() {
        val profileService = StubProfileService(ProfileResult.Success(authenticatedHandle))
        val result = execute(RootCommand { profileService }, emptyList())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("authenticated"))
        assertTrue(result.stdout.contains("alice"))
        assertTrue(result.stdout.contains("next:"))
    }

    @Test
    fun `no-argument live state shows login guidance when not authenticated`() {
        val profileService =
            StubProfileService(
                ProfileResult.Failure("No active session", 11),
            )
        val result = execute(RootCommand { profileService }, emptyList())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("not authenticated"))
        assertTrue(result.stdout.contains("wire login"))
        assertTrue(result.stdout.contains("next:"))
    }

    @Test
    fun `no-argument live state exits zero even when profile fails`() {
        val profileService =
            StubProfileService(
                ProfileResult.Failure("network error while fetching profile", 12),
            )
        val result = execute(RootCommand { profileService }, emptyList())

        assertEquals(0, result.exitCode)
    }

    // --help is verified through Bats integration tests (test/bats/00_smoke.bats)

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: RootCommand,
        args: List<String>,
    ): ExecutionResult {
        val stdoutBuffer = java.io.ByteArrayOutputStream()
        val stderrBuffer = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err

        var exitCode = 0
        try {
            System.setOut(java.io.PrintStream(stdoutBuffer))
            System.setErr(java.io.PrintStream(stderrBuffer))
            command.parse(args)
        } catch (programResult: ProgramResult) {
            exitCode = programResult.statusCode
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        return ExecutionResult(exitCode, stdoutBuffer.toString(Charsets.UTF_8), stderrBuffer.toString(Charsets.UTF_8))
    }

    private class StubProfileService(
        private val profileResult: ProfileResult,
    ) : ProfileService {
        override fun getCurrentProfile(): ProfileResult = profileResult

        override fun updateProfile(update: ProfileUpdate) =
            wirecli.profile.ProfileUpdateResult.Failure(
                "unsupported",
                1,
            )
    }
}
