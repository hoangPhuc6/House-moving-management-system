package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.request.AssignStaffRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.AssignVehiclesRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.CancelMovingScheduleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateMovingScheduleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.RescheduleMovingRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingScheduleResponse;

/** Điều phối lập lịch vận chuyển: tạo chuyến, phân công xe / nhân viên (có kiểm tra trùng lịch), xác nhận, đổi lịch, hủy chuyến. */
public interface ManagementMovingScheduleService {

    /** Tạo chuyến PLANNED cho đơn đã đặt cọc (CONFIRMED) hoặc đã có chuyến (SCHEDULED); đơn chuyển sang SCHEDULED. */
    ManagementMovingScheduleResponse create(Integer bookingId, CreateMovingScheduleRequest request, Long actorUserId);

    ManagementMovingScheduleResponse getDetail(Integer scheduleId);

    /** Ghi đè danh sách xe của chuyến PLANNED / CONFIRMED. */
    ManagementMovingScheduleResponse assignVehicles(Integer scheduleId, AssignVehiclesRequest request, Long actorUserId);

    /** Ghi đè danh sách nhân viên của chuyến PLANNED / CONFIRMED; đúng 1 SUPERVISOR. */
    ManagementMovingScheduleResponse assignStaff(Integer scheduleId, AssignStaffRequest request, Long actorUserId);

    /** PLANNED → CONFIRMED khi đã có xe và giám sát viên; sau bước này giám sát viên mới bắt đầu chuyến được (M7). */
    ManagementMovingScheduleResponse confirm(Integer scheduleId, Long actorUserId);

    /** Đổi giờ chuyến PLANNED / CONFIRMED; xe và nhân viên đang phân công phải còn rảnh ở giờ mới. Giữ nguyên trạng thái chuyến. */
    ManagementMovingScheduleResponse reschedule(Integer scheduleId, RescheduleMovingRequest request, Long actorUserId);

    /**
     * Hủy chuyến chưa bắt đầu và mọi phân công còn hiệu lực. Đơn không còn chuyến chưa xong thì SCHEDULED → CONFIRMED
     * (chờ lập lịch lại), IN_PROGRESS → COMPLETED.
     */
    ManagementMovingScheduleResponse cancel(Integer scheduleId, CancelMovingScheduleRequest request, Long actorUserId);
}
