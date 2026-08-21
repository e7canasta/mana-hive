# 01 · Acta de la sala de diseño

**Presentes:** el modelador estratégico (voz Vernon), el arquitecto de evolución (voz Fowler), Claude como diseñador con agencia. Tercera cafetera en marcha.
**Objeto:** revisión crítica del documento v2 y re-diseño desde el dominio.

---

## El diagnóstico general

El v2 es un documento competente sobre una estructura heredada. Su falla no está en ningún detalle: está en el método. Tomó como axiomas cosas que eran decisiones — los once contextos tal como venían, un orquestador único llamado `aplicacion`, NATS JetStream como columna vertebral — y diseñó hacia abajo desde ahí. Una sesión de diseño real hace lo contrario: parte del problema, deriva el lenguaje, deja que las fronteras emerjan del significado y solo entonces elige mecanismos. Lo que sigue son las seis objeciones levantadas en la pizarra, cada una con su diagnóstico y su resolución. El detalle de cada resolución vive en los documentos 02 a 06.

---

## Objeción 1 — "Esos contextos no salieron del dominio; salieron de las tablas"

**Diagnóstico.** Los contextos del v2 (`ctx-residencia`, `ctx-poblacion`, `ctx-politica`, `ctx-vigilancia`…) reflejan la organización del código existente, que a su vez refleja grupos de tablas. La prueba de que la partición está mal trazada es su invariante más fuerte: la ocupación 1:1 (un residente en una cama, una cama con un residente) cruza dos contextos — residencia posee la cama, población posee la asignación — y por eso vive implementada en la capa de aplicación con IDs opacos e índices coordinados. Cuando la invariante más dura de un subdominio necesita salirse de todos los contextos para poder existir, las fronteras están en el lugar equivocado. Un bounded context se traza donde cambia el significado de las palabras, no donde cambia el prefijo de las tablas.

**Resolución.** Re-derivar los contextos desde las capacidades del dominio (documento 02). El resultado: diez contextos nombrados por lo que el negocio hace — `percepcion`, `situacion`, `criterio`, `respuesta`, `aprendizaje`, `memoria`, `alojamiento`, `cobertura`, `cuidado`, `plataforma` — donde `alojamiento` fusiona estructura física y censo precisamente para que la ocupación 1:1 viva **dentro** de una frontera, como invariante de agregado y no como coordinación de aplicación.

---

## Objeción 2 — "¿Por qué un orquestador? Un módulo llamado `aplicacion` es una bola de lodo con agenda"

**Diagnóstico.** El v2 conservó la capa `mana-app` bajo otro nombre: un lugar único donde viven "los casos de uso" y "la composición entre contextos". La sala ya había señalado esta gravedad en la revisión anterior y el v2 la institucionalizó en vez de resolverla. Un orquestador único acumula transaction scripts anónimos; en tres años, todo proceso de negocio interesante vive ahí, sin nombre, sin ciclo de vida propio, sin poder probarse aislado. Y confunde dos cosas que no se parecen: **comandar** (cambiar el mundo dentro de una frontera) y **coordinar procesos largos** (reaccionar a eventos a lo largo del tiempo, a través de fronteras).

**Resolución.** El orquestador desaparece (documento 03). Los comandos viven en el contexto dueño de la invariante — el servicio de aplicación de cada contexto, delgado, junto a su agregado. Los procesos largos se vuelven **process managers con nombre y máquina de estados propia**: `VidaDeAlerta`, `CicloDeIncidente`, `CicloDePropuesta`, `EjecucionDeRonda`. La composición de lectura (pantallas que cruzan contextos) se muda a un módulo `consultas` que solo lee proyecciones: leer no es orquestar.

---

## Objeción 3 — "¿Por qué NATS JetStream como bus de eventos? Tienen dos logs y dos verdades de orden"

**Diagnóstico.** El v2 tenía un event store en Postgres (la verdad, con `seq_global`) y un JetStream por el medio (otro log, con su propio orden, su propia retención, su propia ventana de dedupe). Dos logs es una fuente de verdad y un rumor. Cada garantía había que razonarla dos veces; la rehidratación del gemelo leía del store pero el vivo llegaba por NATS; el determinismo por secuencia — el principio P4 — quedaba custodiado por el componente equivocado. Para un monolito modular que ya posee un ledger transaccional, el bus interno es infraestructura accidental.

**Resolución.** Arquitectura **ledger-first** (documento 05): el event store de Postgres es el único log y el único orden; los consumidores internos son lectores del ledger con marca de agua, despertados por `NOTIFY` como optimización y garantizados por sondeo. NATS JetStream no se elimina: se **degrada a la frontera**, que es donde brilla — la ingesta desde las celdas de borde (que sí necesitan un buffer con reintento fuera del hub) y, en el futuro, el enlace hacia workers extraídos a proceso propio. El bus es una herramienta de despliegue, no la arquitectura.

---

## Objeción 4 — "'Event sourcing selectivo' sin criterio explícito es una lista de gustos"

**Diagnóstico.** El v2 declaraba qué flujos eran append-only pero no **por qué regla**. Sin criterio, cada agregado nuevo reabre el debate y la selección degenera en preferencia personal.

**Resolución.** Una regla de decisión de tres preguntas, aplicada agregado por agregado en una tabla (documento 03, §2): se event-sourcea cuando la historia es la identidad del proceso, cuando "¿qué era verdad en t?" es una pregunta clínica o legal, o cuando los eventos son el lenguaje publicado hacia otros contextos. Todo lo demás es estado con auditoría atómica. La regla, además, produce un resultado distinto al del v2 en un caso: la **política clínica por residente** pasa a ser event-sourced — cada cambio de criterio sobre una persona es historia clínica, no una fila actualizada.

---

## Objeción 5 — "Los motores están subespecificados. Firmas elegantes no son diseño"

**Diagnóstico.** El v2 definió cinco `fun interface` y declaró pureza, pero esquivó la complejidad real que cada motor debe dominar: la histéresis que separa una transición de un temblor del sensor; el silencio del sensor como hecho de primera clase (¡el sistema que patrulla el silencio no modelaba el silencio del propio sensor!); la presencia de staff como supresor de alarmas; el álgebra de episodios (¿cuándo termina un episodio y se rearma la alarma?); la fatiga de alarmas como presupuesto de diseño; y faltaba un motor entero — **el enrutamiento**: decidir *quién* recibe la alerta, por qué canal y con qué escalera, estaba escondido dentro de "vigilancia" como si fuera plomería y no una decisión clínico-operativa.

**Resolución.** Documento 04: cada motor recibe una **carta de responsabilidad** (propósito, responsable de, explícitamente no responsable de, invariantes, complejidad que debe dominar), interfaces completas con tipos que cargan procedencia y explicación, un sexto motor (`MotorDeEnrutamiento`), y el artefacto que faltaba para tomarse en serio la responsabilidad: el **registro de decisión** — cada invocación de motor deja constancia de insumos, versión de reglas, versión del motor, salida y traza de explicación, incluyendo lo que se **descartó** y por qué.

---

## Objeción 6 — "¿Y cómo sabemos que funciona? La evaluación era una sección de tests, no una capacidad"

**Diagnóstico.** El v2 tenía buenos tests (replay dorado, propiedades) pero tratados como control de calidad del código. En este dominio, evaluar es parte del producto: hay que poder responder "¿por qué no sonó la alarma a las 03:12?" ante una familia, comparar dos versiones de un motor sobre las mismas noches, y medir precisión clínica con verdad de terreno.

**Resolución.** Documento 06: la evaluación como capacidad de primera clase. Replay dorado sobre el ledger real; **modo sombra** (la versión nueva de un motor corre en paralelo y sus divergencias se reportan antes de promoverla); un **simulador de noches sintéticas** con banco de escenarios (la caída de las 03:00, la deambulación, el sensor mudo, la noche tranquila); banco de conformidad que todo adaptador de puerto debe pasar; y el hallazgo que cierra el círculo: los **veredictos humanos de `memoria`** (caída / no-caída / incierto) son las etiquetas con las que se mide la precisión de `situacion` y `respuesta`. El sistema genera su propia verdad de terreno.

---

## Lo que sobrevive a la revisión

No todo se tira; buena parte del v2 (y del sistema Rust original) es diseño ganado con esfuerzo y esta sala lo ratifica: los dos tiempos (evento y reloj) con timers **derivados**, jamás cronómetros persistidos; el determinismo por secuencia y no por reloj de pared; la asimetría del autopilot codificada en tipos (subir es acción, bajar es propuesta); "la ausencia de información no es información" (nulabilidad deliberada); el techo forense de 16 KB en metadatos de auditoría; el monolito modular frente a microservicios; la mutación atómica (estado + auditoría en una transacción); y el veredicto de stack completo — Kotlin + Spring Boot 4 + Modulith 2 con núcleo puro verificado por Konsist, Postgres como SoR, Parquet + DuckDB para analítica, Go descartado para el núcleo por ausencia de tipos suma, Rust reservado al borde o a la preservación del engine existente vía strangler.

## Tabla de decisiones de la sesión

| # | Decisión v3 | Sustituye en v2 | Detalle |
| --- | --- | --- | --- |
| D1 | Contextos derivados de capacidades; `alojamiento` absorbe la invariante 1:1 | Once `ctx-*` heredados | 02 |
| D2 | Process managers nombrados + comandos en su contexto + módulo `consultas` | Orquestador `aplicacion` | 03 |
| D3 | Ledger-first: Postgres como único log interno; NATS a la frontera | JetStream como columna vertebral | 05 |
| D4 | Regla de tres preguntas para event sourcing; `criterio` pasa a ES | Lista selectiva sin criterio | 03 |
| D5 | Cartas de motor + `MotorDeEnrutamiento` + silencio del sensor y fatiga como conceptos + registro de decisión | Cinco `fun interface` | 04 |
| D6 | Evaluación como capacidad: sombra, simulador, conformidad, etiquetas humanas | Sección de tests | 06 |
