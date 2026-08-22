use mana_sdk::PlanogramPlacement;

const WIDTH: usize = 64;
const HEIGHT: usize = 20;
const LABEL_WIDTH: usize = 4;

pub fn render(placements: &[PlanogramPlacement]) -> String {
    let mut cells: Vec<Vec<Option<String>>> = vec![vec![None; WIDTH]; HEIGHT];
    let mut overlapped = 0;
    for placement in placements {
        let x = placement
            .x
            .clamp(0.0, 1.0)
            .mul_add((WIDTH - 1) as f64, 0.5)
            .floor() as usize;
        let y = placement
            .y
            .clamp(0.0, 1.0)
            .mul_add((HEIGHT - 1) as f64, 0.5)
            .floor() as usize;
        let label = truncate(&placement.room_number);
        if cells[y][x].is_some() {
            cells[y][x] = Some("*".to_owned());
            overlapped += 1;
        } else {
            cells[y][x] = Some(label);
        }
    }

    let mut lines = Vec::with_capacity(HEIGHT);
    for row in cells {
        let mut line = String::with_capacity(WIDTH);
        for cell in row {
            match cell {
                Some(label) => line.push_str(&format!("{label:<LABEL_WIDTH$}")),
                None => line.push_str(&" ".repeat(LABEL_WIDTH)),
            }
        }
        lines.push(line.trim_end().to_owned());
    }

    let mut out = String::new();
    out.push_str(&format!(
        "Planograma {}x{} (x,y normalizados 0..1)\n",
        WIDTH, HEIGHT
    ));
    out.push_str(&lines.join("\n"));
    out.push('\n');
    let mut legend = placements
        .iter()
        .map(|placement| {
            format!(
                "{}={}",
                truncate(&placement.room_number),
                placement.room_number
            )
        })
        .collect::<Vec<_>>();
    legend.sort();
    legend.dedup();
    out.push_str(&format!("Leyenda: {}\n", legend.join(" ")));
    if overlapped > 0 {
        out.push_str(&format!(
            "Aviso: {overlapped} habitacion(es) se superponen (marcadas con *)\n"
        ));
    }
    out
}

fn truncate(value: &str) -> String {
    let mut label: String = value.chars().take(LABEL_WIDTH).collect();
    while label.chars().count() < LABEL_WIDTH {
        label.push(' ');
    }
    label
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn renders_placements_with_legend() {
        let placements = vec![PlanogramPlacement {
            id: "p-1".to_owned(),
            wing_id: "w-1".to_owned(),
            room_id: "r-1".to_owned(),
            x: 0.0,
            y: 0.0,
            sort_order: 1,
            room_number: "101".to_owned(),
            room_type: "habitacion".to_owned(),
            stream_key: None,
        }];
        let out = render(&placements);
        assert!(out.contains("101"));
        assert!(out.contains("Leyenda"));
    }

    #[test]
    fn marks_overlapping_placements() {
        let make = |x: f64, y: f64, number: &str| PlanogramPlacement {
            id: format!("p-{number}"),
            wing_id: "w-1".to_owned(),
            room_id: format!("r-{number}"),
            x,
            y,
            sort_order: 1,
            room_number: number.to_owned(),
            room_type: "habitacion".to_owned(),
            stream_key: None,
        };
        let out = render(&[make(0.5, 0.5, "101"), make(0.5, 0.5, "102")]);
        assert!(out.contains('*'));
    }
}
