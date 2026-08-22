CREATE UNIQUE INDEX residents_open_assignment_idx
    ON resident_bed_assignments (resident_id)
    WHERE ends_at IS NULL;

CREATE UNIQUE INDEX beds_open_assignment_idx
    ON resident_bed_assignments (bed_id)
    WHERE ends_at IS NULL;
