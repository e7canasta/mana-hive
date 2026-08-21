# 08 · Acta de la segunda sesión — el v3 revisado con sus propias armas

**Objeto:** aplicar al v3 el método que el v3 le aplicó al v2. Nada está escrito en piedra; un paquete que no resiste su propia revisión no merece implementarse. Cuatro objeciones se levantaron; tres produjeron decisiones nuevas (D7–D9) y una ratificó endureciendo (D10). El documento 07 queda **enmendado** por D9.

---

## Objeción 7 — "Diez contextos el día uno no es descubrimiento; es cartografía"

**Voz que la levanta:** el modelador estratégico, contra su propio mapa.

**Diagnóstico.** El doc 02 derivó el mapa correctamente — desde capacidades, no desde tablas — pero luego cometió un pecado menor de la misma familia: declaró **diez** fronteras como si todas tuvieran hoy la misma evidencia. No la tienen. La frontera entre `situacion` y `respuesta` está probada por el dominio (el hecho no es la alarma; la supresión con constancia lo exige). La frontera entre `cobertura` y `cuidado`, en cambio, es una hipótesis: dos contextos cuyos lenguajes todavía no divergieron en nada — turno, ronda, presencia — y que quizá sean un solo lenguaje de *operación del plantel*. Y `plataforma` no es un bounded context: es subdominio genérico sin lenguaje propio; llamarlo contexto infla el mapa. Un bounded context se **gana** el nombre cuando su lenguaje diverge del vecino; hasta entonces es una carpeta con ambición.

**Resolución (D7).** El mapa del doc 02 pasa a ser **hipótesis con criterio de falsación**, no organigrama:

- Un contexto **existe en código** recién cuando su primera rebanada (doc 06 §8) lo necesita. R0 materializa `situacion` y `respuesta`; los demás nacen con su rebanada, no antes.
- **Prueba de divergencia de lenguaje**, revisada al cierre de cada rebanada: si dos contextos vecinos llevan dos rebanadas sin que ninguna palabra compartida signifique cosas distintas a cada lado, son candidatos a fusión (`cobertura` + `cuidado` → `operacion` es la apuesta de la sala). A la inversa: si dentro de un contexto una palabra empieza a significar dos cosas ("presencia" del plantel para suprimir alarmas vs. "presencia" para liquidar turnos), esa es la señal de partir.
- Los **diez nombres se conservan en el lenguaje** (glosario, eventos, conversación) aunque el código tenga menos fronteras físicas. El lenguaje va adelante; la estructura lo sigue con evidencia.

## Objeción 8 — "`situacion.cama.{id}` es un flujo sin fin — el event sourcing serio cierra los libros"

**Voz que la levanta:** la práctica de los grandes sistemas event-sourced en producción.

**Diagnóstico.** El catálogo del doc 03 define el flujo del gemelo por cama, a perpetuidad. Una cama emite transiciones toda la noche, todas las noches, durante años: decenas de miles de eventos por flujo. Todo lo que el diseño compra con event sourcing se degrada con flujos sin fin: la rehidratación deja de ser "leer el flujo" y pasa a depender de snapshots cada vez más críticos; el archivado es imposible porque el flujo nunca termina; la retención clínica (¿cuántos años de transiciones crudas?) no tiene dónde cortarse; y el replay de una noche exige filtrar un océano. La contabilidad resolvió esto hace siglos: **se cierran los libros**. No se reabre el mayor de 1998 para saber el saldo de hoy; se abre el período con su asiento de apertura.

Y este dominio tiene un período contable natural, nombrado por el propio paquete en cada documento sin darse cuenta: **la noche**. El simulador genera "noches", el replay reproduce "noches", la métrica es "minutos-cama por noche", la frase fundacional habla de "la noche de una residencia".

**Resolución (D8).** La **jornada como período contable** del contexto `situacion`:

- El flujo pasa a ser `situacion.cama.{id}.{jornada}` (jornada = fecha de apertura del turno nocturno/diurno según configuración de la residencia).
- Abre con `JornadaAbierta.v1` — el asiento de apertura: ocupante vinculado, estado inicial del gemelo, `estadoDesde` heredado, salud de señal. Todo lo que la rehidratación necesita, sin leer el pasado.
- Cierra con `JornadaCerrada.v1`: estado final + resumen (conteos por estado, minutos en `Desconocido`, episodios). El cierre lo comanda un process manager pequeño y con nombre — `CierreDeJornada` — que además es el gancho natural para la compactación a Parquet: **jornada cerrada = partición analítica inmutable**.
- La rehidratación del gemelo lee **solo la jornada abierta**: cientos de eventos, no decenas de miles. El snapshot deja de ser pieza crítica de corrección y vuelve a ser lo que debe ser: optimización.
- Un dwell que cruza el corte no se pierde: `JornadaAbierta` hereda `estadoDesde` — el principio de los dos tiempos ya garantiza que la permanencia es derivada, no cronómetro.

Los demás flujos no cambian, y la regla queda explícita para el futuro: **un flujo debe tener final natural**. `respuesta.alerta.{id}` y `aprendizaje.propuesta.{id}` lo tienen (resuelta/decidida son absorbentes); `memoria.incidente.{id}` también (veredicto). `criterio.residente.{id}` es legítimamente largo pero de frecuencia mínima — decenas de eventos en años de estadía — y es precisamente el flujo cuya historia completa **es** el valor clínico: se queda entero, con snapshot por conveniencia.

## Objeción 9 — "Veinticinco módulos Gradle el día cero contradice la rebanada R0" (enmienda al 07)

**Voz que la levanta:** XP — el propio autor del 07, contra su árbol.

**Diagnóstico.** El doc 07 dibujó el árbol **destino** y lo presentó como día uno. Eso repite en el plano físico el error que la Objeción 7 señaló en el plano lógico: fronteras declaradas antes que ganadas. Peor: contradice la R0 del doc 06, que pide un esqueleto andante — y un esqueleto no tiene veinticinco huesos. La confusión de fondo merece nombrarse porque es la enseñanza de la sesión: **la arquitectura es el grafo de dependencias permitidas, no el número de módulos Gradle**. El grafo lo custodian Konsist y Modulith al nivel de paquete igual de bien que al nivel de módulo; el módulo Gradle es una decisión de *build y de equipo* (tiempo de compilación, cadencia de release, propiedad del código), no de diseño. La única excepción real es la pureza gruesa —"cero dependencias por construcción" exige una frontera de módulo— y para eso alcanza **un** módulo puro, no ocho.

**Resolución (D9).** El árbol del 07 se reinterpreta como **destino documentado**; el día uno tiene seis módulos:

```text
registro/
├── build-logic/            # los mismos 4 roles del 07 — el rol nace antes que sus usuarios
├── dominio/                 # PURO (rol nucleo-puro): kernel + TODOS los núcleos de contexto,
│                            #   un paquete por contexto; Konsist vigila las fronteras internas
├── contratos/               # PURO: lenguaje publicado + esquemas + fixtures
├── plataforma-eventos/      # puertos (api) + adaptador memoria + adaptador postgres,
│                            #   source sets separados; el banco de conformidad en testFixtures
├── servicio/                # todas las cáscaras como módulos Modulith (paquete = contexto),
│                            #   process managers, consultas; ApplicationModules.verify() aquí
├── simulador/               # DSL de escenarios + generador de noches (main source, doc 07 §7.1)
└── app/                     # ensamblaje Boot topología A
```

Las garantías no se aflojan un milímetro: `dominio` es puro por construcción (el rol lo impide), las fronteras entre contextos las verifica Modulith dentro de `servicio` y Konsist dentro de `dominio`, y el banco de conformidad corre contra ambos adaptadores desde R0. Lo único que se pospone es la multiplicación de carpetas.

**Disparadores de extracción** — se parte un módulo cuando un hecho lo pide, y el PR que parte cita su disparador:

| Disparador | Extracción |
| --- | --- |
| El build incremental de `dominio` o `servicio` supera el presupuesto (30 s) | El contexto más pesado a su par `nucleo`/`cascara` del árbol destino |
| Un motor entra en modo sombra con cadencia de versión propia (doc 06 §4) | Su núcleo de contexto a módulo propio, para que la huella de build selle solo lo que cambió |
| Un segundo equipo toma propiedad de un contexto | Ese contexto, núcleo y cáscara |
| Se activa la topología B (engine extraído) | `frontera/*` y el worker según el 07 |

Y el disparador inverso queda igual de escrito: un módulo extraído cuyo disparador desaparece se re-absorbe. La estructura respira; el grafo permitido no.

## Objeción 10 — "XP no es una sección; es cómo la sala construye"

**Diagnóstico.** El paquete tiene los artefactos de XP sin decir su nombre, y no decirlo tiene un costo: las prácticas se vuelven opcionales. La revisión detecta que el paquete ya contiene — ganado, no impuesto — el mapeo completo, y decide hacerlo normativo.

**Resolución (D10).** Prácticas con dueño en el paquete:

| Práctica XP | Dónde vive ya | Lo que se vuelve norma |
| --- | --- | --- |
| **Tracer bullet / esqueleto andante** | R0 (doc 06 §8) | Ninguna rebanada abre hasta que la anterior demuestra su cierre |
| **TDD** | dado–cuando–entonces (07 §7.2) | Todo Decider y motor nace de su test; la forma uniforme del núcleo (03 §1) existe para que el ciclo rojo-verde-refactor sea idéntico en todo el sistema |
| **Tests de cliente** | el banco de escenarios (06 §5) | El DSL de escenarios es el idioma en que el equipo clínico **acepta** una regla; un escenario nuevo del banco es una historia de usuario ejecutable |
| **Integración continua** | replay dorado (06 §3) | El hash del corpus corre en cada commit; divergencia no intencional = build roto |
| **Diseño simple / YAGNI** | D7 y D9 de esta acta | Fronteras y módulos se ganan con evidencia; todo "por si acaso" necesita un disparador escrito |
| **Propiedad colectiva** | la forma Decider + los 4 roles de módulo | Cualquiera del equipo entra a cualquier contexto porque todos tienen la misma forma |
| **Refactor sin miedo** | replay dorado + modo sombra + banco de conformidad | Las tres redes de seguridad son la licencia para cambiar; sin ellas, D7/D9 serían temeridad |

## Tabla de decisiones de la sesión

| # | Decisión | Enmienda a | Detalle |
| --- | --- | --- | --- |
| D7 | El mapa de contextos es hipótesis con prueba de divergencia; los contextos nacen con su rebanada; `cobertura`+`cuidado` candidatos a fusión; `plataforma` degradado a genérico sin rango de contexto | 02 §5 | esta acta |
| D8 | Cierre de libros: flujo del gemelo por (cama, jornada) con `JornadaAbierta`/`JornadaCerrada`; PM `CierreDeJornada`; jornada cerrada = partición Parquet; regla "todo flujo debe tener final natural" | 03 §3 | esta acta |
| D9 | Workspace evolutivo: seis módulos el día uno; el árbol del 07 es destino; disparadores de extracción y re-absorción escritos | 07 §2 | esta acta |
| D10 | Las prácticas XP del paquete se vuelven normativas con dueño | transversal | esta acta |

---

*Lo que esta sesión no tocó y ratifica sin reabrir: ledger-first (05), las cartas de los seis motores (04), la regla de las tres preguntas (03 §2) — que D8 no cambia: el gemelo sigue respondiendo sí a las tres; lo que cambia es la **granularidad del flujo**, no el veredicto — y el registro de decisión como artefacto central de rendición de cuentas.*
