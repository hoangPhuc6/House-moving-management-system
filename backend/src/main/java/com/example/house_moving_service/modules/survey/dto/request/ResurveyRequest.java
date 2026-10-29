package com.example.house_moving_service.modules.survey.dto.request;

import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Khảo sát lại một lịch đã hoàn tất. Lịch mới được tạo luôn; ngày / khung giờ / nhân viên có thể truyền để xếp ngay
 * hoặc để trống xếp sau. surveyMethod để trống thì dùng lại phương thức của lần trước.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResurveyRequest {

    @NotBlank(message = "Lý do khảo sát lại không được để trống")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;

    private SurveyMethod surveyMethod;

    private LocalDate surveyDate;

    private Integer timeSlotId;

    private Long staffId;
}
