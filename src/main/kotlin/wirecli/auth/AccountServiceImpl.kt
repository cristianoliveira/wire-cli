package wirecli.auth

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Local-only account management backed by [AuthSessionStore].
 *
 * None of these operations contact Wire: list/switch/remove/current read or
 * rewrite the local credential store only. Network logout is owned by
 * [AuthSessionService].
 */
class AccountServiceImpl(
    private val sessionStore: AuthSessionStore,
) : AccountService {
    override fun listAccounts(): AccountListing {
        logger.debug { "Listing stored accounts" }
        val inventory = sessionStore.readAccounts()
        return AccountListing(accounts = inventory.accounts, activeUserId = inventory.activeUserId)
    }

    override fun currentAccount(): AuthSession? {
        logger.debug { "Reading current account" }
        return sessionStore.readAccounts().activeAccount
    }

    override fun useAccount(userId: String): AuthSession? {
        logger.debug { "Switching active account to: $userId" }
        return sessionStore.setActiveAccount(userId)
    }

    override fun removeAccount(userId: String): AuthSession? {
        logger.debug { "Removing stored account: $userId" }
        return sessionStore.removeAccount(userId)
    }
}
