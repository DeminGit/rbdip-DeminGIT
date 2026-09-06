package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistence {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderPersistence(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public Order save(CreateOrderRequest request, ProductLoader.LoadedProducts loadedProducts) {
        Order order = orderRepository.save(new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), "new"));
        for (int index = 0; index < loadedProducts.products().size(); index++) {
            Product product = loadedProducts.products().get(index);
            int quantity = loadedProducts.lineItems().get(index).quantity();
            orderItemRepository.save(new OrderItem(order.getId(), product.getName(), product.getPrice(), quantity));
        }
        return order;
    }
}