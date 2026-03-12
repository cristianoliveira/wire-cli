package wirecli.presence

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class StubPresenceApiClient(
    private val environment: Map<String, String>,
) : PresenceApiClient {
    override fun fetchPresence(session: AuthSession): PresenceResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "presence_network_error" ->
                PresenceResult.Failure(
                    message = PresenceMessages.NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "presence_server_error" ->
                PresenceResult.Failure(
                    message = PresenceMessages.SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "presence_unauthorized" ->
                PresenceResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            "presence_invalid_value" ->
                PresenceResult.Success(
                    presence =
                        PresenceView(
                            state = PresenceNormalizer.normalize("in_a_call"),
                        ),
                )

            else ->
                PresenceResult.Success(
                    presence =
                        PresenceView(
                            state = PresenceNormalizer.normalize("online"),
                        ),
                )
        }
    }
}
