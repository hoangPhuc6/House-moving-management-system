package com.example.house_moving_service.repository;

import com.example.house_moving_service.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, Integer> {

    List<BookingItem> findAllByBookingId(Integer bookingId);
}

