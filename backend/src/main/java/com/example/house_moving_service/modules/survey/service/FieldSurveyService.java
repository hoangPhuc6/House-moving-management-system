package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Luồng của MOVER được phân công đi khảo sát. Mọi thao tác chỉ áp dụng cho lịch của chính người gọi
 * (lịch người khác → ERR_SURVEY_FORBIDDEN).
 */
public interface FieldSurveyService {

    /**
     * Lịch khảo sát của tôi trong khoảng ngày, sắp theo ngày rồi giờ bắt đầu. from null → hôm nay, to null → from + 7 ngày;
     * status null → mọi trạng thái. Lịch chưa có ngày không hiện.
     */
    List<SurveySummaryResponse> getMySurveys(Long userId, LocalDate from, LocalDate to, SurveyStatus status);

    FieldSurveyResponse getMySurvey(Integer id, Long userId);

    /**
     * Bắt đầu khảo sát lịch CONFIRMED của mình, trong ngày khảo sát và không sớm hơn app.survey.early-start-minutes
     * trước giờ hẹn. Lịch → IN_PROGRESS + actualStartAt; booking → SURVEYING; mở bản nháp báo giá.
     */
    FieldSurveyResponse start(Integer id, Long userId);
}
