use mana_sdk::{Scene, SceneRunner};

use crate::cli::CliError;

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "validate" => {
            let file = options.required("file")?;
            let scene = Scene::from_file(file)?;
            scene.validate()?;
            println!("Escena valida: {}", scene.meta.id);
            Ok(())
        }
        "load" => {
            let file = options.required("file")?;
            let scene = Scene::from_file(file)?;
            scene.validate()?;
            let mut runner = SceneRunner::new(options.client()?);
            let report = runner.run(&scene).await?;
            println!("{}", serde_json::to_string_pretty(&report)?);
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para scene: {}\n\n{}",
            options.verb(),
            crate::cli::usage()
        ))),
    }
}
