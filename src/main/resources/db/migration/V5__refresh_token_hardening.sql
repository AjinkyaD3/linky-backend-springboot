-- Refresh tokens: store only a SHA-256 hash, and remember the previous token of each session so a
-- replayed (already rotated-out) token can be detected and the whole session revoked.

ALTER TABLE refresh_tokens ADD COLUMN previous_token varchar(64);
ALTER TABLE refresh_tokens ADD COLUMN rotated_at timestamp(6);

-- Hash the tokens that already exist so current sessions keep working after the code change.
-- (sha256(bytea) is built in from PostgreSQL 11; the app compares against hex(sha256(token)).)
UPDATE refresh_tokens SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');
