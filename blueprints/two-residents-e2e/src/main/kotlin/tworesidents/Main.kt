package tworesidents

import com.manahive.blueprint.BlueprintOutcome
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.NoticeCommand
import com.manahive.harbor.harborCalibration
import com.manahive.contracts.common.Channel
import com.manahive.kernel.*
import com.manahive.politica.PolicyResolver
import com.manahive.politica.adapters.toSceneCalibration
import com.manahive.politica.adapters.toSentinelCalibration
import com.manahive.recorder.recordingCalibration
import com.manahive.runtime.EngineCalibrations
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.Outbound
import java.time.Duration
import java.time.Instant

// ═══════════════════════════════════════════════════════════════════════════
//  Dos residentes en UN solo runtime.
//
//  Este blueprint maneja el `NightWatchRuntime` de produccion, no dos
//  `PipelineContext` separados. La diferencia importa: con dos contextos, los
//  ledgers estan separados por construccion *del test* y no se prueba nada del
//  sistema. Con un runtime unico, la separacion de estado es una propiedad del
//  codigo que se despliega, y se puede afirmar.
// ═══════════════════════════════════════════════════════════════════════════

val NIGHT = NightId("night-2025-01-15")
val START: Instant = Instant.parse("2024-01-15T22:00:00Z")

val JOSE = ResidentId("jose"); val JOSE_BED = BedId("bed-4"); val CAM_J = MonitorId("CAM-4")
val ELENA = ResidentId("elena"); val ELENA_BED = BedId("bed-5"); val CAM_E = MonitorId("CAM-5")
val ANA = ResidentId("ana"); val ANA_BED = BedId("bed-6"); val CAM_A = MonitorId("CAM-6")

fun t(min: Long): Instant = START.plus(Duration.ofMinutes(min))

// ── Politica ───────────────────────────────────────────────────────────────
//
// Los dos vigilan SITTING_IN_BED, con umbrales distintos. Es a proposito: si
// Elena no tuviera ninguna regla, su "no abre episodio" seria vacuamente cierto
// —no probaria que su calibracion se respeta, solo que no tiene ninguna—. Con
// umbrales distintos, el MISMO estimulo produce resultados distintos, que es lo
// unico que demuestra que cada residente se juzga con lo suyo.

val joseProfile = buildResidentProfile("jose") {
    risk(RiskLevel.HIGH); mobility(MobilityAid.NONE); level(WatchLevel.FALL_RISK)
    resident {
        sitting { alertAfter(Duration.ofMinutes(15)) }
    }
    comeBack(StateKind.LYING) { alertAfter(Duration.ofMinutes(20)) }
}

// Elena queda en STANDARD **puro**, sin umbrales propios. Es el nivel 0:
// "solo observar, sin alertas". Que no abra episodios no es un agujero, es su
// definicion — y lo que hay que comprobar no es que calle, sino que sus
// cambios de escena SI se emitan, porque son los que el hub graba y los que
// alimentan su historial.
// Ana tiene la MISMA configuracion que Jose. Es a proposito: si los dos se
// juzgan igual y aun asi cada uno cobra su aviso con el presupuesto del otro
// agotado, la separacion de estado quedo probada sin ambiguedad.
val anaProfile = buildResidentProfile("ana") {
    risk(RiskLevel.HIGH); mobility(MobilityAid.NONE); level(WatchLevel.FALL_RISK)
    resident {
        sitting { alertAfter(Duration.ofMinutes(15)) }
    }
}

val elenaProfile = buildResidentProfile("elena") {
    risk(RiskLevel.LOW); mobility(MobilityAid.NONE); level(WatchLevel.STANDARD)
}

/** Presupuesto de 1 aviso WARNING: alcanza para probar que no se comparte. */
fun calibrationsFor(profile: ResidentProfileConfig, catalog: DagCatalog, who: String): EngineCalibrations {
    val policy = PolicyResolver.resolve(catalog, profile.profile).value
    return EngineCalibrations(
        scene = policy.toSceneCalibration(),
        sentinel = policy.toSentinelCalibration(),
        harbor = harborCalibration {
            resident(who)
            budget { warning(1) }
            notice { channels = setOf(Channel.CONSOLE); escalationTimeout = Duration.ofMinutes(30) }
            alert { channels = setOf(Channel.PUSH); escalationTimeout = Duration.ofMinutes(5) }
            call {
            channels = setOf(Channel.PUSH, Channel.TABLET)
            escalationTimeout = Duration.ofMinutes(2)
        }
        incident { channels = setOf(Channel.PUSH); escalationTimeout = Duration.ZERO }
        },
        recorder = recordingCalibration { resident(who) },
    )
}

// ── Arnes minimo ───────────────────────────────────────────────────────────

class Scenario(val name: String, val runtime: NightWatchRuntime) {
    private val checks = mutableListOf<Pair<String, Boolean>>()
    val out = mutableMapOf<ResidentId, MutableList<Outbound>>()

    fun observe(who: ResidentId, bed: BedId, cam: MonitorId, kind: ObservationKind, at: Instant) {
        val o = Observation(
            monitor = cam, bed = bed,
            kind = kind, confidence = 0.95, observedAt = at,
        )
        out.getOrPut(who) { mutableListOf() } += runtime.onObservation(who, o)
    }

    /** El barrido entra por el mismo candado que las observaciones. */
    fun tick(at: Instant) {
        runtime.tickAll(at).forEach { (id, o) -> out.getOrPut(id) { mutableListOf() } += o }
    }

    private fun <T : Any> has(who: ResidentId, pick: (Outbound) -> List<Any>, type: Class<T>) =
        out[who].orEmpty().flatMap(pick).any { type.isInstance(it) }

    fun expectScene(who: ResidentId, type: Class<out SceneEvent>, present: Boolean = true) =
        check("${who.value}: ${type.simpleName}${if (present) "" else " ausente"}",
            has(who, { it.sceneFacts }, type) == present)

    fun expectSignal(who: ResidentId, type: Class<out SentinelSignal>, present: Boolean = true) =
        check("${who.value}: ${type.simpleName}${if (present) "" else " ausente"}",
            has(who, { it.signals }, type) == present)

    fun expectDispatches(who: ResidentId, n: Int) =
        check("${who.value}: $n Dispatch",
            out[who].orEmpty().flatMap { it.harborCommands }.count { it.command is NoticeCommand.Dispatch } == n)

    fun expectOpenEpisodes(who: ResidentId, n: Int) =
        check("${who.value}: $n episodio(s) abierto(s) en SU ledger",
            runtime.get(who)!!.openEpisodeCount() == n)

    fun check(desc: String, ok: Boolean) { checks += desc to ok }

    fun report() {
        println("  ── $name ──")
        checks.forEach { println("    ${if (it.second) "✅" else "❌"} ${it.first}") }
        println()
        BlueprintOutcome.record(name, checks)
    }
}

// ── Main ───────────────────────────────────────────────────────────────────

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  Dos residentes · UN runtime · misma noche")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val runtime = NightWatchRuntime()
    runtime.register(JOSE, JOSE_BED, NIGHT, CAM_J, calibrationsFor(joseProfile, FALL_RISK_CATALOG, "jose"))
    runtime.register(ELENA, ELENA_BED, NIGHT, CAM_E, calibrationsFor(elenaProfile, STANDARD_CATALOG, "elena"))
    runtime.register(ANA, ANA_BED, NIGHT, CAM_A, calibrationsFor(anaProfile, FALL_RISK_CATALOG, "ana"))
    println("  José  bed-4 · FALL_RISK · sentado alerta a los 15 min · comeback 20 min")
    println("  Elena bed-5 · STANDARD  · sólo observa: sin alertas, con registro")
    println("  Ana   bed-6 · FALL_RISK · misma config que José, presupuesto propio")
    println("  Runtimes activos: ${runtime.size}")
    println()

    // ── 1 · Mismo estimulo, distinto veredicto ─────────────────────────────
    Scenario("Los dos se sientan 25 minutos", runtime).apply {
        observe(JOSE, JOSE_BED, CAM_J, ObservationKind.IN_BED, t(0))
        observe(ELENA, ELENA_BED, CAM_E, ObservationKind.IN_BED, t(0))
        observe(JOSE, JOSE_BED, CAM_J, ObservationKind.SITTING_IN_BED, t(60))
        observe(ELENA, ELENA_BED, CAM_E, ObservationKind.SITTING_IN_BED, t(60))
        // barrido cada 5 minutos, como en produccion
        for (m in 65L..85L step 5) tick(t(m))

        expectScene(JOSE, SceneEvent.DwellExceeded::class.java)
        expectSignal(JOSE, SentinelSignal.EpisodeOpened::class.java)
        expectOpenEpisodes(JOSE, 1)

        // Elena recibio exactamente lo mismo. En STANDARD no se juzga: no hay
        // episodio ni aviso. Pero su transicion SI se emite — es su historial.
        expectSignal(ELENA, SentinelSignal.EpisodeOpened::class.java, present = false)
        expectDispatches(ELENA, 0)
        expectOpenEpisodes(ELENA, 0)
        expectScene(ELENA, SceneEvent.TransitionDetected::class.java)
    }.report()

    // ── 2 · El presupuesto de uno no silencia al otro ───────────────────────
    // Jose ya gasto su unico WARNING. Si el estado de harbor fuera global,
    // Elena quedaria muda. Con estado por residente, cobra el suyo.
    Scenario("Ana cobra su aviso con el presupuesto de José agotado", runtime).apply {
        check("José ya gastó su único WARNING", runtime.get(JOSE)!!.dispatchedCount() >= 1)

        observe(ANA, ANA_BED, CAM_A, ObservationKind.IN_BED, t(100))
        observe(ANA, ANA_BED, CAM_A, ObservationKind.SITTING_IN_BED, t(105))
        for (m in 110L..135L step 5) tick(t(m))

        expectSignal(ANA, SentinelSignal.EpisodeOpened::class.java)
        expectDispatches(ANA, 1)          // no la silencia el presupuesto de José
        expectOpenEpisodes(ANA, 1)
        expectOpenEpisodes(JOSE, 1)       // y el episodio de José sigue siendo suyo
        expectOpenEpisodes(ELENA, 0)
    }.report()

    // ── 3 · ComeBack: sale de la cama y no vuelve ───────────────────────────
    Scenario("José sale de la cama y no vuelve — ComeBackExceeded", runtime).apply {
        observe(JOSE, JOSE_BED, CAM_J, ObservationKind.IN_BED, t(200))      // vuelve: desarma
        observe(JOSE, JOSE_BED, CAM_J, ObservationKind.STANDING, t(210))    // sale: arma la mina
        for (m in 215L..240L step 5) tick(t(m))                             // 20 min sin volver

        expectScene(JOSE, SceneEvent.ComeBackExceeded::class.java)
        expectScene(ELENA, SceneEvent.ComeBackExceeded::class.java, present = false)
    }.report()

    BlueprintOutcome.summarize()
}
