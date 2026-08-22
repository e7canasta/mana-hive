package com.manahive.contracts.ledger

public sealed interface AppendResult {
    public data class Appended(val lastGlobalSeq: Long) : AppendResult
    public data object Conflict : AppendResult
    public data object Duplicate : AppendResult
}
