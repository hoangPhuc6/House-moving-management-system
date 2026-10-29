package com.example.house_moving_service.modules.survey.dto.request;

import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Dời lịch hẹn khảo sát (trước khi khảo sát), ví dụ khách xin đổi ngày. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleSurveyRequest {

    @NotNull(message = "Ngày khảo sát không được để trống")
    private LocalDate surveyDate;

    @NotNull(message = "Khung giờ khảo sát không được để trống")
    private Integer timeSlotId;

    // null = giữ nhân viên hiện tại
    private Long staffId;

    // null = giữ phương thức hiện tại
    private SurveyMethod surveyMethod;

    @NotBlank(message = "Lý do đổi lịch không được để trống")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
