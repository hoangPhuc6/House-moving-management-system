package com.example.house_moving_service.modules.survey.dto.request;

import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Bộ lọc danh sách lịch khảo sát cho quản lý (query param), mọi field đều không bắt buộc. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyFilterRequest {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    private Long staffId;

    private SurveyStatus status;

    private SurveyMethod method;

    private String bookingCode;

    private String customerPhone;
}
