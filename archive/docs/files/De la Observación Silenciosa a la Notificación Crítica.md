# El Viaje de la Señal: De la Observación Silenciosa a la Notificación Crítica

En el complejo entorno del cuidado residencial, la diferencia entre una caída desatendida y una intervención oportuna reside en la arquitectura del sistema que procesa la información. El ecosistema **HubP** no es simplemente una base de datos; es un sistema de monitoreo clínico diseñado bajo un **flujo reactivo**. Este flujo transforma una observación bruta captada por un sensor en una acción de cuidado orquestada. Para garantizar que ninguna señal se pierda, el sistema se apoya en una "Arquitectura de Guardianes": cuatro binarios especializados que trabajan en equipo, comunicándose a través de un sistema nervioso digital de alta disponibilidad.

## 1. Introducción: Los Cuatro Guardianes del Cuidado

El diseño de HubP se basa en la colaboración de **cuatro guardianes** digitales que aseguran la integridad del proceso de cuidado. Esta estructura permite el **desacoplamiento**: cada componente tiene una misión única y no depende de los procesos internos de los demás, lo que hace que el sistema sea extremadamente robusto.

1. **El Hub (El Ingest):** El receptor central que valida y da identidad a cada percepción.
2. **El Engine (El Cerebro):** Un motor lógico puro que mantiene un "Gemelo Digital" del residente.
3. **El Sentinel (El Juez):** El motor de reglas que aplica la política clínica para decidir la gravedad.
4. **La Vigilancia (El Mensajero):** El responsable de cerrar el lazo operativo con el personal humano.

Esta narrativa de cuidado comienza en el borde de la red, donde un movimiento en la habitación se convierte en el primer bit de información que llega a nuestro centro de datos.

## 2. El Primer Paso: El Hub y la Identidad de la Señal

El **Hub** actúa como la aduana del sistema (Ingest). Su misión es la **persistencia** y la validación: antes de que el sistema "piense", el Hub asegura que el dato sea verídico y único.

Para evitar errores en el historial clínico, el Hub utiliza el `source_event_id` para garantizar la **idempotencia**. Esto significa que, aunque un sensor envíe la misma señal varias veces por un fallo de red, el Hub solo la procesará una vez, creando un rastro de auditoría limpio. Su labor más crítica es la **resolución de identidad**, traduciendo claves técnicas en identidades humanas:

|   |   |   |
|---|---|---|
|Clave del Sensor (Monitor Key)|Identidad de la Cama (Bed ID)|Identidad del Residente (Resident ID)|
|`radar_xyz_123`|Cama 402-A|Juan Pérez (R-882)|

Sin esta traducción, el sistema solo vería bits; gracias al Hub, el sistema sabe exactamente a quién está protegiendo. Una vez validada y guardada en la base de datos local (SQLite), la señal se publica en el bus de mensajería **NATS** para que el "cerebro" pueda interpretarla.

## 3. La Mente del Sistema: El Engine y el Gemelo Digital

El **Engine** es un componente de "lógica pura". Para maximizar la velocidad y la fiabilidad, este binario está **desacoplado** de la base de datos (sin I/O directo); recibe información y proyecta estados. Su función es mantener un **Gemelo Digital** (_Digital Twin_) de cada cama en memoria, una representación constante de la realidad física del residente.

El Engine utiliza una **Máquina de Estados (FSM)** que clasifica la escena en 11 variantes posibles:

- **En cama:** Acostado, Sentado en la cama, En el borde.
- **Fuera de cama:** Parado, En el baño, En la habitación, En el pasillo, Exterior.
- **Mobiliario:** En silla, En silla de ruedas.
- **Incertidumbre:** Desconocido (Estado _Sentinel_, vital para no asumir seguridad si el sensor pierde visión).

Este "Efecto Espejo" permite al Engine detectar no solo el estado actual, sino la **transición** (ej. de "Sentado" a "Parado"). Al detectar un cambio relevante, el Engine genera un evento de escena. Ahora que el sistema entiende _qué_ pasó, es hora de juzgar si ese cambio representa un riesgo.

## 4. El Juez Clínico: El Sentinel y la Política Efectiva

El **Sentinel** es el motor de reglas que actúa como un juez clínico. Su misión es la **hidratación** de la política: toma el evento de la escena y lo cruza con las reglas específicas del residente para generar una **Política Efectiva**.

Para dictar sentencia, el Sentinel consulta una jerarquía de tres capas:

1. **Nivel de Riesgo:** El perfil base del residente (Alto, Medio, Bajo).
2. **Plantilla Clínica:** Reglas predefinidas (ej. "Protocolo de Caídas").
3. **Ajustes Manuales:** Excepciones personalizadas para ese residente.

El Sentinel también es consciente del tiempo, distinguiendo entre el **Día (07:00 - 19:00)** y la **Noche (19:00 - 07:00)**. Esta precisión evita la "fatiga de alarmas", asegurando que el personal solo sea notificado cuando la situación lo requiere. Si el Sentinel decide que hay un riesgo, emite una orden de notificación inmediata a través del bus NATS.

## 5. El Mensajero: La Vigilancia y la Alerta Final

El componente de **Vigilancia** es el puente entre el mundo digital y el mundo humano. Su responsabilidad es gestionar el **Lazo Operativo**, un proceso de cinco pasos que garantiza la entrega del aviso: **Evaluar, Crear, Dirigir, Entregar y Escalar**.

Vigilancia no solo hace sonar un teléfono; gestiona la identidad del staff de turno para saber exactamente a quién debe dirigir el mensaje.

|   |   |   |
|---|---|---|
|Concepto|Función Técnica|Impacto Humano|
|**Notificación**|Registro informativo (Log).|El personal lo consulta en su panel.|
|**Alerta**|Evento crítico con **Escalamiento**.|Si no hay acuse en X segundos, el sistema busca a un superior.|

El escalamiento asegura que ninguna alerta quede en el olvido si un enfermero está atendiendo otra emergencia. Sin embargo, el sistema no solo reacciona a los movimientos; a veces, el mayor riesgo es el silencio.

## 6. El Tiempo como Disparador: El Lazo Operativo y los Timers

Una arquitectura de seguridad profesional debe distinguir entre dos dimensiones temporales: el "Tiempo de Evento" y el "Tiempo de Reloj".

- **Tiempo de Evento (Reactivo):** Una salida de cama detectada por el sensor que genera una alerta inmediata.
- **Tiempo de Reloj (El Barrido):** El sistema detecta que alguien lleva 20 minutos en el baño sin moverse.

|   |   |   |
|---|---|---|
|Escenario|Disparador|Tipo de Reacción|
|Salida de Cama|Cambio de Estado (FSM)|Alerta por Evento Inmediato.|
|Permanencia en Baño|Expiración de Tiempo (Dwell)|**Barrido** (Clock Time).|

El **Barrido** es el "quinto elemento" de HubP. Funciona mediante un **programador durable** (una tabla de _timers_ en SQLite). A diferencia de un simple temporizador de memoria, si el Hub se reinicia, el Barrido sobrevive. Esto garantiza que si un residente cae y queda inmóvil, el sistema disparará la alerta por el simple paso del tiempo, incluso si el sensor no envía nuevos datos.

## 7. Conclusión: La Seguridad Detrás de la Pantalla

La robustez de HubP reside en su "sistema nervioso": una malla de mensajería **NATS** que conecta a los cuatro guardianes. NATS garantiza una entrega de mensajes de tipo "al menos una vez" (_at-least-once_), asegurando que ninguna alerta se pierda en el transporte.

El lazo de seguridad se cierra con una métrica de auditoría crítica: la **Presencia de Staff** detectada por IA. Al registrar el momento exacto en que un humano entra en la habitación, el sistema calcula los `staff_arrival_seconds`. Esto transforma a HubP de un simple detector de caídas en una herramienta de auditoría de calidad, midiendo la capacidad de respuesta real del equipo de salud. Detrás de cada notificación en la pantalla, hay una arquitectura diseñada para que el cuidado nunca sea fruto del azar.