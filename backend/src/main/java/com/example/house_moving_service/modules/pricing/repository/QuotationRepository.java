package com.example.house_moving_service.modules.pricing.repository;

import com.example.house_moving_service.modules.pricing.entity.Quotation;
import com.example.house_moving_service.modules.pricing.enums.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Integer> {

    List<Quotation> findAllByBookingIdOrderByVersionDesc(Integer bookingId);

    List<Quotation> findAllByBookingIdAndStatus(Integer bookingId, QuotationStatus status);

    Optional<Quotation> findTopByBookingIdOrderByVersionDesc(Integer bookingId);

    Optional<Quotation> findByIdAndBookingId(Integer id, Integer bookingId);
}
