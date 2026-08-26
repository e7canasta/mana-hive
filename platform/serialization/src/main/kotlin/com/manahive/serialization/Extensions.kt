package com.manahive.serialization

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.NoticeCommand
import com.manahive.contracts.policy.DagCatalog
import com.manahive.contracts.policy.ResidentProfileConfig
import com.manahive.kernel.ResidentId
import java.io.File
import java.time.Instant

/**
 * Extension functions for SceneEvent serialization.
 *
 * Usage:
 * ```kotlin
 * val json = event.toJson()
 * val event = json.toSceneEvent()
 * val text = event.toText(startTime)
 * ```
 */
fun SceneEvent.toJson(): String = SceneEventCodec.encode(this)

fun String.toSceneEvent(): SerializationResult<SceneEvent> = SceneEventCodec.decode(this)

fun SceneEvent.toText(startTime: Instant): String {
    val offset = java.time.Duration.between(startTime, this.at)
    val type = this::class.simpleName ?: "Unknown"
    val details = SceneEventCodec.formatDetails(this)
    return "t=${com.manahive.serialization.formats.DurationFormat.format(offset)}  $type $details"
}

/**
 * Extension functions for SentinelSignal serialization.
 *
 * Usage:
 * ```kotlin
 * val json = signal.toJson()
 * val signal = json.toSentinelSignal()
 * ```
 */
fun SentinelSignal.toJson(): String = SentinelSignalCodec.encode(this)

fun String.toSentinelSignal(): SerializationResult<SentinelSignal> = SentinelSignalCodec.decode(this)

/**
 * Extension functions for NoticeCommand serialization.
 *
 * Usage:
 * ```kotlin
 * val json = command.toJson()
 * val command = json.toNoticeCommand()
 * ```
 */
fun NoticeCommand.toJson(): String = NoticeCommandCodec.encode(this)

fun String.toNoticeCommand(): SerializationResult<NoticeCommand> = NoticeCommandCodec.decode(this)

/**
 * Extension functions for DagCatalog serialization.
 *
 * Usage:
 * ```kotlin
 * val toml = catalog.toToml()
 * val catalog = toml.toCatalog()
 * catalog.saveTo(File("catalog.toml"))
 * val catalog = File("catalog.toml").loadCatalog()
 * ```
 */
fun DagCatalog.toToml(): String = CatalogCodec.encode(this)

fun String.toCatalog(): SerializationResult<DagCatalog> = CatalogCodec.decode(this)

fun DagCatalog.saveTo(file: File) = CatalogCodec.save(this, file)

fun File.loadCatalog(): SerializationResult<DagCatalog> = CatalogCodec.load(this)

/**
 * Extension functions for ResidentProfileConfig serialization.
 *
 * Usage:
 * ```kotlin
 * val toml = profile.toToml()
 * val profile = toml.toProfile()
 * profile.saveTo(File("jose-policy.toml"))
 * val profile = File("jose-policy.toml").loadProfile()
 * ```
 */
fun ResidentProfileConfig.toToml(): String = PolicyCodec.encode(this)

fun String.toProfile(): SerializationResult<ResidentProfileConfig> = PolicyCodec.decode(this)

fun ResidentProfileConfig.saveTo(file: File) = PolicyCodec.save(this, file)

fun File.loadProfile(): SerializationResult<ResidentProfileConfig> = PolicyCodec.load(this)

/**
 * Extension functions for multi-resident catalog serialization.
 *
 * Usage:
 * ```kotlin
 * val toml = residents.toToml()
 * val residents = toml.toResidentCatalog()
 * residents.saveTo(File("residents.toml"))
 * val residents = File("residents.toml").loadResidentCatalog()
 * ```
 */
fun Map<ResidentId, ResidentProfileConfig>.toToml(): String = MultiResidentCodec.encode(this)

fun String.toResidentCatalog(): SerializationResult<Map<ResidentId, ResidentProfileConfig>> = MultiResidentCodec.decode(this)

fun Map<ResidentId, ResidentProfileConfig>.saveTo(file: File) = MultiResidentCodec.save(this, file)

fun File.loadResidentCatalog(): SerializationResult<Map<ResidentId, ResidentProfileConfig>> = MultiResidentCodec.load(this)
