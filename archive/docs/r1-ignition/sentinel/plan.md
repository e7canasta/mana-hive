# Mi Opinión como Experto

*Me levanto de la mesa, camino hacia la pizarra, y agrego lo que falta.*

---

## Lo que Funciona Bien

### 1. La Unificación de Estructura ✓

> "Alerta e Incidente son lo mismo con diferentes datos. Esto es **Replace Conditional with Polymorphism** al revés — en vez de tipos diferentes, datos diferentes. Más simple, más mantenible, más extensible."

### 2. La Separación de Responsabilidades ✓

> "Sentinel emite. No confirma, no escala, no graba, no entrega. Esto es **Clean Architecture** — el núcleo puro no sabe qué hace el exterior con sus decisiones. El exterior hace lo que quiere."

### 3. El Aggregate Root ✓

> "Episodio como guardián de consistencia. Un episodio por cama a la vez. Esto evita estados inconsistentes — no puede haber dos episodios abiertos para la misma cama."

### 4. Los Domain Events ✓

> "Los eventos son la integración con el exterior. NVR escucha `EpisodioAbierto` y empieza a grabar. Notificaciones escucha `EpisodioCerrado` y deja de notificar. Métricas escucha todo y calcula KPIs. Sin acoplamiento."

### 5. La Severidad Contextual ✓

> "No es solo la transición. Es la transición + el perfil del residente + el horario + la presencia de staff. Un `BedEdge` de noche con riesgo alto y sin staff es INCIDENTE. El mismo `BedEdge` de día con riesgo bajo es ALERTA."

### 6. El Gap de Asistencia ✓

> "Registrar cuánto tiempo tardó el staff en asistir es crítico para gestión de carga. No es para castigar, es para saber si el personal está saturado."

### 7. El Auto-Recovery con Confirmación ✓

> "Para incidentes, incluso si el residente vuelve a estado seguro, el staff debe ir a revisar. Un paciente post-operatorio que se movió y volvió a la cama puede haberse desconectado el suero. La confirmación es seguridad."

---

## Lo que Agrego

### 1. El Concepto de "Paraguas" debe ser Explícito

> "El paraguas no es solo 'hay episodio abierto'. Es 'este episodio es el padre de todos los eventos siguientes hasta que cierre'. Necesitamos hacerlo explícito en el modelo."

```kotlin
// En Episodio
fun esPadreDe(hecho: HechoDeEscena): Boolean =
    estado == EstadoEpisodio.ABIERTO && camaId == hecho.camaId

fun esNotifiableBajoParaguas(hecho: HechoDeEscena, regla: ReglaSentinel): Boolean =
    esPadreDe(hecho) && regla.esNotifiable(hecho.tipo)
```

### 2. La Regla de "Notifiable" debe ser Clara

> "¿Qué hace que un evento sea notifiable bajo un paraguas? No todo es notifiable. Si el episodio es por `BedEdge`, y el residente se sienta en la cama, ¿eso es notifiable? Depende de la política."

```kotlin
// En ReglaSentinel
fun esNotifiable(tipo: TipoHecho): Boolean =
    when {
        tipo == trigger -> true  // siempre notificar el trigger
        tipo in eventosNotables -> true  // configurado en la regla
        else -> false
    }
```

### 3. El "Episodio Padre" debe Registrar su Historia

> "El episodio no es solo un estado. Es una historia. Cada evento bajo el paraguas se agrega al episodio. Esto permite reconstruir qué pasó, generar métricas, y auditar."

```kotlin
// En Episodio
fun agregarEvento(hecho: HechoDeEscena): Episodio =
    copy(eventos = eventos + hecho)
```

### 4. La Condición de Cierre debe ser un Value Object

> "No es solo un enum. Es un Value Object que encapsula la lógica de cuándo cierra. Esto permite extender sin modificar."

```kotlin
sealed interface CondicionCierre {
    fun puedeCerrar(staffAsistio: Boolean, estadoSeguro: Boolean): Boolean
    
    data object SoloSeguro : CondicionCierre {
        override fun puedeCerrar(staffAsistio: Boolean, estadoSeguro: Boolean) =
            estadoSeguro
    }
    
    data object StaffYSeguro : CondicionCierre {
        override fun puedeCerrar(staffAsistio: Boolean, estadoSeguro: Boolean) =
            staffAsistio && estadoSeguro
    }
}
```

### 5. El .dat/.out debe ser Consistente con los Otros Motores

> "Ya tenemos el patrón en scene-batch y politica-batch. Sentinel-batch debe seguir el mismo formato. Esto reduce la curva de aprendizaje y facilita la integración."

```
# Formato .dat (consistente con scene-batch y politica-batch)
resident maria cama 301 riesgo MEDIO

TS+0s BedEdge
TS+10s Standing
TS+20s Lying

# Formato .out (consistente con scene-batch y politica-batch)
TS+0s ABRIR episodio=ep-001 severidad=ALERTA condicion=SOLO_SEGURO trigger=BedEdge
TS+10s INFORMAR episodio=ep-001 hecho=Standing criticidad=INFORMATIVO
TS+20s CERRAR episodio=ep-001 motivo=AUTO_RECOVERY
```

### 6. El Sprint 0 debe Incluir los Contratos de Integración

> "Los contratos de integración con Scene Engine y Politica Engine deben definirse en Sprint 0, no en Sprint 4. Esto permite que los equipos trabajen en paralelo."

```kotlin
// Contrato de integración (definido en Sprint 0)
interface SceneEngineContract {
    fun subscribeToSceneEvents(handler: (HechoDeEscena) -> Unit)
}

interface PoliticaEngineContract {
    fun getReglasEfectivas(residenteId: ResidenteId): ReglasEfectivas
}
```

---

## Lo que Simplifico

### 1. CondicionCierre como Boolean (por ahora)

> "Para el MVP, `requiereStaff: Boolean` es suficiente. El `sealed class` es más extensible, pero añade complejidad. Empezamos simple, extendemos cuando haya una tercera condición."

```kotlin
// MVP
data class Episodio(
    // ...
    val requiereStaff: Boolean,  // true = INCIDENTE, false = ALERTA
)

// Futuro (cuando haya tercera condición)
sealed interface CondicionCierre { ... }
```

### 2. EstadoDeEpisodios como Simple Map

> "Para el MVP, `Map<CamaId, Episodio>` es suficiente para los episodios abiertos. Los cerrados se pueden agregar después para métricas."

```kotlin
// MVP
data class EstadoDeEpisodios(
    val abiertos: Map<CamaId, Episodio>,
)

// Futuro (para métricas)
data class EstadoDeEpisodios(
    val abiertos: Map<CamaId, Episodio>,
    val cerrados: List<Episodio>,
)
```

---

## Lo que Clarifico

### 1. La Diferencia entre Alerta e Incidente

> "La diferencia no es estructural. Es semántica. Una alerta es 'esto puede ser un riesgo, pero se resuelve solo'. Un incidente es 'esto es un riesgo, alguien debe ir'. La estructura es la misma."

### 2. El Papel de las Políticas

> "Las políticas definen QUÉ es un trigger, QUÉ severidad tiene, y QUÉ condición de cierre. Sentinel no define esto. Sentinel consulta las políticas y actúa en consecuencia."

### 3. La Responsabilidad de la Escalada

> "Sentinel no escala. Sentinel emite `CerrarEpisodio` con `duracionGap`. Alguien más (Vigia/HarbOR) decide si escalar basado en ese gap. Sentinel solo informa."

---

## Mi Veredicto Final

```
┌─────────────────────────────────────────────────────────────────┐
│                    VEREDICTO DEL EXPERTO                         │
│                                                                 │
│  ✓ El diseño es SÓLIDO                                          │
│  ✓ La unificación de estructura es CORRECTA                     │
│  ✓ La separación de responsabilidades es LIMPIA                 │
│  ✓ El Aggregate Root es APROPIADO                               │
│  ✓ Los Domain Events son NECESARIOS                             │
│  ✓ La severidad contextual es CLÍNICA                           │
│  ✓ El gap de asistencia es OPERATIVO                            │
│  ✓ El auto-recovery con confirmación es SEGURO                  │
│                                                                 │
│  RECOMENDACIONES:                                               │
│  1. Hacer explícito el concepto de "paraguas"                   │
│  2. Clarificar qué es "notifiable bajo paraguas"                │
│  3. Simplificar CondicionCierre a Boolean para MVP              │
│  4. Definir contratos de integración en Sprint 0                │
│  5. Seguir el formato .dat/.out consistente                     │
│                                                                 │
│  PRÓXIMO PASO:                                                  │
│  Sprint 0 — Fundación del dominio                               │
└─────────────────────────────────────────────────────────────────┘
```

---

*Me siento. La mesa asiente. El roadmap está completo. El equipo de desarrollo agentic puede empezar.*

¿Alguna pregunta antes de cerrar la sesión?
