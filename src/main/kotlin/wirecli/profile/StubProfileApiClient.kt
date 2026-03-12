package wirecli.profile

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class StubProfileApiClient(
    private val environment: Map<String, String>,
) : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "profile_network_error" ->
                ProfileResult.Failure(
                    message = "Profile fetch failed: network is unreachable. Check your connection and retry.",
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "profile_server_error" ->
                ProfileResult.Failure(
                    message = "Profile service is unavailable. Retry later or check server settings.",
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "profile_unauthorized" ->
                ProfileResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            "profile_missing_optional" ->
                ProfileResult.Success(
                    profile =
                        ProfileView(
                            name = "Jane Doe",
                            email = "jane@example.com",
                            handle = null,
                        ),
                )

            else ->
                ProfileResult.Success(
                    profile =
                        ProfileView(
                            name = "Jane Doe",
                            email = "jane@example.com",
                            handle = session.userId,
                        ),
                )
        }
    }
}
