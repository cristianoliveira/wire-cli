package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.ExitCodes
import wirecli.message.MessageService
import wirecli.message.SendMessageResult

private val logger = KotlinLogging.logger {}

class MessageSendCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "send",
        help = "Send a message to a conversation.",
        epilog =
            """
            EXAMPLES:
              Send a message using positional argument:
                wire message send <conversation-id> "Hello World"
              
              Send a multi-word message (use quotes):
                wire message send <conversation-id> "This is a longer message"
              
              Send a message from stdin:
                echo "Hello from stdin" | wire message send <conversation-id>
              
              Positional message takes precedence over stdin:
                echo "stdin message" | wire message send <conversation-id> "positional wins"
            """.trimIndent(),
    ) {
    private val conversation by argument(name = "CONVERSATION", help = "The conversation ID to send the message to")
    private val messageText: String? by
        argument(
            name = "MESSAGE",
            help = "The message text to send (optional; read from stdin if omitted)",
        ).optional()

    override fun run() {
        logger.info {
            "message-send invoked: conversationId=$conversation, messageArgProvided=${messageText != null}"
        }

        // Get final message from args or stdin
        val finalMessage =
            messageText?.takeIf { it.isNotEmpty() }
                ?: run {
                    logger.info { "message-send awaiting stdin input for message body" }
                    readMessageFromStdin()
                }

        if (messageText != null) {
            logger.debug { "message-send input source=argument, messageLength=${finalMessage.length}" }
        } else {
            logger.debug { "message-send input source=stdin, messageLength=${finalMessage.length}" }
        }

        // Validate conversation ID
        if (conversation.isBlank()) {
            logger.warn { "Validation failed: blank conversation ID" }
            echo("validation error: conversation required", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        // Validate message
        if (finalMessage.isBlank()) {
            logger.warn { "Validation failed: blank message" }
            echo("validation error: message required", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        logger.debug { "message-send validation passed: conversationId=$conversation" }

        // Send message
        val messageService = messageServiceProvider()
        val result = messageService.sendMessage(conversation, finalMessage)
        when (result) {
            is SendMessageResult.Success -> {
                logger.info { "message-send outcome=success conversationId=$conversation" }
                echo("Message sent.")
            }

            is SendMessageResult.Failure -> {
                logger.warn {
                    "message-send outcome=failure conversationId=$conversation exitCode=${result.exitCode} message=${result.message}"
                }
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun readMessageFromStdin(): String {
        return System.`in`
            .bufferedReader()
            .readLine()
            ?.trimEnd('\r')
            ?: ""
    }
}
