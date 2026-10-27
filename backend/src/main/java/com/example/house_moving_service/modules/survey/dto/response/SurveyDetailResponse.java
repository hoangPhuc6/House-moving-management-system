package com.example.house_moving_service.modules.survey.dto.response;

import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết một lịch khảo sát cho quản lý: các field của SurveySummaryResponse + thời gian thực tế, ghi chú,
 * tuyến đường (kèm điều kiện ra vào), bản nháp báo giá hiện tại và các lần khảo sát khác của cùng booking.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyDetailResponse {

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

    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private String note;
    private BookingRouteResponse route;

    // Chỉ có khi đây là lần khảo sát mới nhất của booking và booking đang có báo giá DRAFT
    private QuotationDraftDto currentDraft;
    private List<QuotationItemDto> draftItems;

    // Các lần khảo sát khác của cùng booking, mới nhất trước
    private List<SurveySummaryResponse> previousSurveys;
}
