CREATE FUNCTION require_completed_command() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM booking.command_results
        WHERE actor_id = NEW.actor_id AND command_id = NEW.command_id
          AND (outcome IS NULL OR reason_code IS NULL OR processed_at IS NULL OR canonical_request IS NULL)
    ) THEN
        RAISE EXCEPTION 'command result must be completed before commit' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER completed_command_at_commit
AFTER INSERT OR UPDATE ON command_results
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_completed_command();
