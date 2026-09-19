package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NormalizedOrderTest extends AbstractIntegrationTest {
    @Test
    void reusesCustomerAndReferencesPurchasedProduct() {
        Map<?, ?> product = restTemplate.postForObject("/products",
                Map.of("name", "Refactoring", "price", 40), Map.class);
        Long productId = ((Number) product.get("id")).longValue();
        CreateOrderRequest request = new CreateOrderRequest("Shared Customer", "Address", null,
                "regular", null, List.of(new CreateOrderRequest.Item(productId, null)));
        for (int i = 0; i < 2; i++) {
            assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT customer_id) FROM orders", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT product_id FROM order_items", Long.class))
                .containsExactly(productId, productId);
        assertThat(jdbcTemplate.queryForList("SELECT quantity FROM order_items", Integer.class))
                .containsExactly(1, 1);
    }

    @Test
    void invalidRequestsDoNotPersistOrders() {
        List<CreateOrderRequest> invalid = List.of(
                new CreateOrderRequest(null, "Address", null, null, null, List.of()),
                new CreateOrderRequest(" ", "Address", null, null, null, List.of()),
                new CreateOrderRequest("Name", null, null, null, null, List.of()),
                new CreateOrderRequest("Name", " ", null, null, null, List.of()),
                new CreateOrderRequest("Name", "Address", null, null, null, null),
                new CreateOrderRequest("Name", "Address", null, null, null,
                        List.of(new CreateOrderRequest.Item(999L, 0))),
                new CreateOrderRequest("Name", "Address", null, null, null,
                        List.of(new CreateOrderRequest.Item(999L, 1))));
        for (CreateOrderRequest request : invalid) {
            assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    }
}
