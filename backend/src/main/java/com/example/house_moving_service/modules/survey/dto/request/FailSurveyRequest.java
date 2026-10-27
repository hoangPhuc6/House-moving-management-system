package com.example.house_moving_service.modules.survey.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Báo khảo sát thất bại (khách vắng, không vào được nhà...). Dùng chung cho quản lý và MOVER (task 4.7). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailSurveyRequest {

    @NotBlank(message = "Lý do không khảo sát được không được để trống")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
