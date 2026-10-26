package com.example.house_moving_service.modules.booking.repository;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Integer>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByBookingCode(String bookingCode);

    boolean existsByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = {"route"})
    @Query("SELECT b FROM Booking b WHERE b.customer.id = :customerId")
    Page<Booking> findAllByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"route"})
    @Query("SELECT b FROM Booking b WHERE b.customer.id = :customerId AND b.status = :status")
    Page<Booking> findAllByCustomerIdAndStatus(
            @Param("customerId") Long customerId,
            @Param("status") BookingStatus status,
            Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.id = :id AND b.customer.id = :customerId")
    Optional<Booking> findByIdAndCustomerId(@Param("id") Integer id, @Param("customerId") Long customerId);

    // Khóa dòng (SELECT ... FOR UPDATE) đến hết transaction để hai thao tác đổi trạng thái không ghi đè nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Integer id);
}

