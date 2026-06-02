ALTER TABLE accounts ADD COLUMN IF NOT EXISTS minecraft_uuid VARCHAR(36);
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_minecraft_uuid ON accounts (minecraft_uuid);
