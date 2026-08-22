pub mod planogram;
pub mod politica;

use serde_json::Value;

pub enum OutputFormat {
    Pretty,
    Json,
}

impl OutputFormat {
    pub fn from_str(s: Option<&str>) -> Self {
        match s {
            Some("json") => OutputFormat::Json,
            _ => OutputFormat::Pretty,
        }
    }
}

pub fn print_or_render(format: OutputFormat, data: &Value, renderer: impl FnOnce(&Value)) {
    match format {
        OutputFormat::Json => {
            println!("{}", serde_json::to_string_pretty(data).unwrap());
        }
        OutputFormat::Pretty => {
            renderer(data);
        }
    }
}
