package wirecli.shared

data class ConversationError(
    val message: String,
    val exitCode: Int
)
