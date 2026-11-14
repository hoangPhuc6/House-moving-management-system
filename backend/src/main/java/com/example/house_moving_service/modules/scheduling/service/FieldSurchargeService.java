package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldScheduleSurchargesResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;

/**
 * Phụ phí phía hiện trường. Không được phân công vào chuyến → ERR_MOVING_FORBIDDEN; thao tác ghi (trừ cancel) yêu cầu
 * SUPERVISOR → ERR_MOVING_NOT_SUPERVISOR.
 */
public interface FieldSurchargeService {

    /**
     * SUPERVISOR báo sai lệch / đề xuất phụ phí → PROPOSED. Chuyến phải CONFIRMED / IN_PROGRESS
     * (ERR_SURCHARGE_SCHEDULE_INVALID); đơn không CANCELLED / COMPLETED (ERR_SURCHARGE_BOOKING_INVALID_STATUS).
     */
    SurchargeResponse propose(Integer scheduleId, CreateSurchargeRequest request, Long userId);

    // Khoản của chuyến và khoản cấp đơn, kèm cờ chặn bắt đầu chuyến và tiền chưa thu; mọi role được phân công đều xem được
    FieldScheduleSurchargesResponse getBySchedule(Integer scheduleId, Long userId);

    // Người đề xuất rút lại khi còn PROPOSED; người khác → ERR_SURCHARGE_FORBIDDEN
    SurchargeResponse cancel(Integer id, Long userId);

    /**
     * SUPERVISOR xác nhận đã thu tiền: khoản phải ACCEPTED và chưa thu (ERR_SURCHARGE_INVALID_STATUS). Tạo phiếu thu
     * CASH / PARTIAL qua PaymentManagementService#recordManualPayment rồi ghi payment_id, cùng một transaction.
     * Khoản không gắn chuyến: người gọi phải là SUPERVISOR của một chuyến chưa hủy của đơn.
     */
    SurchargeResponse collect(Integer id, Long userId);
}
