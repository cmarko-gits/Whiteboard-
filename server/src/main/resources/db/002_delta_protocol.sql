ALTER TABLE boards ADD COLUMN payload_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE boards ADD COLUMN history_bytes BIGINT NOT NULL DEFAULT 0;
UPDATE boards SET payload_bytes = OCTET_LENGTH(payload);
ALTER TABLE board_commits RENAME TO legacy_board_commits;
CREATE TABLE board_commits (
    board_id VARCHAR(36) NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) REFERENCES accounts(id) ON DELETE SET NULL,
    request_hash VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL,
    payload TEXT,
    created_at BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (board_id, operation_id)
);
INSERT INTO board_commits (board_id, operation_id, account_id, request_hash, revision)
    SELECT board_id, operation_id, account_id, request_hash, revision FROM legacy_board_commits;
DROP TABLE legacy_board_commits;
CREATE UNIQUE INDEX board_commits_revision ON board_commits(board_id, revision);
CREATE INDEX board_commits_account ON board_commits(account_id);
ALTER TABLE board_members ADD COLUMN granted_revision BIGINT NOT NULL DEFAULT 0;
UPDATE board_members SET granted_revision = (SELECT revision FROM boards WHERE boards.id = board_members.board_id);
CREATE TABLE account_quotas (
    account_id VARCHAR(36) PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE
);
INSERT INTO account_quotas (account_id) SELECT id FROM accounts;
CREATE TABLE board_tombstones (
    board_id VARCHAR(36) NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    kind VARCHAR(8) NOT NULL CHECK (kind IN ('PAGE', 'OBJECT')),
    page_id VARCHAR(128) NOT NULL,
    object_id VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL,
    account_id VARCHAR(36) REFERENCES accounts(id) ON DELETE SET NULL,
    operation_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (board_id, kind, page_id, object_id)
);
CREATE TABLE board_creations (
    account_id VARCHAR(36) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    board_id VARCHAR(36) REFERENCES boards(id) ON DELETE SET NULL,
    request_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (account_id, operation_id)
);
