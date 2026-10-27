package com.example.house_moving_service.modules.survey.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Phân công / đổi nhân viên khảo sát; ngày và khung giờ giữ nguyên. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignSurveyStaffRequest {

    @NotNull(message = "Nhân viên khảo sát không được để trống")
    private Long staffId;
}
