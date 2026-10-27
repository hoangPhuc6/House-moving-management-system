package com.example.house_moving_service.modules.survey.service;

import java.time.LocalDateTime;

/**
 * Dành cho module khác (Lập lịch / Phân công): hỏi MOVER có lịch khảo sát trùng giờ không
 * trước khi gán người đó đi chuyển nhà.
 */
public interface SurveyStaffScheduleService {

    /** true nếu nhân viên có lịch khảo sát CONFIRMED / IN_PROGRESS giao với khoảng [start, end); chạm biên không tính. */
    boolean hasSurveyBetween(Long staffId, LocalDateTime start, LocalDateTime end);
}
