# El Corazón del Monitoreo: Gemelos Digitales y Máquinas de Estado

Esta guía conceptual desglosa la arquitectura lógica que permite al sistema de Virtual Rounds proteger a los residentes con precisión quirúrgica. Como especialistas en diseño de sistemas, entenderemos cómo pasamos de un dato crudo de un sensor a una decisión clínica fundamentada, utilizando la potencia de Rust y el rigor del determinismo lógico.

## 1. Introducción: ¿Qué es un Gemelo Digital en el Cuidado de Residentes?

En el ecosistema del `mana-engine`, un **Gemelo Digital** (o _Digital Twin_) no es una representación visual en 3D; es una **fuente de verdad en memoria** que reside en el núcleo del motor. Es un objeto que mantiene el estado vivo de cada cama y residente sin necesidad de consultar la base de datos en cada evaluación.

### El "Avatar Inteligente" y la Propiedad de la Memoria

Para garantizar una latencia mínima y máxima fiabilidad, el `mana-engine` "es dueño" de esta representación. Imagínalo como un **avatar inteligente** que sabe exactamente qué está ocurriendo en la habitación en este milisegundo.

- **Sin consultas externas:** El estado nunca se pide al Hub durante la ejecución para evitar cuellos de botella. El motor confía ciegamente en su imagen en memoria.
- **Re-hidratación Robusta:** Si el sistema se reinicia, el gemelo no se pierde. Se "re-hidrata" mediante un **replay de eventos** (reproducción secuencial) desde el último _watermark_ guardado en el Hub. Esto asegura que, tras un fallo técnico, el avatar vuelva a aparecer exactamente en su última posición conocida, garantizando que ningún residente quede desprotegido.

**Insight de Arquitectura:** El Gemelo Digital es "el qué": la imagen mental constante del sistema. Sin embargo, una imagen no es suficiente; el sistema necesita reglas para interpretar el movimiento. Ahí es donde entra el cerebro del avatar: la Máquina de Estados.

## 2. La Máquina de Estados Finitos (FSM): El Cerebro detrás del Gemelo

Si el Gemelo Digital es el cuerpo del avatar, la **Máquina de Estados Finitos (FSM)** es el conjunto de leyes lógicas que gobierna su comportamiento. La FSM define qué estados son posibles y qué movimientos son legales.

### El Rigor de la Granularidad

Aunque para el equipo asistencial hablamos de grupos generales, la FSM maneja **11 variantes de estado** específicas para cubrir cada matiz del comportamiento humano. El sistema agrupa estos estados para simplificar la toma de decisiones:

- **Grupo** `**in_bed**` **(En cama):** Incluye estados críticos como `Lying` (acostado), `SittingInBed` (sentado en la cama) o `BedEdge` (al borde, indicando una salida inminente).
- **Grupo** `**out_of_bed**` **(Fuera de la cama):** Comprende `Standing` (de pie), `InBathroom` (en el baño), `InRoom` (en la habitación) o `InHallway` (en el pasillo).
- **Grupo** `**furniture**` **(Mobiliario):** Identifica si el residente está en una silla convencional (`InChair`) o en su silla de ruedas (`InWheelchair`).

**El "So What?":** La FSM elimina la ambigüedad. Asegura que un residente no pueda estar en dos estados lógicamente imposibles al mismo tiempo, filtrando ruidos del sensor y errores de interpretación.

## 3. Anatomía de una Transición: De "Acostado" a "Fuera de la Cama"

Cuando el sensor detecta actividad (un evento de percepción), el motor consulta la función `valid_transitions()` para decidir si el cambio es legal. Si lo es, el Gemelo Digital se actualiza y el sistema emite un **Evento de Escena** (`SceneEvent`).

|   |   |   |   |
|---|---|---|---|
|Estado Origen|Acción detectada por el sensor|Estado Destino (Válido)|¿Genera SceneEvent?|
|**Lying** (Acostado)|Incorporación del tronco|**SittingInBed**|Sí (Transición legal)|
|**BedEdge** (Al borde)|El residente se pone de pie|**Standing**|Sí (Transición legal)|
|**InBathroom** (En baño)|Regreso directo a descanso|**Lying**|Sí (Flujo de seguridad)|
|**Lying** (Acostado)|Movimiento en el pasillo|**InHallway (Inválido)**|No (Se ignora por ilógico)|

### La Regla de Oro: El Determinismo (Lección F8)

Para un arquitecto de sistemas, el tiempo puede ser traicionero. Las marcas de tiempo (`created_at`) pueden llegar desordenadas por milisegundos debido a la red. Por ello, el sistema es estrictamente **determinista**:

- **Ignora el reloj para el orden:** Los eventos se procesan siguiendo la secuencia absoluta de la base de datos (**rowid** / secuencia monotónica).
- **Consistencia total:** Si un evento llega con retraso, se encola y se procesa en su lugar lógico, evitando que el Gemelo Digital "salte" de forma errática.

## 4. El Reloj y los Temporizadores (Dwell Rules): El Poder de la Ausencia

El sistema no solo reacciona a lo que ve; reacciona a lo que **deja de ver**. Aquí es donde el **Tiempo de Reloj (Proactivo)** supera al **Tiempo de Evento (Reactivo)**.

Utilizamos umbrales de tiempo específicos llamados _Dwells_ para disparar alarmas de permanencia:

1. **Lying (Acostado):** Umbral de **300 minutos (5 horas)**. Define el ciclo normal de descanso.
2. **Standing (De pie):** Umbral de **5 minutos**. Un residente de pie e inmóvil por mucho tiempo es una señal de posible desorientación o bloqueo.
3. **InBathroom (En el baño):** Umbral de **30 minutos**. Protege la privacidad pero alerta ante posibles caídas en una zona de alto riesgo.

**Insight Crítico (El Barrido):** La función más valiosa es el **barrido** (_scan loop_). El motor revisa constantemente el reloj y puede disparar una alarma aunque el detector no envíe datos nuevos. Esto es vital: si un residente cae y queda inmóvil, el sensor entrará en silencio. El sistema detecta ese **silencio prolongado** y dispara la alarma de permanencia por pura lógica temporal.

## 5. Del Gemelo a la Alerta: El Ciclo Completo

Cuando la lógica de la FSM o el Reloj detectan una anomalía, el motor emite un `SceneEvent` que viaja por el bus hacia el componente de alertas.

"Un sistema de alarmas que falla ruidosamente es un problema; uno que falla en silencio es una demanda".

El lazo operativo solo se cierra con la **Presencia de Staff**, pero con una diferencia tecnológica clave:

- **IA, no botones:** La presencia del personal de enfermería es detectada automáticamente por la inteligencia artificial del sensor.
- **Medición Real:** Al eliminar la dependencia de que el enfermero "recuerde" presionar un botón, obtenemos el dato de `staff_arrival_seconds` con total veracidad. Esto permite medir la calidad real de la respuesta ante incidentes y cerrar el ciclo de cuidado con datos de auditoría indiscutibles.

## 6. Resumen de Aprendizaje: Conceptos Clave para Recordar

Para dominar la arquitectura del motor, marque mentalmente estos pilares:

- [ ] **Gemelo Digital:** La fuente de verdad en memoria, re-hidratable mediante el replay de eventos desde el Hub.
- [ ] **Máquina de Estados (FSM):** El cerebro lógico que maneja 11 variantes de estado para evitar imposibilidades físicas.
- [ ] **Determinismo (Lección F8):** El procesamiento basado en `rowid` (secuencia) y nunca en `created_at` (reloj) para evitar errores de orden.
- [ ] **Barrido (Scan):** El ciclo del reloj que detecta el peligro en el "silencio" del sensor, permitiendo alarmas de permanencia sin movimiento.
- [ ] **Presencia de Staff IA:** La detección automática de la respuesta humana que garantiza la integridad del lazo operativo.