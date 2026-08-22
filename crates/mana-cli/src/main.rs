use std::{env, process};

use cli::{CliError, Options, COMMANDS};
use mana_sdk::{ApiResponse, ManaClient, ManaError};

mod cli;
mod commands;
mod output;
mod render;
mod session;

#[tokio::main]
async fn main() {
    if let Err(error) = run().await {
        eprintln!("Error: {error}");
        process::exit(1);
    }
}

async fn run() -> Result<(), CliError> {
    let arguments = env::args().skip(1).collect::<Vec<_>>();
    if arguments.len() < 2 {
        return Err(CliError::Usage(cli::usage()));
    }
    let options = Options::parse(&arguments[2..], COMMANDS)?;
    let options = options.with_verb(&arguments[1]);
    commands::dispatch(&arguments[0], options).await
}

pub(crate) fn authenticated_client(options: &Options) -> Result<ManaClient, CliError> {
    let mut client = options.client()?;
    if let Some(token) = options.token()? {
        client.set_token(token)?;
    }
    Ok(client)
}

pub(crate) fn unauthenticated_client(options: &Options) -> Result<ManaClient, CliError> {
    options.client()
}

pub(crate) fn response_data<T>(response: ApiResponse<T>) -> Result<T, CliError> {
    response
        .data
        .ok_or(ManaError::MissingResponseBody {
            status: response.status,
        })
        .map_err(CliError::from)
}
