package com.example.house_moving_service.modules.payment.repository;

import com.example.house_moving_service.modules.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    List<Payment> findAllByBookingId(Integer bookingId);

    Optional<Payment> findByTransactionCode(String transactionCode);
}
