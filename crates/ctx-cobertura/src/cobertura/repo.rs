use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use super::{CoverageInput, CoverageResult, WingCoverage};
use crate::CoberturaError;

pub trait CoberturaRepo {
    fn assign_coverage_in_transaction(
        connection: &mut SqliteConnection,
        input: CoverageInput,
        now: Instante,
        by: Option<Id<Actor>>,
    ) -> Result<CoverageResult, CoberturaError>;

    fn clear_coverage_in_transaction(
        connection: &mut SqliteConnection,
        wing_id: &str,
        shift_key: &str,
        now: Instante,
    ) -> Result<WingCoverage, CoberturaError>;

    fn get_coverage(
        connection: &mut SqliteConnection,
        wing_id: &str,
        at: &Instante,
    ) -> Result<Vec<WingCoverage>, CoberturaError>;

    fn list_coverages(
        connection: &mut SqliteConnection,
        wing_id: &str,
    ) -> Result<Vec<WingCoverage>, CoberturaError>;
}
