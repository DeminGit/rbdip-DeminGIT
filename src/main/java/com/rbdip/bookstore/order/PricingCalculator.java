package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Модуль расчёта цены заказа. Намеренно почти не покрыт тестами и
 * содержит magic numbers / нечитаемые ветвления скидок - цель для
 * характеризационных тестов (ЛР2) и mutation-testing гейта PIT (ЛР5).
 */
public class PricingCalculator {

    private static final int BULK_QUANTITY_THRESHOLD = 10;
    private static final BigDecimal BULK_DISCOUNT = new BigDecimal("0.95");
    private static final BigDecimal VIP_DISCOUNT = new BigDecimal("0.90");
    private static final BigDecimal WHOLESALE_DISCOUNT = new BigDecimal("0.85");
    private static final BigDecimal PERCENT_COUPON_DISCOUNT = new BigDecimal("0.80");
    private static final BigDecimal ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_DISCOUNT = new BigDecimal("0.98");
    private static final String VIP_CUSTOMER = "vip";
    private static final String WHOLESALE_CUSTOMER = "wholesale";
    private static final String SAVE10_COUPON = "SAVE10";
    private static final String SAVE20_PERCENT_COUPON = "SAVE20PERCENT";

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = BigDecimal.ZERO;

        for (LineItem item : items) {
            BigDecimal linePrice = item.price().multiply(BigDecimal.valueOf(item.quantity()));
            if (item.quantity() > BULK_QUANTITY_THRESHOLD) {
                linePrice = linePrice.multiply(BULK_DISCOUNT);
            }
            total = total.add(linePrice);
        }

        if (VIP_CUSTOMER.equals(customerType)) {
            total = total.multiply(VIP_DISCOUNT);
        } else if (WHOLESALE_CUSTOMER.equals(customerType)) {
            total = total.multiply(WHOLESALE_DISCOUNT);
        }

        if (SAVE10_COUPON.equals(couponCode)) {
            total = total.subtract(BigDecimal.TEN);
        } else if (SAVE20_PERCENT_COUPON.equals(couponCode)) {
            total = total.multiply(PERCENT_COUPON_DISCOUNT);
        }

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        if (total.compareTo(ORDER_THRESHOLD) > 0) {
            total = total.multiply(LARGE_ORDER_DISCOUNT);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
