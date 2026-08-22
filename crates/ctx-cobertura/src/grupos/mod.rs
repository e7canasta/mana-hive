//! Subdominio de grupos de staff: creacion, actualizacion y membresias temporales.

pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

define_kinds!(StaffGroupKind, MembershipKind);

pub type StaffGroupId = Id<StaffGroupKind>;
pub type MembershipId = Id<MembershipKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum GruposError {
    #[error("el nombre del grupo no puede estar vacio")]
    EmptyName,
    #[error("el nombre excede la longitud maxima de {0} caracteres")]
    NameTooLong(usize),
    #[error("no hay campos para actualizar")]
    EmptyUpdate,
    #[error("el grupo ya esta retirado")]
    AlreadyRetired,
    #[error("el usuario no esta activo")]
    UserNotActive,
}

const MAX_NAME: usize = 120;

#[derive(Clone, Debug)]
pub struct StaffGroup {
    pub id: StaffGroupId,
    pub facility_id: String,
    pub name: String,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Clone, Debug)]
pub struct StaffGroupInput {
    pub facility_id: String,
    pub name: String,
}

#[derive(Clone, Debug, Default)]
pub struct StaffGroupUpdate {
    pub name: Option<String>,
}

#[derive(Clone, Debug)]
pub struct StaffGroupMembership {
    pub id: MembershipId,
    pub staff_group_id: StaffGroupId,
    pub user_id: String,
    pub valid_from: Instante,
    pub valid_to: Option<Instante>,
    pub created_at: Instante,
}

#[derive(Clone, Debug)]
pub struct MembershipInput {
    pub user_id: String,
    pub valid_from: Instante,
}

impl StaffGroup {
    pub fn create(
        id: StaffGroupId,
        input: StaffGroupInput,
        now: Instante,
    ) -> Result<Self, GruposError> {
        let name = validate_name(&input.name)?;
        Ok(Self {
            id,
            facility_id: input.facility_id,
            name,
            retired_at: None,
            retired_by: None,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn apply_update(
        &mut self,
        input: StaffGroupUpdate,
        now: Instante,
    ) -> Result<(), GruposError> {
        if input.name.is_none() {
            return Err(GruposError::EmptyUpdate);
        }
        if let Some(name) = input.name {
            self.name = validate_name(&name)?;
        }
        self.updated_at = now;
        Ok(())
    }

    pub fn retire(&mut self, by: Id<Actor>, now: Instante) -> Result<(), GruposError> {
        if self.retired_at.is_some() {
            return Err(GruposError::AlreadyRetired);
        }
        self.retired_at = Some(now);
        self.retired_by = Some(by);
        self.updated_at = now;
        Ok(())
    }
}

pub fn new_group_id() -> StaffGroupId {
    Id::new(crate::common::random_id("group"))
}

pub fn new_membership_id() -> MembershipId {
    Id::new(crate::common::random_id("membership"))
}

fn validate_name(value: &str) -> Result<String, GruposError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(GruposError::EmptyName);
    }
    if value.chars().count() > MAX_NAME {
        return Err(GruposError::NameTooLong(MAX_NAME));
    }
    Ok(value.to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    fn input() -> StaffGroupInput {
        StaffGroupInput {
            facility_id: "facility-1".to_owned(),
            name: "Enfermeria A".to_owned(),
        }
    }

    #[test]
    fn creates_group_and_validates_name() {
        let group = StaffGroup::create(new_group_id(), input(), instant()).unwrap();
        assert_eq!(group.name, "Enfermeria A");
        assert!(group.retired_at.is_none());

        assert!(matches!(
            StaffGroup::create(
                new_group_id(),
                StaffGroupInput {
                    name: "  ".to_owned(),
                    ..input()
                },
                instant(),
            ),
            Err(GruposError::EmptyName)
        ));
    }

    #[test]
    fn update_rejects_empty_input() {
        let mut group = StaffGroup::create(new_group_id(), input(), instant()).unwrap();
        assert!(matches!(
            group.apply_update(StaffGroupUpdate::default(), instant()),
            Err(GruposError::EmptyUpdate)
        ));
    }

    #[test]
    fn retire_is_idempotent_check() {
        let mut group = StaffGroup::create(new_group_id(), input(), instant()).unwrap();
        group.retire(Id::new("actor-1"), instant()).unwrap();
        assert!(group.retired_at.is_some());
        assert!(matches!(
            group.retire(Id::new("actor-2"), instant()),
            Err(GruposError::AlreadyRetired)
        ));
    }
}
