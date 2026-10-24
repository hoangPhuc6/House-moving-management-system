package com.example.house_moving_service.modules.booking.service;

import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.modules.booking.dto.request.CreateBookingRequest;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingDetailResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSummaryResponse;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
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

