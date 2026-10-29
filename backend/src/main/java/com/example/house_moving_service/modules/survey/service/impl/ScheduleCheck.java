package com.example.house_moving_service.modules.survey.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dữ liệu lịch cần kiểm tra. Field nào null thì SurveyScheduleValidator bỏ qua các bước cần field đó.
 *
 * @param bookingDesiredStartAt ngày giờ khách muốn chuyển nhà (có thể null)
 * @param excludeSurveyId       id của chính lịch đang sửa; null khi tạo mới
 */
record ScheduleCheck(LocalDate surveyDate,
                     Integer timeSlotId,
                     Long staffId,
                     LocalDateTime bookingDesiredStartAt,
                     Integer excludeSurveyId) {
}
