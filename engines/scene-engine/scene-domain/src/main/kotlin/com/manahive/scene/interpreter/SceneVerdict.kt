package com.manahive.scene.interpreter

import com.manahive.contracts.scene.SceneEvent
import com.manahive.scene.core.DigitalTwin

public data class SceneVerdict(
    public val twin: DigitalTwin,
    public val facts: List<SceneEvent>,
)
