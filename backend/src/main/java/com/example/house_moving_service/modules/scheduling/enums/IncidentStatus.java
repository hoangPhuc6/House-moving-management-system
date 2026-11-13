package com.example.house_moving_service.modules.scheduling.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * REPORTED ──investigate──► INVESTIGATING
 * REPORTED / INVESTIGATING ──resolve / reject──► RESOLVED / REJECTED (trạng thái cuối)
 */
public enum IncidentStatus {
    REPORTED,
    INVESTIGATING,
    RESOLVED,
    REJECTED;

    // Còn mở: được giải quyết hoặc bác bỏ
    public static final Set<IncidentStatus> OPEN = EnumSet.of(REPORTED, INVESTIGATING);
}
