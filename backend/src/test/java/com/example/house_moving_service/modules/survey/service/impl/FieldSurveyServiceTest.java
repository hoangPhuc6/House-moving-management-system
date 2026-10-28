package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingItemSnapshot;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldSurveyServiceTest {

    private static final long MOVER_ID = 8L;
    private static final long OTHER_MOVER_ID = 9L;
    private static final int SURVEY_ID = 40;
    private static final int BOOKING_ID = 7;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(TODAY, LocalTime.of(9, 0), SurveyClockConfig.BUSINESS_ZONE).toInstant(),
            SurveyClockConfig.BUSINESS_ZONE);

    @Mock
    private BookingSurveyRepository bookingSurveyRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private QuotationDraftService quotationDraftService;

    private FieldSurveyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FieldSurveyServiceImpl(bookingSurveyRepository, bookingWorkflowService, quotationDraftService,
                new SurveyMapper(), CLOCK);
    }

    private static BookingSurvey survey(SurveyStatus status, Long staffId) {
        return BookingSurvey.builder()
                .id(SURVEY_ID)
                .booking(Booking.builder().id(BOOKING_ID).bookingCode("BK-0007").build())
                .surveyMethod(SurveyMethod.ONSITE).status(status).surveyDate(TODAY)
                .surveyStaff(staffId != null ? Staff.builder().id(staffId).build() : null)
                .build();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- getMySurveys ----------

    @Test
    @DisplayName("getMySurveys: không truyền ngày → hôm nay đến 7 ngày sau, mọi trạng thái, chỉ lịch của người gọi")
    void testGetMySurveys_Defaults() {
        when(bookingSurveyRepository.findByStaffAndDateRange(MOVER_ID, TODAY, TODAY.plusDays(7), EnumSet.allOf(SurveyStatus.class)))
                .thenReturn(List.of(survey(SurveyStatus.CONFIRMED, MOVER_ID)));

        assertThat(service.getMySurveys(MOVER_ID, null, null, null))
                .singleElement()
                .satisfies(s -> assertThat(s.getBookingCode()).isEqualTo("BK-0007"));
    }

    @Test
    @DisplayName("getMySurveys: chỉ truyền from → to = from + 7; lọc theo một trạng thái")
    void testGetMySurveys_FromAndStatus() {
        LocalDate from = TODAY.plusDays(3);
        when(bookingSurveyRepository.findByStaffAndDateRange(MOVER_ID, from, from.plusDays(7), EnumSet.of(SurveyStatus.COMPLETED)))
                .thenReturn(List.of());

        assertThat(service.getMySurveys(MOVER_ID, from, null, SurveyStatus.COMPLETED)).isEmpty();
    }

    @Test
    @DisplayName("getMySurveys: to trước from → ERR_INVALID_INPUT; khoảng hơn 31 ngày → ERR_INVALID_INPUT")
    void testGetMySurveys_InvalidRange() {
        assertError(() -> service.getMySurveys(MOVER_ID, TODAY, TODAY.minusDays(1), null), ErrorCode.ERR_INVALID_INPUT);
        assertError(() -> service.getMySurveys(MOVER_ID, TODAY, TODAY.plusDays(32), null), ErrorCode.ERR_INVALID_INPUT);
        verifyNoInteractions(bookingSurveyRepository);
    }

    @Test
    @DisplayName("getMySurveys: đúng 31 ngày vẫn được")
    void testGetMySurveys_MaxRangeAllowed() {
        when(bookingSurveyRepository.findByStaffAndDateRange(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service.getMySurveys(MOVER_ID, TODAY, TODAY.plusDays(31), null)).isEmpty();
    }

    // ---------- getMySurvey ----------

    private void givenBookingData() {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(BookingSnapshot.builder()
                .id(BOOKING_ID).bookingCode("BK-0007").contactName("Nguyễn Văn A").contactPhone("0901234567")
                .desiredStartAt(TODAY.plusDays(6).atTime(8, 0)).note("Gọi trước 30 phút").build());
        when(bookingWorkflowService.getRoute(BOOKING_ID))
                .thenReturn(BookingRouteResponse.builder().pickupAddress("12 Lê Lợi, Q1").build());
        when(bookingWorkflowService.getItems(BOOKING_ID))
                .thenReturn(List.of(BookingItemSnapshot.builder().id(1).itemName("Tủ lạnh").quantity(1).build()));
    }

    @Test
    @DisplayName("getMySurvey: lịch CONFIRMED của mình → có liên hệ, tuyến đường, đồ khách khai; chưa có bản nháp")
    void testGetMySurvey_Confirmed_NoDraft() {
        when(bookingSurveyRepository.findById(SURVEY_ID)).thenReturn(Optional.of(survey(SurveyStatus.CONFIRMED, MOVER_ID)));
        givenBookingData();

        FieldSurveyResponse result = service.getMySurvey(SURVEY_ID, MOVER_ID);

        assertThat(result.getBooking().getContactPhone()).isEqualTo("0901234567");
        assertThat(result.getBooking().getNote()).isEqualTo("Gọi trước 30 phút");
        assertThat(result.getRoute().getPickupAddress()).isEqualTo("12 Lê Lợi, Q1");
        assertThat(result.getDeclaredItems()).extracting(BookingItemSnapshot::itemName).containsExactly("Tủ lạnh");
        assertThat(result.getDraftItems()).isNull();
        verifyNoInteractions(quotationDraftService);
    }

    @Test
    @DisplayName("getMySurvey: lịch IN_PROGRESS mới nhất → có đồ đạc trên bản nháp")
    void testGetMySurvey_InProgress_WithDraft() {
        BookingSurvey inProgress = survey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(bookingSurveyRepository.findById(SURVEY_ID)).thenReturn(Optional.of(inProgress));
        when(bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(Optional.of(inProgress));
        givenBookingData();
        when(quotationDraftService.findCurrentDraft(BOOKING_ID)).thenReturn(Optional.of(QuotationDraftDto.builder().id(30).build()));
        when(quotationDraftService.listItems(30)).thenReturn(List.of(
                QuotationItemDto.builder().id(1).itemName("Tủ lạnh").build(),
                QuotationItemDto.builder().id(2).itemName("Sofa").build()));

        FieldSurveyResponse result = service.getMySurvey(SURVEY_ID, MOVER_ID);

        assertThat(result.getDraftItems()).extracting(QuotationItemDto::itemName).containsExactly("Tủ lạnh", "Sofa");
    }

    @Test
    @DisplayName("getMySurvey: lịch COMPLETED nhưng không phải lần mới nhất → không hiện bản nháp của lần sau")
    void testGetMySurvey_CompletedNotLatest_NoDraft() {
        when(bookingSurveyRepository.findById(SURVEY_ID)).thenReturn(Optional.of(survey(SurveyStatus.COMPLETED, MOVER_ID)));
        when(bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(BOOKING_ID))
                .thenReturn(Optional.of(BookingSurvey.builder().id(99).build()));
        givenBookingData();

        assertThat(service.getMySurvey(SURVEY_ID, MOVER_ID).getDraftItems()).isNull();
        verify(quotationDraftService, never()).findCurrentDraft(any());
    }

    @Test
    @DisplayName("getMySurvey: lịch của MOVER khác / chưa phân công → ERR_SURVEY_FORBIDDEN, không lộ dữ liệu đơn")
    void testGetMySurvey_NotOwner() {
        when(bookingSurveyRepository.findById(SURVEY_ID)).thenReturn(Optional.of(survey(SurveyStatus.CONFIRMED, OTHER_MOVER_ID)));
        assertError(() -> service.getMySurvey(SURVEY_ID, MOVER_ID), ErrorCode.ERR_SURVEY_FORBIDDEN);

        when(bookingSurveyRepository.findById(SURVEY_ID)).thenReturn(Optional.of(survey(SurveyStatus.PENDING, null)));
        assertError(() -> service.getMySurvey(SURVEY_ID, MOVER_ID), ErrorCode.ERR_SURVEY_FORBIDDEN);

        verifyNoInteractions(bookingWorkflowService, quotationDraftService);
    }

    @Test
    @DisplayName("getMySurvey: không tồn tại → ERR_SURVEY_NOT_FOUND")
    void testGetMySurvey_NotFound() {
        when(bookingSurveyRepository.findById(99)).thenReturn(Optional.empty());

        assertError(() -> service.getMySurvey(99, MOVER_ID), ErrorCode.ERR_SURVEY_NOT_FOUND);
    }
}
