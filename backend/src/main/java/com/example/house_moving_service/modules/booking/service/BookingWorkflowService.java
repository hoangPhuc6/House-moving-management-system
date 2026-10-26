package com.example.house_moving_service.modules.booking.service;

import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;
import com.example.house_moving_service.modules.booking.dto.response.BookingItemSnapshot;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;

import java.util.List;
import java.util.Set;

/**
 * Thao tác trên đơn hàng dành cho module khác (survey, quotation...) — tách khỏi BookingService/BookingManagementService
 * (API cho controller) để module khác chỉ phụ thuộc đúng phần cần dùng.
 * Mọi method ném ERR_BOOKING_NOT_FOUND nếu đơn hàng không tồn tại.
 */
public interface BookingWorkflowService {

    // Reference (proxy) tới đơn hàng để gắn quan hệ JPA. 
    Booking getReference(Integer bookingId);

    BookingSnapshot getSnapshot(Integer bookingId);

    // Đồ đạc khách đã khai báo khi đặt đơn. 
    List<BookingItemSnapshot> getItems(Integer bookingId);

    // Lộ trình kèm điều kiện ra vào đã tách từ chuỗi (pickupAccess / dropoffAccess). 
    BookingRouteResponse getRoute(Integer bookingId);

    // Ghi đè điều kiện ra vào; truyền null cho đầu nào thì giữ nguyên đầu đó. 
    void updateRouteAccess(Integer bookingId, RouteAccessInfo pickup, RouteAccessInfo dropoff);

    /**
     * Đổi trạng thái đơn và ghi booking_status_history. Đơn được khóa dòng trong lúc đổi.
     * Trạng thái hiện tại không thuộc allowedFrom thì ném ERR_BOOKING_INVALID_STATUS;
     * đã ở trạng thái đích thì không làm gì (không ghi lịch sử).
     */
    void changeStatus(Integer bookingId, Set<BookingStatus> allowedFrom, BookingStatus to, Long actorUserId, String note);
}
