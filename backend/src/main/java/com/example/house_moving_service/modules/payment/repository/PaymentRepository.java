package com.example.house_moving_service.modules.payment.repository;

import com.example.house_moving_service.modules.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer>, JpaSpecificationExecutor<Payment> {

    List<Payment> findAllByBookingId(Integer bookingId);

    List<Payment> findAllByBookingIdOrderByCreatedAtDesc(Integer bookingId);

    Optional<Payment> findByTransactionCode(String transactionCode);
}
