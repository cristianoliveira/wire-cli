package wirecli.auth

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileAuthSessionStoreTest {
    private fun newStore(): Pair<FileAuthSessionStore, File> {
        val dir = Files.createTempDirectory("wirecli-auth-session-test")
        val file = dir.resolve("session").toFile()
        return FileAuthSessionStore(sessionFile = file) to file
    }

    private fun account(
        userId: String,
        token: String = "tok-$userId",
        server: String? = null,
        label: String? = null,
    ) = StoredAccount(userId = userId, accessToken = token, server = server, label = label)

    @Test
    fun `addAccount throws when user id is blank`() {
        val (store, _) = newStore()
        assertFailsWith<IllegalArgumentException> {
            store.addAccount(StoredAccount(userId = " ", accessToken = "token", server = null))
        }
    }

    @Test
    fun `readActiveSession throws when session file path is blank`() {
        val store = FileAuthSessionStore(sessionFile = File(""))
        assertFailsWith<IllegalStateException> { store.readActiveSession() }
    }

    @Test
    fun `readAccounts is empty when no file exists`() {
        val (store, _) = newStore()
        val inventory = store.readAccounts()
        assertTrue(inventory.accounts.isEmpty())
        assertNull(inventory.activeUserId)
    }

    @Test
    fun `addAccount marks the account active by default`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))

        val active = store.readActiveSession()
        assertEquals("alice@wire.com", active?.userId)
        assertEquals("alice@wire.com", store.readAccounts().activeUserId)
    }

    @Test
    fun `addAccount is additive and preserves other accounts`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))
        store.addAccount(account("bob@wire.com"))

        val accounts = store.readAccounts().accounts.map { it.userId }.sorted()
        assertEquals(listOf("alice@wire.com", "bob@wire.com"), accounts)
    }

    @Test
    fun `addAccount replaces an existing account for the same user id`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com", token = "old"))
        store.addAccount(account("alice@wire.com", token = "new"))

        val inventory = store.readAccounts()
        assertEquals(1, inventory.accounts.size)
        assertEquals("new", inventory.accounts.single().accessToken)
    }

    @Test
    fun `addAccount with makeActive false keeps the previous active pointer`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))
        store.addAccount(account("bob@wire.com"), makeActive = false)

        assertEquals("alice@wire.com", store.readAccounts().activeUserId)
        assertEquals("alice@wire.com", store.readActiveSession()?.userId)
    }

    @Test
    fun `setActiveAccount switches the active pointer and returns the account`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))
        store.addAccount(account("bob@wire.com")) // bob becomes active

        val switched = store.setActiveAccount("alice@wire.com")

        assertEquals("alice@wire.com", switched?.userId)
        assertEquals("alice@wire.com", store.readAccounts().activeUserId)
        assertEquals("alice@wire.com", store.readActiveSession()?.userId)
    }

    @Test
    fun `setActiveAccount returns null for an unknown account and keeps state`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))

        assertNull(store.setActiveAccount("ghost@wire.com"))
        assertEquals("alice@wire.com", store.readAccounts().activeUserId)
    }

    @Test
    fun `removeAccount removes a single account and returns it`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))
        store.addAccount(account("bob@wire.com"))

        val removed = store.removeAccount("alice@wire.com")

        assertEquals("alice@wire.com", removed?.userId)
        assertEquals(listOf("bob@wire.com"), store.readAccounts().accounts.map { it.userId })
    }

    @Test
    fun `removeAccount of the active account clears the active pointer`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))
        store.addAccount(account("bob@wire.com")) // bob active

        store.removeAccount("bob@wire.com")

        val inventory = store.readAccounts()
        assertEquals(listOf("alice@wire.com"), inventory.accounts.map { it.userId })
        assertNull(inventory.activeUserId)
        assertNull(store.readActiveSession())
    }

    @Test
    fun `removeAccount of a non-active account keeps the active pointer`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))
        store.addAccount(account("bob@wire.com")) // bob active

        store.removeAccount("alice@wire.com")

        assertEquals("bob@wire.com", store.readAccounts().activeUserId)
    }

    @Test
    fun `removeAccount of the last account deletes the session file`() {
        val (store, file) = newStore()
        store.addAccount(account("alice@wire.com"))
        assertTrue(file.exists())

        store.removeAccount("alice@wire.com")

        assertFalse(file.exists())
        assertNull(store.readActiveSession())
        assertTrue(store.readAccounts().accounts.isEmpty())
    }

    @Test
    fun `removeAccount returns null for an unknown account`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com"))

        assertNull(store.removeAccount("ghost@wire.com"))
    }

    @Test
    fun `writes persist across store instances`() {
        val (_, file) = newStore()
        val first = FileAuthSessionStore(sessionFile = file)
        first.addAccount(account("alice@wire.com"))

        val second = FileAuthSessionStore(sessionFile = file)
        assertEquals("alice@wire.com", second.readActiveSession()?.userId)
    }

    @Test
    fun `v1 session file is migrated to v2 with active pointer preserved`() {
        val (_, file) = newStore()
        val v1Content =
            listOf(
                "wire-cli-session-store:1",
                "alice@wire.com",
                "token-v1",
                "wire.com",
            ).joinToString("\n")
        file.writeText(v1Content)

        val store = FileAuthSessionStore(sessionFile = file)
        val inventory = store.readAccounts()

        assertEquals("alice@wire.com", inventory.activeUserId)
        assertEquals("alice@wire.com", inventory.activeAccount?.userId)
        assertEquals("token-v1", inventory.activeAccount?.accessToken)

        // Migration rewrote the file to v3.
        val rewritten = file.readLines()
        assertEquals("wire-cli-session-store:3", rewritten.first())
        assertTrue(rewritten.any { it.startsWith("active:") })
    }

    @Test
    fun `legacy session file without schema header is migrated`() {
        val (_, file) = newStore()
        file.writeText("alice@wire.com\ntoken-legacy\n\n")

        val store = FileAuthSessionStore(sessionFile = file)
        assertEquals("alice@wire.com", store.readActiveSession()?.userId)
        assertEquals("wire-cli-session-store:3", file.readLines().first())
    }

    @Test
    fun `addAccount persists and reads an optional label`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com", label = "work"))

        val stored = store.readAccounts().accounts.single()
        assertEquals("work", stored.label)
    }

    @Test
    fun `labels survive a v3 round-trip through disk`() {
        val (_, file) = newStore()
        FileAuthSessionStore(sessionFile = file).addAccount(account("alice@wire.com", label = "work"))
        FileAuthSessionStore(sessionFile = file).addAccount(account("bob@wire.com", label = "personal"))

        val inventory = FileAuthSessionStore(sessionFile = file).readAccounts()
        assertEquals(
            mapOf("alice@wire.com" to "work", "bob@wire.com" to "personal"),
            inventory.accounts.associate { it.userId to it.label },
        )
    }

    @Test
    fun `readActiveSession returns the credential without exposing the label`() {
        val (store, _) = newStore()
        store.addAccount(account("alice@wire.com", label = "work"))

        val active = store.readActiveSession()
        assertEquals("alice@wire.com", active?.userId)
        assertEquals("tok-alice@wire.com", active?.accessToken)
    }

    @Test
    fun `v2 session file migrates to v3 with label-less accounts`() {
        val (_, file) = newStore()
        val v2Content =
            listOf(
                "wire-cli-session-store:2",
                "active: alice@wire.com",
                "alice@wire.com",
                "token-v2",
                "wire.com",
            ).joinToString("\n")
        file.writeText(v2Content)

        val inventory = FileAuthSessionStore(sessionFile = file).readAccounts()
        assertEquals("alice@wire.com", inventory.activeUserId)
        assertNull(inventory.activeAccount?.label)
        assertEquals("wire-cli-session-store:3", file.readLines().first())
    }

    @Test
    fun `unsupported schema version reports a diagnostic without an active account`() {
        val (_, file) = newStore()
        file.writeText("wire-cli-session-store:9\nalice@wire.com\ntoken\n\n")

        val inventory = FileAuthSessionStore(sessionFile = file).readAccounts()
        assertTrue(inventory.accounts.isEmpty())
        assertNull(inventory.activeUserId)
        assertFalse(inventory.diagnosticMessage.isNullOrBlank())
    }
}
