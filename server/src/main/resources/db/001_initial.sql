CREATE TABLE accounts (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(512) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL
);
CREATE TABLE account_actions (
    token_hash VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL,
    expires_at BIGINT NOT NULL
);
CREATE INDEX account_actions_account ON account_actions(account_id);
CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    family_id VARCHAR(36) NOT NULL,
    access_hash VARCHAR(64) NOT NULL UNIQUE,
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    access_expires BIGINT NOT NULL,
    refresh_expires BIGINT NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX sessions_account ON sessions(account_id);
CREATE INDEX sessions_family ON sessions(family_id);
CREATE TABLE boards (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE INDEX boards_owner ON boards(owner_id);
CREATE TABLE board_members (
    board_id VARCHAR(36) NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    account_id VARCHAR(36) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('EDITOR', 'COMMENTER', 'VIEWER')),
    PRIMARY KEY (board_id, account_id)
);
CREATE INDEX board_members_account ON board_members(account_id);
CREATE TABLE board_invites (
    token_hash VARCHAR(64) PRIMARY KEY,
    board_id VARCHAR(36) NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('EDITOR', 'COMMENTER', 'VIEWER')),
    expires_at BIGINT NOT NULL
);
CREATE INDEX board_invites_board ON board_invites(board_id);
CREATE TABLE board_commits (
    board_id VARCHAR(36) NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    request_hash VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (board_id, operation_id)
);
