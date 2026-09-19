package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorCharacterizationTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "10, 0, regular, UNKNOWN, 0.00",
        "10, -1, regular, UNKNOWN, 0.00",
        "-10, 1, regular, UNKNOWN, 0.00",
        "10, 1, regular, SAVE10, 0.00",
        "10.01, 1, regular, SAVE10, 0.01",
        "10, 11, regular, UNKNOWN, 104.50",
        "100, 1, VIP, save10, 100.00",
        "100, 1, , , 100.00",
        "0.004, 1, regular, , 0.00",
        "1100, 1, vip, , 990.00"
    })
    void characterizesBoundaries(String price, int quantity, String type, String coupon, String expected) {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)), type, coupon);
        assertThat(total).isEqualByComparingTo(expected);
        assertThat(total.scale()).isEqualTo(2);
    }

    @Test
    void accumulatesDifferentLinesBeforeApplyingCoupons() {
        assertThat(calculator.calculateOrderTotal(List.of(
                new PricingCalculator.LineItem(new BigDecimal("10"), 2),
                new PricingCalculator.LineItem(new BigDecimal("5"), 3)), "regular", "SAVE10"))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void appliesBulkVipAndPercentCouponDiscounts() {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal("100.00"), 11)),
                "vip", "SAVE20PERCENT");

        assertThat(total).isEqualByComparingTo("752.40");
    }

    @Test
    void appliesWholesaleAndFixedCouponDiscounts() {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal("100.00"), 2)),
                "wholesale", "SAVE10");

        assertThat(total).isEqualByComparingTo("160.00");
    }

    @Test
    void doesNotApplyBulkDiscountAtQuantityTen() {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal("10.00"), 10)),
                "regular", null);

        assertThat(total).isEqualByComparingTo("100.00");
    }

    @Test
    void clampsNegativeCouponResultToZero() {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal("5.00"), 1)),
                "regular", "SAVE10");

        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    void appliesLargeOrderDiscountOnlyAboveThreshold() {
        BigDecimal atThreshold = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal("1000.00"), 1)),
                "regular", null);
        BigDecimal aboveThreshold = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal("1000.01"), 1)),
                "regular", null);

        assertThat(atThreshold).isEqualByComparingTo("1000.00");
        assertThat(aboveThreshold).isEqualByComparingTo("980.01");
    }

    @Test
    void roundsResultHalfUpAndHandlesEmptyItems() {
        BigDecimal rounded = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal("1.005"), 1)),
                "regular", null);
        BigDecimal empty = calculator.calculateOrderTotal(List.of(), "regular", null);

        assertThat(rounded).isEqualByComparingTo("1.01");
        assertThat(empty).isEqualByComparingTo("0.00");
    }
}
