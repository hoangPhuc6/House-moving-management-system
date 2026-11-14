package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingSurchargeRepository extends JpaRepository<BookingSurcharge, Integer>,
        JpaSpecificationExecutor<BookingSurcharge> {

    // Khóa dòng (SELECT ... FOR UPDATE) để hai thao tác đổi trạng thái phụ phí không ghi đè nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BookingSurcharge s WHERE s.id = :id")
    Optional<BookingSurcharge> findByIdForUpdate(@Param("id") Integer id);

    // Mọi khoản của đơn, cũ nhất trước
    List<BookingSurcharge> findAllByBookingIdOrderByRequestedAtAscIdAsc(Integer bookingId);

    // Khoản của một chuyến cùng các khoản cấp đơn (không gắn chuyến), cũ nhất trước
    @Query("""
            SELECT s FROM BookingSurcharge s
            WHERE s.booking.id = :bookingId AND (s.schedule IS NULL OR s.schedule.id = :scheduleId)
            ORDER BY s.requestedAt ASC, s.id ASC
            """)
    List<BookingSurcharge> findAllForSchedule(@Param("bookingId") Integer bookingId, @Param("scheduleId") Integer scheduleId);

    // Id các khoản của đơn ở các trạng thái truyền vào (dùng để chặn bắt đầu chuyến)
    @Query("""
            SELECT s.id FROM BookingSurcharge s
            WHERE s.booking.id = :bookingId AND s.status IN :statuses
            ORDER BY s.id ASC
            """)
    List<Integer> findIdsByBookingIdAndStatusIn(@Param("bookingId") Integer bookingId,
                                                @Param("statuses") Collection<SurchargeStatus> statuses);
}
