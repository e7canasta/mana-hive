> "Un sensor detecta movimiento en la cama. El Scene Engine traduce eso en un hecho: 'el residente se levantó'. El Sentinel juzga: 'según la política clínica de este residente, eso es un incidente'. Harbor notifica a la enfermera. El Hub registra todo para que mañana el auditor pueda preguntar '¿por qué sonó la alarma a las 3:00?' y el sistema responda con cada paso de razonamiento."



```
Start: DigitalTwin at Lying, stateSince=03:00:00ZEvents:

1. t=0s (03:00:00): OBS IN_BED conf=0.95 → IN_BED maps to PersonState.Lying, which matches the current state, so this gets discarded as a duplicate. The stateSince is 03:00:00 and now is 03:00:00, so the hysteresis check passes.

2. t=2s (03:00:02): OBS BED_EDGE conf=0.92 → BED_EDGE maps to PersonState.BedEdge. The hysteresis requirement is 1.5s, and2s has elapsed since the last state change, so this transition is accepted.

3. t=4s (03:00:04): OBS STANDING conf=0.90 → STANDING maps to PersonState.Standing. The hysteresis for BED_EDGE→STANDING is 1.2s, and 2s has passed, so this is accepted.

4. t=4m0s (03:04:00): OBS STANDING conf=0.95 → This is a duplicate since we're already in Standing state, so it's discarded.

5. t=6s (03:00:06): OBS IN_HALLWAY conf=0.88 → IN_HALLWAY maps to PersonState.InHallway. The hysteresis for STANDING→IN_HALLWAY is 2s, and we've waited long enough, so this transition is accepted.

6. t=8s (03:00:08): OBS OUTDOOR conf=0.85 → OUTDOOR maps to PersonState.Outdoor. The hysteresis for IN_HALLWAY→OUTDOOR is 2s, and 2s has passed exactly, so this transition is accepted.
```