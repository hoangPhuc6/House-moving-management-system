package com.example.house_moving_service.modules.survey.mapper;

import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyStaffResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SurveyMapper {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public SurveyTimeSlotResponse toTimeSlotResponse(SurveyTimeSlot slot) {
        if (slot == null) {
            return null;
        }
        return SurveyTimeSlotResponse.builder()
                .id(slot.getId())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .isActive(slot.getIsActive())
                .label(slot.getStartTime().format(TIME_FORMAT) + " - " + slot.getEndTime().format(TIME_FORMAT))
                .build();
    }

    public SurveyStaffResponse toStaffResponse(Staff staff) {
        if (staff == null) {
            return null;
        }
        return SurveyStaffResponse.builder()
                .userId(staff.getId())
                .staffCode(staff.getStaffCode())
                .fullName(staff.getFullName())
                .build();
    }

    // Đọc booking / route / staff qua quan hệ entity: danh sách đã fetch join sẵn ở BookingSurveySpecification
    public SurveySummaryResponse toSummary(BookingSurvey survey) {
        Booking booking = survey.getBooking();
        return SurveySummaryResponse.builder()
                .id(survey.getId())
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .contactName(booking.getContactName())
                .contactPhone(booking.getContactPhone())
                .pickupAddress(booking.getRoute() != null ? booking.getRoute().getPickupAddress() : null)
                .surveyMethod(survey.getSurveyMethod())
                .surveyDate(survey.getSurveyDate())
                .timeSlot(toTimeSlotResponse(survey.getTimeSlot()))
                .staff(toStaffResponse(survey.getSurveyStaff()))
                .status(survey.getStatus())
                .createdAt(survey.getCreatedAt())
                .build();
    }

    public SurveyDetailResponse toDetail(BookingSurvey survey,
                                         BookingRouteResponse route,
                                         QuotationDraftDto currentDraft,
                                         List<QuotationItemDto> draftItems,
                                         List<SurveySummaryResponse> previousSurveys) {
        SurveySummaryResponse summary = toSummary(survey);
        return SurveyDetailResponse.builder()
                .id(summary.getId())
                .bookingId(summary.getBookingId())
                .bookingCode(summary.getBookingCode())
                .contactName(summary.getContactName())
                .contactPhone(summary.getContactPhone())
                .pickupAddress(summary.getPickupAddress())
                .surveyMethod(summary.getSurveyMethod())
                .surveyDate(summary.getSurveyDate())
                .timeSlot(summary.getTimeSlot())
                .staff(summary.getStaff())
                .status(summary.getStatus())
                .createdAt(summary.getCreatedAt())
                .actualStartAt(survey.getActualStartAt())
                .actualEndAt(survey.getActualEndAt())
                .note(survey.getNote())
                .route(route)
                .currentDraft(currentDraft)
                .draftItems(draftItems)
                .previousSurveys(previousSurveys)
                .build();
    }
}
