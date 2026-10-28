ALTER TABLE audit_entries ADD COLUMN reason_code varchar(64);

UPDATE audit_entries a SET reason_code = CASE
    WHEN a.action = 'IDEMPOTENCY_CONFLICT' THEN 'IDEMPOTENCY_CONFLICT'
    ELSE COALESCE((SELECT r.reason_code FROM command_results r
                   WHERE r.actor_id = a.actor_id AND r.command_id = a.command_id
                     AND r.request_hash = a.request_hash), 'UNKNOWN')
END;

ALTER TABLE audit_entries ALTER COLUMN reason_code SET NOT NULL;
