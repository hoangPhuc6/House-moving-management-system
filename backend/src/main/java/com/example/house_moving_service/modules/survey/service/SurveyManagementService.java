package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;

/**
 * Các thao tác ghi trên lịch khảo sát dành cho Admin / Dispatcher. Phần chỉ đọc nằm ở SurveyQueryService.
 */
public interface SurveyManagementService {

    /**
     * Tạo lịch khảo sát cho booking đang PENDING / PENDING_SURVEY và chưa có lịch nào đang hoạt động.
     * Đủ ngày + khung giờ + nhân viên → CONFIRMED, thiếu → PENDING. Không đổi trạng thái booking.
     */
    SurveyDetailResponse create(CreateSurveyRequest request, Long actorId);
}
