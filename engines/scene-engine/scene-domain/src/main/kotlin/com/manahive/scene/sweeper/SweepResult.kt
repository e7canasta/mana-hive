package com.manahive.scene.sweeper

import com.manahive.contracts.scene.SceneFact

public data class SweepResult(
    public val facts: List<SceneFact>,
    public val marks: DwellMarks,
)
