-- El nivel de riesgo es el dato que elige el preset base del perfil. Sin el, el
-- catalogo se puede servir pero no se puede evaluar: era la razon de fondo por
-- la que el hub guardaba politica y no producia una sola alerta.
ALTER TABLE alarm_profile_versions
    ADD COLUMN risk_level TEXT NOT NULL DEFAULT 'medium';

-- `mode` era standard | enhanced | intensive, que es otro eje: describia cuanto
-- se vigila, no de donde salen las reglas. El contrato del cliente pide
-- preset | custom —si el perfil sigue el preset o lleva ajustes propios— y la
-- forma wire la manda el contrato.
UPDATE alarm_profile_versions
   SET mode = CASE WHEN mode = 'standard' THEN 'preset' ELSE 'custom' END;
