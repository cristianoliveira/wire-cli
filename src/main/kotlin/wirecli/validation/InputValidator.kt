package wirecli.validation

object InputValidator {
    private const val MAX_EMAIL_LENGTH = 254
    private const val MAX_DEVICE_ID_LENGTH = 100
    private const val MIN_PASSWORD_LENGTH = 8

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val deviceIdRegex = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$")
    private val uuidRegex =
        Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
        )
    private val userIdRegex = Regex("^[^@\\s]+@[^@\\s]+$")

    fun validateEmail(email: String): String {
        val normalizedEmail = email.trim()
        require(normalizedEmail.isNotEmpty()) { "Email must not be empty." }
        require(normalizedEmail.length <= MAX_EMAIL_LENGTH) {
            "Email must be at most $MAX_EMAIL_LENGTH characters."
        }
        require(emailRegex.matches(normalizedEmail)) { "Email format is invalid." }
        return normalizedEmail
    }

    fun validatePassword(password: String): String {
        require(password.isNotBlank()) { "Password must not be empty." }
        require(password.length >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters." }
        require(password.any { it.isUpperCase() }) { "Password must contain at least one uppercase letter." }
        require(password.any { it.isDigit() }) { "Password must contain at least one number." }
        return password
    }

    fun validateDeviceId(id: String): String {
        val normalizedId = id.trim()
        require(normalizedId.isNotEmpty()) { "Device ID must not be empty." }
        require(normalizedId.length <= MAX_DEVICE_ID_LENGTH) {
            "Device ID must be at most $MAX_DEVICE_ID_LENGTH characters."
        }
        require(deviceIdRegex.matches(normalizedId)) {
            "Device ID format is invalid. Use letters, numbers, '.', '_', ':', or '-'."
        }
        return normalizedId
    }

    fun validateConversationId(id: String): String {
        val normalizedId = id.trim()
        require(normalizedId.isNotEmpty()) { "Conversation ID must not be empty." }
        require(uuidRegex.matches(normalizedId)) { "Conversation ID must be a valid UUID." }
        return normalizedId
    }

    fun validateUserId(id: String): String {
        val normalizedId = id.trim()
        require(normalizedId.isNotEmpty()) { "User ID must not be empty." }
        require(userIdRegex.matches(normalizedId)) {
            "User ID format is invalid. Expected format: value@domain."
        }
        return normalizedId
    }

    fun validateRequiredText(
        value: String,
        fieldName: String,
    ): String {
        require(value.isNotBlank()) { "$fieldName must not be empty." }
        return value
    }

    fun validatePositiveLong(
        value: Long,
        fieldName: String,
    ): Long {
        require(value > 0) { "$fieldName must be a positive integer." }
        return value
    }
}
