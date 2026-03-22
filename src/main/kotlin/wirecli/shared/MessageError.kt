package wirecli.shared

data class MessageError(
    val message: String,
    val exitCode: Int
)
