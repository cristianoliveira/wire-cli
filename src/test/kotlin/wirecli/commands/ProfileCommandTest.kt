package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService
import wirecli.profile.ProfileUpdate
import wirecli.profile.ProfileUpdateResult
import wirecli.profile.ProfileView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileCommandTest {
    @Test
    fun `name command updates display name`() {
        val service = StubProfileService()
        val command = ProfileNameCommand { service }

        val result = execute(command, listOf("Cristiano Oliveira"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Profile updated successfully."))
        assertTrue(result.stdout.contains("Name: Cristiano Oliveira"))
        assertEquals(ProfileUpdate(name = "Cristiano Oliveira"), service.lastUpdate)
    }

    @Test
    fun `name command rejects blank display name`() {
        val service = StubProfileService()
        val command = ProfileNameCommand { service }

        val result = execute(command, listOf("   "))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: name required", result.stderr.trim())
        assertEquals(null, service.lastUpdate)
    }

    @Test
    fun `name command rejects empty display name`() {
        val service = StubProfileService()
        val command = ProfileNameCommand { service }

        val result = execute(command, listOf(""))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: name required", result.stderr.trim())
        assertEquals(null, service.lastUpdate)
    }

    @Test
    fun `name command returns service failure`() {
        val command =
            ProfileNameCommand {
                StubProfileService(
                    updateResult =
                        ProfileUpdateResult.Failure(
                            message = "Profile update service is unavailable. Retry later or check server settings.",
                            exitCode = 13,
                        ),
                )
            }

        val result = execute(command, listOf("Cristiano"))

        assertEquals(1, result.exitCode)
        assertEquals(
            "Profile update service is unavailable. Retry later or check server settings.",
            result.stderr.trim(),
        )
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: ProfileNameCommand,
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

        return ExecutionResult(
            exitCode = exitCode,
            stdout = stdoutBuffer.toString(Charsets.UTF_8),
            stderr = stderrBuffer.toString(Charsets.UTF_8),
        )
    }

    private class StubProfileService(
        private val updateResult: ProfileUpdateResult =
            ProfileUpdateResult.Success(ProfileView(name = "Cristiano Oliveira", email = null, handle = null)),
    ) : ProfileService {
        var lastUpdate: ProfileUpdate? = null
            private set

        override fun getCurrentProfile(): ProfileResult {
            return ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane"))
        }

        override fun updateProfile(update: ProfileUpdate): ProfileUpdateResult {
            lastUpdate = update
            return updateResult
        }
    }
}
