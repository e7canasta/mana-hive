# Plantilla de modelo de contexto

Completar en `hub/docs/contextos/ctx-<nombre>.md` **antes** de escribir codigo
del contexto. El documento es la spec del crate: que pregunta responde, que
posee y que no puede dejar de ser cierto.

Node es la **referencia**, no la especificacion. Donde el modelo actual este mal,
se disena de nuevo y se anota en "Que cambia respecto de hoy". La base es nueva
y no hay datos que preservar; los clientes existentes son evidencia del contrato
que funcionan, así que cada cambio de contrato viaja con su cambio de cliente en
el mismo PR.

---

## Estructura

### 1 · La pregunta

Una frase. Si necesitás dos, probablemente son dos contextos.

> *Vigilancia: ¿qué alertas hay, quién las atendió y a quién se le avisó?*

### 2 · Clase

`núcleo` · `soporte` · `genérico`. Determina cuánto se gasta acá: el núcleo se
lleva el sistema de tipos y los tests de propiedades; soporte, CRUD honesto;
genérico, lo más aburrido posible.

### 3 · Agregados

La raíz y qué cuelga de ella. Un agregado es la unidad de consistencia
transaccional: lo que se lee y se escribe junto, y donde viven los invariantes.

### 4 · Tablas que posee

Lista exhaustiva. **Cada tabla del esquema pertenece a exactamente un contexto.**
Si dos la reclaman, o el límite está mal o falta un read model.

Incluir tablas nuevas del rediseño y marcar las que se retiran.

| Tabla | Estado | Notas |
| --- | --- | --- |
| `alerts` | rediseñada | el estado pasa a llevar actor y fecha adentro |
| `notification_deliveries` | nueva | hoy no existe registro de haber avisado |

### 5 · Casos de uso

Comandos y consultas, con actor y capability. **Es el inventario que hay que
extraer de `api/domains/*.js`**, no una lista aspiracional: si Node hace algo que
no está acá, se perdió en la traducción.

| Tipo | Caso de uso | Capability | Hoy en |
| --- | --- | --- | --- |
| comando | Reconocer una alerta | `alerts.manage` | `PATCH /api/v1/alerts/:id` |
| consulta | Alertas abiertas de un ala | `alerts.read` | `GET /api/v1/alerts` |

### 6 · Invariantes

Lo que no puede dejar de ser cierto. Salen de `memoria-sor.md §4`, de
`DATA-MODEL.md` y **de leer el código**, que es donde están los que nadie
escribió.

Cada uno con la forma en que se hace imposible de violar: tipo, constructor,
constraint o test.

### 7 · Qué cambia respecto de hoy

El diff de diseño contra el modelo actual, con el porqué. Acá van los defectos
conocidos que le tocan a este contexto y **cualquier otro que aparezca al
modelar** — no hay una lista cerrada de cambios autorizados.

Marcar explícitamente cuáles **requieren tocar un panel**. Esa es la única
columna que cuesta plata.

### 8 · Cruces con otros contextos

Lo que este contexto necesita de otro y no puede importar. Cada cruce vive en
`mana-app` y se declara acá para que la excepción sea visible antes de escribirla.

### 9 · Escenas que lo prueban

Qué escena cubre qué invariante. Las escenas se escriben **contra Node, antes de
migrar**, y son el criterio de aceptación del contexto.

| Escena | Prueba |
| --- | --- |
| `vigilancia/ciclo-de-alerta.json` | open → acknowledged → attending → resolved |
| `vigilancia/permanencia-vence-sola.json` | la permanencia vence por tiempo, no por evento |

### 10 · Preguntas abiertas

Lo que no se sabe todavía, con quién decide. Una pregunta abierta escrita es
barata; descubierta a mitad de la implementación, no.
