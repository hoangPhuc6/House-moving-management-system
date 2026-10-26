package com.example.house_moving_service.modules.booking.repository;

import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Integer> {

    List<BookingStatusHistory> findAllByBookingIdOrderByChangedAtAsc(Integer bookingId);

    Page<BookingStatusHistory> findAllByBookingIdOrderByChangedAtDesc(Integer bookingId, Pageable pageable);
}
