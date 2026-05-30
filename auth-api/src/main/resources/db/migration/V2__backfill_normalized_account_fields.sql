ALTER TABLE accounts ADD COLUMN IF NOT EXISTS username_normalized VARCHAR(32);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS email_normalized VARCHAR(255);

UPDATE accounts
SET username_normalized = lower(username)
WHERE username_normalized IS NULL;

UPDATE accounts
SET email_normalized = lower(email)
WHERE email_normalized IS NULL;

ALTER TABLE accounts ALTER COLUMN username_normalized SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN email_normalized SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_username_normalized ON accounts (username_normalized);
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_email_normalized ON accounts (email_normalized);
CREATE UNIQUE INDEX IF NOT EXISTS ux_launcher_sessions_refresh_token_hash ON launcher_sessions (refresh_token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS ux_game_tickets_ticket_hash ON game_tickets (ticket_hash);
CREATE INDEX IF NOT EXISTS ix_launcher_sessions_account_id ON launcher_sessions (account_id);
CREATE INDEX IF NOT EXISTS ix_game_tickets_account_id ON game_tickets (account_id);
CREATE INDEX IF NOT EXISTS ix_game_tickets_expires_at ON game_tickets (expires_at);
