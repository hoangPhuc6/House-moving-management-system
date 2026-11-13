package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.request.MovingNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingProgressRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldMovingScheduleResponse;

/**
 * Luồng hiện trường của chuyến vận chuyển. Người gọi phải có dòng phân công chưa hủy trên chuyến
 * (không có → ERR_MOVING_FORBIDDEN); thao tác ghi còn yêu cầu role SUPERVISOR (→ ERR_MOVING_NOT_SUPERVISOR).
 * Mọi thao tác ghi khóa dòng chuyến và nối một dòng vào nhật ký (moving_schedules.note).
 */
public interface FieldMovingScheduleService {

    FieldMovingScheduleResponse getDetail(Integer id, Long userId);

    /**
     * Chuyến CONFIRMED → IN_PROGRESS + actualStartAt. Chỉ trong ngày của giờ hẹn, sớm nhất app.moving.early-start-minutes
     * trước giờ hẹn. Xe AVAILABLE → IN_USE; đơn {SCHEDULED, CONFIRMED} → IN_PROGRESS. request null = không ghi chú.
     */
    FieldMovingScheduleResponse start(Integer id, MovingNoteRequest request, Long userId);

    /** Ghi một mốc tiến độ vào nhật ký, chuyến phải IN_PROGRESS; không đổi trạng thái. Mốc OTHER bắt buộc có note. */
    FieldMovingScheduleResponse reportProgress(Integer id, MovingProgressRequest request, Long userId);

    /**
     * Chuyến IN_PROGRESS → COMPLETED + actualEndAt; phân công nhân viên / xe chưa hủy → COMPLETED; xe IN_USE → AVAILABLE.
     * Đơn → COMPLETED khi không còn chuyến nào khác chưa xong (PLANNED, CONFIRMED, IN_PROGRESS, RESCHEDULED).
     */
    FieldMovingScheduleResponse complete(Integer id, MovingNoteRequest request, Long userId);
}
