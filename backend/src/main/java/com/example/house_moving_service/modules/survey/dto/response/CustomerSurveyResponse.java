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

/**
 * Lịch khảo sát khách hàng được xem. Không có ghi chú nội bộ (note), tệp đính kèm hay số điện thoại nhân viên:
 * khách muốn đổi lịch thì liên hệ tổng đài / điều phối viên.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSurveyResponse {

    private Integer id;
    private SurveyMethod surveyMethod;
    private SurveyStatus status;

    // null khi lịch chưa xếp ngày / khung giờ
    private LocalDate surveyDate;
    private String timeSlotLabel;

    // null khi chưa phân công nhân viên
    private String staffName;

    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private LocalDateTime createdAt;
}
