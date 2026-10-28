CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE resources (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(100) NOT NULL,
    type varchar(20) NOT NULL CHECK (type IN ('ROOM', 'EQUIPMENT')),
    capacity integer CHECK (capacity > 0),
    description text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE bookings (
    id uuid PRIMARY KEY,
    resource_id uuid NOT NULL REFERENCES resources(id),
    owner_id uuid NOT NULL,
    title varchar(100) NOT NULL CHECK (length(btrim(title)) > 0),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at timestamptz NOT NULL,
    cancelled_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (starts_at < ends_at),
    CHECK ((status = 'CONFIRMED' AND cancelled_at IS NULL) OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)),
    EXCLUDE USING gist (resource_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
        WHERE (status = 'CONFIRMED')
);
CREATE INDEX bookings_owner_start_id ON bookings (owner_id, starts_at, id);
CREATE INDEX bookings_resource_start ON bookings (resource_id, starts_at);
