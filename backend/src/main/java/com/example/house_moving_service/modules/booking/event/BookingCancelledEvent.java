package com.example.house_moving_service.modules.booking.event;

/**
 * Phát ra trong transaction hủy đơn (khách hoặc quản lý hủy). Module khác lắng nghe để dọn dữ liệu liên quan
 * (ví dụ survey hủy lịch khảo sát) mà booking không phải phụ thuộc ngược vào các module đó.
 */
public record BookingCancelledEvent(Integer bookingId, Long actorUserId, String reason) {
}
