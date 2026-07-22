package wirecli.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountServiceImplTest {
    private fun account(
        userId: String,
        label: String? = null,
    ) = StoredAccount(userId = userId, accessToken = "tok-$userId", server = null, label = label)

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
                FakeStore(listOf(account("alice@wire.com", label = "work")), activeUserId = "alice@wire.com"),
            )
        assertEquals("alice@wire.com", service.currentAccount()?.userId)
        assertEquals("work", service.currentAccount()?.label)
    }

    @Test
    fun `currentAccount returns null when no account is active`() {
        val service = AccountServiceImpl(FakeStore(emptyList(), activeUserId = null))
        assertNull(service.currentAccount())
    }

    @Test
    fun `useAccount resolves a label to the account and activates it`() {
        val store =
            FakeStore(
                listOf(account("alice@wire.com", label = "work"), account("bob@wire.com", label = "personal")),
                activeUserId = "alice@wire.com",
            )
        val service = AccountServiceImpl(store)

        val switched = service.useAccount("personal")

        assertEquals("bob@wire.com", switched?.userId)
        assertEquals("bob@wire.com", store.setActiveCalledWith)
    }

    @Test
    fun `useAccount falls back to userId when no label matches`() {
        val store = FakeStore(listOf(account("alice@wire.com")), activeUserId = "alice@wire.com")
        val service = AccountServiceImpl(store)

        val switched = service.useAccount("alice@wire.com")

        assertEquals("alice@wire.com", switched?.userId)
        assertTrue(store.setActiveCalledWith == "alice@wire.com")
    }

    @Test
    fun `useAccount returns null when neither label nor userId matches`() {
        val service = AccountServiceImpl(FakeStore(emptyList(), activeUserId = null))
        assertNull(service.useAccount("ghost"))
    }

    @Test
    fun `removeAccount resolves a label before delegating to the store`() {
        val store = FakeStore(listOf(account("alice@wire.com", label = "work")), activeUserId = "alice@wire.com")
        val service = AccountServiceImpl(store)

        val removed = service.removeAccount("work")

        assertEquals("alice@wire.com", removed?.userId)
        assertTrue(store.removeCalledWith == "alice@wire.com")
    }

    private class FakeStore(
        private val accounts: List<StoredAccount>,
        private val activeUserId: String?,
    ) : AuthSessionStore {
        var setActiveCalledWith: String? = null
        var removeCalledWith: String? = null

        override fun readActiveSession(): AuthSession? = accounts.firstOrNull { it.userId == activeUserId }?.toAuthSession()

        override fun readAccounts(): AccountInventory = AccountInventory(accounts, activeUserId)

        override fun addAccount(
            account: StoredAccount,
            makeActive: Boolean,
        ) = Unit

        override fun setActiveAccount(userId: String): StoredAccount? {
            setActiveCalledWith = userId
            return accounts.firstOrNull { it.userId == userId }
        }

        override fun removeAccount(userId: String): StoredAccount? {
            removeCalledWith = userId
            return accounts.firstOrNull { it.userId == userId }
        }
    }
}
