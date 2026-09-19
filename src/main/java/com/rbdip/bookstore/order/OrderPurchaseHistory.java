package com.rbdip.bookstore.order;

import com.rbdip.bookstore.purchase.PurchaseHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPurchaseHistory implements PurchaseHistory {
    private final OrderRepository orderRepository;

    public OrderPurchaseHistory(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchased(Long productId, String authorName) {
        return orderRepository.hasPurchased(productId, authorName);
    }
}
