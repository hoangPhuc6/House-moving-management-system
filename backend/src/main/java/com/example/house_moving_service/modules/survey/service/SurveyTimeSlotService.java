package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.survey.dto.request.SurveyTimeSlotRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;

import java.util.List;

public interface SurveyTimeSlotService {

    // activeOnly = true: chỉ khung giờ đang dùng; null / false: tất cả. Luôn sắp theo giờ bắt đầu
    List<SurveyTimeSlotResponse> getAll(Boolean activeOnly);

    SurveyTimeSlotResponse create(SurveyTimeSlotRequest request);

    SurveyTimeSlotResponse update(Integer id, SurveyTimeSlotRequest request);

    // Đảo trạng thái bật / tắt. Tắt không ảnh hưởng lịch đã hẹn, chỉ không cho chọn khung giờ đó cho lịch mới
    SurveyTimeSlotResponse toggle(Integer id);

    /**
     * Dùng nội bộ trong module survey khi tạo / đổi lịch: ném ERR_SURVEY_TIME_SLOT_NOT_FOUND nếu không tồn tại,
     * ERR_SURVEY_TIME_SLOT_INACTIVE nếu đã tắt.
     */
    SurveyTimeSlot getActiveSlot(Integer id);
}
