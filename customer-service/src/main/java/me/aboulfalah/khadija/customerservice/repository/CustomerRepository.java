package me.aboulfalah.khadija.customerservice.repository;

import me.aboulfalah.khadija.customerservice.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer,Long> {
}
