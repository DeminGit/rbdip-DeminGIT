package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.BookstoreApplication;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MigrationHttpLoadTest {
    @Test
    void apiStaysAvailableWhileValidatedSchemaIsContracted() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(postgres.getJdbcUrl());
            source.setUser(postgres.getUsername());
            source.setPassword(postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            Flyway.configure().dataSource(source).target("1").load().migrate();
            jdbc.execute("INSERT INTO orders (customer_full_name, customer_address) VALUES ('Anna van der Meer', 'Home')");
            Flyway.configure().dataSource(source).target("3").load().migrate();
            // Legacy writers still work on the expanded schema, including updates.
            jdbc.execute("INSERT INTO orders (customer_full_name, customer_address) VALUES ('Prince', 'Home')");
            jdbc.execute("UPDATE orders SET customer_full_name = 'Updated Customer' WHERE id = 2");
            assertThat(jdbc.queryForObject("SELECT first_name FROM customers WHERE id = "
                    + "(SELECT customer_id FROM orders WHERE id = 2)", String.class)).isEqualTo("Updated");

            try (var context = new SpringApplicationBuilder(BookstoreApplication.class).run(
                    "--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.flyway.target=3", "--spring.jpa.properties.hibernate.generate_statistics=false",
                    "--spring.datasource.hikari.maximum-pool-size=2", "--spring.datasource.hikari.minimum-idle=2",
                    "--spring.datasource.hikari.data-source-properties.prepareThreshold=1")) {
                int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
                RestTemplate client = new RestTemplateBuilder().rootUri("http://localhost:" + port)
                        .setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(5)).build();
                exerciseDuringMigration(client, source);
            }
            assertThat(jdbc.queryForObject("SELECT first_name || ' ' || last_name FROM customers "
                    + "WHERE id = (SELECT customer_id FROM orders WHERE id = 1)", String.class))
                    .isEqualTo("Anna van der Meer");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'orders' AND column_name = 'customer_full_name'", Integer.class)).isZero();
        }
    }

    private void exerciseDuringMigration(RestTemplate client, PGSimpleDataSource source) throws Exception {
        Map<?, ?> product = client.postForObject("/products", Map.of("name", "Book", "price", 10), Map.class);
        Long id = ((Number) product.get("id")).longValue();
        CreateOrderRequest request = new CreateOrderRequest("Load Customer", "Home", null, "regular", null,
                List.of(new CreateOrderRequest.Item(id, 1)));
        // Warm server-side prepared statements before changing the schema.
        for (int i = 0; i < 6; i++) {
            assertThat(client.postForEntity("/orders", request, Map.class).getStatusCode().value()).isEqualTo(201);
        }
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger requests = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch afterMigration = new CountDownLatch(3);
        AtomicBoolean migrated = new AtomicBoolean();
        var pool = Executors.newFixedThreadPool(3);
        try {
            for (int i = 0; i < 3; i++) {
                pool.submit(() -> {
                    try {
                        while (running.get()) {
                            assertThat(client.postForEntity("/orders", request, Map.class).getStatusCode().value())
                                    .isEqualTo(201);
                            List<?> orders = client.getForObject("/orders", List.class);
                            assertThat(orders).isNotEmpty();
                            assertThat(orders.stream().map(o -> (String) ((Map<?, ?>) o).get("customerFullName")))
                                    .contains("Load Customer");
                            requests.incrementAndGet();
                            started.countDown();
                            if (migrated.get()) {
                                afterMigration.countDown();
                            }
                        }
                    } catch (Throwable failure) {
                        failures.add(failure);
                    }
                });
            }
            assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
            Flyway.configure().dataSource(source).load().migrate();
            migrated.set(true);
            assertThat(afterMigration.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            running.set(false);
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(failures).isEmpty();
        assertThat(requests.get()).isGreaterThanOrEqualTo(6);
    }
}
