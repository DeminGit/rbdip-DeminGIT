package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductLoader {

    private final ProductRepository productRepository;

    public ProductLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public LoadedProducts load(List<CreateOrderRequest.Item> requestedItems) {
        List<Product> products = new ArrayList<>();
        List<PricingCalculator.LineItem> lineItems = new ArrayList<>();
        for (CreateOrderRequest.Item requestedItem : requestedItems) {
            Product product = productRepository.findById(requestedItem.productId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "product " + requestedItem.productId() + " not found"));
            int quantity = requestedItem.quantity() == null ? 1 : requestedItem.quantity();
            products.add(product);
            lineItems.add(new PricingCalculator.LineItem(product.getPrice(), quantity));
        }
        return new LoadedProducts(products, lineItems);
    }

    public record LoadedProducts(
            List<Product> products,
            List<PricingCalculator.LineItem> lineItems) {
    }
}