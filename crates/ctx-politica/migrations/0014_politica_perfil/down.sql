UPDATE alarm_profile_versions
   SET mode = CASE WHEN mode = 'preset' THEN 'standard' ELSE 'intensive' END;

ALTER TABLE alarm_profile_versions DROP COLUMN risk_level;
