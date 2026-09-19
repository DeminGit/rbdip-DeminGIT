package com.rbdip.bookstore.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
            select distinct o from Order o
            join fetch o.customer
            left join fetch o.items i
            left join fetch i.product
            order by o.id
            """)
    List<Order> findAllWithDetails();

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM orders o
                JOIN customers c ON c.id = o.customer_id
                JOIN order_items i ON i.order_id = o.id
                WHERE i.product_id = :productId
                  AND c.first_name || CASE WHEN c.last_name = '' THEN '' ELSE ' ' || c.last_name END = :authorName
            )
            """, nativeQuery = true)
    boolean hasPurchased(Long productId, String authorName);
}
