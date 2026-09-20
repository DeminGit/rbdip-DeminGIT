package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingMutationTest {
    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "100, 1, vip, SAVE10, 80.00",
        "100, 1, wholesale, SAVE20PERCENT, 68.00",
        "100, 11, regular, , 1024.10",
        "1010, 1, regular, SAVE10, 1000.00",
        "1010.01, 1, regular, SAVE10, 980.01",
        "1250, 1, regular, SAVE20PERCENT, 1000.00",
        "10, 9, regular, , 90.00",
        "999.99, 1, regular, , 999.99"
    })
    void guardsDiscountOrderAndThresholds(String price, int quantity, String type, String coupon, String expected) {
        assertThat(calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)), type, coupon))
                .isEqualByComparingTo(expected);
    }

    @Test
    void roundsOnceAfterAccumulatingAllLines() {
        assertThat(calculator.calculateOrderTotal(List.of(
                new PricingCalculator.LineItem(new BigDecimal("0.004"), 1),
                new PricingCalculator.LineItem(new BigDecimal("0.004"), 1)), "regular", null))
                .isEqualByComparingTo("0.01");
    }
}
