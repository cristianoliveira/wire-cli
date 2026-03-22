package wirecli.shared

data class AuthError(
    val message: String,
    val exitCode: Int
)
