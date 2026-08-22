use diesel::prelude::*;
use diesel::{OptionalExtension, SqliteConnection};
use mana_kernel::{Actor, Id, Instante};

use crate::common::{parse_instant, stored_domain};
use crate::schema::resident_bed_assignments;
use crate::PoblacionError;

use super::repo::AsignacionesRepo;
use super::{AsignacionesError, AssignResult, AssignmentId, BedAssignment, BedRef};
use crate::residentes::{repo::ResidentesRepo, ResidentId};

#[derive(Queryable, Selectable)]
#[diesel(table_name = resident_bed_assignments)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
pub(crate) struct AssignmentRow {
    pub(crate) id: String,
    pub(crate) resident_id: String,
    pub(crate) bed_id: String,
    pub(crate) starts_at: String,
    pub(crate) ends_at: Option<String>,
    pub(crate) created_at: String,
    pub(crate) created_by: Option<String>,
}

#[derive(Insertable)]
#[diesel(table_name = resident_bed_assignments)]
struct NewAssignmentRow<'a> {
    id: &'a str,
    resident_id: &'a str,
    bed_id: &'a str,
    starts_at: &'a str,
    ends_at: Option<&'a str>,
    created_at: &'a str,
    created_by: Option<&'a str>,
}

#[derive(AsChangeset)]
#[diesel(table_name = resident_bed_assignments)]
#[diesel(treat_none_as_null = true)]
struct AssignmentChangeset<'a> {
    ends_at: Option<&'a str>,
}

impl AsignacionesRepo for SqliteConnection {
    fn assign_in_transaction(
        connection: &mut SqliteConnection,
        id: AssignmentId,
        resident_id: ResidentId,
        bed_id: &BedRef,
        starts_at: Instante,
        created_by: Option<Id<Actor>>,
    ) -> Result<AssignResult, PoblacionError> {
        <SqliteConnection as ResidentesRepo>::ensure_resident_active(connection, &resident_id)?;

        let resident_latest = latest(connection, Some(&resident_id), None)?;
        let bed_latest = latest(connection, None, Some(bed_id))?;
        let same_open = matches!(
            (&resident_latest, &bed_latest),
            (Some(resident), Some(bed)) if resident.is_open() && resident.id == bed.id
        );

        let resident_closed = if same_open {
            None
        } else {
            validate_and_close(connection, resident_latest, &starts_at, "residente")?
        };
        let bed_closed = validate_and_close(connection, bed_latest, &starts_at, "de la cama")?;

        let assignment =
            BedAssignment::assign(id, resident_id, bed_id.clone(), starts_at, created_by);
        let created_at = assignment.created_at.to_string();
        let starts_at = assignment.starts_at.to_string();
        let created_by = assignment.created_by.as_ref().map(ToString::to_string);
        diesel::insert_into(resident_bed_assignments::table)
            .values(NewAssignmentRow {
                id: assignment.id.as_str(),
                resident_id: assignment.resident_id.as_str(),
                bed_id: assignment.bed_id.as_str(),
                starts_at: &starts_at,
                ends_at: None,
                created_at: &created_at,
                created_by: created_by.as_deref(),
            })
            .execute(connection)
            .map_err(PoblacionError::database)?;

        Ok(AssignResult {
            created: assignment,
            resident_closed,
            bed_closed,
        })
    }

    fn release_in_transaction(
        connection: &mut SqliteConnection,
        bed_id: &BedRef,
        ends_at: Instante,
    ) -> Result<BedAssignment, PoblacionError> {
        let open = open_for_bed(connection, bed_id)?;
        let Some(mut open) = open else {
            return Err(PoblacionError::Asignaciones(AsignacionesError::FreeBed));
        };
        open.close(ends_at)?;
        close_row(connection, &open)?;
        Ok(open)
    }

    fn close_open_for_resident_in_transaction(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
        ends_at: Instante,
    ) -> Result<Option<BedAssignment>, PoblacionError> {
        // La abierta, no "la ultima si resulta estar abierta". El invariante
        // dice que hay a lo sumo una abierta por residente, asi que hay que
        // preguntarlo directo: con `latest` + filtro, un empate de `starts_at`
        // devolvia una asignacion ya cerrada y el egreso no cerraba nada.
        let open = open_for_resident(connection, resident_id)?;
        let Some(mut open) = open else {
            return Ok(None);
        };
        open.close(ends_at)?;
        close_row(connection, &open)?;
        Ok(Some(open))
    }

    fn list_assignments(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
    ) -> Result<Vec<BedAssignment>, PoblacionError> {
        resident_bed_assignments::table
            .filter(resident_bed_assignments::resident_id.eq(resident_id.as_str()))
            .select(AssignmentRow::as_select())
            .order((
                resident_bed_assignments::starts_at.asc(),
                resident_bed_assignments::rowid.asc(),
            ))
            .load::<AssignmentRow>(connection)
            .map_err(PoblacionError::database)?
            .into_iter()
            .map(BedAssignment::try_from)
            .collect()
    }

    fn list_open_assignments(
        connection: &mut SqliteConnection,
    ) -> Result<Vec<BedAssignment>, PoblacionError> {
        resident_bed_assignments::table
            .filter(resident_bed_assignments::ends_at.is_null())
            .select(AssignmentRow::as_select())
            .order((
                resident_bed_assignments::resident_id.asc(),
                resident_bed_assignments::starts_at.asc(),
                resident_bed_assignments::rowid.asc(),
            ))
            .load::<AssignmentRow>(connection)
            .map_err(PoblacionError::database)?
            .into_iter()
            .map(BedAssignment::try_from)
            .collect()
    }

    fn open_assignment_for_resident(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
    ) -> Result<Option<BedAssignment>, PoblacionError> {
        open_for_resident(connection, resident_id)
    }
}

fn latest(
    connection: &mut SqliteConnection,
    resident_id: Option<&ResidentId>,
    bed_id: Option<&BedRef>,
) -> Result<Option<BedAssignment>, PoblacionError> {
    let mut filter = resident_bed_assignments::table.into_boxed();
    if let Some(resident_id) = resident_id {
        filter = filter.filter(resident_bed_assignments::resident_id.eq(resident_id.as_str()));
    }
    if let Some(bed_id) = bed_id {
        filter = filter.filter(resident_bed_assignments::bed_id.eq(bed_id.as_str()));
    }
    filter
        .select(AssignmentRow::as_select())
        .order((
            resident_bed_assignments::starts_at.desc(),
            resident_bed_assignments::rowid.desc(),
        ))
        .first(connection)
        .optional()
        .map_err(PoblacionError::database)?
        .map(BedAssignment::try_from)
        .transpose()
}

fn open_for_bed(
    connection: &mut SqliteConnection,
    bed_id: &BedRef,
) -> Result<Option<BedAssignment>, PoblacionError> {
    resident_bed_assignments::table
        .filter(resident_bed_assignments::bed_id.eq(bed_id.as_str()))
        .filter(resident_bed_assignments::ends_at.is_null())
        .select(AssignmentRow::as_select())
        .first(connection)
        .optional()
        .map_err(PoblacionError::database)?
        .map(BedAssignment::try_from)
        .transpose()
}

fn open_for_resident(
    connection: &mut SqliteConnection,
    resident_id: &ResidentId,
) -> Result<Option<BedAssignment>, PoblacionError> {
    resident_bed_assignments::table
        .filter(resident_bed_assignments::resident_id.eq(resident_id.as_str()))
        .filter(resident_bed_assignments::ends_at.is_null())
        .select(AssignmentRow::as_select())
        .first(connection)
        .optional()
        .map_err(PoblacionError::database)?
        .map(BedAssignment::try_from)
        .transpose()
}

/// Valida el orden de intervalos (invariante 4) y cierra la asignacion abierta
/// si la hay (invariante 3). `side` describe el lado para el error.
fn validate_and_close(
    connection: &mut SqliteConnection,
    latest: Option<BedAssignment>,
    starts_at: &Instante,
    side: &'static str,
) -> Result<Option<BedAssignment>, PoblacionError> {
    let Some(mut latest) = latest else {
        return Ok(None);
    };
    if latest.is_open() {
        if starts_at < &latest.starts_at {
            return Err(PoblacionError::Asignaciones(
                AsignacionesError::OverlappingInterval { side },
            ));
        }
        latest.close(*starts_at)?;
        close_row(connection, &latest)?;
        Ok(Some(latest))
    } else {
        let ends_at = latest
            .ends_at
            .as_ref()
            .ok_or(PoblacionError::InvalidStoredData(
                "asignacion sin ends_at en la fila".to_owned(),
            ))?;
        if starts_at < ends_at {
            return Err(PoblacionError::Asignaciones(
                AsignacionesError::OverlappingInterval { side },
            ));
        }
        Ok(None)
    }
}

fn close_row(
    connection: &mut SqliteConnection,
    assignment: &BedAssignment,
) -> Result<(), PoblacionError> {
    let ends_at = assignment.ends_at.as_ref().map(ToString::to_string).ok_or(
        PoblacionError::InvalidStoredData("asignacion cerrada sin ends_at".to_owned()),
    )?;
    diesel::update(resident_bed_assignments::table.find(assignment.id.as_str()))
        .set(AssignmentChangeset {
            ends_at: Some(&ends_at),
        })
        .execute(connection)
        .map_err(PoblacionError::database)?;
    Ok(())
}

impl TryFrom<AssignmentRow> for BedAssignment {
    type Error = PoblacionError;

    fn try_from(row: AssignmentRow) -> Result<Self, PoblacionError> {
        let starts_at = parse_instant("starts_at", row.starts_at)?;
        let created_at = parse_instant("created_at", row.created_at)?;
        let bed_id = BedRef::new(row.bed_id).map_err(|error| stored_domain("assignment", error))?;
        let mut assignment = BedAssignment::assign(
            AssignmentId::new(row.id),
            ResidentId::new(row.resident_id),
            bed_id,
            starts_at,
            row.created_by.map(Id::<Actor>::new),
        );
        assignment.created_at = created_at;
        assignment.ends_at = row
            .ends_at
            .map(|value| parse_instant("ends_at", value))
            .transpose()?;
        Ok(assignment)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::residentes::ResidentInput;
    use crate::testsupport::{instant, store};

    fn create_resident(store: &crate::PopulationStore, name: &str) -> ResidentId {
        store
            .create_resident(
                ResidentInput {
                    full_name: name.to_owned(),
                    external_id: None,
                    birth_date: None,
                    admission_date: Some("2026-01-15".to_owned()),
                },
                instant(),
            )
            .unwrap()
            .id
    }

    #[test]
    fn assign_moves_and_releases_without_crossing_invariants() {
        let store = store();
        let carmen = create_resident(&store, "Carmen Ruiz");
        let pedro = create_resident(&store, "Pedro Gomez");

        let first = store
            .assign(&carmen, &BedRef::new("bed-1").unwrap(), instant(), None)
            .unwrap();
        assert!(first.resident_closed.is_none());
        assert!(first.bed_closed.is_none());

        let later = "2026-08-18T13:00:00.000Z".parse().unwrap();
        let moved = store
            .assign(&carmen, &BedRef::new("bed-2").unwrap(), later, None)
            .unwrap();
        assert!(moved.created.is_open());
        assert!(!moved.resident_closed.unwrap().is_open());
        assert!(moved.bed_closed.is_none());

        let pedro_assignment = store
            .assign(&pedro, &BedRef::new("bed-1").unwrap(), later, None)
            .unwrap();
        assert!(pedro_assignment.bed_closed.is_none());
        assert!(pedro_assignment.created.is_open());

        let released = store
            .release(&BedRef::new("bed-1").unwrap(), later)
            .unwrap();
        assert!(!released.is_open());
        assert!(matches!(
            store.release(&BedRef::new("bed-1").unwrap(), later),
            Err(PoblacionError::Asignaciones(AsignacionesError::FreeBed))
        ));

        assert_eq!(store.list_assignments(&carmen).unwrap().len(), 2);
    }

    #[test]
    fn rejects_overlapping_and_backdated_intervals() {
        let store = store();
        let carmen = create_resident(&store, "Carmen Ruiz");
        let t0 = "2026-08-01T10:00:00.000Z".parse().unwrap();
        store
            .assign(&carmen, &BedRef::new("bed-1").unwrap(), t0, None)
            .unwrap();

        let earlier = "2026-08-01T09:00:00.000Z".parse().unwrap();
        assert!(matches!(
            store.assign(&carmen, &BedRef::new("bed-2").unwrap(), earlier, None),
            Err(PoblacionError::Asignaciones(
                AsignacionesError::OverlappingInterval { side: "residente" }
            ))
        ));

        let closed_earlier = "2026-07-30T10:00:00.000Z".parse().unwrap();
        store
            .assign(&carmen, &BedRef::new("bed-2").unwrap(), t0, None)
            .unwrap();
        store
            .release(
                &BedRef::new("bed-2").unwrap(),
                "2026-08-02T10:00:00.000Z".parse().unwrap(),
            )
            .unwrap();
        let pedro = create_resident(&store, "Pedro Gomez");
        assert!(matches!(
            store.assign(&pedro, &BedRef::new("bed-2").unwrap(), closed_earlier, None),
            Err(PoblacionError::Asignaciones(
                AsignacionesError::OverlappingInterval { side: "de la cama" }
            ))
        ));
    }

    #[test]
    fn assign_to_inactive_or_unknown_resident_is_not_found() {
        let store = store();
        let carmen = create_resident(&store, "Carmen Ruiz");
        store
            .discharge(&carmen, "2026-08-01", "actor-1", instant())
            .unwrap();
        assert!(matches!(
            store.assign(&carmen, &BedRef::new("bed-1").unwrap(), instant(), None),
            Err(PoblacionError::NotFound)
        ));
        assert!(matches!(
            store.assign(
                &ResidentId::new("resident-missing"),
                &BedRef::new("bed-1").unwrap(),
                instant(),
                None,
            ),
            Err(PoblacionError::NotFound)
        ));
    }

    #[test]
    fn concurrent_writes_keep_one_open_assignment_per_side() {
        let store = store();
        let carmen = create_resident(&store, "Carmen Ruiz");
        let pedro = create_resident(&store, "Pedro Gomez");
        let bed = BedRef::new("bed-1").unwrap();

        let store_a = store.clone();
        let store_b = store.clone();
        let carmen = carmen.clone();
        let pedro = pedro.clone();
        let bed_a = bed.clone();

        let handle_a = std::thread::spawn(move || {
            store_a.assign(&carmen, &bed_a, instant(), None).map(|_| ())
        });
        let handle_b =
            std::thread::spawn(move || store_b.assign(&pedro, &bed, instant(), None).map(|_| ()));

        // Ambas escrituras pueden ganar (la segunda serializa y mueve al
        // primero); el invariante es el estado final: una sola asignacion
        // abierta para la cama.
        let results = [handle_a.join().unwrap(), handle_b.join().unwrap()];
        assert!(results.iter().all(|result| result.is_ok()), "{results:?}");

        let mut connection = store.pool.get().unwrap();
        let open = store
            .list_open_assignments_in_transaction(&mut connection)
            .unwrap();
        assert_eq!(open.len(), 1);
        assert_eq!(open[0].bed_id.as_str(), "bed-1");
    }

    #[test]
    fn open_assignment_read_model_is_available() {
        let store = store();
        let carmen = create_resident(&store, "Carmen Ruiz");
        store
            .assign(&carmen, &BedRef::new("bed-1").unwrap(), instant(), None)
            .unwrap();

        let mut connection = store.pool.get().unwrap();
        let open = store
            .open_assignment_for_resident_in_transaction(&mut connection, &carmen)
            .unwrap();
        assert!(open.is_some());
        drop(connection);
        let mut connection = store.pool.get().unwrap();
        let all = store
            .list_open_assignments_in_transaction(&mut connection)
            .unwrap();
        assert_eq!(all.len(), 1);
        assert_eq!(all[0].bed_id.as_str(), "bed-1");
    }
}
