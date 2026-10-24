package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.booking.CancelBookingRequest;
import com.example.house_moving_service.dto.request.booking.CreateBookingRequest;
import com.example.house_moving_service.dto.response.PageResponse;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import com.example.house_moving_service.enums.BookingStatus;
import org.springframework.data.domain.Pageable;

public interface BookingService {
    BookingDetailResponse createBooking(CreateBookingRequest request, Long currentUserId);

    BookingDetailResponse getBookingById(Integer id, Long currentUserId);

    PageResponse<BookingSummaryResponse> getCustomerBookings(Long currentUserId, BookingStatus status, Pageable pageable);

    default PageResponse<BookingSummaryResponse> getCustomerBookings(Long currentUserId, Pageable pageable) {
        return getCustomerBookings(currentUserId, null, pageable);
    }

    BookingDetailResponse cancelBooking(Integer id, CancelBookingRequest request, Long currentUserId);
}

