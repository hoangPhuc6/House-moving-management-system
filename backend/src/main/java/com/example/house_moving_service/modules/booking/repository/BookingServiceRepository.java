package com.example.house_moving_service.modules.booking.repository;

import com.example.house_moving_service.modules.booking.entity.BookingService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingServiceRepository extends JpaRepository<BookingService, Integer> {

    List<BookingService> findAllByBookingId(Integer bookingId);
}
