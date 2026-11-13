package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingHistoryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingTripResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingHistoryResponse;

import java.util.List;

/**
 * Đọc các chuyến và lịch sử vận chuyển của một đơn. Phía khách chỉ chủ đơn xem được (ERR_BOOKING_FORBIDDEN),
 * không thấy chuyến CANCELLED, ghi chú tự do hay id nhân viên. Đơn không tồn tại → ERR_BOOKING_NOT_FOUND.
 */
public interface MovingHistoryService {

    // Các chuyến chưa hủy của đơn, sắp theo giờ hẹn bắt đầu
    List<CustomerMovingScheduleResponse> getCustomerSchedules(Integer bookingId, Long customerUserId);

    // Một chuyến của đơn; không tồn tại / không thuộc đơn / đã CANCELLED → ERR_MOVING_SCHEDULE_NOT_FOUND
    CustomerMovingTripResponse getCustomerSchedule(Integer bookingId, Integer scheduleId, Long customerUserId);

    CustomerMovingHistoryResponse getCustomerHistory(Integer bookingId, Long customerUserId);

    // Đầy đủ cho ADMIN / DISPATCHER: mọi chuyến, nhật ký gốc, mọi phân công và xe
    ManagementMovingHistoryResponse getManagementHistory(Integer bookingId);
}
