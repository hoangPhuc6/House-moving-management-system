package com.example.house_moving_service.modules.inventory.service;

import com.example.house_moving_service.modules.inventory.dto.request.AllocateMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.request.CancelAllocationRequest;
import com.example.house_moving_service.modules.inventory.dto.request.SettleMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.response.BookingMaterialsResponse;
import com.example.house_moving_service.modules.inventory.dto.response.CustomerMaterialChargeResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialAllocationResponse;
import com.example.house_moving_service.modules.inventory.dto.response.ScheduleMaterialsResponse;

import java.util.List;

/** Cấp phát vật tư theo chuyến: xuất cho chuyến, chốt sau chuyến, hủy cấp phát; xem theo chuyến / đơn / MOVER / khách. */
public interface MaterialAllocationService {

    /** Mọi dòng cấp phát của chuyến (mọi trạng thái, theo id tăng dần) kèm tổng tiền tạm tính / đã tính. */
    ScheduleMaterialsResponse getScheduleMaterials(Integer scheduleId);

    /**
     * Xuất vật tư cho chuyến (PLANNED / CONFIRMED / RESCHEDULED / IN_PROGRESS). Chưa có dòng ALLOCATED cùng vật tư thì
     * tạo dòng mới (chụp unit_price); đã có thì tăng quantity_allocated, giữ giá cũ. Mỗi món một EXPORT.
     */
    ScheduleMaterialsResponse allocate(Integer scheduleId, AllocateMaterialsRequest request, Long actorUserId);

    /**
     * Chốt vật tư sau chuyến (chuyến COMPLETED): request có đúng và đủ mọi dòng ALLOCATED; từng dòng
     * used + returned + damaged = allocated, TOOL thì used = 0. returned → RETURN, damaged → DAMAGED (change 0).
     * Dòng chuyển SETTLED với charged_amount = used × unit_price; tổng tiền cộng vào đơn một lần nếu > 0.
     */
    ScheduleMaterialsResponse settle(Integer scheduleId, SettleMaterialsRequest request, Long actorUserId);

    /**
     * Hủy cấp phát (dòng ALLOCATED, chuyến PLANNED / CONFIRMED / RESCHEDULED / CANCELLED): RETURN toàn bộ, status
     * CANCELLED, không tính tiền.
     */
    MaterialAllocationResponse cancel(Integer allocationId, CancelAllocationRequest request, Long actorUserId);

    /**
     * Vật tư của cả đơn gom theo chuyến (chuyến có dòng cấp phát, scheduleId tăng dần), mọi trạng thái, kèm tổng tiền
     * đã tính. Không có đơn → ERR_BOOKING_NOT_FOUND.
     */
    BookingMaterialsResponse getBookingMaterials(Integer bookingId);

    /**
     * Vật tư của chuyến cho MOVER được phân công (phân công chưa CANCELLED), không có tiền. Không phân công →
     * ERR_MOVING_FORBIDDEN.
     */
    ScheduleMaterialsResponse getFieldScheduleMaterials(Integer scheduleId, Long userId);

    /**
     * Vật tư đã tính tiền trong đơn của khách: chỉ dòng SETTLED có charged_amount > 0. Không phải chủ đơn →
     * ERR_BOOKING_FORBIDDEN.
     */
    List<CustomerMaterialChargeResponse> getCustomerCharges(Integer bookingId, Long customerUserId);
}
