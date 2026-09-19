-- Runs in the migration transaction. Acquire every contract lock without waiting
-- while holding a partial lock set: otherwise DDL and an order transaction can
-- deadlock (the application writes customers before orders).
DO $$
DECLARE
    attempts integer := 0;
BEGIN
    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '4' AND success)
       AND NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '5' AND success) THEN
        LOOP
            BEGIN
                LOCK TABLE customers, orders, order_items IN ACCESS EXCLUSIVE MODE NOWAIT;
                EXIT;
            EXCEPTION WHEN lock_not_available THEN
                -- The exception subtransaction releases all partially acquired locks.
                attempts := attempts + 1;
                IF attempts >= 500 THEN
                    RAISE EXCEPTION 'Contract lock acquisition timed out; retry migration when traffic permits';
                END IF;
                PERFORM pg_sleep(0.01);
            END;
        END LOOP;
    END IF;
END;
$$;
