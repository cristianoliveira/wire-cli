package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import wirecli.conversation.ConversationService
import wirecli.conversation.ListConversationsResult
import wirecli.hooks.DaemonHookService
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageService
import wirecli.runtime.DaemonProcessMarker
import wirecli.sync.SyncService
import wirecli.sync.SyncStatusResult
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}
private const val DAEMON_COMMAND_NAME = "daemon"
private const val DAEMON_COMMAND_HELP = "Keep Wire synchronization active, cache messages, and run configured hooks."

class DaemonCommand(
    private val syncServiceProvider: () -> SyncService,
    private val processMarkerProvider: () -> DaemonProcessMarker,
    private val messageServiceProvider: () -> MessageService? = { null },
    private val conversationServiceProvider: () -> ConversationService? = { null },
    private val daemonHookServiceProvider: () -> DaemonHookService? = { null },
    private val awaitTermination: () -> Unit = { CountDownLatch(1).await() },
) : CliktCommand(name = DAEMON_COMMAND_NAME, help = DAEMON_COMMAND_HELP) {
    private val verbose by option(
        "--verbose",
        "-v",
        help = "Emit new-message events as JSON lines to stdout.",
    ).flag()

    override fun run() {
        when (val result = syncServiceProvider().startContinuousSync()) {
            is SyncStatusResult.Success -> {
                processMarkerProvider().use { marker ->
                    marker.start()
                    marker.recordUpdate()
                    echo("Message sync daemon is active.")
                    startPeriodicHealthLogging()
                    val daemonHookService = daemonHookServiceProvider()
                    if (verbose || daemonHookService != null) {
                        runBlocking { observeDaemon(daemonHookService) }
                    } else {
                        awaitTermination()
                    }
                }
            }

            is SyncStatusResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private fun startPeriodicHealthLogging() {
        val syncService = syncServiceProvider()
        val daemonStart = Instant.now()
        var lastLoggedReachable: Boolean? = null

        Thread.ofPlatform().daemon().start {
            while (true) {
                try {
                    Thread.sleep(DAEMON_HEALTH_INTERVAL_MS)
                    lastLoggedReachable = logHealthSnapshot(syncService, daemonStart, lastLoggedReachable)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: InterruptedException,
                ) {
                    logger.info { "Daemon health logging stopped." }
                    break
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    logger.warn(e) { "Daemon health check error (continuing)" }
                }
            }
        }
    }

    private fun logHealthSnapshot(
        syncService: SyncService,
        daemonStart: Instant,
        lastLoggedReachable: Boolean?,
    ): Boolean? {
        val uptime = Duration.between(daemonStart, Instant.now())
        val statusResult = syncService.getCurrentSyncStatus()
        when (statusResult) {
            is SyncStatusResult.Success -> {
                val m = statusResult.view.metrics
                val statusName = statusResult.view.status.name
                val reachable = m.network?.connected ?: true

                logConnectivityChange(uptime, lastLoggedReachable, reachable, statusName, m)
                logHeartbeat(uptime, statusName, m)
                return reachable
            }

            is SyncStatusResult.Failure -> {
                logger.warn { "Daemon health check failed: ${statusResult.message}" }
                return null
            }
        }
    }

    private fun logConnectivityChange(
        uptime: Duration,
        lastLoggedReachable: Boolean?,
        reachable: Boolean,
        statusName: String,
        m: wirecli.sync.HealthMetrics,
    ) {
        if (lastLoggedReachable != null && lastLoggedReachable && !reachable) {
            logger.warn {
                "Daemon lost network connectivity after ${formatDuration(uptime)} " +
                    "(state=$statusName, lag=${m.lagMs}ms, pending=${m.pendingMessages}, " +
                    "mls=${m.mlsPct}%, latency=${m.network?.estimatedLatencyMs ?: "?"}ms)"
            }
        } else if (lastLoggedReachable != null && !lastLoggedReachable && reachable) {
            logger.info {
                "Daemon recovered network connectivity after ${formatDuration(uptime)} " +
                    "(state=$statusName, lag=${m.lagMs}ms, pending=${m.pendingMessages}, " +
                    "mls=${m.mlsPct}%)"
            }
        }
    }

    private fun logHeartbeat(
        uptime: Duration,
        statusName: String,
        m: wirecli.sync.HealthMetrics,
    ) {
        logger.info {
            "Daemon heartbeat after ${formatDuration(uptime)} — " +
                "state=$statusName, lag=${m.lagMs}ms, pending=${m.pendingMessages}, " +
                "mls=${m.mlsPct}%, net=${m.network?.let { "${it.connected}" } ?: "?"}, " +
                "lat=${m.network?.estimatedLatencyMs ?: "?"}ms"
        }
    }

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return when {
            hours > 0 -> "${hours}h${minutes}m"
            minutes > 0 -> "${minutes}m${seconds}s"
            else -> "${seconds}s"
        }
    }

    private companion object {
        private const val DAEMON_HEALTH_INTERVAL_MS = 60_000L
    }

    private suspend fun observeDaemon(daemonHookService: DaemonHookService?) =
        coroutineScope {
            daemonHookService?.let { service ->
                launch { service.observe() }
            }
            if (verbose) {
                launch { observeMessages() }
            }
            launch(Dispatchers.IO) { awaitTermination() }
        }

    private suspend fun observeMessages() {
        val messageService = messageServiceProvider()
        val conversationService = conversationServiceProvider()
        if (messageService == null || conversationService == null) return

        val conversations = resolveConversations(conversationService)
        if (conversations.isEmpty()) return

        coroutineScope {
            conversations.forEach { (conversationId, conversationName) ->
                launch(Dispatchers.Default) {
                    val knownMessageIds = mutableSetOf<String>()
                    var hasBaselineSnapshot = false

                    messageService.observeMessages(conversationId)
                        .catch { /* transient failures logged by service */ }
                        .collect { result ->
                            when (result) {
                                is FetchMessagesResult.Success -> {
                                    if (!hasBaselineSnapshot) {
                                        result.view.messages.forEach { knownMessageIds += it.id }
                                        hasBaselineSnapshot = true
                                        return@collect
                                    }

                                    val newMessages =
                                        result.view.messages.filter { it.id !in knownMessageIds }
                                    result.view.messages.forEach { knownMessageIds += it.id }
                                    newMessages.forEach { message ->
                                        echo(
                                            formatMessageEvent(
                                                conversationId = conversationId,
                                                conversationName = conversationName,
                                                message = message,
                                            ),
                                        )
                                    }
                                }

                                is FetchMessagesResult.Failure -> {
                                    // Non-fatal — skip this emission and continue watching.
                                }
                            }
                        }
                }
            }
        }
    }

    private fun resolveConversations(conversationService: ConversationService): List<Pair<String, String>> =
        when (val result = conversationService.listConversations()) {
            is ListConversationsResult.Success ->
                result.view.conversations.map { it.id to it.name }
            is ListConversationsResult.Failure -> emptyList()
        }

    private fun formatMessageEvent(
        conversationId: String,
        conversationName: String,
        message: wirecli.message.ConversationMessage,
    ): String =
        buildJsonObject {
            put("conversationId", JsonPrimitive(conversationId))
            put("conversationName", JsonPrimitive(conversationName))
            put("messageId", JsonPrimitive(message.id))
            put("senderId", JsonPrimitive(message.senderId))
            put("senderName", JsonPrimitive(message.senderName))
            put("timestamp", JsonPrimitive(message.timestamp))
            put("content", JsonPrimitive(message.content))
        }.toString()
}
