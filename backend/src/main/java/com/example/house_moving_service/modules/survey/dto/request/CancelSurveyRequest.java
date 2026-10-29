package com.example.house_moving_service.modules.survey.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hủy lịch khảo sát chưa diễn ra (lịch không cần nữa). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelSurveyRequest {

    @NotBlank(message = "Lý do hủy lịch không được để trống")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
