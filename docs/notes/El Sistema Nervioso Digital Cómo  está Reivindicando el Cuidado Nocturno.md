# El Sistema Nervioso Digital: Cómo "mana-hive" está Reivindicando el Cuidado Nocturno

### 1. Introducción: El Silencio Crítico de la Madrugada

La madrugada en una institución de salud es un periodo de extrema vulnerabilidad. Bajo un silencio aparente, el personal nocturno enfrenta una carga cognitiva abrumadora; el agotamiento acumulado y la dispersión geográfica en las instalaciones crean el riesgo latente de no detectar a tiempo un evento crítico en una habitación. Como arquitectos de sistemas, nuestra responsabilidad trasciende el código: debemos resolver un problema profundamente humano.

¿Cómo puede un software garantizar que la persona adecuada llegue a la habitación adecuada exactamente a tiempo, sin sucumbir al ruido tecnológico? La respuesta no está en añadir más sensores, sino en una arquitectura distribuida y dirigida por eventos. Introducimos **mana-hive**, una plataforma que actúa como el "sistema nervioso central" del monitoreo asistencial, transformando percepciones crudas en juicios clínicos precisos y trazables.

### 2. La Máquina del Tiempo de la Auditoría: Reproducibilidad Total

En el cuidado de pacientes críticos, el silencio de una alarma puede ser tan significativo como su activación. La arquitectura de **mana-hive** se fundamenta en la "Machine Reproducibility" (Reproducibilidad por Máquina), donde el **Hub** actúa como un _System of Record_ (SoR) inmutable respaldado por Postgres. A diferencia de los registros tradicionales, el Hub no solo guarda un log de eventos, sino el rastro del razonamiento aplicado mediante el `DecisionRecord`.

Esta trazabilidad permite reconstruir con exactitud quirúrgica el estado mental del sistema meses después de un incidente:

"El Hub permite responder exactamente por qué una alarma sonó o no en un momento específico, permitiendo la reconstrucción total del estado y la lógica aplicada."

Desde una perspectiva clínica y legal, este registro inmutable de telemetría de juicio es vital. No solo prueba qué ocurrió, sino que valida la integridad de los algoritmos frente a revisiones médicas, permitiendo auditar por qué se descartó una señal basándose en el contexto del paciente en ese microsegundo.

### 3. Lógica Pura: El Cerebro Clínico Libre de Ruido Tecnológico

Para construir confianza, el núcleo del sistema debe ser determinista. **mana-hive** implementa una filosofía de "Pure Core" mediante el uso del plugin de Gradle `manahive.pure-domain`. Esta arquitectura aísla la lógica clínica (Deciders y transiciones de estado) de cualquier dependencia de infraestructura, como NATS o Spring. La dualidad se completa con el plugin `manahive.spring-service`, que actúa como un "cascarón" (shell) delgado encargado de la mensajería y la inyección de dependencias.

Esta separación asegura que el sistema se comporte como una función pura: si los mismos datos entran, siempre sale la misma decisión, sea en tiempo real o en un _replay_ histórico. La estructura de motores se organiza en tres niveles técnicos:

- **Pure Domain:** Lógica de negocio pura y agregados dirigidos por eventos (Event-Sourced Aggregates) sin dependencias externas.
- **Spring Service:** La infraestructura técnica que gestiona suscripciones de NATS JetStream y la serialización de datos.
- **Batch Tool:** Herramientas CLI que permiten el "Golden Replay", verificando la lógica contra datos históricos sin necesidad de un entorno de ejecución completo.

### 4. El "Presupuesto de Fatiga": Combatiendo el Desgaste del Cuidador

Un cuidador saturado por falsas alarmas es un riesgo de seguridad. El motor **Sentinel** aborda este reto mediante el `FatigueBudget` (Presupuesto de Fatiga). Utilizando un `EpisodeLedger` para rastrear cada interacción por residente durante un turno, el sistema es capaz de detectar cuándo se ha superado el umbral de carga cognitiva del personal (por defecto, 12 alertas).

Esta capacidad humaniza la arquitectura al reconocer los límites biológicos del cuidador. Para lograrlo, el Sentinel distingue entre un **Incidente** (un riesgo que requiere alerta inmediata) y una **Occurrence** (datos clínicos que ocurren bajo el "paraguas" de un episodio activo y no deben generar ruido adicional). La misión es absoluta:

"Llegar a tiempo con el menor número de falsas alarmas posible."

### 5. Gemelos Digitales: De Sensores Ruidosos a Hechos Clínicos Estables

El **Scene Engine** actúa como el traductor de la realidad física, manteniendo un "Digital Twin" (Gemelo Digital) de cada cama. Mediante procesos de histéresis y umbrales de confianza, el motor purifica las observaciones crudas para emitir estados estables. Un componente crítico aquí es el **ClockSweeper**: este proceso periódico permite que el sistema genere hechos clínicos basados en la _ausencia_ de eventos, como cuando un residente permanece fuera de la cama más tiempo del permitido por su política de seguridad.

Esta transición de datos se gestiona como una tubería de refinamiento:

- **Percepción:** Datos crudos y ruidosos (ej. detección de movimiento).
- **Hecho de Escena (Scene Fact):** Estado de alto nivel validado por el Digital Twin (ej. "Residente fuera de la cama por 5 minutos").
- **Señal del Sentinel:** Juicio clínico que evalúa el hecho contra reglas de riesgo (ej. Incidente de caída potencial).

### 6. El Lenguaje Publicado: La Elegancia de la Comunicación Versatizada

La comunicación entre los diferentes _Bounded Contexts_ de **mana-hive** se realiza mediante NATS JetStream, utilizando contratos de eventos inmutables. El bus de mensajes se configura con una "Limits-based retention" (retención basada en límites, típicamente de 7 días), funcionando como un buffer de alto rendimiento y no como archivo permanente, responsabilidad delegada al Hub.

Para garantizar la evolución del sistema sin interrupciones, se utiliza una taxonomía de sujetos estricta bajo el patrón: `{domain}.{type}.{version}.{discriminator}`. Este versionado (v1, v2) permite realizar despliegues "blue-green", donde nuevos motores pueden coexistir con los antiguos hasta que la transición sea segura. En este flujo, el motor **Politica** actúa como una Capa Anticorrupción (ACL), traduciendo las configuraciones administrativas del Hub en calibraciones técnicas que los motores pueden ejecutar sin conocer la complejidad de la base de datos central.

### 7. Conclusión: Hacia un Futuro de Decisiones Transparentes

La arquitectura de **mana-hive** redefine el estándar de la tecnología asistencial al integrar determinismo funcional y empatía operativa. La transparencia del sistema no es una promesa vacía, sino que se materializa en el contenedor `Explained<T>`, que envuelve cada resultado con los pasos lógicos y las reglas que lo produjeron.

Al final del día, como arquitectos, operamos bajo una máxima ineludible: **"una decisión sin un 'porqué' se considera inexistente"**. La implementación del `DecisionRecord` asegura que cada intervención tecnológica sea justificable ante el personal médico y las familias.

**Pregunta final:** Al confiar el cuidado de sus propios seres queridos a la tecnología, ¿elegirían un sistema que simplemente emite alertas, o uno que puede explicar con total transparencia el razonamiento detrás de cada segundo de vigilancia?