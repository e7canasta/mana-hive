pub mod auditoria;
pub mod cobertura;
pub mod cuidado;
pub mod escenas;
pub mod historia;
pub mod identidad;
pub mod observacion;
pub mod platform;
pub mod poblacion;
pub mod politica;
pub mod residencia;
pub mod streams;
pub mod vigilancia;

use crate::cli::{CliError, Options};

pub async fn dispatch(domain: &str, options: Options) -> Result<(), CliError> {
    match domain {
        "api" => platform::dispatch(&options).await,
        "identidad" | "identity" => identidad::dispatch(&options).await,
        "auditoria" => auditoria::dispatch(&options).await,
        "residencia" => residencia::dispatch(&options).await,
        "poblacion" => poblacion::dispatch(&options).await,
        "cobertura" => cobertura::dispatch(&options).await,
        "cuidado" => cuidado::dispatch(&options).await,
        "historia" => historia::dispatch(&options).await,
        "politica" => politica::dispatch(&options).await,
        "vigilancia" => vigilancia::dispatch(&options).await,
        "observacion" => observacion::dispatch(&options).await,
        "streams" => streams::dispatch(&options).await,
        "scene" => escenas::dispatch(&options).await,
        _ => Err(CliError::Usage(format!(
            "dominio desconocido: {domain}\n\n{}",
            crate::cli::usage()
        ))),
    }
}
