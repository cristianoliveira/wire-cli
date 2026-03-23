package wirecli.profile

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.shared.ProfileError
import wirecli.shared.Result

class StubProfileApiClient(
    private val environment: Map<String, String>,
) : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult<ProfileView> {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "profile_network_error" ->
                Result.Failure(
                    error = ProfileError(
                        message = "Profile fetch failed: network is unreachable. Check your connection and retry.",
                        exitCode = ExitCodes.NETWORK_ERROR,
                    ),
                )

            "profile_server_error" ->
                Result.Failure(
                    error = ProfileError(
                        message = "Profile service is unavailable. Retry later or check server settings.",
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "profile_unauthorized" ->
                Result.Failure(
                    error = ProfileError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            "profile_missing_optional" ->
                Result.Success(
                    value =
                        ProfileView(
                            name = "Jane Doe",
                            email = "jane@example.com",
                            handle = null,
                        ),
                )

            else ->
                Result.Success(
                    value =
                        ProfileView(
                            name = "Jane Doe",
                            email = "jane@example.com",
                            handle = session.userId,
                        ),
                )
        }
    }
}
