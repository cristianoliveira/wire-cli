package wirecli.exporting

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupDateTime
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupReaction
import com.wire.backup.data.BackupUser
import com.wire.backup.ingest.ImportDataPager
import com.wire.backup.ingest.ImportResultPager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WireBackupJsonExporterTest {
    @Test
    fun `rejects missing backup file`() {
        val destination = Files.createTempDirectory("wire-export-test")

        val result = WireBackupJsonExporter().export(Path("fixtures/missing.wbu"), destination, null)

        assertEquals(ExportResult.Failure("backup file not found: fixtures/missing.wbu"), result)
    }

    @Test
    fun `rejects malformed backup file`() {
        val input = Files.createTempFile("wire-export-test", ".wbu")
        Files.writeString(input, "synthetic invalid backup")

        val result =
            WireBackupJsonExporter().export(
                input,
                Files.createTempDirectory("wire-export-test"),
                null,
            )

        assertIs<ExportResult.Failure>(result)
    }

    @Test
    fun `messages carry resolved conversation and sender names when includeNames enabled`() {
        val destination = Files.createTempDirectory("wire-export-test")
        val exporter = WireBackupJsonExporter()

        val result =
            exporter.write(
                FakePager(arrayOf(teamChannel), arrayOf(alice), arrayOf(helloMessage)),
                destination,
                ExportOptions(includeNames = true),
            )

        assertEquals(ExportResult.Success(1, 1, 1, destination), result)
        val message = readMessages(destination).single()
        assertEquals("Team Channel", message["conversationName"]!!.jsonPrimitive.content)
        assertEquals("Alice", message["senderName"]!!.jsonPrimitive.content)
        assertEquals("@alice", message["senderHandle"]!!.jsonPrimitive.content)
    }

    @Test
    fun `messages omit name fields by default`() {
        val destination = Files.createTempDirectory("wire-export-test")

        WireBackupJsonExporter().write(
            FakePager(arrayOf(teamChannel), arrayOf(alice), arrayOf(helloMessage)),
            destination,
            ExportOptions.DEFAULT,
        )

        val message = readMessages(destination).single()
        assertFalse(message.containsKey("conversationName"))
        assertFalse(message.containsKey("senderName"))
        assertFalse(message.containsKey("senderHandle"))
    }

    @Test
    fun `unknown conversation or sender names are omitted even when includeNames enabled`() {
        val destination = Files.createTempDirectory("wire-export-test")

        WireBackupJsonExporter().write(
            // Pages intentionally omit the conversation and user referenced by the message.
            FakePager(emptyArray(), emptyArray(), arrayOf(helloMessage)),
            destination,
            ExportOptions(includeNames = true),
        )

        val message = readMessages(destination).single()
        assertFalse(message.containsKey("conversationName"))
        assertFalse(message.containsKey("senderName"))
        assertFalse(message.containsKey("senderHandle"))
        // Core identity fields stay stable so consumers never lose the UUIDs.
        assertTrue(message.containsKey("conversationId"))
        assertTrue(message.containsKey("senderId"))
    }

    private fun readMessages(destination: java.nio.file.Path) =
        Files.readAllLines(destination.resolve("messages.jsonl"))
            .filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }

    private companion object {
        val teamConversationId = BackupQualifiedId("conv-id", "wire.com")
        val aliceId = BackupQualifiedId("alice-id", "wire.com")

        val teamChannel = BackupConversation(teamConversationId, "Team Channel", null)
        val alice = BackupUser(aliceId, "Alice", "@alice")
        val helloMessage =
            BackupMessage(
                id = "msg-1",
                conversationId = teamConversationId,
                senderUserId = aliceId,
                senderClientId = "client-1",
                creationDate = BackupDateTime(0L),
                content = BackupMessageContent.Text("hello"),
            )
    }

    private class SinglePagePager<T>(private val items: Array<T>) : ImportDataPager<T> {
        private var consumed = false

        override fun hasMorePages(): Boolean = !consumed

        override fun nextPage(): Array<T> {
            check(!consumed) { "no more pages" }
            consumed = true
            return items
        }
    }

    private class FakePager(
        conversations: Array<BackupConversation>,
        users: Array<BackupUser>,
        messages: Array<BackupMessage>,
    ) : ImportResultPager {
        override val totalPagesCount: Int = 1
        override val conversationsPager = SinglePagePager(conversations)
        override val usersPager = SinglePagePager(users)
        override val messagesPager = SinglePagePager(messages)
        override val reactionsPager = SinglePagePager<BackupReaction>(emptyArray())

        override fun close() {}
    }
}
