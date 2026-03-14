package wirecli.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationFormatterTest {
    private val formatter = ConversationFormatter()

    @Test
    fun `toTable returns empty message for empty list`() {
        val result = formatter.toTable(emptyList())

        assertEquals("No conversations found.", result)
    }

    @Test
    fun `toTable formats conversations as ASCII table`() {
        val conversations =
            listOf(
                Conversation(
                    id = "conv-001",
                    name = "Team Collaboration",
                    type = ConversationType.GROUP,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 8,
                    createdAt = "2024-12-20T14:00:00Z",
                    updatedAt = "2025-03-13T16:20:00Z",
                ),
                Conversation(
                    id = "conv-002",
                    name = "alice@example.com",
                    type = ConversationType.ONE_TO_ONE,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 2,
                    createdAt = "2025-01-10T08:00:00Z",
                    updatedAt = "2025-03-13T15:30:00Z",
                ),
            )

         val result = formatter.toTable(conversations)

         // Verify ID column is present in header
         assertTrue(result.contains("ID"))

         // Verify conversation IDs are shown
         assertTrue(result.contains("conv-001"))
         assertTrue(result.contains("conv-002"))

         // Verify other columns still work
         assertTrue(result.contains("Team Collaboration"))
         assertTrue(result.contains("alice@example.com"))
         assertTrue(result.contains("group"))
         assertTrue(result.contains("one_to_one"))
         assertTrue(result.contains("2024-12-20"))
         assertTrue(result.contains("2025-01-10"))
     }

    @Test
    fun `toJson returns empty array for empty list`() {
        val result = formatter.toJson(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `toJson formats conversations as JSON array`() {
        val conversations =
            listOf(
                Conversation(
                    id = "conv-001",
                    name = "Team",
                    type = ConversationType.GROUP,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 5,
                    createdAt = "2024-12-20T14:00:00Z",
                    updatedAt = "2025-03-13T16:20:00Z",
                ),
            )

        val result = formatter.toJson(conversations)

        assertTrue(result.startsWith("["))
        assertTrue(result.endsWith("]"))
        assertTrue(result.contains("\"id\":\"conv-001\""))
        assertTrue(result.contains("\"name\":\"Team\""))
        // EnumType.toString() returns the value string (lowercase)
        assertTrue(result.contains("\"type\":\"group\"") || result.contains("\"type\":\"") && result.contains("group"))
        assertTrue(result.contains("\"status\":\"active\""))
        assertTrue(result.contains("\"memberCount\":5"))
    }

    @Test
    fun `toJsonLines formats conversations as JSON lines`() {
        val conversations =
            listOf(
                Conversation(
                    id = "conv-001",
                    name = "Team",
                    type = ConversationType.GROUP,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 5,
                    createdAt = "2024-12-20T14:00:00Z",
                    updatedAt = "2025-03-13T16:20:00Z",
                ),
                Conversation(
                    id = "conv-002",
                    name = "Chat",
                    type = ConversationType.ONE_TO_ONE,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 2,
                    createdAt = "2025-01-10T08:00:00Z",
                    updatedAt = "2025-03-13T15:30:00Z",
                ),
            )

        val result = formatter.toJsonLines(conversations)

        val lines = result.split("\n")
        assertEquals(2, lines.size)

        assertTrue(lines[0].contains("\"id\":\"conv-001\""))
        assertTrue(lines[1].contains("\"id\":\"conv-002\""))

        // Verify they're not wrapped in array brackets
        assertTrue(lines[0].startsWith("{"))
        assertTrue(lines[0].endsWith("}"))
    }

    @Test
    fun `toDetail formats single conversation with all fields`() {
        val conversation =
            Conversation(
                id = "conv-001",
                name = "Team Collaboration",
                type = ConversationType.GROUP,
                status = ConversationStatus.ACTIVE,
                memberCount = 8,
                createdAt = "2024-12-20T14:00:00Z",
                updatedAt = "2025-03-13T16:20:00Z",
            )

        val result = formatter.toDetail(conversation)

        assertTrue(result.contains("ID:") && result.contains("conv-001"))
        assertTrue(result.contains("Name:") && result.contains("Team Collaboration"))
        assertTrue(result.contains("Type:") && result.contains("group"))
        assertTrue(result.contains("Status:") && result.contains("active"))
        assertTrue(result.contains("Members:") && result.contains("8"))
        assertTrue(result.contains("Created:") && result.contains("2024-12-20T14:00:00Z"))
        assertTrue(result.contains("Updated:") && result.contains("2025-03-13T16:20:00Z"))
    }

    @Test
    fun `toJson escapes special characters in JSON`() {
        val conversations =
            listOf(
                Conversation(
                    id = "conv-001",
                    name = "Team \"Awesome\"",
                    type = ConversationType.GROUP,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 5,
                    createdAt = "2024-12-20T14:00:00Z",
                    updatedAt = "2025-03-13T16:20:00Z",
                ),
            )

        val result = formatter.toJson(conversations)

         assertTrue(result.contains("\"name\":\"Team \\\"Awesome\\\"\""))
     }

     @Test
     fun `toTable shows ID as first column and truncates to 24 characters`() {
         val longId = "550e8400-e29b-41d4-a716-446655440000" // 36 chars (UUID length)
         val conversations =
             listOf(
                 Conversation(
                     id = longId,
                     name = "Test Conv",
                     type = ConversationType.GROUP,
                     status = ConversationStatus.ACTIVE,
                     memberCount = 3,
                     createdAt = "2025-03-13T10:00:00Z",
                     updatedAt = "2025-03-13T10:00:00Z",
                 ),
             )

         val result = formatter.toTable(conversations)
         val lines = result.split("\n")

         // Check header contains ID
         assertTrue(lines[0].contains("ID"))

         // Check ID is shown (truncated to 24 chars)
         val expectedTruncatedId = longId.take(24) // "550e8400-e29b-41d4-a716"
         assertTrue(result.contains(expectedTruncatedId), "Expected to find truncated ID: $expectedTruncatedId")

         // Verify the full long ID is NOT fully present (should be truncated)
         assertTrue(!result.contains(longId), "Full ID should be truncated")
     }
 }
