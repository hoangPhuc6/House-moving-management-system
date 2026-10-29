package com.example.house_moving_service.modules.survey.dto.request;

import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Tạo lịch khảo sát. Ngày / khung giờ / nhân viên có thể để trống và xếp sau; đủ cả ba thì lịch là CONFIRMED. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSurveyRequest {

    @NotNull(message = "Đơn hàng không được để trống")
    private Integer bookingId;

    @NotNull(message = "Phương thức khảo sát không được để trống")
    private SurveyMethod surveyMethod;

    private LocalDate surveyDate;

    private Integer timeSlotId;

    private Long staffId;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;
}
