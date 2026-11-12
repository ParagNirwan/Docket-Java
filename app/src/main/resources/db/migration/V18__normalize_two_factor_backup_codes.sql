-- Earlier versions stored backup codes as comma-delimited strings despite the JSONB column.
UPDATE users
SET two_factor_backup_codes = to_jsonb(string_to_array(
        trim(both '"' from two_factor_backup_codes::text),
        ','))
WHERE jsonb_typeof(two_factor_backup_codes) = 'string';

UPDATE users
SET two_factor_backup_codes = '[]'::jsonb
WHERE two_factor_backup_codes IS NULL;

ALTER TABLE users
    ALTER COLUMN two_factor_backup_codes SET DEFAULT '[]'::jsonb,
    ALTER COLUMN two_factor_backup_codes SET NOT NULL;
