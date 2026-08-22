# Motor de recomendacion

## Objetivo de producto

Ayudar al equipo a revisar si el nivel de vigilancia configurado sigue siendo
razonable con la evidencia observada, sin convertir el resultado en diagnóstico.

## Ubicacion

- Motor puro: `crates/mana-motores/src/recomendacion/`.
- Hidratación de señales: `crates/mana-app/src/recomendacion.rs`.
- Política como dato: `[recomendacion]` en `config/alarm-catalog.toml`.
- Vista y contrato: `crates/mana-app/src/politica.rs` y `packages/contracts`.

## API

```text
recomendar(
    senales,
    accesorio,
    rasgos,
    politica,
    templates,
) -> Recomendacion
```

La entrada ya está completa. El motor no busca residentes, camas, incidentes ni
alertas.

La salida contiene:

- `level`;
- `score`;
- `factors` con peso y explicación;
- `signals_evaluated`;
- `suggested_template`.

## Regla clínica de datos faltantes

`None` significa no observado. No se transforma en cero.

Un residente nuevo puede devolver nivel bajo con `signals_evaluated = 0`. Eso no
significa que esté bien: significa que todavía no hay evidencia suficiente.

## Ejemplo de producto

```text
3.2 salidas de cama por noche       +2
0.45 m/s de velocidad de marcha     +2
riesgo de caída declarado            +1
andador declarado                    +1
---------------------------------------
puntaje                               6
nivel sugerido                       alto
```

La UI puede mostrar los factores y el equipo puede aceptar o ignorar la sugerencia.

## Escenarios puros

Los casos están en `mana-motores/src/recomendacion/tests.rs` y cubren:

- ausencia de evidencia;
- marcha no observada;
- silla de ruedas sin penalización de marcha;
- bandas de puntaje;
- incidentes graves;
- alertas emitidas;
- acumulación de factores;
- plantilla sugerida.

La escena `politica-blueprint` verifica el envelope HTTP junto con el catalogo,
el perfil efectivo y la recomendacion por residente.
