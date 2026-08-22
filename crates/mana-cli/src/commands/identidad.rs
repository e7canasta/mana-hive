use crate::{cli::CliError, output, session};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "login" => {
            let username = options.required("username")?;
            let password = options.required("password")?;
            let mut client = options.client()?;
            let response = client.login(username, password).await?;
            let data = crate::response_data(response)?;
            let token = data.token.clone();
            if !options.has("no-store") {
                session::store_token(&token)?;
                eprintln!("token guardado en sesion (0600)");
            }
            output::print_json(&data)?;
            Ok(())
        }
        "logout" => {
            let mut client = crate::authenticated_client(options)?;
            let response = client.logout().await?;
            session::delete_token()?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "me" => {
            let client = crate::authenticated_client(options)?;
            let response = client.current_user().await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "usuarios" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_users().await?;
            let data = crate::response_data(response)?;
            let rows = data
                .users
                .iter()
                .map(|user| {
                    vec![
                        user.username.clone(),
                        user.display_name.clone(),
                        user.role.clone(),
                        user.job_title.clone().unwrap_or_else(|| "-".to_owned()),
                        if user.active != 0 { "si" } else { "no" }.to_owned(),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(
                &["username", "display_name", "role", "job_title", "active"],
                &rows,
            );
            Ok(())
        }
        "create-user" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .create_user(mana_sdk::CreateUserRequest {
                    username: options.required("username")?.to_owned(),
                    display_name: options.required("display-name")?.to_owned(),
                    role: options.required("role")?.to_owned(),
                    job_title: options.get("job-title").map(str::to_owned),
                    password: options.required("password")?.to_owned(),
                })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "update-user" => {
            let client = crate::authenticated_client(options)?;
            let user_id = options.required("user-id")?;
            let response = client
                .update_user(
                    user_id,
                    mana_sdk::UpdateUserRequest {
                        display_name: options.get("display-name").map(str::to_owned),
                        role: options.get("role").map(str::to_owned),
                        job_title: options
                            .get("job-title")
                            .map(|value| Some(value.to_owned())),
                        active: options
                            .get("active")
                            .and_then(|v| v.parse::<bool>().ok()),
                        password: options.get("password").map(str::to_owned),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para identidad: {}\n\n{}",
            options.verb(),
            crate::cli::usage()
        ))),
    }
}
