Sesión de diseño · mana-hive · lenguaje ubicuo

# El idioma de la noche

Seis contextos, cuatro niveles y un vocabulario que el director médico, la enfermera de guardia y el compilador entienden igual. Esta es la conversación de la sala.

- **Martin Fowler**Sobre el lenguaje y el DSL
- **Vaughn Vernon**Sobre el mapa de contextos
- **Dirección médica**Quien elige el nivel de cada residente
- **Staff de guardia**Quien recibe (o no) la notificación
- **Ingeniería**Quien tiene que hacerlo correr

## La pregunta que ordena todo

Director de la residencia

Yo no quiero un sistema que “detecte caídas”. Quiero poder contestar una pregunta, delante de una familia y delante de un inspector: **¿por qué la alarma sonó — o no sonó — a las 03:12?** Y quiero que la respuesta no dependa de que alguien se acuerde.

Ese es el requisito del que cuelga todo el diseño. mana-hive es una **plataforma de vigilancia nocturna para residencias** con un objetivo escrito en una sola frase: que la persona correcta llegue a la habitación correcta a tiempo, con la menor cantidad posible de falsas alarmas, y que después se pueda demostrar por qué se tomó cada decisión.

La última cláusula es la cara. Es la que obliga a que el sistema no sea un monolito con reglas escondidas, sino un conjunto de piezas que se hablan en un idioma que se puede leer, discutir y auditar.

Martin Fowler

Entonces el problema no es de software todavía. Es de _vocabulario_. Antes de decidir cómo se guarda nada, tienen que ponerse de acuerdo en cómo se llaman las cosas — y ese acuerdo tiene que sobrevivir intacto desde la boca del director hasta el nombre de una clase.

Si la enfermera dice “se sentó en la cama” y el código dice `state_2`, ya perdieron. Cada traducción es un lugar donde se cuela un malentendido que después nadie puede auditar.

## Lo que se dice, y lo que corre

La prueba del lenguaje ubicuo es esta tabla. A la izquierda, frases reales dichas en la residencia. A la derecha, lo que existe en el código — el mismo nombre, no una traducción.

Lo que dice el staff

“José se sentó en la cama.”

Lo que existe en el código**SceneEvent.TransitionDetected** LYING → SITTING_IN_BED

Lo que dice el staff

“Lleva mucho rato en el baño.”

Lo que existe en el código**SceneEvent.DwellExceeded** IN_BATHROOM

Lo que dice el staff

“Esto amerita que vaya alguien.”

Lo que existe en el código**SentinelSignal.EpisodeOpened** regla · severidad

Lo que dice el staff

“Me llegó el aviso al bíper.”

Lo que existe en el código**NoticeCommand.Dispatch** → AlarmEvent

Lo que dice el staff

“Entré yo, ya está resuelto.”

Lo que existe en el código**ClosureCondition.STAFF_OR_SAFE** → EpisodeClosed

Nótese lo que _no_ aparece: no hay “alerta” a secas. El sistema distingue tres cosas que en la conversación informal se confunden todo el tiempo — **un hecho** (José se sentó), **un juicio** (esto amerita alguien) y **una entrega** (a Susan le llegó al bíper). Son tres palabras porque son tres decisiones distintas, tomadas por tres piezas distintas, en tres momentos distintos.

Vaughn Vernon

Y ahí ya tienen las costuras del mapa de contextos. Un hecho, un juicio y una entrega no pertenecen al mismo modelo. Si los mezclan en un solo servicio, el día que quieran cambiar la política de fatiga van a tener que tocar el detector de posturas.

## El mapa: seis contextos, seis responsabilidades

Cada caja de abajo es un **bounded context**: un modelo con su propio vocabulario, su propia regla de consistencia y una sola pregunta que le toca contestar. Lo importante para la dirección no son los nombres — es la columna _“no le toca”_. Ahí está escrito qué cosa **no** puede hacerle daño a qué otra.

#### Escenascene-engine

Responde

_¿Qué está pasando en la habitación?_ Sostiene el gemelo digital de la cama: en qué estado está el residente y desde cuándo.

Publica

SceneEvent

No le toca

Decidir si eso amerita molestar a nadie. Escena no sabe qué es una alarma.

#### Vigilanciasentinel

Responde

_¿Este hecho amerita un episodio?_ Es el juicio clínico: contrasta cada hecho contra las reglas efectivas de ese residente.

Publica

SentinelSignal

No le toca

A quién avisar, por qué canal, ni cuántas veces. Vigilancia abre el episodio **siempre** que la regla dispara — sin presupuesto.

#### Faroharbor

Responde

_¿A quién le llega, por dónde, y hasta cuándo insisto?_ Ruteo, entrega, escalación y presupuesto de fatiga.

Publica

AlarmEvent

No le toca

Reinterpretar el juicio clínico. Si Vigilancia dijo que hay episodio, hay episodio — Faro decide si además suena.

#### Grabadorarecorder

Responde

_¿Qué hay que tener grabado, y de qué ventana de tiempo?_ Órdenes al NVR y registro de evidencia.

Publica

RecordingCommand · EvidenceRecord

No le toca

Avisar. Graba en paralelo a la notificación, no después de ella.

#### Reglaspolitica-engine

Responde

_¿Cuál es la política vigente de este residente, ahora?_ Catálogo, plantillas y excepciones, resueltos a una calibración.

Publica

PolicyCalibration

No le toca

Observar ni decidir. Solo resuelve. Cada residente tiene exactamente una política válida por instante.

#### Patiohub — system of record

Responde

_¿Qué pasó realmente?_ Ledger de eventos, censo, catálogo de políticas y la moviola para reconstruir cualquier noche.

Publica

EffectiveRules · CensusSnapshot

No le toca

Decidir en tiempo real. El Patio recuerda; los motores deciden.

ia-cell **percibe**_→_Escena **afirma**_→_Vigilancia **juzga**_→_Faro **entrega**_→_Patio **recuerda**

Vaughn Vernon

La relación entre casi todos estos pares es **Published Language + Conformist**: el de aguas abajo consume el contrato tal cual, sin negociarlo. Es la relación correcta cuando el contrato está bien nombrado, porque no cuesta nada mantenerla.

La excepción interesante es Reglas → Escena, que lleva **capa anticorrupción**. Reglas habla en lenguaje clínico — “aviso a los quince minutos” — y Escena necesita histéresis en milisegundos y umbrales de confianza. Esa traducción es tan delicada que merece un lugar propio con nombre propio, en vez de ensuciar el modelo de Escena con vocabulario de política.

## El DSL, en tres alturas

Acá está lo que hace distinto a este sistema. No hay _un_ lenguaje específico de dominio: hay tres, y son el mismo vocabulario visto desde tres alturas. Ninguna de las tres traduce a las otras — las tres nombran las mismas cosas.

### Altura 1 — El director elige un nivel

La dirección médica no configura milisegundos. Contesta tres preguntas por residente y de ahí sale el nivel.

¿El residente se mueve bien?
  │
  ├─ **Sí** → ¿Se levanta de noche?
  │        ├─ **Sí** → NIVEL 1 · NIGHT-WANDERING
  │        └─ **No** → ¿Tiene riesgo de caída?
  │                  ├─ **Sí** → NIVEL 2 · FALL-RISK
  │                  └─ **No** → NIVEL 0 · STANDARD
  │
  └─ **No** → ¿Es crítico?
           ├─ **Sí** → NIVEL 3 · CRITICAL
           └─ **No** → NIVEL 2 · FALL-RISK

Y el nivel es lo único que hay que revisar cuando el residente cambia. Después de una caída: STANDARD → FALL-RISK. Post-operatorio: → CRITICAL. Recuperado: CRITICAL → STANDARD. Una decisión, no una planilla.

Los tiempos que hay detrás de cada nivel están escritos y son revisables — pero el director los lee, no los teclea:

|Estado|Standard|Night-wandering|Fall-risk|Critical|
|---|---|---|---|---|
|Acostado|sin alerta|sin alerta|sin alerta|sin alerta|
|Sentado en cama|—|20 / 30 min|15 / 20 min|10 / 15 min crit|
|Borde de cama|—|3 / 5 min|1 / 2 min|1 / 2 min crit|
|Parado|—|10 / 15 min|2 / 3 min|2 / 3 min crit|
|En el baño|—|15 / 25 min|10 / 15 min|5 / 10 min crit|
|Fuera de la habitación|—|5 / 10 min|5 / 10 min|2 / 5 min crit|

Los dos números son **aviso** / **episodio**: el primero es un preaviso silencioso, el segundo abre el episodio.

### Altura 2 — El perfil del residente

Cuando un residente no entra limpio en un nivel, el perfil lo dice en una línea. Este es José, de la 301, tal como está escrito hoy en el repositorio:

**buildResidentProfile**("jose") {
    risk(HIGH)
    mobility(NONE)
    template("standard")

    resident {
        sitting  { alertAfter(**15.minutos**) }   _// el catálogo dice 45_
        bathroom { alertAfter(**10.minutos**) }   _// el catálogo dice 30_
    }
}

Se lee en voz alta sin traducir: _“José, riesgo alto, sin ayuda de marcha, plantilla estándar; si se queda sentado más de quince minutos avisen, y si está en el baño más de diez también.”_ Esa frase es el código. No hay una versión “de negocio” y otra “técnica” que se puedan desincronizar.

La resolución tiene un orden fijo y auditable — **catálogo → plantilla → excepción**, y la excepción gana. De ahí sale una sola PolicyCalibration que cada motor recorta a lo que necesita: Escena recibe histéresis y umbrales de confianza, Vigilancia recibe reglas de alerta, Faro recibe presupuesto y canales, Grabadora recibe ventanas de grabación.

Por qué importa el orden

Porque hace la pregunta del inspector contestable: cuando el sistema dice “a las 03:12 el umbral era diez minutos”, puede además decir **de dónde salió ese diez** — del catálogo, de la plantilla o de una excepción firmada para José. Eso es la _procedencia_, y viaja con la regla.

### Altura 3 — La noche escrita como escenario

Martin Fowler

Esta es la parte que me interesa. Un DSL que solo sirve para configurar es media herramienta. El que sirve además para _escribir el caso_ antes de activarlo cambia quién puede participar en la conversación.

En blueprints/ cada escenario es una noche escrita en el mismo vocabulario, y corre. Es tres cosas a la vez: prueba de aceptación en integración continua, entrada del _golden replay_, y la herramienta con la que el staff clínico **ve** una regla antes de habilitarla.

ctx.**pipeline**("José va al baño y tarda — dwell exceeded") {
    obs(IN_BED,        "0s")
    obs(SITTING_IN_BED,"2h47m")
    obs(STANDING,      "2h48m")
    obs(IN_BATHROOM,   "2h50m")
    obs(IN_ROOM,       "3h18m")
    obs(IN_BED,        "3h20m")

    _// las condiciones de aceptación, en el idioma de la clínica_
    thenSceneEventPresent(TransitionDetected)
    thenSignalPresent(EpisodeOpened)
    thenHarborCommandPresent(Dispatch)
}

El director puede leer ese bloque y decir “no, ahí no quiero que suene”. Y esa objeción se convierte en un cambio de una línea, con la prueba que la sostiene.

## Dos residentes, la misma noche

La forma más rápida de mostrar qué hace la política es correr la misma noche con dos perfiles. Los dos escenarios existen hoy en el repositorio.

#### Josébed-4 · 301 · riesgo alto

- Plantilla estándar con dos excepciones: sentado 15 min, baño 10 min.
- Histéresis acostado→parado bajada a 1 segundo: quiere que un levantarse brusco se note.
- Grabación en tres disparadores — levantarse, demora en baño, episodio crítico.

**8** escenarios corriendo**Episodios** se abren**Notificaciones** salen**Evidencia** queda grabada

#### Susanbed-5 · 401 · riesgo bajo

- Plantilla estándar, sin ninguna excepción. Tres líneas de perfil.
- Se sienta, va al baño, camina, se levanta de golpe — la misma coreografía que José.
- Escena la sigue igual: los hechos se registran todos.

**5** escenarios corriendo**0** episodios abiertos**0** notificaciones**0** grabaciones

Ese **cero** de la derecha es el resultado más importante del documento. Susan es observada toda la noche y nadie es despertado por ella. Vigilar no es alarmar, y el sistema tiene esa distinción metida en la estructura, no en un ajuste de sensibilidad.

El caso que cambia el turno

### A Susan le cambian la medicación

La dirección quiere, solo por esta semana, saber si se demora en el baño. No hay que tocar el sistema, ni redeployar nada, ni llamar a ingeniería. Se agrega un bloque al perfil:

resident {
    bathroom {
        warningAfter(**5.minutos**)    _// aviso silencioso_
        alertAfter(**15.minutos**)     _// esto sí abre episodio_
    }
}

La misma noche, el mismo residente, la misma cama. Cambió una intención clínica, y se escribió como intención clínica.

## La objeción del staff: “nos va a sonar todo el tiempo”

Enfermera de guardia

El radar de piso que tenemos hoy nos hace ir a la habitación y José ya está acostado, o estaba nada más que en el baño. A la tercera noche uno deja de correr. Eso es lo peligroso.

Es la objeción correcta y el sistema tiene cuatro respuestas estructurales, no un dial de sensibilidad:

|Mecanismo|Dónde vive|Qué hace|
|---|---|---|
|Histéresis|Escena|Un estado tiene que sostenerse para contar como transición. Mata el parpadeo del sensor antes de que llegue a ser un hecho.|
|Presupuesto de fatiga|Faro|Tope de avisos por turno y por residente. Se agota y las siguientes se suprimen — pero **con registro**. Lo crítico nunca se suprime.|
|Cierre por presencia|Vigilancia|Si entra staff, el episodio cierra solo. Nadie tiene que acusar recibo de nada mientras atiende a alguien.|
|Supresión con causa|Faro|Un aviso que no sonó igual deja constancia de por qué no sonó. El silencio también es una decisión registrada.|

El reparto es deliberado: **Vigilancia siempre abre el episodio** cuando la regla dispara — el juicio clínico no se negocia con el presupuesto. Es Faro el que decide si además suena. Así, cuando alguien pregunta “¿esto se detectó?”, la respuesta no queda contaminada por “¿esto sonó?”.

## Volviendo a las 03:12

Con el mapa puesto, la pregunta del principio se contesta por partes, y cada parte tiene un dueño:

|La pregunta|Quién contesta|
|---|---|
|¿Qué estaba pasando en la habitación?|Escena — el gemelo digital, estado y desde cuándo|
|¿Qué regla estaba vigente para ese residente?|Reglas — con procedencia: catálogo, plantilla o excepción|
|¿Se abrió episodio? ¿Por qué sí o por qué no?|Vigilancia — el juicio, citado|
|¿Se avisó? ¿A quién? ¿Se suprimió y por qué?|Faro — entrega, escalación, supresión con causa|
|¿Hay video de esos minutos?|Grabadora — ventana antes y después del disparador|
|¿Se puede volver a correr y dar lo mismo?|Patio — ledger, replay y huellas de versión|

La pieza que hace esto sólido es la **huella**: cada decisión cita la versión de las reglas, la del gemelo digital y la del motor que la tomó. Con eso, cualquier decisión es reproducible a máquina — se vuelve a correr la noche y da lo mismo. No es “lo revisamos en el log”; es “lo volvemos a ejecutar delante suyo”.

Martin Fowler

Y esa reproducibilidad se paga con una disciplina bastante aburrida: los modelos de dominio son puros. Nada de base de datos, nada de red, nada de reloj del sistema adentro. La misma entrada da el mismo resultado, siempre. Es lo que permite correr una noche entera con reloj virtual en un segundo.

## Lo que todavía no está

Para que la sesión sirva, el estado real. Nada de esto es especulación — está anotado en el repositorio:

- abierto**El catálogo real no está conectado al Patio.** El servicio de políticas del hub todavía usa valores por defecto en código. El motor de reglas resuelve bien; falta que el hub le entregue el catálogo de verdad y una API para editar plantillas.
- abierto**Falta el “dwell inverso”.** Hoy el sistema contesta “¿cuánto lleva _en_ este estado?”. Para la pregunta de la enfermera — “avisame si no vuelve a la cama” — hace falta “¿cuánto lleva _fuera_ de este estado?”. Está diseñado y documentado; no está implementado.
- abierto**Los escenarios de demora de Susan están desactivados.** El barrido de reloj que dispara los avisos por permanencia necesita depuración de tiempos. El caso de la medicación está escrito y comentado, no verde.
- olor de lenguaje**“Susan” nombra dos cosas.** Es la enfermera de guardia que pide la configuración en un blueprint, y es la residente de la 401 en otro. En un sistema cuyo argumento central es el lenguaje ubicuo, ese choque hay que resolverlo antes de que llegue a un contrato.
- desfasado**El README describe un módulo que no existe.** Nombra harbor donde el mapa de contextos y el código dicen harbor (Faro), y apunta a documentos en files/ que hoy viven en docs/.

## El acuerdo de la sala

Director de la residencia

Lo que me llevo es que yo elijo un nivel por residente y lo cambio cuando el residente cambia. Y que si mañana quiero saber por qué no sonó algo, la respuesta está escrita en palabras que yo puedo leer.

Vaughn Vernon

Y que si en seis meses quieren cambiar cómo se entrega la notificación, van a tocar Faro y solamente Faro. Eso es lo que compraron con la separación.

Martin Fowler

Mi única condición es que el lenguaje no se degrade. En el momento en que aparezca un término en el código que nadie en esta sala pueda pronunciar, o dos términos para la misma cosa, vuelvan a esta reunión. El lenguaje ubicuo no se instala: se mantiene.

Sesión de diseño · mana-hive  
Fuentes: docs/CONTEXT-MAP.md · docs/NIVELES-MONITOREO.md · docs/DECISION-TREE.md · docs/POLITICA-GUIDE.md  
Escenarios: blueprints/jose-301-e2e-pipeline · blueprints/susan-e2e-standard · blueprints/jose-301-sitting-bed