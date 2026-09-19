package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderQueryTest extends AbstractIntegrationTest {
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void fetchesDifferentCustomersAndProductsInOneQueryWithoutDroppingEmptyOrders() {
        Long first = product("First book");
        Long second = product("Second book");
        for (int i = 0; i < 12; i++) {
            CreateOrderRequest request = new CreateOrderRequest("Customer " + i, "Home", null,
                    "regular", null, List.of(new CreateOrderRequest.Item(first, 1),
                    new CreateOrderRequest.Item(second, 2)));
            assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode().value())
                    .isEqualTo(201);
        }
        jdbcTemplate.execute("INSERT INTO orders (customer_id) SELECT customer_id FROM orders LIMIT 1");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var response = restTemplate.getForEntity("/orders", List.class);
        long queryCount = statistics.getPrepareStatementCount();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<?> orders = response.getBody();
        assertThat(orders).hasSize(13);
        assertThat(queryCount).isEqualTo(1);
        assertThat(orders.stream().filter(o -> ((List<?>) ((Map<?, ?>) o).get("items")).size() == 2).count())
                .isEqualTo(12);
        assertThat(orders.stream().filter(o -> ((List<?>) ((Map<?, ?>) o).get("items")).isEmpty()).count())
                .isEqualTo(1);
        Map<?, ?> firstOrder = (Map<?, ?>) orders.get(0);
        assertThat(((List<?>) firstOrder.get("items")).stream()
                .map(item -> (String) ((Map<?, ?>) item).get("productName")))
                .containsExactlyInAnyOrder("First book", "Second book");
    }

    private Long product(String name) {
        Map<?, ?> response = restTemplate.postForObject("/products", Map.of("name", name, "price", 10), Map.class);
        return ((Number) response.get("id")).longValue();
    }
}
