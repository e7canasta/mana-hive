package com.manahive.contracts.common

import java.security.MessageDigest

/**
 * Strongly-typed fingerprint for change detection.
 *
 * Value class: zero runtime cost, compiler enforces correctness.
 * A Fingerprint can never be passed where a String is expected.
 *
 * @property value The SHA-256 hex string
 */
@JvmInline
public value class Fingerprint(public val value: String) {
    init {
        require(value.isNotEmpty()) { "Fingerprint must not be empty" }
    }

    override fun toString(): String = value
}

/**
 * Compute SHA-256 fingerprint from a string.
 *
 * @return Fingerprint wrapping the SHA-256 hex string
 */
public fun String.toFingerprint(): Fingerprint {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this.toByteArray())
    val hex = hash.joinToString("") { "%02x".format(it) }
    return Fingerprint(hex)
}

/**
 * Build a fingerprint from multiple parts.
 *
 * Centralizes the pattern: sort, join, hash.
 * Ensures consistent fingerprinting across the codebase.
 *
 * @param parts Key-value pairs to include in the fingerprint
 * @return Fingerprint wrapping the SHA-256 hex string
 */
public fun buildFingerprint(vararg parts: Pair<String, Any>): Fingerprint {
    val combined = parts
        .sortedBy { it.first }
        .joinToString(":") { "${it.first}=${it.second}" }
    return combined.toFingerprint()
}
