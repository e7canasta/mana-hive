package com.manahive.harbor.batch

/**
 * Typed errors for harbor batch operations.
 */
public sealed class HarborError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    public class SignalsNotFound(public val path: String) :
        HarborError("Signals file not found: $path")

    public class ParseError(public val lineNumber: Int, message: String) :
        HarborError("Parse error at line $lineNumber: $message")

    public class InvalidConfig(message: String) :
        HarborError("Invalid config: $message")
}
