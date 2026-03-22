package wirecli.shared

/**
 * A generic Result type representing either a Success or Failure.
 * This eliminates DRY violations across all modules while maintaining type safety.
 */
sealed interface Result<out T, out E> {
    /**
     * Represents a successful operation with a value of type T.
     */
    data class Success<out T>(val value: T) : Result<T, Nothing>

    /**
     * Represents a failed operation with an error of type E.
     */
    data class Failure<out E>(val error: E) : Result<Nothing, E>
}
