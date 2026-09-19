package com.rbdip.bookstore.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query(value = """
            INSERT INTO customers (first_name, last_name, address, phone)
            VALUES (CASE WHEN strpos(:name, ' ') IN (0, length(:name)) THEN :name
                         ELSE left(:name, strpos(:name, ' ') - 1) END,
                    CASE WHEN strpos(:name, ' ') IN (0, length(:name)) THEN ''
                         ELSE substring(:name FROM strpos(:name, ' ') + 1) END, :address, :phone)
            ON CONFLICT (first_name, last_name, address, phone)
            DO UPDATE SET first_name = EXCLUDED.first_name
            RETURNING *
            """, nativeQuery = true)
    Customer resolve(String name, String address, String phone);
}
