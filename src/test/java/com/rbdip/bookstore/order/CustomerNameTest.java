package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.customer.Customer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerNameTest {
    @ParameterizedTest
    @ValueSource(strings = {"Ivan Petrov", "Prince", "Anna van der Meer", "Name ", " First  Last"})
    void preservesThePublicFullName(String fullName) {
        Customer customer = new Customer(fullName, "Address", null);
        assertThat(customer.getFullName()).isEqualTo(fullName);
        assertThat(customer.getAddress()).isEqualTo("Address");
        assertThat(customer.getPhone()).isNull();
    }
}
