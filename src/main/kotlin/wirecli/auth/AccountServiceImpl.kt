package wirecli.auth

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Local-only account management backed by [AuthSessionStore].
 *
 * None of these operations contact Wire: list/switch/remove/current read or
 * rewrite the local credential store only. Network logout is owned by
 * [AuthSessionService]. [useAccount] and [removeAccount] resolve a selector by
 * matching an account's label first, then its userId.
 */
class AccountServiceImpl(
    private val sessionStore: AuthSessionStore,
) : AccountService {
    override fun listAccounts(): AccountListing {
        logger.debug { "Listing stored accounts" }
        val inventory = sessionStore.readAccounts()
        return AccountListing(accounts = inventory.accounts, activeUserId = inventory.activeUserId)
    }

    override fun currentAccount(): StoredAccount? {
        logger.debug { "Reading current account" }
        return sessionStore.readAccounts().activeAccount
    }

    override fun useAccount(selector: String): StoredAccount? {
        logger.debug { "Switching active account by selector: $selector" }
        val userId = resolveSelector(selector) ?: return null
        return sessionStore.setActiveAccount(userId)
    }

    override fun removeAccount(selector: String): StoredAccount? {
        logger.debug { "Removing stored account by selector: $selector" }
        val userId = resolveSelector(selector) ?: return null
        return sessionStore.removeAccount(userId)
    }

    private fun resolveSelector(selector: String): String? {
        val accounts = sessionStore.readAccounts().accounts
        return accounts
            .firstOrNull { it.label == selector }
            ?.userId
            ?: accounts.firstOrNull { it.userId == selector }?.userId
    }
}
