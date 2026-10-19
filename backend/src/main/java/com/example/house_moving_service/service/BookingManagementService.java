package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.booking.BookingManagementFilterRequest;
import com.example.house_moving_service.dto.request.booking.CancelBookingRequest;
import com.example.house_moving_service.dto.response.PageResponse;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import org.springframework.data.domain.Pageable;

public interface BookingManagementService {

    /**
     * Xem chi tiết bất kỳ đơn hàng nào trong hệ thống (dành cho Admin & Dispatcher, không check IDOR).
     */
    BookingDetailResponse getBookingDetailByManagement(Integer bookingId);

    /**
     * Tìm kiếm và lọc danh sách đơn hàng toàn hệ thống theo nhiều tiêu chí động.
     */
    PageResponse<BookingSummaryResponse> searchAllBookings(BookingManagementFilterRequest filter, Pageable pageable);

    /**
     * Quản trị viên hoặc Điều phối viên hủy đơn hàng với quyền quản lý.
     * Cho phép hủy ở mọi trạng thái ngoại trừ đơn đã bị CANCELLED.
     */
    BookingDetailResponse cancelBookingByManagement(Integer bookingId, CancelBookingRequest request, Long staffUserId);
}

