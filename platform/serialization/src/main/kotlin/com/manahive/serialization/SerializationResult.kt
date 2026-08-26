package com.manahive.serialization

/**
 * Typed errors for serialization operations.
 *
 * Fowler: "Replace Error Code with Exception" — errors are values, not strings.
 * Vernon: "Value Object" — each error carries context.
 */
sealed interface SerializationError {
    val message: String

    data class UnknownFormat(
        val name: String,
        val available: Set<String>,
    ) : SerializationError {
        override val message: String
            get() = "Unknown format: $name. Available: $available"
    }

    data class ParseError(
        val line: Int,
        val reason: String,
        val source: String = "",
    ) : SerializationError {
        override val message: String
            get() = "Parse error at line $line: $reason${if (source.isNotEmpty()) " in $source" else ""}"
    }

    data class FileNotFound(
        val path: String,
    ) : SerializationError {
        override val message: String
            get() = "File not found: $path"
    }

    data class InvalidState(
        val state: String,
        val validStates: Set<String>,
    ) : SerializationError {
        override val message: String
            get() = "Invalid state: $state. Valid states: $validStates"
    }

    data class MissingField(
        val fieldName: String,
        val typeName: String,
    ) : SerializationError {
        override val message: String
            get() = "Missing required field '$fieldName' in $typeName"
    }

    data class InvalidTransition(
        val from: String,
        val to: String,
    ) : SerializationError {
        override val message: String
            get() = "Invalid transition: $from -> $to"
    }
}

/**
 * Wrapper exception for SerializationError.
 */
class SerializationException(val error: SerializationError) : RuntimeException(error.message)

/**
 * Result type for serialization operations.
 *
 * Vernon: "Either monad" — success or error, never exceptions.
 */
sealed interface SerializationResult<out T> {
    data class Success<T>(val value: T) : SerializationResult<T>
    data class Failure(val error: SerializationError) : SerializationResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw SerializationException(error)
    }

    fun <R> map(transform: (T) -> R): SerializationResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> SerializationResult<R>): SerializationResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun onSuccess(action: (T) -> Unit): SerializationResult<T> {
        if (this is Success) action(value)
        return this
    }

    fun onError(action: (SerializationError) -> Unit): SerializationResult<T> {
        if (this is Failure) action(error)
        return this
    }
}

inline fun <T> serialization(block: () -> T): SerializationResult<T> = try {
    SerializationResult.Success(block())
} catch (e: SerializationException) {
    SerializationResult.Failure(e.error)
} catch (e: Exception) {
    SerializationResult.Failure(SerializationError.ParseError(0, e.message ?: "Unknown error"))
}
