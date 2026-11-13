package com.example.house_moving_service.modules.scheduling.enums;

import java.util.EnumSet;
import java.util.Set;

public enum MovingScheduleStatus {
    PLANNED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    RESCHEDULED;

    // Chuyến chưa xong: còn chuyến ở các trạng thái này thì đơn chưa được chuyển COMPLETED
    public static final Set<MovingScheduleStatus> UNFINISHED = EnumSet.of(PLANNED, CONFIRMED, IN_PROGRESS, RESCHEDULED);
}
