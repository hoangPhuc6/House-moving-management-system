package com.example.house_moving_service.modules.survey.enums;

import java.util.EnumSet;
import java.util.Set;

public enum SurveyStatus {
    PENDING,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    RESURVEY_REQUIRED;

    // Khảo sát còn đang chạy; mỗi booking tối đa 1 (uq_booking_surveys_one_active_per_booking)
    public static final Set<SurveyStatus> ACTIVE = EnumSet.of(PENDING, CONFIRMED, IN_PROGRESS);

    // Đang giữ khung giờ của nhân viên (uq_booking_surveys_staff_slot)
    public static final Set<SurveyStatus> OCCUPYING_SLOT = EnumSet.of(CONFIRMED, IN_PROGRESS);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
