package com.example.house_moving_service.modules.quotation.repository;

import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, Integer> {

    Optional<Quotation> findByBookingIdAndStatus(Integer bookingId, QuotationStatus status);

    Optional<Quotation> findTopByBookingIdOrderByVersionDesc(Integer bookingId);

    List<Quotation> findAllByBookingIdOrderByVersionDesc(Integer bookingId);

    List<Quotation> findAllByBookingIdAndStatusIn(Integer bookingId, Collection<QuotationStatus> statuses);

    boolean existsByBookingIdAndStatus(Integer bookingId, QuotationStatus status);

    @Query("SELECT COALESCE(MAX(q.version), 0) FROM Quotation q WHERE q.booking.id = :bookingId")
    int findMaxVersion(@Param("bookingId") Integer bookingId);
}
