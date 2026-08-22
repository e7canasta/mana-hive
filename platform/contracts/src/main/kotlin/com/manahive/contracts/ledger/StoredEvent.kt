package com.manahive.contracts.ledger

import com.manahive.contracts.EventEnvelope

public data class StoredEvent(
    public val globalSeq: Long,
    public val stream: String,
    public val streamSeq: Long,
    public val envelope: EventEnvelope,
)
