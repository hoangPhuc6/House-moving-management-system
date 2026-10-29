package com.example.house_moving_service.modules.survey.dto.response;

import com.example.house_moving_service.modules.booking.dto.response.BookingItemSnapshot;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
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
 * Chi tiết một lịch khảo sát cho MOVER được phân công: những gì cần mang theo khi tới nhà khách
 * (liên hệ, tuyến đường kèm điều kiện ra vào, đồ khách đã khai) và đồ đạc đang ghi trên bản nháp.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldSurveyResponse {

    private Integer id;
    private SurveyStatus status;
    private SurveyMethod surveyMethod;
    private LocalDate surveyDate;
    private SurveyTimeSlotResponse timeSlot;
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private String note;

    private FieldBookingInfo booking;
    private BookingRouteResponse route;

    // Đồ đạc khách khai lúc đặt đơn, để đối chiếu tại nhà
    private List<BookingItemSnapshot> declaredItems;

    // Đồ đạc trên bản nháp báo giá hiện tại; null khi chưa bắt đầu khảo sát
    private List<QuotationItemDto> draftItems;
}
