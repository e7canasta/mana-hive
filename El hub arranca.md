mana-hive · plan de sprint

# El hub arranca

SPEC-06 se escribió contra `ad6b2df`, antes de que entraran 02–05. Dos de sus cinco cambios ya están hechos. Pero da por sentado un hub que funciona, y el hub nunca arrancó. Esto propone partirla en cuatro, con esa precondición primero.

**Base:** árbol de trabajo tras cerrar SPEC-05**check:** verde · 463 tests**Blueprints:** 9 / 9

## Lo que cambió desde que se escribió la spec

Verifiqué cada premisa contra el código en vez de leerla. La spec quedó desactualizada en las dos direcciones: sobreestima lo que falta en dos puntos y lo subestima gravemente en uno.

**El cambio 2 ya está hecho.** `PolicyService` no tiene `TODO`, ni `hardcoded`, ni `not production ready`. Lee capas, las pliega con `toAlarmProfile`, resuelve con `catalogFor(nivel)` y devuelve `Explained`. Un residente sin capas tira `NoPolicyForResident` en vez de caer a un STANDARD silencioso. Los criterios 1 y 2 de la spec ya se cumplen. leído

**El cambio 5 también.** `fingerprintOf` ya incorpora `catalog.version.value`, con el comentario explicando por qué: dos catálogos de versión distinta tienen que producir huellas distintas aunque las reglas coincidan. El criterio 7 no necesita código, sólo el test que lo fije. leído

### Y lo que subestima

La spec habla de «conectar el catálogo» y «agregar una API». Las dos cosas se montan sobre un servicio Spring que no se puede instanciar:

./gradlew :hub:hub-service:bootRun

**APPLICATION FAILED TO START**

Parameter 0 of constructor in com.manahive.hub.api.LedgerController
required a bean of type 'com.manahive.hub.ledger.EventStore'
that could not be found.

Falla en `EventStore` antes siquiera de llegar a `PolicyLayerStore`. En todo `hub-service` hay **cero** clases `@Configuration` y cero `@Bean`, contra nueve anotaciones de Spring que esperan que alguien las satisfaga — cinco controladores, dos listeners de NATS, y el `PolicyService` de arriba. corrido

La buena noticia es que casi todo existe. `EventStore` es una clase real en `hub-domain`, e `InMemoryLedger` ya implementa el `LedgerPort` que necesita. Falta declararlos. El único que no tiene implementación de ninguna clase es `PolicyLayerStore`, que hoy es una `fun interface` sin un solo implementador. leído

## El patrón de fondo

Es la tercera vez que aparece la misma forma, y creo que es el hallazgo que más vale la pena mirar de frente.

|Momento|Qué estaba verde|Qué estaba roto|
|---|---|---|
|SPEC-00|—|Consumidores y tests escritos contra APIs que nunca se implementaron|
|Cierre de SPEC-05|`check` verde · «6 blueprints pasan»|Los blueprints imprimían ✅ y salían con 0 pasara lo que pasara; uno llevaba un sprint entero roto sin abrir un solo episodio|
|Hoy, ante SPEC-06|`check` verde · 463 tests|El servicio Spring nunca arrancó, y ningún test carga un contexto|

`./gradlew check` mide compilación, no comportamiento. Hasta acá se toleró porque todo lo entregado eran motores puros que los blueprints sí ejercitaban de punta a punta. **Sprint 6 es la primera spec cuyo entregable es un servicio corriendo con superficie HTTP**, y ahí deja de ser tolerable.

El criterio 8 de la propia spec — «cambiar el nivel de un residente por la API → el motor recibe la calibración nueva» — es literalmente inalcanzable sobre un servicio que no bootea. Hay **cero** `@SpringBootTest` en el repositorio y `hub-service` no tiene source set de test. leído

### El andamio ya está pago

El plugin de convención `manahive.spring-service` ya agrega `spring-boot-starter-test` a los seis servicios. Un test que cargue el contexto no cuesta configuración de build: cuesta escribirlo. Es la misma jugada que cerró los blueprints este sprint — hacer que la cosa _pueda_ fallar.

Un matiz de honestidad: **no arranqué los otros cinco servicios.** Sé que cuatro de ellos tienen una clase `@Configuration` y `recorder-service` ninguna, pero con dos anotaciones cada uno su superficie es mucho menor que la del hub. El hub es el caso atípico, no necesariamente el único.

## Las cuatro tareas

El orden es una cadena de dependencia real, no una priorización: hasta que el contexto no cargue, nada de lo que sigue se puede probar de punta a punta.

1. 06a
    
    ### El hub arrancadesbloquea el resto
    
    Una clase `@Configuration` en `hub-service` que declare la cadena que ya existe, una implementación de `PolicyLayerStore` (in-memory alcanza), y un `@SpringBootTest` que cargue el contexto y corra dentro de `check`.
    
    El criterio es binario y no admite interpretación: el contexto carga en un test, y ese test está en la corrida normal. Es la tarea más chica de las cuatro.
    
    - ya está`EventStore`, `InMemoryLedger`, `InMemoryRawPolicyStore`, `InMemorySemanticBucketStore`, `InMemoryPolicyCatalog` — todos en `hub-domain`, ninguno declarado como bean
    - faltaLa `@Configuration`, el implementador de `PolicyLayerStore`, y el test de contexto
    
2. 06b
    
    ### Las capas como historia
    
    Los cuatro eventos del cambio 1 de la spec — `WatchLevelAssigned`, `ManualAdjustmentAdded`, `ManualAdjustmentRevoked`, `TimeWindowDefined` — con actor, instante y `motivo` obligatorio, y el fold que reconstruye `PolicyLayers`. Nunca un `UPDATE`.
    
    Es lo que hace contestable «¿de dónde salió ese diez, y quién lo puso?», que es la pregunta que justifica que el hub sea System of Record y no una tabla de configuración.
    
    - ya está`PolicyLayers` con sus cuatro campos, y `toAlarmProfile` con cobertura en `PolicyProjectionSpec`
    - faltaLos cuatro eventos — hoy `grep` da cero para los cuatro nombres — y el fold
    
3. 06c
    
    ### La API que el director toca
    
    La parte grande, y la que la spec describe bien. Consolidar los cuatro controladores de política a la luz de AD-1, agregar escritura, y que `GET /policy` devuelva procedencia por regla.
    
    El criterio 11 — decidir para cada controlador si sobrevive, se fusiona o se retira — me parece el trabajo más valioso y el peor estimado de toda la spec. Son tres vocabularios para el mismo dominio; elegir cuál sobrevive es diseño, no cableado.
    
    - ya estáCuatro controladores montados: `/api/policies`, `/api/policies/raw`, `/api/semantic-buckets`, `/api/catalog`
    - faltaToda la escritura: cero `@PostMapping`, `@PutMapping` y `@DeleteMapping` en el hub entero
    
4. 06d
    
    ### El cambio llega a los motores
    
    Escritura en el hub → evento al bus → los motores recalibran. Los contratos (`CalibrationChanged`, `PolicyChangeDetected`, `PolicyEvent`) y el ingest/egress de `politica-service` ya existen; hay que verificar contra `platform/messaging` cuál es el asunto correcto y no inventar uno.
    
    Acá vive el criterio 8, el de integración de punta a punta. Es el único que demuestra que las cuatro tareas se sostienen juntas.
    
    - ya estáLos tres contratos en `contracts.policy`, y los dos `@Component` de NATS en el hub
    - faltaEl cableado, y una corrida que lo demuestre — el wiring de NATS nunca se ejercitó
    

## Dos notas sobre la spec misma

### La nota de autorización se adelanta

Fuera de alcance dice: «hoy cualquiera puede cambiar el nivel de cualquier residente». Hoy no puede nadie — no hay un solo endpoint de escritura y el servicio no corre. Esa frase se vuelve verdadera **exactamente cuando aterriza 06c**. La spec de autorización va junto con esa, no después de todo.

### Los criterios ya no dicen la verdad

Cuatro de los once criterios de aceptación cambiaron de estado sin que nadie los tocara. Vale reescribirlos antes de empezar, porque una lista que sobreestima lo hecho y esconde una precondición es peor que no tenerla.

|Criterio|Estado real|Dónde cae|
|---|---|---|
|1 · Sin `TODO` ni `hardcoded`|Ya se cumple|—|
|2 · Residente sin capas da error|Ya se cumple|Falta el test que lo fije|
|7 · Versiones distintas, huellas distintas|Sólo falta el test|06a|
|8 · Integración de punta a punta|Inalcanzable hoy|06a → 06d|
|3, 4, 5 · Procedencia, `motivo`, historia|Sin empezar|06b, 06c|
|11 · Los cuatro controladores resueltos|Sin empezar|06c|

## Cómo seguimos

Vos desarrollás las cuatro y me avisás cuando termines; yo reviso. Para que la revisión sirva, la regla de la casa del roadmap aplica igual que siempre: _un escenario que no pasa es una hipótesis sobre el motor, no un veredicto._ Voy a verificar corriendo, no leyendo — igual que salió esto.

Lo único que pediría llevar de 06a en adelante es el hábito nuevo: cada vez que algo se declare terminado, que exista una corrida que lo pueda contradecir.

_corrido_ejecuté el comando y esta es su salida_leído_verificado sobre el código fuente