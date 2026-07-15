package wirecli.user

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

/**
 * Deterministic in-memory user API client used by the stub backend
 * (`WIRE_BACKEND=stub`) and unit tests.
 *
 * Failure modes are selected via the `WIRE_STUB_MODE` environment variable.
 */
class StubUserApiClient(
    private val environment: Map<String, String>,
) : UserApiClient {
    // Known directory of users used to satisfy search and get operations.
    private val knownUsers =
        listOf(
            KaliumUser(
                id = "alice-uuid@example.wire.com",
                name = "Alice Almond",
                handle = "alice",
                email = "alice@example.com",
                team = "Acme",
                connection = UserConnectionState.ACCEPTED,
            ),
            KaliumUser(
                id = "bob-uuid@example.wire.com",
                name = "Bob Butter",
                handle = "bob",
                email = null,
                team = null,
                connection = UserConnectionState.NOT_CONNECTED,
            ),
            KaliumUser(
                id = "cara-uuid@example.wire.com",
                name = "Cara Cashew",
                handle = "cara",
                email = "cara@example.com",
                team = "Acme",
                connection = UserConnectionState.PENDING,
            ),
        )

    override fun searchUsers(
        session: AuthSession,
        query: UserSearchQuery,
    ): UserSearchResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "user_search_network_error" ->
                UserSearchResult.Failure(
                    message = UserMessages.SEARCH_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "user_search_server_error" ->
                UserSearchResult.Failure(
                    message = UserMessages.SEARCH_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "user_unauthorized" ->
                UserSearchResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> {
                val matches =
                    knownUsers
                        .filter { user -> !query.contactsOnly || user.connection == UserConnectionState.ACCEPTED }
                        .filter { user -> user.matches(query.query) }
                        .take(query.limit)
                UserSearchResult.Success(view = UserListView(users = matches.toUserViews()))
            }
        }
    }

    override fun getUser(
        session: AuthSession,
        userId: String,
    ): UserGetResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "user_get_network_error" ->
                UserGetResult.Failure(
                    message = UserMessages.GET_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "user_get_server_error" ->
                UserGetResult.Failure(
                    message = UserMessages.GET_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "user_unauthorized" ->
                UserGetResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> {
                val user =
                    knownUsers.find { it.id == userId }
                        ?: return UserGetResult.Failure(
                            message = UserMessages.USER_NOT_FOUND,
                            exitCode = UserExitCodes.NOT_FOUND,
                        )
                UserGetResult.Success(view = user.toUserView())
            }
        }
    }

    private fun KaliumUser.matches(query: String): Boolean {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return true
        return listOfNotNull(name, handle, email, id)
            .any { it.contains(normalized, ignoreCase = true) }
    }
}

private fun KaliumUser.toUserView(): UserView =
    UserView(
        id = id,
        name = name,
        handle = handle,
        email = email,
        team = team,
        connection = connection,
    )

private fun List<KaliumUser>.toUserViews(): List<UserView> = map { it.toUserView() }
