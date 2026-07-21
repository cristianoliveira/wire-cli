package wirecli.runtime

import wirecli.auth.AccountService
import wirecli.auth.AccountServiceImpl
import wirecli.auth.AuthApiClient
import wirecli.auth.AuthSessionService
import wirecli.auth.AuthSessionServiceImpl
import wirecli.auth.FileAuthSessionStore
import wirecli.auth.RealKaliumAuthClient
import wirecli.auth.SdkKaliumAuthRuntime
import wirecli.auth.StandardAuthResponseParser
import wirecli.auth.StandardAuthenticationOrchestrator
import wirecli.auth.StubAuthApiClient
import wirecli.connection.AuthGuardedConnectionService
import wirecli.connection.ConnectionApiClient
import wirecli.connection.ConnectionService
import wirecli.connection.RealKaliumConnectionApiClient
import wirecli.connection.SdkKaliumConnectionRuntime
import wirecli.connection.SessionBackedConnectionService
import wirecli.connection.StubConnectionApiClient
import wirecli.conversation.AuthGuardedConversationService
import wirecli.conversation.ConversationApiClient
import wirecli.conversation.ConversationService
import wirecli.conversation.RealKaliumConversationApiClient
import wirecli.conversation.SdkKaliumConversationRuntime
import wirecli.conversation.SessionBackedConversationService
import wirecli.conversation.StubConversationApiClient
import wirecli.device.AuthGuardedDeviceService
import wirecli.device.DeviceApiClient
import wirecli.device.DeviceService
import wirecli.device.RealKaliumDeviceApiClient
import wirecli.device.SdkKaliumDeviceRuntime
import wirecli.device.SessionBackedDeviceService
import wirecli.device.StubDeviceApiClient
import wirecli.download.AuthGuardedDownloadService
import wirecli.download.DownloadApiClient
import wirecli.download.DownloadService
import wirecli.download.RealKaliumDownloadApiClient
import wirecli.download.SdkKaliumDownloadRuntime
import wirecli.download.SessionBackedDownloadService
import wirecli.download.StubDownloadApiClient
import wirecli.exporting.DefaultExportService
import wirecli.exporting.DefaultLocalBackupService
import wirecli.exporting.ExportService
import wirecli.exporting.LocalBackupService
import wirecli.exporting.SdkLocalCacheBackupRuntime
import wirecli.exporting.WireBackupJsonExporter
import wirecli.importing.DefaultImportService
import wirecli.importing.ImportService
import wirecli.importing.SdkWireBackupRuntime
import wirecli.importing.WireBackupImporter
import wirecli.message.AuthGuardedMessageService
import wirecli.message.DaemonBackedMessageService
import wirecli.message.MessageApiClient
import wirecli.message.MessageService
import wirecli.message.RealKaliumMessageApiClient
import wirecli.message.RecentMessageRefresher
import wirecli.message.SdkKaliumMessageRuntime
import wirecli.message.SessionBackedMessageService
import wirecli.message.StubMessageApiClient
import wirecli.presence.AuthGuardedPresenceService
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceService
import wirecli.presence.RealKaliumPresenceApiClient
import wirecli.presence.SdkKaliumPresenceRuntime
import wirecli.presence.SessionBackedPresenceService
import wirecli.presence.StubPresenceApiClient
import wirecli.profile.AuthGuardedProfileService
import wirecli.profile.ProfileApiClient
import wirecli.profile.ProfileService
import wirecli.profile.RealKaliumProfileApiClient
import wirecli.profile.SdkKaliumProfileRuntime
import wirecli.profile.SessionBackedProfileService
import wirecli.profile.StubProfileApiClient
import wirecli.sync.AuthGuardedSyncService
import wirecli.sync.RealKaliumSyncApiClient
import wirecli.sync.SdkKaliumSyncRuntime
import wirecli.sync.SessionBackedSyncService
import wirecli.sync.StubSyncApiClient
import wirecli.sync.SyncApiClient
import wirecli.sync.SyncService
import wirecli.sync.SyncStatusResult
import wirecli.team.AuthGuardedTeamService
import wirecli.team.RealKaliumTeamApiClient
import wirecli.team.SdkKaliumTeamRuntime
import wirecli.team.SessionBackedTeamService
import wirecli.team.StubTeamApiClient
import wirecli.team.TeamApiClient
import wirecli.team.TeamService
import wirecli.user.AuthGuardedUserService
import wirecli.user.RealKaliumUserApiClient
import wirecli.user.SdkKaliumUserRuntime
import wirecli.user.SessionBackedUserService
import wirecli.user.StubUserApiClient
import wirecli.user.UserApiClient
import wirecli.user.UserService
import java.util.Locale

interface KaliumRuntime : AutoCloseable {
    val authSessionService: AuthSessionService
    val exportService: ExportService
    val localBackupService: LocalBackupService
    val importService: ImportService
    val profileService: ProfileService
    val presenceService: PresenceService
    val deviceService: DeviceService
    val syncService: SyncService
    val conversationService: ConversationService
    val messageService: MessageService
    val userService: UserService
    val connectionService: ConnectionService
    val downloadService: DownloadService
    val teamService: TeamService
    val accountService: AccountService

    fun shutdown()

    override fun close() {
        shutdown()
    }
}

object KaliumRuntimeBootstrap {
    // Supported selector values: stub | real
    const val ENV_BACKEND_SELECTOR = "WIRE_BACKEND"

    fun create(): KaliumRuntime {
        val environment = System.getenv()
        val backend =
            RuntimeBackendSelector.resolve(
                environmentBackend = environment[ENV_BACKEND_SELECTOR],
            )
        return createWithBackend(environment, backend.factory)
    }

    internal fun createWithBackend(
        environment: Map<String, String>,
        backendFactory: RuntimeBackendFactory,
    ): KaliumRuntime {
        return DefaultKaliumRuntime(
            environment = environment,
            backendFactory = backendFactory,
        )
    }

    internal fun resolveBackendForTests(environmentBackend: String?): String {
        return RuntimeBackendSelector.resolve(environmentBackend).name
    }
}

private class DefaultKaliumRuntime(
    private val environment: Map<String, String>,
    backendFactory: RuntimeBackendFactory,
) : KaliumRuntime {
    private val sessionStore = FileAuthSessionStore()
    private val backendLazy = lazy { backendFactory.create(environment) }
    private val backend by backendLazy

    override val authSessionService: AuthSessionService by lazy {
        AuthSessionServiceImpl(
            apiClient = backend.authApiClient,
            sessionStore = sessionStore,
        )
    }

    private val localCacheBackupRuntime by lazy { SdkLocalCacheBackupRuntime(environment) }

    override val exportService: ExportService by lazy {
        DefaultExportService(
            sessionProvider = sessionStore,
            localCacheBackupRuntime = localCacheBackupRuntime,
            exporters = listOf(WireBackupJsonExporter()),
        )
    }

    override val localBackupService: LocalBackupService by lazy {
        DefaultLocalBackupService(sessionStore, localCacheBackupRuntime)
    }

    override val importService: ImportService by lazy {
        DefaultImportService(
            sessionProvider = sessionStore,
            importers = listOf(WireBackupImporter(SdkWireBackupRuntime(environment))),
        )
    }

    override val profileService: ProfileService by lazy {
        AuthGuardedProfileService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedProfileService(
                    sessionStore = sessionStore,
                    apiClient = backend.profileApiClient,
                    presenceApiClient = backend.presenceApiClient,
                ),
        )
    }

    override val presenceService: PresenceService by lazy {
        AuthGuardedPresenceService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedPresenceService(
                    sessionStore = sessionStore,
                    apiClient = backend.presenceApiClient,
                ),
        )
    }

    override val deviceService: DeviceService by lazy {
        AuthGuardedDeviceService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedDeviceService(
                    sessionStore = sessionStore,
                    apiClient = backend.deviceApiClient,
                ),
        )
    }

    override val syncService: SyncService by lazy {
        AuthGuardedSyncService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedSyncService(
                    sessionStore = sessionStore,
                    apiClient = backend.syncApiClient,
                ),
        )
    }

    override val conversationService: ConversationService by lazy {
        AuthGuardedConversationService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedConversationService(
                    sessionStore = sessionStore,
                    apiClient = backend.conversationApiClient,
                ),
        )
    }

    override val messageService: MessageService by lazy {
        DaemonBackedMessageService(
            delegateProvider = {
                AuthGuardedMessageService(
                    authSessionService = authSessionService,
                    delegate =
                        SessionBackedMessageService(
                            sessionStore = sessionStore,
                            apiClient = backend.messageApiClient,
                            conversationApiClient = backend.conversationApiClient,
                            syncRefresher =
                                RecentMessageRefresher { session ->
                                    when (val result = backend.syncApiClient.forceSyncAndWait(session)) {
                                        is SyncStatusResult.Success -> null
                                        is SyncStatusResult.Failure ->
                                            RecentMessageRefresher.RefreshFailure(result.message, result.exitCode)
                                    }
                                },
                        ),
                )
            },
            daemonStatus = FileDaemonProcessMarker(DaemonMarkerPath.resolve(environment)),
        )
    }

    override val userService: UserService by lazy {
        AuthGuardedUserService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedUserService(
                    sessionStore = sessionStore,
                    apiClient = backend.userApiClient,
                ),
        )
    }

    override val connectionService: ConnectionService by lazy {
        AuthGuardedConnectionService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedConnectionService(
                    sessionStore = sessionStore,
                    apiClient = backend.connectionApiClient,
                ),
        )
    }

    override val downloadService: DownloadService by lazy {
        AuthGuardedDownloadService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedDownloadService(
                    sessionStore = sessionStore,
                    apiClient = backend.downloadApiClient,
                ),
        )
    }

    override val teamService: TeamService by lazy {
        AuthGuardedTeamService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedTeamService(
                    sessionStore = sessionStore,
                    apiClient = backend.teamApiClient,
                ),
        )
    }

    override val accountService: AccountService by lazy {
        AccountServiceImpl(sessionStore)
    }

    override fun shutdown() {
        if (!backendLazy.isInitialized()) return
        backend.shutdown()
    }
}

private enum class RuntimeBackendSelector(val factory: RuntimeBackendFactory) {
    STUB(StubRuntimeBackendFactory),
    REAL(RealRuntimeBackendFactory),
    ;

    companion object {
        // Real backend is the default; stub mode requires explicit env override for testing.
        private val default = REAL

        fun resolve(environmentBackend: String?): RuntimeBackendSelector {
            return parse(environmentBackend)
                ?: default
        }

        private fun parse(rawValue: String?): RuntimeBackendSelector? {
            if (rawValue == null) return null

            return entries.firstOrNull { backend ->
                backend.name.equals(rawValue.trim().uppercase(Locale.US), ignoreCase = false)
            }
        }
    }
}

internal interface RuntimeBackendFactory {
    fun create(environment: Map<String, String>): RuntimeBackend
}

internal interface RuntimeBackend {
    val authApiClient: AuthApiClient
    val profileApiClient: ProfileApiClient
    val presenceApiClient: PresenceApiClient
    val deviceApiClient: DeviceApiClient
    val syncApiClient: SyncApiClient
    val conversationApiClient: ConversationApiClient
    val messageApiClient: MessageApiClient
    val userApiClient: UserApiClient
    val connectionApiClient: ConnectionApiClient
    val downloadApiClient: DownloadApiClient
    val teamApiClient: TeamApiClient

    fun shutdown()
}

private object StubRuntimeBackendFactory : RuntimeBackendFactory {
    override fun create(environment: Map<String, String>): RuntimeBackend {
        return object : RuntimeBackend {
            override val authApiClient: AuthApiClient = StubAuthApiClient(environment)
            override val profileApiClient: ProfileApiClient = StubProfileApiClient(environment)
            override val presenceApiClient: PresenceApiClient = StubPresenceApiClient(environment)
            override val deviceApiClient: DeviceApiClient = StubDeviceApiClient(environment)
            override val syncApiClient: SyncApiClient = StubSyncApiClient(environment)
            override val conversationApiClient: ConversationApiClient = StubConversationApiClient(environment)
            override val messageApiClient: MessageApiClient = StubMessageApiClient(environment)
            override val userApiClient: UserApiClient = StubUserApiClient(environment)
            override val connectionApiClient: ConnectionApiClient = StubConnectionApiClient(environment)
            override val downloadApiClient: DownloadApiClient = StubDownloadApiClient(environment)
            override val teamApiClient: TeamApiClient = StubTeamApiClient(environment)

            override fun shutdown() {
                // No background resources in stub backend.
            }
        }
    }
}

private object RealRuntimeBackendFactory : RuntimeBackendFactory {
    override fun create(environment: Map<String, String>): RuntimeBackend {
        return object : RuntimeBackend {
            private val authRuntimeLazy = lazy { SdkKaliumAuthRuntime(environment) }
            private val profileRuntimeLazy = lazy { SdkKaliumProfileRuntime(environment) }
            private val presenceRuntimeLazy = lazy { SdkKaliumPresenceRuntime(environment) }
            private val deviceRuntimeLazy = lazy { SdkKaliumDeviceRuntime(environment) }
            private val syncRuntimeLazy = lazy { SdkKaliumSyncRuntime(environment) }
            private val conversationRuntimeLazy = lazy { SdkKaliumConversationRuntime(environment) }
            private val messageRuntimeLazy = lazy { SdkKaliumMessageRuntime(environment) }
            private val userRuntimeLazy = lazy { SdkKaliumUserRuntime(environment) }
            private val connectionRuntimeLazy = lazy { SdkKaliumConnectionRuntime(environment) }

            private val authRuntime by authRuntimeLazy
            private val profileRuntime by profileRuntimeLazy
            private val presenceRuntime by presenceRuntimeLazy
            private val deviceRuntime by deviceRuntimeLazy
            private val syncRuntime by syncRuntimeLazy
            private val conversationRuntime by conversationRuntimeLazy
            private val messageRuntime by messageRuntimeLazy
            private val userRuntime by userRuntimeLazy
            private val connectionRuntime by connectionRuntimeLazy

            override val authApiClient: AuthApiClient by lazy {
                val orchestrator =
                    StandardAuthenticationOrchestrator(
                        runtime = authRuntime,
                        parser = StandardAuthResponseParser(),
                    )
                RealKaliumAuthClient(orchestrator)
            }
            override val profileApiClient: ProfileApiClient by lazy { RealKaliumProfileApiClient(profileRuntime) }
            override val presenceApiClient: PresenceApiClient by lazy { RealKaliumPresenceApiClient(presenceRuntime) }
            override val deviceApiClient: DeviceApiClient by lazy { RealKaliumDeviceApiClient(deviceRuntime) }
            override val syncApiClient: SyncApiClient by lazy { RealKaliumSyncApiClient(syncRuntime) }

            override val conversationApiClient: ConversationApiClient by lazy {
                RealKaliumConversationApiClient(conversationRuntime)
            }

            override val messageApiClient: MessageApiClient by lazy {
                RealKaliumMessageApiClient(messageRuntime)
            }

            override val userApiClient: UserApiClient by lazy {
                RealKaliumUserApiClient(userRuntime)
            }

            override val connectionApiClient: ConnectionApiClient by lazy {
                RealKaliumConnectionApiClient(connectionRuntime)
            }

            override val downloadApiClient: DownloadApiClient by lazy {
                RealKaliumDownloadApiClient(SdkKaliumDownloadRuntime(environment))
            }

            override val teamApiClient: TeamApiClient by lazy {
                RealKaliumTeamApiClient(SdkKaliumTeamRuntime(environment))
            }

            override fun shutdown() {
                if (authRuntimeLazy.isInitialized()) authRuntime.close()
                if (profileRuntimeLazy.isInitialized()) profileRuntime.close()
                if (presenceRuntimeLazy.isInitialized()) presenceRuntime.close()
                if (deviceRuntimeLazy.isInitialized()) deviceRuntime.shutdown()
                if (syncRuntimeLazy.isInitialized()) syncRuntime.shutdown()
                if (conversationRuntimeLazy.isInitialized()) conversationRuntime.shutdown()
                if (messageRuntimeLazy.isInitialized()) messageRuntime.shutdown()
                if (userRuntimeLazy.isInitialized()) userRuntime.shutdown()
                if (connectionRuntimeLazy.isInitialized()) connectionRuntime.shutdown()
            }
        }
    }
}
