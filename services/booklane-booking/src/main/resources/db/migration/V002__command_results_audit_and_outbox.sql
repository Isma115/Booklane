CREATE TABLE command_results (
    actor_id uuid NOT NULL,
    command_id uuid NOT NULL,
    request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    command_type varchar(40) NOT NULL,
    resource_id uuid NOT NULL,
    outcome varchar(20) CHECK (outcome IN ('SUCCEEDED', 'REJECTED')),
    reason_code varchar(64),
    booking_id uuid REFERENCES bookings(id),
    resource_version bigint CHECK (resource_version >= 0),
    canonical_request jsonb,
    already_cancelled boolean,
    processed_at timestamptz,
    PRIMARY KEY (actor_id, command_id)
);
CREATE TABLE audit_entries (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL,
    command_id uuid NOT NULL,
    request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    action varchar(40) NOT NULL,
    resource_id uuid NOT NULL,
    booking_id uuid REFERENCES bookings(id),
    outcome varchar(20) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'REJECTED')),
    reason varchar(500),
    occurred_at timestamptz NOT NULL,
    UNIQUE (actor_id, command_id, request_hash, action)
);
CREATE INDEX audit_occurred_id ON audit_entries (occurred_at, id);
CREATE INDEX audit_resource_occurred ON audit_entries (resource_id, occurred_at);
CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    sequence bigserial NOT NULL UNIQUE,
    aggregate_id uuid NOT NULL,
    event_type varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error varchar(1000)
);
CREATE INDEX outbox_pending_sequence ON outbox_events (sequence) WHERE published_at IS NULL;
