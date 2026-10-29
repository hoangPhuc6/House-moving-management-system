package com.example.house_moving_service.modules.survey.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hoàn tất khảo sát. Body không bắt buộc; resultNote là nhận xét chung (vd. cầu thang hẹp, phải tháo cửa tủ lạnh). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteSurveyRequest {

    @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
    private String resultNote;
}
