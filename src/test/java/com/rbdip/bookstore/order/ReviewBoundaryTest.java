package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.purchase.PurchaseHistory;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewBoundaryTest extends AbstractIntegrationTest {
    @Autowired
    private PurchaseHistory purchaseHistory;

    @Test
    void purchaseContractMatchesAuthorAndProductAndReviewsKeepTheirExistingPolicy() {
        Map<?, ?> product = restTemplate.postForObject("/products", Map.of("name", "Book", "price", 10), Map.class);
        Long id = ((Number) product.get("id")).longValue();
        assertThat(purchaseHistory.hasPurchased(id, "Reader Name")).isFalse();
        CreateOrderRequest request = new CreateOrderRequest("Reader Name", "Home", null, "regular", null,
                List.of(new CreateOrderRequest.Item(id, 1)));
        assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode().value()).isEqualTo(201);
        assertThat(purchaseHistory.hasPurchased(id, "Reader Name")).isTrue();
        assertThat(purchaseHistory.hasPurchased(id, "Other Reader")).isFalse();
        assertThat(purchaseHistory.hasPurchased(id + 1, "Reader Name")).isFalse();
        assertThat(purchaseHistory.hasPurchased(id, null)).isFalse();

        String path = "/products/" + id + "/reviews";
        assertThat(restTemplate.postForEntity(path,
                Map.of("authorName", "Reader Name", "rating", 5, "comment", "Helpful"), Map.class)
                .getStatusCode().value()).isEqualTo(201);
        assertThat(restTemplate.postForEntity(path, Map.of("rating", 4), Map.class).getStatusCode().value())
                .isEqualTo(201);
        List<?> reviews = restTemplate.getForObject(path, List.class);
        assertThat(reviews).hasSize(2);
        assertThat(reviews.stream().map(r -> (String) ((Map<?, ?>) r).get("authorName")))
                .containsExactlyInAnyOrder("Reader Name", "anonymous");
        assertThat(reviews.stream().map(r -> (String) ((Map<?, ?>) r).get("comment")))
                .containsExactlyInAnyOrder("Helpful", "");
    }
}
