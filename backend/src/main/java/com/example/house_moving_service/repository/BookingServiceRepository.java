package com.example.house_moving_service.repository;

import com.example.house_moving_service.entity.BookingService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingServiceRepository extends JpaRepository<BookingService, Integer> {

    List<BookingService> findAllByBookingId(Integer bookingId);
}

