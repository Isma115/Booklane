#!/usr/bin/env bash
set -euo pipefail

if [[ ! "${BOOKLANE_DB_PASSWORD:-}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "PostgreSQL health check is missing its application credential." >&2
    exit 1
fi

PGPASSWORD="$BOOKLANE_DB_PASSWORD" psql -h 127.0.0.1 -U booklane_booking -d booklane \
    -tAc 'SELECT 1' >/dev/null
