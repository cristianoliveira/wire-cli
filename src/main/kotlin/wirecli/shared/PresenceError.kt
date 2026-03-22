package wirecli.shared

data class PresenceError(
    val message: String,
    val exitCode: Int
)
