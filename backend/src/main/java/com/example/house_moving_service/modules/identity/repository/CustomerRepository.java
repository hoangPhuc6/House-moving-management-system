package com.example.house_moving_service.modules.identity.repository;

import com.example.house_moving_service.modules.identity.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

}
