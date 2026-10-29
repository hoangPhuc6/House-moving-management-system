package com.example.house_moving_service.modules.booking.repository;

import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRouteRepository extends JpaRepository<BookingRoute, Integer> {

    Optional<BookingRoute> findByBookingId(Integer bookingId);
}
