package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import wirecli.auth.ExitCodes
import wirecli.message.MessageService
import wirecli.message.SendTypingResult
import wirecli.message.TypingStatus

class MessageTypingCommand(
    private val messageServiceProvider: () -> MessageService,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val heartbeatIntervalMs: Long = TYPING_HEARTBEAT_INTERVAL_MS,
    private val isProcessAlive: (Long) -> Boolean = { pid -> ProcessHandle.of(pid).isPresent },
) : CliktCommand(
        name = "typing",
        help = "Send typing status while a process is running.",
        epilog =
            """
            EXAMPLES:
              Send STARTED while a process is alive:
                some-command &
                wire message typing <conversation-id> --while-pid <pid>
            """.trimIndent(),
    ) {
    private val conversationId by argument(name = "CONVERSATION_ID", help = "The conversation ID")
    private val whilePid by
        option("--while-pid", help = "Keep STARTED heartbeats while this PID is alive").long().required()

    override fun run() {
        if (conversationId.isBlank()) {
            echo("validation error: conversation required", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }
        if (whilePid <= 0L) {
            echo("validation error: while-pid must be a positive integer", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }
        if (!isProcessAlive(whilePid)) {
            echo("validation error: while-pid must reference a running process", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        val messageService = messageServiceProvider()

        sendOrExit(messageService.sendTypingStatus(conversationId, TypingStatus.STARTED))
        echo("Typing started.")

        while (true) {
            sleep(heartbeatIntervalMs)
            if (!isProcessAlive(whilePid)) {
                break
            }
            sendOrExit(messageService.sendTypingStatus(conversationId, TypingStatus.STARTED))
        }

        sendOrExit(messageService.sendTypingStatus(conversationId, TypingStatus.STOPPED))
        echo("Typing stopped.")
    }

    private fun sendOrExit(result: SendTypingResult) {
        if (result is SendTypingResult.Failure) {
            echo(result.message, err = true)
            throw ProgramResult(result.exitCode)
        }
    }

    private companion object {
        const val TYPING_HEARTBEAT_INTERVAL_MS = 3_000L
    }
}
