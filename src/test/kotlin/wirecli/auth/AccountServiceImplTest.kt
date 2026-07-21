package wirecli.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountServiceImplTest {
    private fun account(userId: String) = AuthSession(userId = userId, accessToken = "tok-$userId", server = null)

    @Test
    fun `listAccounts returns all accounts and the active pointer`() {
        val service =
            AccountServiceImpl(
                FakeStore(
                    accounts = listOf(account("alice@wire.com"), account("bob@wire.com")),
                    activeUserId = "bob@wire.com",
                ),
            )

        val listing = service.listAccounts()

        assertEquals(listOf("alice@wire.com", "bob@wire.com"), listing.accounts.map { it.userId })
        assertEquals("bob@wire.com", listing.activeUserId)
    }

    @Test
    fun `currentAccount returns the active account`() {
        val service =
            AccountServiceImpl(
                FakeStore(listOf(account("alice@wire.com")), activeUserId = "alice@wire.com"),
            )
        assertEquals("alice@wire.com", service.currentAccount()?.userId)
    }

    @Test
    fun `currentAccount returns null when no account is active`() {
        val service = AccountServiceImpl(FakeStore(emptyList(), activeUserId = null))
        assertNull(service.currentAccount())
    }

    @Test
    fun `useAccount delegates to the store and returns the activated account`() {
        val store = FakeStore(listOf(account("alice@wire.com"), account("bob@wire.com")), activeUserId = "alice@wire.com")
        val service = AccountServiceImpl(store)

        val switched = service.useAccount("bob@wire.com")

        assertEquals("bob@wire.com", switched?.userId)
        assertTrue(store.setActiveCalledWith == "bob@wire.com")
    }

    @Test
    fun `useAccount returns null when the account is absent`() {
        val service = AccountServiceImpl(FakeStore(emptyList(), activeUserId = null))
        assertNull(service.useAccount("ghost@wire.com"))
    }

    @Test
    fun `removeAccount delegates to the store and returns the removed account`() {
        val store = FakeStore(listOf(account("alice@wire.com")), activeUserId = "alice@wire.com")
        val service = AccountServiceImpl(store)

        val removed = service.removeAccount("alice@wire.com")

        assertEquals("alice@wire.com", removed?.userId)
        assertTrue(store.removeCalledWith == "alice@wire.com")
    }

    private class FakeStore(
        private val accounts: List<AuthSession>,
        private val activeUserId: String?,
    ) : AuthSessionStore {
        var setActiveCalledWith: String? = null
        var removeCalledWith: String? = null

        override fun readActiveSession(): AuthSession? = accounts.firstOrNull { it.userId == activeUserId }

        override fun readAccounts(): AccountInventory = AccountInventory(accounts, activeUserId)

        override fun addAccount(
            account: AuthSession,
            makeActive: Boolean,
        ) = Unit

        override fun setActiveAccount(userId: String): AuthSession? {
            setActiveCalledWith = userId
            return accounts.firstOrNull { it.userId == userId }
        }

        override fun removeAccount(userId: String): AuthSession? {
            removeCalledWith = userId
            return accounts.firstOrNull { it.userId == userId }
        }
    }
}
