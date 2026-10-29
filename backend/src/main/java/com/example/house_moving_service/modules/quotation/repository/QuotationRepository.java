package com.example.house_moving_service.modules.quotation.repository;

import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    List<Quotation> findAllByBookingIdAndStatus(Integer bookingId, QuotationStatus status);

    Optional<Quotation> findByIdAndBookingId(Integer id, Integer bookingId);

    /**
     * Chuyển hàng loạt báo giá đã quá hạn (valid_until < now) từ fromStatus sang toStatus.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Quotation q SET q.status = :toStatus "
            + "WHERE q.status = :fromStatus AND q.validUntil IS NOT NULL AND q.validUntil < :now")
    int bulkUpdateStatusForOverdue(@Param("fromStatus") QuotationStatus fromStatus,
                                   @Param("toStatus") QuotationStatus toStatus,
                                   @Param("now") LocalDateTime now);
}
