package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class NormalizationMigrationTest {
    @Test
    void preservesHistoricalDataAndAcceptsLegacyWrites() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(postgres.getJdbcUrl());
            source.setUser(postgres.getUsername());
            source.setPassword(postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            Flyway.configure().dataSource(source).target("1").load().migrate();
            jdbc.execute("INSERT INTO products (name, price) VALUES ('Book', 10)");
            jdbc.execute("INSERT INTO orders (customer_full_name, customer_address) VALUES ('Old Customer', 'Home')");
            jdbc.execute("INSERT INTO orders (customer_full_name, customer_address) VALUES ('Old Customer', 'Home')");
            jdbc.execute("INSERT INTO order_items (order_id, product_name, product_price) VALUES (1, 'Book', 10)");
            jdbc.execute("INSERT INTO order_items (order_id, product_name, product_price) VALUES (2, 'Deleted', 12)");

            Flyway.configure().dataSource(source).target("2").load().migrate();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id IS NOT NULL",
                    Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE product_id IS NOT NULL",
                    Integer.class)).isEqualTo(2);
            jdbc.execute("INSERT INTO orders (customer_full_name, customer_address) VALUES ('Old Customer', 'Home')");
            jdbc.execute("INSERT INTO order_items (order_id, product_name, product_price) VALUES (3, 'Book', 10)");
            assertThat(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = 3", Long.class))
                    .isEqualTo(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = 1", Long.class));
            assertThat(jdbc.queryForObject("SELECT product_id FROM order_items WHERE order_id = 3", Long.class))
                    .isEqualTo(1L);
        }
    }
}
