use diesel::SqliteConnection;
use mana_kernel::Instante;

use super::{AttributeId, AttributeInput, ResidentAttribute};
use crate::residentes::ResidentId;
use crate::PoblacionError;

/// Repositorio del subdominio de atributos. No tiene rutas HTTP en F3: se
/// ejercita desde el dominio y la persistencia.
pub trait AtributosRepo {
    fn create_attribute_in_transaction(
        connection: &mut SqliteConnection,
        id: AttributeId,
        input: AttributeInput,
        now: Instante,
    ) -> Result<ResidentAttribute, PoblacionError>;

    fn list_attributes(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
    ) -> Result<Vec<ResidentAttribute>, PoblacionError>;
}
