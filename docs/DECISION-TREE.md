# Elección de Nivel — Decision Tree para el Director

## Flujo de Decisión

```
¿El residente se mueve bien?
    │
    ├── SÍ → ¿Se levanta de noche?
    │       │
    │       ├── SÍ → NIVEL 1: NIGHT-WANDERING
    │       │
    │       └── NO → ¿Tiene riesgo de caída?
    │               │
    │               ├── SÍ → NIVEL 2: FALL-RISK
    │               │
    │               └── NO → NIVEL 0: STANDARD
    │
    └── NO → ¿Es crítico?
            │
            ├── SÍ → NIVEL 3: CRITICAL
            │
            └── NO → NIVEL 2: FALL-RISK
```

## Ejemplos

| Residente | Nivel | Razón |
|-----------|-------|-------|
| María, 65, se mueve bien | STANDARD | Sin riesgo |
| José, 78, se levanta de noche | NIGHT-WANDERING | Riesgo nocturno |
| Pedro, 82, con andador | FALL-RISK | Riesgo de caída |
| Ana, 90, demencia severa | CRITICAL | Alto riesgo |
| Carlos, 75, post-operatorio | CRITICAL | Observación especial |

## Cambios de Nivel

El nivel puede cambiar en cualquier momento:
- **De día a noche**: STANDARD → NIGHT-WANDERING
- **Después de caída**: STANDARD → FALL-RISK
- **Post-operatorio**: cualquier nivel → CRITICAL
- **Recuperación**: CRITICAL → STANDARD

## Para el Director

> *"Dr. García, para cada residente usted elige un nivel:*
>
> *1. ¿Se mueve bien? Si no → FALL-RISK o CRITICAL*
> *2. ¿Se levanta de noche? Si sí → NIGHT-WANDERING*
> *3. ¿Tiene riesgo de caída? Si sí → FALL-RISK*
> *4. Si nada de esto aplica → STANDARD*
>
> *Si el residente cambia, usted cambia el nivel. No necesita configurar tiempos, solo el nivel."*
