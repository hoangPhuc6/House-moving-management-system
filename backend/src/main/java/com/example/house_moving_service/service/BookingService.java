package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.booking.CancelBookingRequest;
import com.example.house_moving_service.dto.request.booking.CreateBookingRequest;
import com.example.house_moving_service.dto.response.PageResponse;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import org.springframework.data.domain.Pageable;

public interface BookingService {
    BookingDetailResponse createBooking(CreateBookingRequest request, Long currentUserId);


    BookingDetailResponse getBookingById(Integer id, Long currentUserId);


    PageResponse<BookingSummaryResponse> getCustomerBookings(Long currentUserId, Pageable pageable);

    BookingDetailResponse cancelBooking(Integer id, CancelBookingRequest request, Long currentUserId);
}

