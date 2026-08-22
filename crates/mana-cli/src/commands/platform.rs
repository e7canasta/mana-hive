use mana_sdk::health;

use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "health" => {
            let response = health(&options.client()?).await?;
            output::print_json(&response.data)?;
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para api: {}\n\n{}",
            options.verb(),
            crate::cli::usage()
        ))),
    }
}
