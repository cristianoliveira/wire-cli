package wirecli.runtime

import wirecli.auth.AuthApiClient
import wirecli.auth.AuthApiResult
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import wirecli.connection.ConnectionActionResult
import wirecli.connection.ConnectionApiClient
import wirecli.connection.ConnectionListResult
import wirecli.conversation.ConversationApiClient
import wirecli.conversation.StubConversationApiClient
import wirecli.device.DeviceApiClient
import wirecli.device.DeviceDeleteResult
import wirecli.device.DeviceDetailResult
import wirecli.device.DeviceListResult
import wirecli.device.DeviceVerifyResult
import wirecli.download.DownloadApiClient
import wirecli.download.StubDownloadApiClient
import wirecli.message.DeleteMessageResult
import wirecli.message.DeleteScope
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageApiClient
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.SetMessageReadResult
import wirecli.message.ToggleReactionResult
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceResult
import wirecli.presence.WritablePresenceState
import wirecli.profile.ProfileApiClient
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileUpdate
import wirecli.profile.ProfileUpdateResult
import wirecli.sync.ConversationSyncStatusResult
import wirecli.sync.DiagnosticsResult
import wirecli.sync.PerConversationDiagnosticsResult
import wirecli.sync.ResetResult
import wirecli.sync.SyncApiClient
import wirecli.sync.SyncStatusResult
import wirecli.team.TeamApiClient
import wirecli.team.TeamReadResult
import wirecli.user.UserApiClient
import wirecli.user.UserGetResult
import wirecli.user.UserSearchQuery
import wirecli.user.UserSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class KaliumRuntimeDeferredInitializationTest {
    @Test
    fun `auth service initialization does not touch profile and presence clients`() {
        val counters = BackendCounters()
        val runtime = KaliumRuntimeBootstrap.createWithBackend(emptyMap(), countingBackendFactory(counters))

        runtime.authSessionService

        assertEquals(1, counters.authApiClientAccesses)
        assertEquals(0, counters.profileApiClientAccesses)
        assertEquals(0, counters.presenceApiClientAccesses)
        assertEquals(0, counters.deviceApiClientAccesses)
        assertEquals(0, counters.syncApiClientAccesses)

        runtime.close()
    }

    @Test
    fun `shutdown does not initialize backend when no service is used`() {
        val counters = BackendCounters()
        val runtime = KaliumRuntimeBootstrap.createWithBackend(emptyMap(), countingBackendFactory(counters))

        runtime.close()

        assertEquals(0, counters.authApiClientAccesses)
        assertEquals(0, counters.profileApiClientAccesses)
        assertEquals(0, counters.presenceApiClientAccesses)
        assertEquals(0, counters.deviceApiClientAccesses)
        assertEquals(0, counters.syncApiClientAccesses)
        assertEquals(0, counters.messageApiClientAccesses)
        assertEquals(0, counters.shutdownCalls)
    }
}

private data class BackendCounters(
    var authApiClientAccesses: Int = 0,
    var profileApiClientAccesses: Int = 0,
    var presenceApiClientAccesses: Int = 0,
    var deviceApiClientAccesses: Int = 0,
    var syncApiClientAccesses: Int = 0,
    var messageApiClientAccesses: Int = 0,
    var userApiClientAccesses: Int = 0,
    var connectionApiClientAccesses: Int = 0,
    var shutdownCalls: Int = 0,
)

private fun countingBackendFactory(counters: BackendCounters): RuntimeBackendFactory {
    return object : RuntimeBackendFactory {
        override fun create(environment: Map<String, String>): RuntimeBackend =
            object : RuntimeBackend {
                override val authApiClient: AuthApiClient
                    get() = NoopAuthApiClient.also { counters.authApiClientAccesses += 1 }

                override val profileApiClient: ProfileApiClient
                    get() = NoopProfileApiClient.also { counters.profileApiClientAccesses += 1 }

                override val presenceApiClient: PresenceApiClient
                    get() = NoopPresenceApiClient.also { counters.presenceApiClientAccesses += 1 }

                override val deviceApiClient: DeviceApiClient
                    get() = NoopDeviceApiClient.also { counters.deviceApiClientAccesses += 1 }

                override val conversationApiClient: ConversationApiClient by lazy {
                    StubConversationApiClient(environment)
                }

                override val syncApiClient: SyncApiClient
                    get() = NoopSyncApiClient.also { counters.syncApiClientAccesses += 1 }

                override val messageApiClient: MessageApiClient
                    get() = NoopMessageApiClient.also { counters.messageApiClientAccesses += 1 }

                override val userApiClient: UserApiClient
                    get() = NoopUserApiClient.also { counters.userApiClientAccesses += 1 }

                override val connectionApiClient: ConnectionApiClient
                    get() = NoopConnectionApiClient.also { counters.connectionApiClientAccesses += 1 }

                override val downloadApiClient: DownloadApiClient
                    get() = StubDownloadApiClient()

                override val teamApiClient: TeamApiClient
                    get() = NoopTeamApiClient

                override fun shutdown() {
                    counters.shutdownCalls += 1
                }
            }
    }
}

private object NoopAuthApiClient : AuthApiClient {
    override fun login(input: LoginInput): AuthApiResult {
        return AuthApiResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun logout(session: AuthSession): AuthApiResult {
        return AuthApiResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopProfileApiClient : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult {
        return ProfileResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun updateProfile(
        session: AuthSession,
        update: ProfileUpdate,
    ): ProfileUpdateResult {
        return ProfileUpdateResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopPresenceApiClient : PresenceApiClient {
    override fun fetchPresence(session: AuthSession): PresenceResult {
        return PresenceResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun updatePresence(
        session: AuthSession,
        state: WritablePresenceState,
    ): PresenceResult {
        return PresenceResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopDeviceApiClient : DeviceApiClient {
    override fun listDevices(session: AuthSession): DeviceListResult {
        return DeviceListResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun listDevicesForUser(
        session: AuthSession,
        userId: String,
    ): DeviceListResult {
        return DeviceListResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun getDeviceDetail(
        session: AuthSession,
        deviceId: String,
    ): DeviceDetailResult {
        return DeviceDetailResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun deleteDevice(
        session: AuthSession,
        deviceId: String,
        password: String?,
    ): DeviceDeleteResult {
        return DeviceDeleteResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun verifyDevice(
        session: AuthSession,
        deviceId: String,
    ): DeviceVerifyResult {
        return DeviceVerifyResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopSyncApiClient : SyncApiClient {
    override fun forceSyncAndWait(session: AuthSession): SyncStatusResult {
        return SyncStatusResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun getSyncStatus(session: AuthSession): SyncStatusResult {
        return SyncStatusResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun getDiagnostics(session: AuthSession): DiagnosticsResult {
        return DiagnosticsResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun resetSync(
        session: AuthSession,
        force: Boolean,
    ): ResetResult {
        return ResetResult.Success("Reset successful (test mode)")
    }

    override fun getConversationSyncStatus(
        session: AuthSession,
        conversationId: String,
    ): ConversationSyncStatusResult {
        return ConversationSyncStatusResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun getPerConversationDiagnostics(
        session: AuthSession,
        conversationId: String,
    ): PerConversationDiagnosticsResult {
        return PerConversationDiagnosticsResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopUserApiClient : UserApiClient {
    override fun searchUsers(
        session: AuthSession,
        query: UserSearchQuery,
    ): UserSearchResult {
        return UserSearchResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun getUser(
        session: AuthSession,
        userId: String,
    ): UserGetResult {
        return UserGetResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopConnectionApiClient : ConnectionApiClient {
    override fun sendRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        return ConnectionActionResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun acceptRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        return ConnectionActionResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun ignoreRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        return ConnectionActionResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun cancelRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        return ConnectionActionResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun blockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        return ConnectionActionResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun unblockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        return ConnectionActionResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun listConnections(session: AuthSession): ConnectionListResult =
        ConnectionListResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
}

private object NoopTeamApiClient : TeamApiClient {
    override fun readTeam(session: AuthSession): TeamReadResult {
        return TeamReadResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopMessageApiClient : MessageApiClient {
    override fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): SendMessageResult {
        return SendMessageResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): FetchMessagesResult {
        return FetchMessagesResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun searchMessages(
        session: AuthSession,
        query: String,
        conversationId: String?,
        limit: Int,
    ): SearchMessagesResult {
        return SearchMessagesResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun toggleReaction(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        emoji: String,
    ): ToggleReactionResult {
        return ToggleReactionResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun deleteMessage(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        scope: DeleteScope,
    ): DeleteMessageResult {
        return DeleteMessageResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun setMessageRead(
        session: AuthSession,
        conversationId: String,
        messageId: String,
    ): SetMessageReadResult {
        return SetMessageReadResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}
