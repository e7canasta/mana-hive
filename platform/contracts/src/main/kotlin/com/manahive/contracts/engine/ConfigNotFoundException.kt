package com.manahive.contracts.engine

import com.manahive.kernel.ResidentId

/**
 * Exception thrown when configuration cannot be found for a resident.
 *
 * This is an unchecked exception (extends RuntimeException) that callers
 * may choose to handle. It provides context about what went wrong.
 *
 * Fowler: "Replace Exception Code with Special Case" — caller can
 * handle the special case gracefully.
 */
public class ConfigNotFoundException(
    public val residentId: ResidentId,
    message: String = "Configuration not found for resident: ${residentId.value}",
) : RuntimeException(message)
