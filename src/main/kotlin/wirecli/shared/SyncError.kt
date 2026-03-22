package wirecli.shared

data class SyncError(
    val message: String,
    val exitCode: Int
)
