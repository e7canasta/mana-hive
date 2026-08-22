use std::{
    fs,
    path::{Path, PathBuf},
};

use thiserror::Error;

#[derive(Debug, Error)]
pub enum SessionError {
    #[error("no se encontro HOME para guardar la sesion")]
    NoHome,
    #[error("no se pudo escribir el token: {0}")]
    Write(std::io::Error),
    #[error("no se pudo leer el token: {0}")]
    Read(std::io::Error),
    #[error("no se pudo fijar permisos del token: {0}")]
    Permissions(std::io::Error),
}

fn token_path() -> Result<PathBuf, SessionError> {
    if let Ok(override_path) = std::env::var("MANA_TOKEN_FILE") {
        if !override_path.trim().is_empty() {
            return Ok(PathBuf::from(override_path));
        }
    }
    let home = std::env::var_os("HOME").ok_or(SessionError::NoHome)?;
    Ok(PathBuf::from(home).join(".mana").join("token"))
}

pub fn read_token() -> Result<Option<String>, SessionError> {
    let path = token_path()?;
    match fs::read_to_string(&path) {
        Ok(contents) => {
            let token = contents.trim().to_owned();
            Ok(if token.is_empty() { None } else { Some(token) })
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(SessionError::Read(error)),
    }
}

pub fn store_token(token: &str) -> Result<(), SessionError> {
    let path = token_path()?;
    let parent = path
        .parent()
        .ok_or_else(|| SessionError::Write(std::io::Error::other("sin directorio padre")))?;
    fs::create_dir_all(parent).map_err(SessionError::Write)?;
    fs::write(&path, token).map_err(SessionError::Write)?;
    set_private(&path)?;
    Ok(())
}

pub fn delete_token() -> Result<(), SessionError> {
    let path = token_path()?;
    match fs::remove_file(&path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(SessionError::Write(error)),
    }
}

#[cfg(unix)]
fn set_private(path: &Path) -> Result<(), SessionError> {
    use std::os::unix::fs::PermissionsExt;

    fs::set_permissions(path, fs::Permissions::from_mode(0o600)).map_err(SessionError::Permissions)
}

#[cfg(not(unix))]
fn set_private(_path: &Path) -> Result<(), SessionError> {
    Ok(())
}
