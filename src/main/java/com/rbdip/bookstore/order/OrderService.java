package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderValidator orderValidator;
    private final ProductLoader productLoader;
    private final PricingCalculator pricingCalculator;
    private final OrderPersistence orderPersistence;
    private final OrderNotificationService notificationService;

    public OrderService(
            OrderValidator orderValidator,
            ProductLoader productLoader,
            PricingCalculator pricingCalculator,
            OrderPersistence orderPersistence,
            OrderNotificationService notificationService) {
        this.orderValidator = orderValidator;
        this.productLoader = productLoader;
        this.pricingCalculator = pricingCalculator;
        this.orderPersistence = orderPersistence;
        this.notificationService = notificationService;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        orderValidator.validate(request);
        ProductLoader.LoadedProducts loadedProducts = productLoader.load(request.items());
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                loadedProducts.lineItems(), request.customerType(), request.couponCode());
        Order order = orderPersistence.save(request, loadedProducts);
        notificationService.sendConfirmation(request.customerFullName(), order.getId(), total);

        return order;
    }
}
