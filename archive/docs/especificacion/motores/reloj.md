# Motor de reloj

## Objetivo de producto

Disparar permanencias, confirmaciones y escalamiento aunque no llegue un evento
nuevo del detector.

## Estado

Especificado, todavía no implementado. La evaluación pura ya recibe `Disparo::Barrido`;
falta el scheduler durable que invoque ese camino.

## Diseño

```text
ingesta o creación de alerta
        -> timer durable en SQLite
        -> claim transaccional
        -> mana-app hidrata el contexto
        -> mana-motores evalúa
        -> Vigilancia persiste la alerta o escalamiento
```

El scheduler recomendado es una tarea de Tokio sobre una tabla `timers` con
`fire_at`, `kind`, `payload_json` y `claimed_at`.

## Reglas de producto

- reiniciar el hub no debe perder una permanencia en curso;
- dos ejecutores no deben disparar dos veces el mismo timer;
- un timer atrasado se ejecuta y se mide el retraso;
- una transición vieja no se reinterpreta como nueva;
- el escalamiento se cancela cuando la alerta recibe acuse.

## Escena futura

Configurar una permanencia de dos minutos, ingerir una salida de cama, avanzar el
reloj sin enviar otro evento y verificar que aparece la alerta. Después avanzar
otra vez sin acuse y verificar el escalamiento.
