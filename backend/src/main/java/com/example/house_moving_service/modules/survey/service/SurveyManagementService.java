package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.survey.dto.request.AssignSurveyStaffRequest;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.RescheduleSurveyRequest;
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

    /**
     * Phân công / đổi MOVER cho lịch PENDING / CONFIRMED; ngày và khung giờ giữ nguyên.
     * Lịch đã có ngày + khung giờ thì kiểm tra trùng lịch cho người mới; chưa có thì chỉ kiểm tra người hợp lệ.
     * Phân công lại đúng người đang phụ trách thì không đổi gì.
     */
    SurveyDetailResponse assign(Integer id, AssignSurveyStaffRequest request, Long actorId);

    /**
     * Dời lịch hẹn (ngày, khung giờ, có thể kèm đổi người / phương thức) cho lịch PENDING / CONFIRMED.
     * Đã bắt đầu khảo sát thì không dời được; khảo sát xong muốn làm lại thì dùng khảo sát lại.
     */
    SurveyDetailResponse reschedule(Integer id, RescheduleSurveyRequest request, Long actorId);
}
