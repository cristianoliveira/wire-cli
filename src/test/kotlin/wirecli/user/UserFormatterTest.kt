package wirecli.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserFormatterTest {
    private val formatter = UserFormatter()

    private val alice =
        UserView(
            id = "alice-uuid@example.wire.com",
            name = "Alice Almond",
            handle = "alice",
            email = "alice@example.com",
            team = "Acme",
            connection = UserConnectionState.ACCEPTED,
        )

    private val bob =
        UserView(
            id = "bob-uuid@example.wire.com",
            name = null,
            handle = null,
            email = null,
            team = null,
            connection = UserConnectionState.NOT_CONNECTED,
        )

    @Test
    fun `table shows no-results message for empty list`() {
        assertEquals(UserMessages.NO_RESULTS, formatter.toTable(emptyList()))
    }

    @Test
    fun `table includes header and user rows`() {
        val table = formatter.toTable(listOf(alice, bob))

        assertTrue(table.contains("ID"))
        assertTrue(table.contains("NAME"))
        assertTrue(table.contains("HANDLE"))
        assertTrue(table.contains("CONNECTION"))
        assertTrue(table.contains("alice-uuid@example.wire.com"))
        assertTrue(table.contains("Alice Almond"))
        assertTrue(table.contains("accepted"))
    }

    @Test
    fun `json output is schema-versioned with users array`() {
        val json = formatter.toJson(listOf(alice))

        assertTrue(json.startsWith("""{"schemaVersion":${UserListView.SCHEMA_VERSION},"users":["""))
        assertTrue(json.contains("\"id\":\"alice-uuid@example.wire.com\""))
        assertTrue(json.contains("\"name\":\"Alice Almond\""))
        assertTrue(json.contains("\"handle\":\"alice\""))
        assertTrue(json.contains("\"connection\":\"accepted\""))
        assertTrue(json.endsWith("]}"))
    }

    @Test
    fun `json renders nulls for missing optional fields`() {
        val json = formatter.toJson(listOf(bob))

        assertTrue(json.contains("\"name\":null"))
        assertTrue(json.contains("\"handle\":null"))
        assertTrue(json.contains("\"email\":null"))
        assertTrue(json.contains("\"team\":null"))
        assertFalse(json.contains("\"name\":\"null\""))
    }

    @Test
    fun `json escapes quotes and backslashes`() {
        val tricky =
            UserView(
                id = "id@domain",
                name = "A\"B\\C",
                handle = "h",
                email = null,
                team = null,
                connection = UserConnectionState.UNKNOWN,
            )
        val json = formatter.toJson(listOf(tricky))

        assertTrue(json.contains("A\\\"B\\\\C"))
    }

    @Test
    fun `json lines emits one object per line`() {
        val lines = formatter.toJsonLines(listOf(alice, bob))

        assertEquals(2, lines.split("\n").size)
        assertTrue(lines.split("\n")[0].contains("alice"))
    }

    @Test
    fun `detail shows labeled fields and dashes for missing values`() {
        val detail = formatter.toDetail(bob)

        assertTrue(detail.contains("ID: bob-uuid@example.wire.com"))
        assertTrue(detail.contains("Name: -"))
        assertTrue(detail.contains("Handle: -"))
        assertTrue(detail.contains("Connection: not_connected"))
    }
}
