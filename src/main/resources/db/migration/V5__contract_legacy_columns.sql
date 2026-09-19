-- Apply only after every application instance reads/writes the normalized schema.
DROP TRIGGER order_customer_compat ON orders;
DROP TRIGGER order_product_compat ON order_items;
DROP TRIGGER customer_name_compat ON customers;
DROP FUNCTION sync_order_customer();
DROP FUNCTION sync_order_product();
DROP FUNCTION sync_customer_name();
ALTER TABLE orders DROP COLUMN customer_full_name;
ALTER TABLE orders DROP COLUMN customer_address;
ALTER TABLE orders DROP COLUMN customer_phone;
ALTER TABLE order_items DROP COLUMN product_name;
ALTER TABLE order_items DROP COLUMN product_price;
ALTER TABLE customers DROP COLUMN customer_full_name;
