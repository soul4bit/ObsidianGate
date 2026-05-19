CREATE TABLE accounts (
    id UUID NOT NULL,
    username VARCHAR(32) NOT NULL,
    username_normalized VARCHAR(32) NOT NULL,
    email VARCHAR(255) NOT NULL,
    email_normalized VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_accounts_username_normalized ON accounts (username_normalized);
CREATE UNIQUE INDEX ux_accounts_email_normalized ON accounts (email_normalized);
CREATE UNIQUE INDEX ux_accounts_username ON accounts (username);
CREATE UNIQUE INDEX ux_accounts_email ON accounts (email);

CREATE TABLE launcher_sessions (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    device_name VARCHAR(128),
    user_agent VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_launcher_sessions PRIMARY KEY (id),
    CONSTRAINT fk_launcher_sessions_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE UNIQUE INDEX ux_launcher_sessions_refresh_token_hash ON launcher_sessions (refresh_token_hash);
CREATE INDEX ix_launcher_sessions_account_id ON launcher_sessions (account_id);

CREATE TABLE game_tickets (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    username VARCHAR(32) NOT NULL,
    uuid VARCHAR(36) NOT NULL,
    ticket_hash VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    server_id VARCHAR(64),
    CONSTRAINT pk_game_tickets PRIMARY KEY (id),
    CONSTRAINT fk_game_tickets_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE UNIQUE INDEX ux_game_tickets_ticket_hash ON game_tickets (ticket_hash);
CREATE INDEX ix_game_tickets_account_id ON game_tickets (account_id);
CREATE INDEX ix_game_tickets_expires_at ON game_tickets (expires_at);
