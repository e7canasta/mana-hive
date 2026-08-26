package com.manahive.serialization

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import java.time.Duration
import java.io.File

/**
 * Catalog codec: DagCatalog ↔ TOML.
 *
 * Usage:
 * ```kotlin
 * val toml = catalog.toToml()
 * val result = toml.toCatalog()
 * result.onSuccess { catalog -> ... }
 * catalog.saveTo(File("catalog.toml"))
 * ```
 */
object CatalogCodec : FileCodec<DagCatalog> {

    override fun encode(obj: DagCatalog): String {
        val toml = CatalogToml(
            version = obj.version.value,
            resident = obj.residentStates.map { (state, rule) ->
                state.name.lowercase() to StateRuleToml(
                    warningAfter = rule.warningAfter?.toString(),
                    alertAfter = rule.alertAfter?.toString(),
                    severity = rule.severity.name,
                    closure = rule.closureCondition.name,
                )
            }.toMap(),
            transitions = obj.transitions.map { (key, rule) ->
                "${key.from.name.lowercase()}To${key.to.name.lowercase()}" to TransitionRuleToml(
                    hysteresis = rule.hysteresis.toString(),
                    recordBefore = rule.recordBefore?.toString(),
                    recordAfter = rule.recordAfter?.toString(),
                )
            }.toMap(),
            room = RoomRulesToml(
                staffEnters = obj.roomStates["staffEnters"]?.let {
                    StaffEntersRuleToml(closeEpisode = it.closeEpisode)
                }
            ),
        )

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(toml)
    }

    override fun decode(text: String): SerializationResult<DagCatalog> = serialization {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val config = mapper.readValue(text, CatalogToml::class.java)

        val residentStates = mutableMapOf<StateKind, ResidentStateRule>()
        for ((stateName, rule) in config.resident) {
            val state = StateKindInput.parseStateKind(stateName).getOrThrow()
            residentStates[state] = ResidentStateRule(
                state = state,
                warningAfter = rule.warningAfter?.let { Duration.parse(it) },
                alertAfter = rule.alertAfter?.let { Duration.parse(it) },
                severity = rule.severity?.let { try { Severity.valueOf(it) } catch (_: Exception) { null } } ?: Severity.INFO,
                closureCondition = rule.closure?.let { try { ClosureCondition.valueOf(it) } catch (_: Exception) { null } } ?: ClosureCondition.SAFE_ONLY,
            )
        }

        val transitions = mutableMapOf<TransitionKey, DagTransitionRule>()
        for ((transitionName, rule) in config.transitions) {
            val parts = transitionName.split("To")
            if (parts.size != 2) {
                throw SerializationException(SerializationError.InvalidTransition(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }))
            }
            val from = StateKindInput.parseStateKind(parts[0]).getOrThrow()
            val to = StateKindInput.parseStateKind(parts[1]).getOrThrow()
            transitions[TransitionKey(from, to)] = DagTransitionRule(
                from = from,
                to = to,
                hysteresis = Duration.parse(rule.hysteresis),
                recordBefore = rule.recordBefore?.let { Duration.parse(it) },
                recordAfter = rule.recordAfter?.let { Duration.parse(it) },
            )
        }

        DagCatalog(
            version = CatalogVersion(config.version),
            residentStates = residentStates,
            transitions = transitions,
            roomStates = mapOf(
                "staffEnters" to RoomStateRule(
                    event = "staffEnters",
                    closeEpisode = config.room.staffEnters?.closeEpisode ?: false,
                )
            ),
        )
    }
}
