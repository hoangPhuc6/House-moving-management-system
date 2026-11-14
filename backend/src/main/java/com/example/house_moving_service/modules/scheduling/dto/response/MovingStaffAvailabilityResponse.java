package com.example.house_moving_service.modules.scheduling.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/** Nhân viên hiện trường và tình trạng rảnh trong khung giờ; busyReason = MOVING / SURVEY khi bận. */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovingStaffAvailabilityResponse(
        Long userId,
        String staffCode,
        String fullName,
        boolean available,
        String busyReason
) {
    public static final String BUSY_MOVING = "MOVING";
    public static final String BUSY_SURVEY = "SURVEY";
}
