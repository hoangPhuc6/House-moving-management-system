package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.survey.dto.request.CancelSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.FailSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.SurveyQueryService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyManagementServiceCancelFailTest {

    private static final int SURVEY_ID = 40;
    private static final int BOOKING_ID = 7;
    private static final long ACTOR_ID = 3L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(TODAY, LocalTime.of(9, 0), SurveyClockConfig.BUSINESS_ZONE).toInstant(),
            SurveyClockConfig.BUSINESS_ZONE);

    @Mock
    private BookingSurveyRepository bookingSurveyRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private StaffLookupService staffLookupService;
    @Mock
    private SurveyScheduleValidator surveyScheduleValidator;
    @Mock
    private SurveyQueryService surveyQueryService;
    @Mock
    private QuotationDraftService quotationDraftService;

    private SurveyManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SurveyManagementServiceImpl(bookingSurveyRepository, bookingWorkflowService, staffLookupService,
                surveyScheduleValidator, surveyQueryService, quotationDraftService, CLOCK);
    }

    private BookingSurvey givenSurvey(SurveyStatus status) {
        BookingSurvey survey = BookingSurvey.builder()
                .id(SURVEY_ID).booking(Booking.builder().id(BOOKING_ID).build())
                .surveyMethod(SurveyMethod.ONSITE).status(status)
                .build();
        when(bookingSurveyRepository.findByIdForUpdate(SURVEY_ID)).thenReturn(Optional.of(survey));
        return survey;
    }

    private void givenBooking(BookingStatus status) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID))
                .thenReturn(BookingSnapshot.builder().id(BOOKING_ID).status(status).build());
    }

    private void givenDetailReturned() {
        when(surveyQueryService.getDetail(SURVEY_ID)).thenReturn(SurveyDetailResponse.builder().id(SURVEY_ID).build());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private static CancelSurveyRequest cancelReason(String reason) {
        return CancelSurveyRequest.builder().reason(reason).build();
    }

    private static FailSurveyRequest failReason(String reason) {
        return FailSurveyRequest.builder().reason(reason).build();
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("cancel: PENDING / CONFIRMED → CANCELLED + note CANCEL; không đổi booking, không đụng báo giá")
    void testCancel_Success() {
        for (SurveyStatus status : new SurveyStatus[]{SurveyStatus.PENDING, SurveyStatus.CONFIRMED}) {
            BookingSurvey survey = givenSurvey(status);
            givenDetailReturned();

            service.cancel(SURVEY_ID, cancelReason("  Xếp nhầm lịch  "), ACTOR_ID);

            assertThat(survey.getStatus()).isEqualTo(SurveyStatus.CANCELLED);
            assertThat(survey.getNote()).isEqualTo("[2026-11-02 09:00][CANCEL][user#3] Xếp nhầm lịch");
        }
        verifyNoInteractions(quotationDraftService);
        verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancel: IN_PROGRESS / COMPLETED / CANCELLED → ERR_SURVEY_INVALID_STATUS, không lưu")
    void testCancel_InvalidStatus() {
        for (SurveyStatus status : new SurveyStatus[]{SurveyStatus.IN_PROGRESS, SurveyStatus.COMPLETED, SurveyStatus.CANCELLED}) {
            givenSurvey(status);

            assertError(() -> service.cancel(SURVEY_ID, cancelReason("x"), ACTOR_ID), ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
        verify(bookingSurveyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancel: không tồn tại → ERR_SURVEY_NOT_FOUND")
    void testCancel_NotFound() {
        when(bookingSurveyRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());

        assertError(() -> service.cancel(99, cancelReason("x"), ACTOR_ID), ErrorCode.ERR_SURVEY_NOT_FOUND);
    }

    // ---------- fail ----------

    @Test
    @DisplayName("fail: IN_PROGRESS → RESURVEY_REQUIRED + note FAIL; bản nháp EXPIRED; booking SURVEYING → PENDING_SURVEY")
    void testFail_InProgress() {
        BookingSurvey survey = givenSurvey(SurveyStatus.IN_PROGRESS);
        givenBooking(BookingStatus.SURVEYING);
        givenDetailReturned();

        service.fail(SURVEY_ID, failReason(" Khách vắng nhà "), ACTOR_ID);

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.RESURVEY_REQUIRED);
        assertThat(survey.getNote()).isEqualTo("[2026-11-02 09:00][FAIL][user#3] Khách vắng nhà");
        verify(quotationDraftService).expireDraft(BOOKING_ID, "Khảo sát thất bại: Khách vắng nhà");
        verify(bookingWorkflowService).changeStatus(BOOKING_ID, EnumSet.of(BookingStatus.SURVEYING),
                BookingStatus.PENDING_SURVEY, ACTOR_ID, "Khảo sát thất bại: Khách vắng nhà");
        verify(bookingSurveyRepository).saveAndFlush(survey);
    }

    @Test
    @DisplayName("fail: CONFIRMED (chưa bấm bắt đầu, booking chưa SURVEYING) → RESURVEY_REQUIRED, không đổi trạng thái booking")
    void testFail_ConfirmedBeforeStart() {
        BookingSurvey survey = givenSurvey(SurveyStatus.CONFIRMED);
        givenBooking(BookingStatus.PENDING_SURVEY);
        givenDetailReturned();

        service.fail(SURVEY_ID, failReason("Không vào được chung cư"), ACTOR_ID);

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.RESURVEY_REQUIRED);
        verify(quotationDraftService).expireDraft(BOOKING_ID, "Khảo sát thất bại: Không vào được chung cư");
        verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fail: PENDING / COMPLETED → ERR_SURVEY_INVALID_STATUS, không đụng booking / báo giá")
    void testFail_InvalidStatus() {
        for (SurveyStatus status : new SurveyStatus[]{SurveyStatus.PENDING, SurveyStatus.COMPLETED, SurveyStatus.RESURVEY_REQUIRED}) {
            givenSurvey(status);

            assertError(() -> service.fail(SURVEY_ID, failReason("x"), ACTOR_ID), ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
        verifyNoInteractions(quotationDraftService, bookingWorkflowService);
    }

    @Test
    @DisplayName("fail: đơn đã hủy → ERR_SURVEY_BOOKING_INVALID_STATUS")
    void testFail_BookingCancelled() {
        givenSurvey(SurveyStatus.IN_PROGRESS);
        givenBooking(BookingStatus.CANCELLED);

        assertError(() -> service.fail(SURVEY_ID, failReason("x"), ACTOR_ID), ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        verifyNoInteractions(quotationDraftService);
    }

    @Test
    @DisplayName("fail: đổi trạng thái booking lỗi → lỗi lan ra (transaction rollback cả survey lẫn báo giá)")
    void testFail_BookingChangeFails() {
        givenSurvey(SurveyStatus.IN_PROGRESS);
        givenBooking(BookingStatus.SURVEYING);
        doThrow(new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS))
                .when(bookingWorkflowService).changeStatus(any(), any(), any(), any(), any());

        assertError(() -> service.fail(SURVEY_ID, failReason("x"), ACTOR_ID), ErrorCode.ERR_BOOKING_INVALID_STATUS);
        verify(surveyQueryService, never()).getDetail(any());
    }
}
