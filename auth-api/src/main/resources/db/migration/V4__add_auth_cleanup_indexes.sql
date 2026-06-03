CREATE INDEX IF NOT EXISTS ix_launcher_sessions_expires_at ON launcher_sessions (expires_at);
CREATE INDEX IF NOT EXISTS ix_launcher_sessions_revoked_at ON launcher_sessions (revoked_at);
CREATE INDEX IF NOT EXISTS ix_game_tickets_used_at ON game_tickets (used_at);
