# Los motores

Decision de arquitectura, tomada el 2026-08-19 y ejecutada en el refactor de
`mana-motores`. El movimiento no cambia contratos HTTP ni comportamiento de
producto: cambia la frontera de compilacion para que los motores sean puros por
construccion.

---

## El problema

Antes del refactor habia dos motores puros en casas distintas:

| Motor | Donde vive | Por que ahi |
| --- | --- | --- |
| Alarmas (`evaluacion.rs`) | `mana-app/motor/` | Porque cruza cuatro contextos |
| Recomendacion | `ctx-politica/recomendacion/` | Porque la politica es de Politica |

Las dos razones son buenas y el resultado es incoherente: son **la misma clase
de cosa** —una funcion pura que convierte evidencia en una decision— y ninguna
de las dos casas es su casa.

Y hay algo peor, que es la razon de fondo: los dos son **puros por disciplina**.
Nada impide que mañana alguien meta una consulta en el medio. `mana-app` depende
de diesel y de todos los stores; la unica cosa que mantiene la frontera es que
nos acordemos.

## La decision

Un crate `mana-motores`, y la regla que lo hace valer:

> **`mana-motores` no depende de nada que haga IO.** No importa Diesel,
> `mana-storage`, `mana-observation` ni un `ctx-*`. Solo usa tipos de valor,
> serializacion, TOML de catalogo y `mana-kernel`.

Con eso los motores dejan de ser puros por disciplina y pasan a serlo **por
construccion**: la consulta que alguien quiera escribir adentro no compila. Es
la misma jugada que ya hace `xtask` con la independencia entre `ctx-*`, pero
sale gratis porque la hace Cargo.

Y de paso son evaluables de verdad: sus tests no tocan una base, no necesitan
migraciones y corren en milisegundos.

## La direccion de dependencias

```text
mana-kernel <- mana-motores <- ctx-politica
                    ^               ^
                    |               |
                    +----------- mana-app
```

**Politica depende de los motores, no al reves.** Es contraintuitivo y es lo
importante: los tipos de valor que la politica declara como dato —niveles,
acciones, sensibilidad, clase de regla, la politica de recomendacion con sus
pesos— **se mudan a `mana-motores`**. `ctx-politica` los reexporta para el
perfil persistido; el parser y el resolver del catálogo están en el crate puro.

Si fuera al reves —motores dependiendo de Politica— el crate arrastraria diesel
y `mana-storage` de forma transitiva, y la regla de arriba seria una intencion
en vez de una garantia. `mana-kernel` no tiene una sola dependencia de IO, y por
eso es la unica base posible.

**Lo que se corto:** `evaluacion.rs` importaba
`ctx_vigilancia::{AlertLevel, EvidenceKind}`. El motor define su propio nivel y
su propia clase de evidencia, y **`mana-app` traduce** al tipo de Vigilancia
cuando persiste. La decision ya no depende de la forma de la tabla.

**La regla nueva para `xtask`:** ningun `ctx-*` puede depender de `mana-app`, y
`mana-motores` no puede depender de ningun `ctx-*`. Es el mismo blindaje que ya
tiene `mana-observation`, que no es un contexto pero posee tablas.

## Como corre un motor

Un motor no es una funcion que alguien llama cuando se acuerda. **Destila**: le
entra evidencia y sale una estructura del dominio, y lo hace todo el tiempo o
cuando algo lo estimula. Es el mismo verbo que ya usa el bridge cuando convierte
señal cruda en eventos.

De ahi salen cuatro piezas con nombre, iguales para los tres motores:

```text
  entrada  ──▶  [ motor ]  ──▶  destilacion
     ▲              ▲
     │              │ politica (dato)
     │
  hidratacion  ← la arma `mana-app` yendo a buscar a los contextos
```

| Pieza | Quien la tiene | Que es |
| --- | --- | --- |
| **Entrada** | `mana-motores` | Todo lo que el motor necesita, ya junto. Nunca va a buscarlo |
| **Politica** | catalogo, como dato | Umbrales, pesos, cortes. Cambiarlos es un diff |
| **Motor** | `mana-motores` | La decision. Pura, sin IO, testeable con literales |
| **Hidratacion** | `mana-app` | Las consultas que arman la entrada. Cruza contextos |
| **Seam** | `mana-app` | Hidrata, corre el motor, persiste la destilacion |

La ventaja practica es que cada motor se prueba dos veces y por separado: la
decision con una entrada escrita a mano y sin base, y la hidratacion con base
pero sin logica de decision. Cuando algo falla, el test que se rompe dice cual
de las dos cosas fue.

## API de los motores

Los contratos internos son valores, no endpoints:

```text
AlarmCatalog::resolve_rules(...) -> ResolvedRule
recomendar(...)                  -> Recomendacion
evaluar(...)                     -> Vec<AlertaNueva>
```

Los tres reciben entradas completas y no hacen IO. `mana-app` hidrata esas
entradas y persiste la destilacion por la API del contexto dueño.

## Los dos tiempos, otra vez

La distincion que ordena F11 vale para los tres motores, no solo para las
alarmas:

**Estimulado.** Llega evidencia, el motor corre. La ingesta de un evento dispara
el motor de alarmas y ya lo hace hoy.

**Por reloj.** No lo dispara nada: lo dispara que pase el tiempo. El barrido de
permanencias, la recomendacion diaria, autopilot. Un motor que solo corre cuando
llega evidencia no puede notar una ausencia — y la ausencia de evidencia es
justamente lo que preocupa en una residencia.

| Motor | Estimulo | Destilacion |
| --- | --- | --- |
| Alarmas | Evento de ingesta + barrido del reloj | Alerta, con su regla y su evidencia |
| Recomendacion | Reloj diario + bajo demanda | Nivel propuesto, con factores y puntaje |
| Autopilot | Reloj diario | Decision de aplicar, o propuesta para que alguien confirme |

## Lo que decide un motor lo podria haber decidido una persona

Es la propiedad que ordena todo lo demas, y la primera version de este documento
la tenia mal: separaba la salida de un motor de la de un humano, como si la del
motor fuera de segunda categoria. No lo es.

Si autopilot sube a alguien a nivel alto, **decidio lo mismo que hubiera decidido
una persona mirando los mismos datos**. Para el resto del sistema —el panel, la
auditoria, el historial, el motor de alarmas que despues evalua con ese perfil—
las dos son la misma cosa: una version de perfil vigente desde un instante, con
su autor.

Hay que separar dos cosas que yo habia juntado:

| | Que es | Como se trata |
| --- | --- | --- |
| **El razonamiento** | Señales, puntaje, factores | Derivado y reconstruible. La regla de F6 aplica: no se persiste como hecho, se recalcula |
| **La decision** | "esta residente pasa a nivel alto" | **Un hecho.** Tiene autor y fecha, es append-only, y no se recalcula nunca porque ya ocurrio |

Guardar el razonamiento para no recalcularlo en cada request es legitimo —hoy la
recomendacion de cuarenta residentes son ciento sesenta consultas— y lo guardado
es una materializacion, de la misma familia que `current_bed_states`. Si cambia
el catalogo, esa materializacion no se migra: se recalcula.

Una decision aplicada, en cambio, **no se recalcula jamas**. Paso.

### La consecuencia: una sola puerta

Si un humano y un motor son indistinguibles para el resto, tienen que entrar por
**el mismo caso de uso**, con las mismas invariantes. Lo unico que los separa es
el actor.

Hoy no es asi y es un defecto: `update_profile` valida la plantilla y los
overrides contra el catalogo, y `autopilot` llama a `apply_profile` derecho,
salteando esa validacion. Son dos puertas con reglas distintas para escribir la
misma tabla — la clase de cosa que un dia produce un perfil que el panel no sabe
mostrar.

De ahi salen tres condiciones:

1. **Autopilot es un actor**, con identidad propia, no una rama de codigo. Ya hay
   donde ponerlo: `alarm_profile_versions.updated_by` guarda un actor y no un
   booleano "fue automatico". La tabla ya trata igual a los dos; el codigo no.
2. **Ningun lector ramifica por origen.** Nada de `if (source == "autopilot")`
   desparramado. El origen es un campo que se muestra, no una bifurcacion.
3. **La razon viaja con la decision.** Preguntar "¿por que esta en alto?" tiene
   que contestar igual de bien en los dos casos: "lo puso Gaston el martes" o
   "autopilot lo subio con estos factores". Sin eso, la decision automatica es la
   unica que no se puede discutir, y es justo la que mas falta hace poder
   discutir.

## La API de Politica

Los motores no revuelven adentro de Politica: le preguntan. Politica contesta
tres cosas y ninguna sabe que existe una cama o un evento:

```text
perfil_efectivo(residente, instante) -> reglas vigentes con su procedencia
politica_de_recomendacion()          -> ventanas, pesos y cortes
politica_de_aplicacion()             -> cuando autopilot puede actuar solo
```

La primera es la que importa y la que hoy esta desparramada: `mana-app` arma el
perfil efectivo leyendo `self.policy` y `self.catalog` por separado y aplicando
las capas a mano. Eso es logica de Politica viviendo afuera de Politica, y es
como se llega a que el panel muestre una cosa y el motor evalue otra.

**`at` no es opcional.** Se pregunta por la politica vigente **cuando ocurrio**,
no por la de ahora: evaluar una caida de hace tres semanas con la configuracion
de hoy es reescribir la historia.

## Que entra

| Motor | Que decide | Estado |
| --- | --- | --- |
| **Alarmas** | Observacion + perfil efectivo -> ¿alerta, de que nivel? | Extraido a `mana-motores` |
| **Recomendacion** | Señales + rasgos -> ¿que nivel proponer? | Extraido a `mana-motores` |
| **Autopilot** | Recomendacion + politica de aplicacion -> ¿la aplico sola? | `mana-motores::decidir` |
| **Factores de riesgo** | Que señal pesa cuanto | Ya es dato en el catalogo |

## Autopilot no es la recomendacion otra vez

Es la recomendacion **mas una politica de aplicacion**, y esa politica no es
simetrica.

Subir el nivel de alarma por evidencia observada es seguro: en el peor caso
suena de mas. **Bajarlo no.** Un residente con catorce dias tranquilos puede
estar tranquilo porque la configuracion actual esta funcionando, y bajarle la
vigilancia por eso es sacarle justo lo que lo tiene bien. Un autopilot que baja
niveles solo es un sistema que se apaga cuando funciona.

Asi que la politica de aplicacion es dato, como el resto, y arranca asimetrica:

- subir automatico, con un minimo de `signals_evaluated` que respalde el cambio;
- bajar **nunca solo**: se propone y lo confirma alguien;
- un piso de tiempo entre cambios, para que el nivel no oscile dia por dia.

Los tres numeros van al catalogo. El mecanismo —correr la recomendacion de cada
residente en autopilot y aplicar lo que la politica permita— es lo unico que
queda en codigo.

## Como se ejecuta sin romper nada

1. Crear `mana-motores` y blindarlo contra dependencias de IO.
2. Mudar los tipos de valor de `ctx-politica/catalogo`; `ctx-politica` los
   reexporta para no tocar a sus consumidores.
3. Mudar `recomendacion` y sus tests.
4. Mudar `evaluacion.rs` y sus tests; `mana-app` traduce al contexto de
   Vigilancia al persistir.
5. Agregar la regla de dependencia a `xtask`.

Estos cinco pasos están completos. Autopilot ya tiene su decision pura y el seam
de composicion: `mana-app` hidrata, `decidir` filtra y el contexto persiste solo
las subidas permitidas. El scheduler que lo dispara y el reloj durable siguen
pendientes.

Las specs de producto y las escenas están en
`docs/especificacion/motores/`.
