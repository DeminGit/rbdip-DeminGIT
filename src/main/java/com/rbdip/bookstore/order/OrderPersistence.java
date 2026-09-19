package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerRepository;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistence {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;

    public OrderPersistence(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                            CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
    }

    public Order save(CreateOrderRequest request, ProductLoader.LoadedProducts loadedProducts) {
        Customer customer = customerRepository.resolve(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = orderRepository.save(new Order(customer, "new"));
        for (int index = 0; index < loadedProducts.products().size(); index++) {
            Product product = loadedProducts.products().get(index);
            int quantity = loadedProducts.lineItems().get(index).quantity();
            orderItemRepository.save(new OrderItem(order.getId(), product, quantity));
        }
        return order;
    }
}
