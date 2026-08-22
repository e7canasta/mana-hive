# Motor de alarmas

## Objetivo de producto

Convertir una observación del detector en una alerta explicable, sin duplicarla
cuando el detector repite el mismo estado.

## Ubicacion

- Motor puro: `crates/mana-motores/src/alarmas.rs`.
- Lazo con IO: `crates/mana-app/src/motor/lazo.rs`.
- Persistencia: `crates/ctx-vigilancia/`.
- Evidencia y estado de cama: `crates/mana-observation/`.

## API

```text
evaluar(contexto, perfil_efectivo, ya_avisadas, disparo)
    -> Vec<AlertaNueva>
```

El motor recibe:

- estado actual y anterior;
- `state_since`;
- turno local;
- perfil efectivo;
- reglas ya avisadas durante el episodio;
- si la evaluación la disparó un evento o un barrido.

Devuelve decisiones. No crea filas ni conoce `AlertInput`.

## Reglas de producto

- Una transición se evalúa cuando ocurre.
- Una permanencia se evalúa cuando vence el tiempo.
- Un estado desconocido no equivale a estar fuera de la cama.
- Una cama sin residente continúa vigilada con la política fija.
- Una regla ya avisada en el episodio no vuelve a sonar.
- La evidencia de una permanencia es una ventana, no el último evento.

## Ejemplo de producto

```text
estado anterior: acostado
estado actual: de pie
turno: noche
regla: bed_exit
acción: alarm
=> crear alerta high con evidencia del evento
```

## Escena

`motores-alarmas-blueprint.json` prueba el flujo real:

1. crear cama y residente;
2. configurar nivel alto;
3. ingerir estado acostado;
4. ingerir salida de cama;
5. verificar que nace una alerta `bed_exit` de nivel alto;
6. repetir el estado y verificar que no nace otra alerta.
