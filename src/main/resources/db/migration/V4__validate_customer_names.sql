-- Separate validation from expansion; old and new application versions still work.
ALTER TABLE customers VALIDATE CONSTRAINT customer_names_present;
ALTER TABLE customers ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE customers ALTER COLUMN last_name SET NOT NULL;
