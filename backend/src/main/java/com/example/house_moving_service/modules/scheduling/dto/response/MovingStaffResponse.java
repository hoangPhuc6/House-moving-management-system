package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovingStaffResponse(
        Integer assignmentId,
        Long userId,
        String staffCode,
        String fullName,
        String phoneNumber,
        StaffAssignmentRole role,
        AssignmentStatus status
) {
}
