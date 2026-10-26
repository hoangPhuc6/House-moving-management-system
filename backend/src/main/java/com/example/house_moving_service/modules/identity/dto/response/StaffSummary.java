package com.example.house_moving_service.modules.identity.dto.response;

import lombok.Builder;

/** Thông tin tóm tắt một nhân viên, trả cho module khác qua StaffLookupService. */
@Builder
public record StaffSummary(
        Long userId,
        String staffCode,
        String fullName,
        String email,
        String employmentStatus
) {
}
