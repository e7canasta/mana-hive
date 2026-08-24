package com.manahive.scene.sweeper

import com.manahive.contracts.scene.SceneEvent

public data class SweepResult(
    public val facts: List<SceneEvent>,
    public val marks: DwellMarks,
)
