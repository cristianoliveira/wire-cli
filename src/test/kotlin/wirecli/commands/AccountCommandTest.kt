package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.auth.AccountListing
import wirecli.auth.AccountService
import wirecli.auth.StoredAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountCommandTest {
    private val alice = StoredAccount("alice@wire.com", "tok-a", "wire.com", label = "work")
    private val bob = StoredAccount("bob@wire.com", "tok-b", null, label = null)

    @Test
    fun `account list marks the active account and shows labels when present`() {
        val service = FakeAccountService(AccountListing(listOf(alice, bob), activeUserId = "alice@wire.com"))
        val result = run(listOf("list"), service)

        assertEquals(0, result.exitCode)
        val lines = result.stdout.trimEnd().lines()
        assertEquals(2, lines.size)
        assertTrue(lines.any { it.startsWith("* work  alice@wire.com  (wire.com)") })
        assertTrue(lines.any { it.startsWith("  bob@wire.com") })
    }

    @Test
    fun `account list reports when there are no stored accounts`() {
        val service = FakeAccountService(AccountListing(emptyList(), activeUserId = null))
        val result = run(listOf("list"), service)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("No stored accounts"))
    }

    @Test
    fun `account use resolves a label and prints a labeled confirmation`() {
        val service = FakeAccountService(AccountListing(listOf(alice), activeUserId = "alice@wire.com"))
        val result = run(listOf("use", "work"), service)

        assertEquals(0, result.exitCode)
        assertEquals("work", service.useCalledWith)
        assertTrue(result.stdout.contains("Switched to work (alice@wire.com)"))
    }

    @Test
    fun `account use accepts a userId when no label is set`() {
        val service = FakeAccountService(AccountListing(listOf(bob), activeUserId = "bob@wire.com"))
        val result = run(listOf("use", "bob@wire.com"), service)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Switched to bob@wire.com"))
    }

    @Test
    fun `account use exits with validation error for an unknown account`() {
        val service = FakeAccountService(AccountListing(emptyList(), activeUserId = null))
        val result = run(listOf("use", "ghost"), service)

        assertEquals(processExitCode(wirecli.auth.ExitCodes.VALIDATION_ERROR), result.exitCode)
        assertTrue(result.stderr.contains("No stored account for 'ghost'"))
    }

    @Test
    fun `account remove removes and prints confirmation`() {
        val service = FakeAccountService(AccountListing(listOf(alice), activeUserId = "alice@wire.com"))
        val result = run(listOf("remove", "work"), service)

        assertEquals(0, result.exitCode)
        assertEquals("work", service.removeCalledWith)
        assertTrue(result.stdout.contains("Removed work (alice@wire.com)"))
    }

    @Test
    fun `whoami prints label and userId when labeled`() {
        val service = FakeAccountService(AccountListing(listOf(alice), activeUserId = "alice@wire.com"))
        val result = runWhoami(emptyList(), service)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("work  alice@wire.com"))
    }

    @Test
    fun `whoami errors when no account is active`() {
        val service = FakeAccountService(AccountListing(emptyList(), activeUserId = null))
        val result = runWhoami(emptyList(), service)

        assertTrue(result.exitCode != 0)
        assertTrue(result.stderr.contains("No active account"))
    }

    private data class ExecutionResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun run(
        args: List<String>,
        service: FakeAccountService,
    ): ExecutionResult =
        execute(args) {
            AccountCommand { service }
        }

    private fun runWhoami(
        args: List<String>,
        service: FakeAccountService,
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
                is AccountCommand -> command.parse(args)
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

    private class FakeAccountService(
        private val listing: AccountListing,
        private val current: StoredAccount? = listing.accounts.firstOrNull { it.userId == listing.activeUserId },
    ) : AccountService {
        var useCalledWith: String? = null
        var removeCalledWith: String? = null

        override fun listAccounts(): AccountListing = listing

        override fun currentAccount(): StoredAccount? = current

        override fun useAccount(selector: String): StoredAccount? {
            useCalledWith = selector
            return listing.accounts.resolve(selector)
        }

        override fun removeAccount(selector: String): StoredAccount? {
            removeCalledWith = selector
            return listing.accounts.resolve(selector)
        }

        private fun List<StoredAccount>.resolve(selector: String): StoredAccount? =
            firstOrNull { it.label == selector } ?: firstOrNull { it.userId == selector }
    }
}
