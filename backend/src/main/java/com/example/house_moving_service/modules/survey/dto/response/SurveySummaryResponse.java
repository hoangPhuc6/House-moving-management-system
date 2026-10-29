package com.example.house_moving_service.modules.survey.dto.response;

import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveySummaryResponse {

    private Integer id;
    private Integer bookingId;
    private String bookingCode;
    private String contactName;
    private String contactPhone;
    private String pickupAddress;
    private SurveyMethod surveyMethod;
    private LocalDate surveyDate;
    private SurveyTimeSlotResponse timeSlot;
    private SurveyStaffResponse staff;
    private SurveyStatus status;
    private LocalDateTime createdAt;
}
