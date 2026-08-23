package com.manahive.contracts.dag

import com.manahive.kernel.DagId

/**
 * Exception thrown when a DAG cannot be found.
 *
 * This is an unchecked exception (extends RuntimeException) that callers
 * may choose to handle.
 *
 * Fowler: "Replace Exception Code with Special Case" — caller can
 * handle the special case gracefully.
 */
public class DagNotFoundException(
    public val dagId: DagId,
    message: String = "DAG not found: ${dagId.value}",
) : RuntimeException(message)
