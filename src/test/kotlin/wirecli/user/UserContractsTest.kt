package wirecli.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UserContractsTest {
    @Test
    fun `UserSearchQuery rejects blank query`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                UserSearchQuery(query = "   ")
            }
        assertTrue(error.message!!.contains("blank", ignoreCase = true))
    }

    @Test
    fun `UserSearchQuery rejects limit below minimum`() {
        assertFailsWith<IllegalArgumentException> {
            UserSearchQuery(query = "alice", limit = 0)
        }
    }

    @Test
    fun `UserSearchQuery rejects limit above maximum`() {
        assertFailsWith<IllegalArgumentException> {
            UserSearchQuery(query = "alice", limit = UserSearchQuery.MAX_LIMIT + 1)
        }
    }

    @Test
    fun `UserSearchQuery accepts boundary limits and applies default`() {
        assertEquals(UserSearchQuery.DEFAULT_LIMIT, UserSearchQuery("alice").limit)
        assertEquals(1, UserSearchQuery("alice", limit = UserSearchQuery.MIN_LIMIT).limit)
        assertEquals(UserSearchQuery.MAX_LIMIT, UserSearchQuery("alice", limit = UserSearchQuery.MAX_LIMIT).limit)
    }

    @Test
    fun `UserListView is schema-versioned`() {
        assertEquals(1, UserListView(emptyList()).schemaVersion)
        assertEquals(1, UserListView.SCHEMA_VERSION)
    }

    @Test
    fun `UserConnectionState stringifies to stable values`() {
        assertEquals("not_connected", UserConnectionState.NOT_CONNECTED.toString())
        assertEquals("accepted", UserConnectionState.ACCEPTED.toString())
        assertEquals("blocked", UserConnectionState.BLOCKED.toString())
        assertEquals("pending", UserConnectionState.PENDING.toString())
        assertEquals("unknown", UserConnectionState.UNKNOWN.toString())
    }
}
