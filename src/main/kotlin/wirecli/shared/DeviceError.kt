package wirecli.shared

data class DeviceError(
    val message: String,
    val exitCode: Int
)
