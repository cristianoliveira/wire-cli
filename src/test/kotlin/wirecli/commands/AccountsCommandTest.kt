package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.auth.AccountsListing
import wirecli.auth.AccountsService
import wirecli.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountsCommandTest {
    private val alice = AuthSession("alice@wire.com", "tok-a", "wire.com")
    private val bob = AuthSession("bob@wire.com", "tok-b", null)

    @Test
    fun `accounts list marks the active account with a star`() {
        val service = FakeAccountsService(AccountsListing(listOf(alice, bob), activeUserId = "bob@wire.com"))
        val result = run(listOf("list"), service)

        assertEquals(0, result.exitCode)
        val lines = result.stdout.trimEnd().lines()
        assertEquals(2, lines.size)
        assertTrue(lines.any { it.startsWith("* bob@wire.com") })
        assertTrue(lines.any { it.startsWith("  alice@wire.com  (wire.com)") })
    }

    @Test
    fun `accounts list reports when there are no stored accounts`() {
        val service = FakeAccountsService(AccountsListing(emptyList(), activeUserId = null))
        val result = run(listOf("list"), service)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("No stored accounts"))
    }

    @Test
    fun `accounts use switches and prints confirmation`() {
        val service = FakeAccountsService(AccountsListing(listOf(alice), activeUserId = "alice@wire.com"))
        val result = run(listOf("use", "alice@wire.com"), service)

        assertEquals(0, result.exitCode)
        assertEquals("alice@wire.com", service.useCalledWith)
        assertTrue(result.stdout.contains("Switched to alice@wire.com"))
    }

    @Test
    fun `accounts use exits with validation error for an unknown account`() {
        val service = FakeAccountsService(AccountsListing(emptyList(), activeUserId = null))
        val result = run(listOf("use", "ghost@wire.com"), service)

        assertEquals(processExitCode(wirecli.auth.ExitCodes.VALIDATION_ERROR), result.exitCode)
        assertTrue(result.stderr.contains("No stored account for 'ghost@wire.com'"))
    }

    @Test
    fun `accounts remove removes and prints confirmation`() {
        val service = FakeAccountsService(AccountsListing(listOf(alice), activeUserId = "alice@wire.com"))
        val result = run(listOf("remove", "alice@wire.com"), service)

        assertEquals(0, result.exitCode)
        assertEquals("alice@wire.com", service.removeCalledWith)
        assertTrue(result.stdout.contains("Removed alice@wire.com"))
    }

    @Test
    fun `whoami prints the active account`() {
        val service = FakeAccountsService(AccountsListing(listOf(alice), activeUserId = "alice@wire.com"))
        val result = runWhoami(emptyList(), service)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("alice@wire.com"))
    }

    @Test
    fun `whoami errors when no account is active`() {
        val service = FakeAccountsService(AccountsListing(emptyList(), activeUserId = null))
        val result = runWhoami(emptyList(), service)

        assertTrue(result.exitCode != 0)
        assertTrue(result.stderr.contains("No active account"))
    }

    private data class ExecutionResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun run(
        args: List<String>,
        service: FakeAccountsService,
    ): ExecutionResult =
        execute(args) {
            AccountsCommand { service }
        }

    private fun runWhoami(
        args: List<String>,
        service: FakeAccountsService,
    ): ExecutionResult =
        execute(args) {
            WhoamiCommand { service }
        }

    private fun execute(
        args: List<String>,
        build: () -> Any,
    ): ExecutionResult {
        val stdoutBuffer = java.io.ByteArrayOutputStream()
        val stderrBuffer = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        var exitCode = 0
        try {
            System.setOut(java.io.PrintStream(stdoutBuffer))
            System.setErr(java.io.PrintStream(stderrBuffer))
            when (val command = build()) {
                is AccountsCommand -> command.parse(args)
                is WhoamiCommand -> command.parse(args)
            }
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

    private class FakeAccountsService(
        private val listing: AccountsListing,
        private val current: AuthSession? = listing.accounts.firstOrNull { it.userId == listing.activeUserId },
    ) : AccountsService {
        var useCalledWith: String? = null
        var removeCalledWith: String? = null

        override fun listAccounts(): AccountsListing = listing

        override fun currentAccount(): AuthSession? = current

        override fun useAccount(userId: String): AuthSession? {
            useCalledWith = userId
            return listing.accounts.firstOrNull { it.userId == userId }
        }

        override fun removeAccount(userId: String): AuthSession? {
            removeCalledWith = userId
            return listing.accounts.firstOrNull { it.userId == userId }
        }
    }
}
