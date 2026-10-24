package com.example.house_moving_service.modules.booking.repository;

import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Integer> {

    List<BookingStatusHistory> findAllByBookingIdOrderByChangedAtAsc(Integer bookingId);

    Page<BookingStatusHistory> findAllByBookingIdOrderByChangedAtDesc(Integer bookingId, Pageable pageable);
}

