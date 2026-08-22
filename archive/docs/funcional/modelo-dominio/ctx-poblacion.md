# Modelo de dominio: `ctx-poblacion`

## Pregunta del contexto

Quien vive en la residencia (padron), donde esta asignado (cama) y cuando
entro/salio (ciclo clinico).

## Objetos de dominio

```text
Resident (agregado raiz)
  id: ResidentId
  full_name: String
  external_id: Option<String>
  birth_date: Option<NaiveDate>
  admission_date: Option<NaiveDate>
  status: ResidentStatus (active | discharged)
  discharged_at: Option<NaiveDate>
  discharged_by: Option<Id<Actor>>
  created_at: Instante
  updated_at: Instante

BedAssignment (agregado de asociacion)
  id: AssignmentId
  resident_id: ResidentId
  bed_id: BedRef (opaco, sin FK a beds)
  starts_at: Instante
  ends_at: Option<Instante>
  created_at: Instante
  created_by: Option<Id<Actor>>

ResidentAttribute (agregado)
  id: AttributeId
  resident_id: ResidentId
  code: AttributeCode (fall_risk | wandering)
  value: String
  source: String
  source_ref: Option<String>
  recorded_by: Option<Id<Actor>>
  recorded_at: Instante
  valid_from: NaiveDate
  valid_to: Option<NaiveDate>
```

### Value objects

| Tipo              | Significado                                                                                    |
| ----------------- | ---------------------------------------------------------------------------------------------- |
| `BedRef`          | Identificador opaco de cama. Se valida contra Residencia en `mana-app`, no en `ctx-poblacion`. |
| `ResidentStatus`  | Enum `active \| discharged`. Estado del ciclo clinico, no un flag generico.                    |
| `AttributeCode`   | Vocabulario controlado: `fall_risk`, `wandering`. Validado en el limite.                       |
| `ResidentInput`   | Datos de creacion de un residente.                                                             |
| `ResidentUpdate`  | Datos de actualizacion parcial.                                                                |
| `DischargeResult` | Resultado del egreso: residente actualizado + asignacion cerrada (si habia).                   |
| `AssignResult`    | Resultado de asignacion: nueva asignacion + cerradas de ambos lados.                           |

## Invariantes

1. Una asignacion abierta por residente: indice parcial unico
   `WHERE ends_at IS NULL` en `resident_id`.
2. Una asignacion abierta por cama: indice parcial unico
   `WHERE ends_at IS NULL` en `bed_id`.
3. Asignar cierra activas de ambos lados: `assign_in_transaction` cierra la
   asignacion abierta del residente y de la cama antes de crear la nueva.
4. Intervalos ordenados sin solapamiento: dentro de la transaccion,
   `starts_at >= ends_at` del ultimo intervalo de cada lado.
5. Liberar no egresa: `release` solo cierra la asignacion; no modifica el
   `status` del residente.
6. Egreso cierra la abierta: `discharge_in_transaction` cierra la asignacion
   abierta del residente en la misma transaccion.
7. Egreso >= ingreso: `Resident::discharge` valida `date >= admission_date`.
8. Atributo tiene source y recorded_at: `ResidentAttribute::create` requiere
   ambos campos.
9. Proyecciones: hook documentado para limpiar proyecciones de ambas camas
   al reasignar. Sin contexto Observacion aun; queda audit entry como
   registro.
10. Activo sin cama esta permitido: el status del residente no depende de
    tener asignacion.

## Mapeo a casos de uso

| Caso              | Entidades                   | Regla principal            | Servicio de aplicacion |
| ----------------- | --------------------------- | -------------------------- | ---------------------- |
| POP-01 Alta       | `Resident`                  | status = active            | `create_resident`      |
| POP-02 Actualizar | `Resident`                  | campos parciales           | `update_resident`      |
| POP-03 Listar     | `Resident`, `BedAssignment` | read model compuesto       | `list_residents`       |
| POP-04 Detalle    | `Resident`                  | solo lectura               | `resident_detail`      |
| POP-05 Asignar    | `BedAssignment`             | invariantes 1-4            | `assign_bed` (saga)    |
| POP-06 Historial  | `BedAssignment`             | solo lectura               | `list_assignments`     |
| POP-07 Liberar    | `BedAssignment`             | invariante 5, 409 si libre | `release_bed`          |
| POP-08 Egresar    | `Resident`, `BedAssignment` | invariantes 6-7            | `discharge_resident`   |

Detalle funcional: [`../casos-uso/ctx-poblacion.md`](../casos-uso/ctx-poblacion.md).

## Mapeo a modelo de datos

| Objeto              | Tabla                      | Columnas principales                                                                                                 | Restricciones e indices                                                                  | Migracion                               |
| ------------------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------- |
| `Resident`          | `residents`                | id, full_name, external_id, birth_date, admission_date, status, discharged_at, discharged_by, created_at, updated_at | PK id; status CHECK (active, discharged)                                                 | `0005_poblacion`                        |
| `BedAssignment`     | `resident_bed_assignments` | id, resident_id, bed_id, starts_at, ends_at, created_at, created_by                                                  | PK id; UNIQUE (resident_id) WHERE ends_at IS NULL; UNIQUE (bed_id) WHERE ends_at IS NULL | `0005_poblacion` + `0006_poblacion_api` |
| `ResidentAttribute` | `resident_attributes`      | id, resident_id, code, value, source, source_ref, recorded_by, recorded_at, valid_from, valid_to                     | PK id; FK resident_id; CHECK code IN (fall_risk, wandering)                              | `0005_poblacion`                        |

## Realizacion

- Dominio: `crates/ctx-poblacion/src/{residentes,asignaciones,atributos}/mod.rs`
- Persistencia: `crates/ctx-poblacion/src/{residentes,asignaciones,atributos}/sqlite.rs`
- Repositorio: `crates/ctx-poblacion/src/{residentes,asignaciones,atributos}/repo.rs`
- Store: `crates/ctx-poblacion/src/lib.rs` (`PopulationStore`)
- Aplicacion: `crates/mana-app/src/poblacion.rs`
- Transporte: `crates/mana-http/src/poblacion.rs`
- Wire: `crates/mana-wire/src/lib.rs` (DTOs)
- SDK: `crates/mana-sdk/src/poblacion.rs`
- CLI: `crates/mana-cli/src/commands/poblacion.rs`
- Migraciones: `crates/ctx-poblacion/migrations/0005_poblacion` y `0006_poblacion_api`
