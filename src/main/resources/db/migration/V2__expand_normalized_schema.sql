-- Keep legacy columns until all consumers have switched (V5 contract).
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    customer_full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50),
    UNIQUE NULLS NOT DISTINCT (customer_full_name, address, phone)
);

INSERT INTO customers (customer_full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone FROM orders;

ALTER TABLE orders ADD COLUMN customer_id BIGINT REFERENCES customers(id);
UPDATE orders o SET customer_id = c.id FROM customers c
WHERE o.customer_full_name = c.customer_full_name
  AND o.customer_address IS NOT DISTINCT FROM c.address
  AND o.customer_phone IS NOT DISTINCT FROM c.phone;
ALTER TABLE orders ALTER COLUMN customer_id SET NOT NULL;
CREATE INDEX orders_customer_idx ON orders(customer_id);

-- V1 did not store product IDs: preserve unmatched historical snapshots as products.
INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price FROM order_items i
WHERE NOT EXISTS (SELECT 1 FROM products p
                  WHERE p.name = i.product_name AND p.price = i.product_price);
ALTER TABLE order_items ADD COLUMN product_id BIGINT REFERENCES products(id);
UPDATE order_items i SET product_id = (
    SELECT min(p.id) FROM products p WHERE p.name = i.product_name AND p.price = i.product_price
);
ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
CREATE INDEX order_items_order_idx ON order_items(order_id);
CREATE INDEX order_items_product_idx ON order_items(product_id);

CREATE FUNCTION sync_order_customer() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.customer_id IS NULL OR (TG_OP = 'UPDATE' AND
        (NEW.customer_full_name, NEW.customer_address, NEW.customer_phone) IS DISTINCT FROM
        (OLD.customer_full_name, OLD.customer_address, OLD.customer_phone)) THEN
        INSERT INTO customers (customer_full_name, address, phone)
        VALUES (NEW.customer_full_name, NEW.customer_address, NEW.customer_phone)
        ON CONFLICT (customer_full_name, address, phone)
        DO UPDATE SET customer_full_name = EXCLUDED.customer_full_name
        RETURNING id INTO NEW.customer_id;
    ELSE
        SELECT customer_full_name, address, phone
        INTO NEW.customer_full_name, NEW.customer_address, NEW.customer_phone
        FROM customers WHERE id = NEW.customer_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER order_customer_compat BEFORE INSERT OR UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION sync_order_customer();

CREATE FUNCTION sync_order_product() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.product_id IS NULL OR (TG_OP = 'UPDATE' AND
        (NEW.product_name, NEW.product_price) IS DISTINCT FROM (OLD.product_name, OLD.product_price)) THEN
        SELECT min(id) INTO NEW.product_id FROM products
        WHERE name = NEW.product_name AND price = NEW.product_price;
        IF NEW.product_id IS NULL THEN
            INSERT INTO products (name, price) VALUES (NEW.product_name, NEW.product_price)
            RETURNING id INTO NEW.product_id;
        END IF;
    ELSE
        SELECT name, price INTO NEW.product_name, NEW.product_price
        FROM products WHERE id = NEW.product_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER order_product_compat BEFORE INSERT OR UPDATE ON order_items
FOR EACH ROW EXECUTE FUNCTION sync_order_product();
