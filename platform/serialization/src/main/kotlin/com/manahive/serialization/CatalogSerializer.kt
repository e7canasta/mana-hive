package com.manahive.serialization

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Catalog serializer: DagCatalog ↔ TOML.
 *
 * Usage:
 * ```kotlin
 * // Serialize to TOML
 * val toml = CatalogSerializer.toToml(catalog)
 *
 * // Deserialize from TOML
 * val catalog = CatalogSerializer.fromToml(toml)
 *
 * // Save to file
 * CatalogSerializer.saveToFile(catalog, File("catalog.toml"))
 *
 * // Load from file
 * val catalog = CatalogSerializer.loadFromFile(File("catalog.toml"))
 * ```
 */
object CatalogSerializer {

    fun toToml(catalog: DagCatalog): String {
        val toml = CatalogToml(
            version = catalog.version.value,
            resident = catalog.residentStates.map { (state, rule) ->
                state.name.lowercase() to StateRuleToml(
                    warningAfter = rule.warningAfter?.toString(),
                    alertAfter = rule.alertAfter?.toString(),
                    severity = rule.severity.name,
                    closure = rule.closureCondition.name,
                )
            }.toMap(),
            transitions = catalog.transitions.map { (key, rule) ->
                "${key.from.name.lowercase()}To${key.to.name.lowercase()}" to TransitionRuleToml(
                    hysteresis = rule.hysteresis.toString(),
                    recordBefore = rule.recordBefore?.toString(),
                    recordAfter = rule.recordAfter?.toString(),
                )
            }.toMap(),
            room = RoomRulesToml(
                staffEnters = catalog.roomStates["staffEnters"]?.let {
                    StaffEntersRuleToml(closeEpisode = it.closeEpisode)
                }
            ),
        )

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(toml)
    }

    fun fromToml(toml: String): DagCatalog {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val config = mapper.readValue(toml, CatalogToml::class.java)

        return DagCatalog(
            version = CatalogVersion(config.version),
            residentStates = config.resident.map { (stateName, rule) ->
                val state = StateKind.valueOf(stateName.uppercase())
                state to ResidentStateRule(
                    state = state,
                    warningAfter = rule.warningAfter?.let { Duration.parse(it) },
                    alertAfter = rule.alertAfter?.let { Duration.parse(it) },
                    severity = rule.severity?.let { Severity.valueOf(it) } ?: Severity.INFO,
                    closureCondition = rule.closure?.let { ClosureCondition.valueOf(it) } ?: ClosureCondition.SAFE_ONLY,
                )
            }.toMap(),
            transitions = config.transitions.map { (transitionName, rule) ->
                val parts = transitionName.split("To")
                val from = StateKind.valueOf(parts[0].uppercase())
                val to = StateKind.valueOf(parts[1].uppercase())
                TransitionKey(from, to) to DagTransitionRule(
                    from = from,
                    to = to,
                    hysteresis = Duration.parse(rule.hysteresis),
                    recordBefore = rule.recordBefore?.let { Duration.parse(it) },
                    recordAfter = rule.recordAfter?.let { Duration.parse(it) },
                )
            }.toMap(),
            roomStates = mapOf(
                "staffEnters" to RoomStateRule(
                    event = "staffEnters",
                    closeEpisode = config.room.staffEnters?.closeEpisode ?: false,
                )
            ),
        )
    }

    fun saveToFile(catalog: DagCatalog, file: java.io.File) {
        file.parentFile?.mkdirs()
        file.writeText(toToml(catalog))
    }

    fun loadFromFile(file: java.io.File): DagCatalog {
        if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")
        return fromToml(file.readText())
    }
}
