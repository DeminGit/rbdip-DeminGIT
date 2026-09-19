package com.rbdip.bookstore.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query(value = """
            INSERT INTO customers (customer_full_name, address, phone) VALUES (:name, :address, :phone)
            ON CONFLICT (customer_full_name, address, phone)
            DO UPDATE SET customer_full_name = EXCLUDED.customer_full_name
            RETURNING *
            """, nativeQuery = true)
    Customer resolve(String name, String address, String phone);
}
