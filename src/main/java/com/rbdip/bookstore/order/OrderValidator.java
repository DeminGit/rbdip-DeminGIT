package com.rbdip.bookstore.order;

import org.springframework.stereotype.Component;

@Component
public class OrderValidator {

    public void validate(CreateOrderRequest request) {
        validateCustomer(request);
        validateItems(request);
    }

    private void validateCustomer(CreateOrderRequest request) {
        if (request.customerFullName() == null || request.customerFullName().isBlank()) {
            throw new IllegalArgumentException("customerFullName is required");
        }
        if (request.customerAddress() == null || request.customerAddress().isBlank()) {
            throw new IllegalArgumentException("customerAddress is required");
        }
    }

    private void validateItems(CreateOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
        for (CreateOrderRequest.Item item : request.items()) {
            if (item.quantity() != null && item.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }
}