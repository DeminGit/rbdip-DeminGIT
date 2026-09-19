ALTER TABLE customers ADD COLUMN first_name VARCHAR(255);
ALTER TABLE customers ADD COLUMN last_name VARCHAR(255);

-- Split at the first space; preserve the remaining name verbatim, including spaces.
CREATE FUNCTION sync_customer_name() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    separator integer;
BEGIN
    IF NEW.first_name IS NULL OR (TG_OP = 'UPDATE' AND
        NEW.customer_full_name IS DISTINCT FROM OLD.customer_full_name) THEN
        separator := strpos(NEW.customer_full_name, ' ');
        IF separator = length(NEW.customer_full_name) THEN
            separator := 0;
        END IF;
        NEW.first_name := CASE WHEN separator = 0 THEN NEW.customer_full_name
                              ELSE left(NEW.customer_full_name, separator - 1) END;
        NEW.last_name := CASE WHEN separator = 0 THEN ''
                             ELSE substring(NEW.customer_full_name FROM separator + 1) END;
    ELSE
        NEW.customer_full_name := NEW.first_name ||
            CASE WHEN NEW.last_name = '' THEN '' ELSE ' ' || NEW.last_name END;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER customer_name_compat BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION sync_customer_name();

UPDATE customers SET customer_full_name = customer_full_name;
ALTER TABLE customers ADD CONSTRAINT customer_names_present
    CHECK (first_name IS NOT NULL AND last_name IS NOT NULL) NOT VALID;
ALTER TABLE customers ADD CONSTRAINT customer_name_contact_unique
    UNIQUE NULLS NOT DISTINCT (first_name, last_name, address, phone);
