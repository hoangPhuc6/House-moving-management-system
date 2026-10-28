package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.survey.dto.request.ResurveyRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.SurveyQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyManagementServiceResurveyTest {

    private static final int OLD_ID = 40;
    private static final int NEW_ID = 41;
    private static final int BOOKING_ID = 7;
    private static final long ACTOR_ID = 3L;
    private static final long STAFF_ID = 8L;
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

    private BookingSurvey givenOldSurvey(SurveyStatus status) {
        BookingSurvey survey = BookingSurvey.builder()
                .id(OLD_ID).booking(Booking.builder().id(BOOKING_ID).build())
                .surveyMethod(SurveyMethod.ONSITE).status(status)
                .build();
        when(bookingSurveyRepository.findByIdForUpdate(OLD_ID)).thenReturn(Optional.of(survey));
        return survey;
    }

    private void givenLatest(int latestId) {
        when(bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(BOOKING_ID))
                .thenReturn(Optional.of(BookingSurvey.builder().id(latestId).build()));
    }

    private void givenBooking(BookingStatus status) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID))
                .thenReturn(BookingSnapshot.builder().id(BOOKING_ID).status(status).build());
    }

    // Tạo lịch mới thành công: không có lịch active, lưu thì gán id cho bản ghi mới
    private void givenNewSurveyCanBeCreated() {
        when(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(BOOKING_ID, SurveyStatus.ACTIVE)).thenReturn(Optional.empty());
        when(bookingWorkflowService.getReference(BOOKING_ID)).thenReturn(Booking.builder().id(BOOKING_ID).build());
        when(bookingSurveyRepository.saveAndFlush(any(BookingSurvey.class))).thenAnswer(inv -> {
            BookingSurvey s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(NEW_ID);
            }
            return s;
        });
        when(surveyQueryService.getDetail(NEW_ID)).thenReturn(SurveyDetailResponse.builder().id(NEW_ID).build());
    }

    private static ResurveyRequest reason(String reason) {
        return ResurveyRequest.builder().reason(reason).build();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private BookingSurvey capturedNewSurvey() {
        ArgumentCaptor<BookingSurvey> captor = ArgumentCaptor.forClass(BookingSurvey.class);
        verify(bookingSurveyRepository, times(2)).saveAndFlush(captor.capture());
        List<BookingSurvey> saved = captor.getAllValues();
        return saved.get(1);
    }

    @Test
    @DisplayName("Từ COMPLETED mới nhất: lịch cũ RESURVEY_REQUIRED + note; báo giá hết hiệu lực; booking → PENDING_SURVEY; trả lịch MỚI")
    void testResurvey_Success_WithoutSchedule() {
        BookingSurvey old = givenOldSurvey(SurveyStatus.COMPLETED);
        givenLatest(OLD_ID);
        givenBooking(BookingStatus.SURVEYING);
        givenNewSurveyCanBeCreated();

        SurveyDetailResponse result = service.resurvey(OLD_ID, reason("  Khách mua thêm sofa  "), ACTOR_ID);

        assertThat(result.getId()).isEqualTo(NEW_ID);
        assertThat(old.getStatus()).isEqualTo(SurveyStatus.RESURVEY_REQUIRED);
        assertThat(old.getNote()).isEqualTo("[2026-11-02 09:00][RESURVEY][user#3] Khách mua thêm sofa");
        verify(quotationDraftService).expireOpenQuotations(BOOKING_ID, "Khảo sát lại: Khách mua thêm sofa");
        verify(bookingWorkflowService).changeStatus(BOOKING_ID, EnumSet.of(BookingStatus.SURVEYING, BookingStatus.QUOTED),
                BookingStatus.PENDING_SURVEY, ACTOR_ID, "Khảo sát lại: Khách mua thêm sofa");

        BookingSurvey created = capturedNewSurvey();
        assertThat(created.getStatus()).isEqualTo(SurveyStatus.PENDING);
        assertThat(created.getSurveyMethod()).isEqualTo(SurveyMethod.ONSITE);
        assertThat(created.getNote()).isEqualTo("[2026-11-02 09:00][RESURVEY][user#3] Khảo sát lại từ lịch #40: Khách mua thêm sofa");
    }

    @Test
    @DisplayName("Kèm ngày + khung giờ + nhân viên + phương thức → lịch mới CONFIRMED; validator nhận excludeSurveyId = null")
    void testResurvey_WithSchedule_Confirmed() {
        givenOldSurvey(SurveyStatus.COMPLETED);
        givenLatest(OLD_ID);
        givenBooking(BookingStatus.QUOTED);
        givenNewSurveyCanBeCreated();
        LocalDate day = TODAY.plusDays(2);
        when(surveyScheduleValidator.validate(any())).thenReturn(SurveyTimeSlot.builder().id(1).build());
        when(staffLookupService.getReference(STAFF_ID)).thenReturn(Staff.builder().id(STAFF_ID).build());

        service.resurvey(OLD_ID, ResurveyRequest.builder().reason("Khách mua thêm sofa").surveyMethod(SurveyMethod.ONLINE)
                .surveyDate(day).timeSlotId(1).staffId(STAFF_ID).build(), ACTOR_ID);

        verify(surveyScheduleValidator).validate(new ScheduleCheck(day, 1, STAFF_ID, null, null));
        BookingSurvey created = capturedNewSurvey();
        assertThat(created.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(created.getSurveyMethod()).isEqualTo(SurveyMethod.ONLINE);
        assertThat(created.getSurveyStaff().getId()).isEqualTo(STAFF_ID);
    }

    @Test
    @DisplayName("Thứ tự: lịch cũ rời trạng thái → báo giá hết hiệu lực → booking đổi trạng thái → mới tạo lịch mới")
    void testResurvey_Order() {
        givenOldSurvey(SurveyStatus.COMPLETED);
        givenLatest(OLD_ID);
        givenBooking(BookingStatus.SURVEYING);
        givenNewSurveyCanBeCreated();

        service.resurvey(OLD_ID, reason("x"), ACTOR_ID);

        InOrder order = inOrder(bookingSurveyRepository, quotationDraftService, bookingWorkflowService, surveyScheduleValidator);
        order.verify(bookingSurveyRepository).saveAndFlush(argThat(s -> s.getId() == OLD_ID));
        order.verify(quotationDraftService).expireOpenQuotations(any(), any());
        order.verify(bookingWorkflowService).changeStatus(any(), any(), any(), any(), any());
        order.verify(surveyScheduleValidator).validate(any());
    }

    @Test
    @DisplayName("Khách đã chấp nhận báo giá → ERR_QUOTATION_ALREADY_ACCEPTED, không đổi gì")
    void testResurvey_AcceptedQuotation() {
        BookingSurvey old = givenOldSurvey(SurveyStatus.COMPLETED);
        givenLatest(OLD_ID);
        givenBooking(BookingStatus.QUOTED);
        when(quotationDraftService.hasAcceptedQuotation(BOOKING_ID)).thenReturn(true);

        assertError(() -> service.resurvey(OLD_ID, reason("x"), ACTOR_ID), ErrorCode.ERR_QUOTATION_ALREADY_ACCEPTED);
        assertThat(old.getStatus()).isEqualTo(SurveyStatus.COMPLETED);
        verify(quotationDraftService, never()).expireOpenQuotations(any(), any());
        verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lịch COMPLETED nhưng không phải lần mới nhất → ERR_SURVEY_INVALID_STATUS")
    void testResurvey_NotLatest() {
        givenOldSurvey(SurveyStatus.COMPLETED);
        givenLatest(99);

        assertError(() -> service.resurvey(OLD_ID, reason("x"), ACTOR_ID), ErrorCode.ERR_SURVEY_INVALID_STATUS);
        verify(bookingWorkflowService, never()).getSnapshot(any());
    }

    @Test
    @DisplayName("Lịch chưa hoàn tất (CONFIRMED / IN_PROGRESS) → ERR_SURVEY_INVALID_STATUS")
    void testResurvey_NotCompleted() {
        for (SurveyStatus status : new SurveyStatus[]{SurveyStatus.CONFIRMED, SurveyStatus.IN_PROGRESS, SurveyStatus.RESURVEY_REQUIRED}) {
            givenOldSurvey(status);

            assertError(() -> service.resurvey(OLD_ID, reason("x"), ACTOR_ID), ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
        verify(quotationDraftService, never()).hasAcceptedQuotation(any());
    }

    @Test
    @DisplayName("Booking không ở SURVEYING / QUOTED (vd. đã hủy, đã duyệt) → ERR_SURVEY_BOOKING_INVALID_STATUS")
    void testResurvey_InvalidBookingStatus() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.CANCELLED, BookingStatus.APPROVED, BookingStatus.PENDING_SURVEY}) {
            givenOldSurvey(SurveyStatus.COMPLETED);
            givenLatest(OLD_ID);
            givenBooking(status);

            assertError(() -> service.resurvey(OLD_ID, reason("x"), ACTOR_ID), ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
        verify(quotationDraftService, never()).hasAcceptedQuotation(any());
    }

    @Test
    @DisplayName("Lịch mới bị validator chặn (trùng lịch MOVER) → lỗi lan ra để transaction hoàn tác các bước trước")
    void testResurvey_NewScheduleInvalid_Propagates() {
        givenOldSurvey(SurveyStatus.COMPLETED);
        givenLatest(OLD_ID);
        givenBooking(BookingStatus.SURVEYING);
        when(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(BOOKING_ID, SurveyStatus.ACTIVE)).thenReturn(Optional.empty());
        when(surveyScheduleValidator.validate(any())).thenThrow(new AppException(ErrorCode.ERR_SURVEY_STAFF_CONFLICT));

        assertError(() -> service.resurvey(OLD_ID, ResurveyRequest.builder().reason("x")
                .surveyDate(TODAY.plusDays(1)).timeSlotId(1).staffId(STAFF_ID).build(), ACTOR_ID), ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
        verify(surveyQueryService, never()).getDetail(any());
    }
}
