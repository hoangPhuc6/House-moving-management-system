package com.example.house_moving_service.modules.pricing.repository;

import com.example.house_moving_service.modules.pricing.entity.Quotation;
import com.example.house_moving_service.modules.pricing.enums.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Integer> {

    List<Quotation> findAllByBookingIdOrderByVersionDesc(Integer bookingId);

    List<Quotation> findAllByBookingIdAndStatus(Integer bookingId, QuotationStatus status);

    Optional<Quotation> findTopByBookingIdOrderByVersionDesc(Integer bookingId);

    Optional<Quotation> findByIdAndBookingId(Integer id, Integer bookingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Quotation q SET q.status = :toStatus "
            + "WHERE q.status = :fromStatus AND q.validUntil IS NOT NULL AND q.validUntil < :now")
    int bulkUpdateStatusForOverdue(@Param("fromStatus") QuotationStatus fromStatus,
                                   @Param("toStatus") QuotationStatus toStatus,
                                   @Param("now") LocalDateTime now);
}
