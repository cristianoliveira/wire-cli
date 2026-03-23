package wirecli.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputValidatorTest {
    @Test
    fun `validateEmail accepts normalized valid email`() {
        val result = InputValidator.validateEmail("  user@example.com  ")

        assertEquals("user@example.com", result)
    }

    @Test
    fun `validateEmail rejects blank value`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validateEmail("   ") }

        assertEquals("Email must not be empty.", error.message)
    }

    @Test
    fun `validateEmail rejects invalid format`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validateEmail("invalid-email") }

        assertEquals("Email format is invalid.", error.message)
    }

    @Test
    fun `validatePassword accepts strong password`() {
        val result = InputValidator.validatePassword("StrongPass1")

        assertEquals("StrongPass1", result)
    }

    @Test
    fun `validatePassword rejects short value`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validatePassword("A1short") }

        assertEquals("Password must be at least 8 characters.", error.message)
    }

    @Test
    fun `validatePassword rejects missing uppercase`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validatePassword("lowercase1") }

        assertEquals("Password must contain at least one uppercase letter.", error.message)
    }

    @Test
    fun `validatePassword rejects missing number`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validatePassword("NoNumberPass") }

        assertEquals("Password must contain at least one number.", error.message)
    }

    @Test
    fun `validateDeviceId accepts expected symbols`() {
        val result = InputValidator.validateDeviceId("device_01:mobile-prod")

        assertEquals("device_01:mobile-prod", result)
    }

    @Test
    fun `validateDeviceId rejects blank value`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validateDeviceId("   ") }

        assertEquals("Device ID must not be empty.", error.message)
    }

    @Test
    fun `validateConversationId accepts valid uuid`() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"

        val result = InputValidator.validateConversationId(uuid)

        assertEquals(uuid, result)
    }

    @Test
    fun `validateConversationId rejects invalid uuid`() {
        val error =
            assertFailsWith<IllegalArgumentException> { InputValidator.validateConversationId("conversation-1") }

        assertEquals("Conversation ID must be a valid UUID.", error.message)
    }

    @Test
    fun `validateUserId accepts qualified id`() {
        val result = InputValidator.validateUserId("alice@example.com")

        assertEquals("alice@example.com", result)
    }

    @Test
    fun `validateUserId rejects invalid format`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validateUserId("alice") }

        assertEquals("User ID format is invalid. Expected format: value@domain.", error.message)
    }

    @Test
    fun `validateRequiredText rejects blank value`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validateRequiredText("  ", "Message") }

        assertEquals("Message must not be empty.", error.message)
    }

    @Test
    fun `validatePositiveLong rejects zero`() {
        val error = assertFailsWith<IllegalArgumentException> { InputValidator.validatePositiveLong(0L, "while-pid") }

        assertEquals("while-pid must be a positive integer.", error.message)
    }
}
