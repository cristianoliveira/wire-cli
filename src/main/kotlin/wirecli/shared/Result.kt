package wirecli.shared

/**
 * Generic Result type for operations that can either succeed or fail.
 *
 * This is a unified Result type used across all modules to reduce code duplication
 * and ensure consistency in error handling patterns.
 *
 * @param T The type of the success value
 */
sealed interface Result<out T> {
    /**
     * Represents a successful operation with a value.
     *
     * @param value The result value of the successful operation
     */
    data class Success<T>(val value: T) : Result<T>

    /**
     * Represents a failed operation with error details.
     *
     * @param message Human-readable error message
     * @param exitCode CLI exit code for error classification
     */
    data class Failure(val message: String, val exitCode: Int) : Result<Nothing>
}

// Type aliases for each module to maintain clear semantic boundaries
// while sharing the same underlying Result implementation

/**
 * Result type for device operations.
 */
typealias DeviceResult<T> = Result<T>

/**
 * Result type for authentication operations.
 */
typealias AuthResult<T> = Result<T>

/**
 * Result type for authentication API operations (returns AuthSession).
 */
typealias AuthApiResult<T> = Result<T>

/**
 * Result type for sync operations.
 */
typealias SyncResult<T> = Result<T>

/**
 * Result type for profile operations.
 */
typealias ProfileResult<T> = Result<T>

/**
 * Result type for message operations.
 */
typealias MessageResult<T> = Result<T>

/**
 * Result type for presence operations.
 */
typealias PresenceResult<T> = Result<T>

/**
 * Result type for conversation operations.
 */
typealias ConversationResult<T> = Result<T>
