package wirecli.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRedactorTest {
    @Test
    fun `redacts key value style secrets`() {
        val input = "auth failed: password=super-secret token=abc123"

        assertEquals(
            "auth failed: password=<redacted> token=<redacted>",
            AuthRedactor.redact(input),
        )
    }

    @Test
    fun `redacts bearer tokens and jwt values`() {
        val input = "Authorization: Bearer abcdefghijklmnop and jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturepart"

        assertEquals(
            "Authorization: <redacted> <redacted> and jwt <redacted>",
            AuthRedactor.redact(input),
        )
    }

    @Test
    fun `redacts embedded credentials in urls`() {
        val input = "request failed at https://jane:super-secret@example.com/path"

        assertEquals(
            "request failed at https://jane:<redacted>@example.com/path",
            AuthRedactor.redact(input),
        )
    }
}
