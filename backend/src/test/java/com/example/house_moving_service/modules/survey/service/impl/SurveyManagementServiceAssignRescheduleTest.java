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
import com.example.house_moving_service.modules.survey.dto.request.AssignSurveyStaffRequest;
import com.example.house_moving_service.modules.survey.dto.request.RescheduleSurveyRequest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyManagementServiceAssignRescheduleTest {

    private static final int SURVEY_ID = 40;
    private static final int BOOKING_ID = 7;
    private static final long ACTOR_ID = 3L;
    private static final long OLD_STAFF = 5L;
    private static final long NEW_STAFF = 8L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate DAY = TODAY.plusDays(1);
    private static final LocalDateTime DESIRED_START = TODAY.plusDays(5).atTime(8, 0);
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(TODAY, LocalTime.of(9, 0), SurveyClockConfig.BUSINESS_ZONE).toInstant(),
            SurveyClockConfig.BUSINESS_ZONE);

    private static final SurveyTimeSlot SLOT_0800 = SurveyTimeSlot.builder().id(1)
            .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).isActive(true).build();
    private static final SurveyTimeSlot SLOT_1330 = SurveyTimeSlot.builder().id(3)
            .startTime(LocalTime.of(13, 30)).endTime(LocalTime.of(15, 30)).isActive(true).build();

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

    private static Staff staff(long id) {
        return Staff.builder().id(id).build();
    }

    private BookingSurvey givenSurvey(SurveyStatus status, LocalDate date, SurveyTimeSlot slot, Long staffId) {
        BookingSurvey survey = BookingSurvey.builder()
                .id(SURVEY_ID).booking(Booking.builder().id(BOOKING_ID).build())
                .surveyMethod(SurveyMethod.ONSITE).status(status)
                .surveyDate(date).timeSlot(slot).surveyStaff(staffId != null ? staff(staffId) : null)
                .build();
        when(bookingSurveyRepository.findByIdForUpdate(SURVEY_ID)).thenReturn(Optional.of(survey));
        return survey;
    }

    private void givenBooking(BookingStatus status) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(BookingSnapshot.builder()
                .id(BOOKING_ID).status(status).desiredStartAt(DESIRED_START).build());
    }

    private void givenDetailReturned() {
        when(surveyQueryService.getDetail(SURVEY_ID)).thenReturn(SurveyDetailResponse.builder().id(SURVEY_ID).build());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- assign ----------

    private static AssignSurveyStaffRequest assignTo(long staffId) {
        return AssignSurveyStaffRequest.builder().staffId(staffId).build();
    }

    @Test
    @DisplayName("assign: PENDING đã có ngày + khung giờ → CONFIRMED; validator chạy đủ với excludeSurveyId = id; note lần đầu '(chưa có)'")
    void testAssign_PendingWithSchedule_Confirmed() {
        BookingSurvey survey = givenSurvey(SurveyStatus.PENDING, DAY, SLOT_0800, null);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(staffLookupService.getReference(NEW_STAFF)).thenReturn(staff(NEW_STAFF));
        givenDetailReturned();

        SurveyDetailResponse result = service.assign(SURVEY_ID, assignTo(NEW_STAFF), ACTOR_ID);

        assertThat(result.getId()).isEqualTo(SURVEY_ID);
        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(survey.getSurveyStaff().getId()).isEqualTo(NEW_STAFF);
        assertThat(survey.getNote()).isEqualTo("[2026-11-02 09:00][ASSIGN][user#3] Phân công: (chưa có) → user#8");
        verify(surveyScheduleValidator).validate(new ScheduleCheck(DAY, SLOT_0800.getId(), NEW_STAFF, DESIRED_START, SURVEY_ID));
        verify(bookingSurveyRepository).saveAndFlush(survey);
    }

    @Test
    @DisplayName("assign: PENDING chưa có ngày / khung giờ → vẫn PENDING; validator chỉ nhận nhân viên")
    void testAssign_PendingWithoutSchedule_StaysPending() {
        BookingSurvey survey = givenSurvey(SurveyStatus.PENDING, null, null, null);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(staffLookupService.getReference(NEW_STAFF)).thenReturn(staff(NEW_STAFF));
        givenDetailReturned();

        service.assign(SURVEY_ID, assignTo(NEW_STAFF), ACTOR_ID);

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.PENDING);
        verify(surveyScheduleValidator).validate(new ScheduleCheck(null, null, NEW_STAFF, null, SURVEY_ID));
    }

    @Test
    @DisplayName("assign: CONFIRMED đổi người → vẫn CONFIRMED; note ghi người cũ → người mới")
    void testAssign_ConfirmedChangeStaff() {
        BookingSurvey survey = givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(staffLookupService.getReference(NEW_STAFF)).thenReturn(staff(NEW_STAFF));
        givenDetailReturned();

        service.assign(SURVEY_ID, assignTo(NEW_STAFF), ACTOR_ID);

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(survey.getNote()).endsWith("Phân công: user#5 → user#8");
    }

    @Test
    @DisplayName("assign: phân công lại đúng người đang phụ trách → không kiểm tra, không lưu, không ghi note")
    void testAssign_SameStaff_NoChange() {
        BookingSurvey survey = givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
        givenBooking(BookingStatus.PENDING_SURVEY);
        givenDetailReturned();

        service.assign(SURVEY_ID, assignTo(OLD_STAFF), ACTOR_ID);

        assertThat(survey.getNote()).isNull();
        verifyNoInteractions(surveyScheduleValidator);
        verify(bookingSurveyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assign: lịch đã IN_PROGRESS / COMPLETED → ERR_SURVEY_INVALID_STATUS")
    void testAssign_InvalidStatus() {
        for (SurveyStatus status : new SurveyStatus[]{SurveyStatus.IN_PROGRESS, SurveyStatus.COMPLETED, SurveyStatus.CANCELLED}) {
            givenSurvey(status, DAY, SLOT_0800, OLD_STAFF);

            assertError(() -> service.assign(SURVEY_ID, assignTo(NEW_STAFF), ACTOR_ID), ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
        verifyNoInteractions(surveyScheduleValidator);
    }

    @Test
    @DisplayName("assign: lịch không tồn tại → ERR_SURVEY_NOT_FOUND")
    void testAssign_NotFound() {
        when(bookingSurveyRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());

        assertError(() -> service.assign(99, assignTo(NEW_STAFF), ACTOR_ID), ErrorCode.ERR_SURVEY_NOT_FOUND);
    }

    @Test
    @DisplayName("assign: đơn đã hủy / hoàn tất → ERR_SURVEY_BOOKING_INVALID_STATUS")
    void testAssign_BookingClosed() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.CANCELLED, BookingStatus.COMPLETED}) {
            givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
            givenBooking(status);

            assertError(() -> service.assign(SURVEY_ID, assignTo(NEW_STAFF), ACTOR_ID), ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
        verifyNoInteractions(surveyScheduleValidator);
    }

    @Test
    @DisplayName("assign: validator báo trùng → lỗi lan ra, lịch giữ người cũ, không lưu")
    void testAssign_ValidatorFails() {
        BookingSurvey survey = givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(surveyScheduleValidator.validate(any())).thenThrow(new AppException(ErrorCode.ERR_SURVEY_STAFF_CONFLICT));

        assertError(() -> service.assign(SURVEY_ID, assignTo(NEW_STAFF), ACTOR_ID), ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
        assertThat(survey.getSurveyStaff().getId()).isEqualTo(OLD_STAFF);
        verify(bookingSurveyRepository, never()).saveAndFlush(any());
    }

    // ---------- reschedule ----------

    private static RescheduleSurveyRequest rescheduleTo(LocalDate date, int slotId, Long staffId, SurveyMethod method) {
        return RescheduleSurveyRequest.builder()
                .surveyDate(date).timeSlotId(slotId).staffId(staffId).surveyMethod(method)
                .reason("  Khách bận buổi sáng  ").build();
    }

    @Test
    @DisplayName("reschedule: dời sang ngày / khung giờ mới, giữ người và phương thức; note có lịch cũ → mới + lý do")
    void testReschedule_Success_KeepStaffAndMethod() {
        BookingSurvey survey = givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
        givenBooking(BookingStatus.PENDING_SURVEY);
        LocalDate newDay = DAY.plusDays(2);
        when(surveyScheduleValidator.validate(any())).thenReturn(SLOT_1330);
        givenDetailReturned();

        service.reschedule(SURVEY_ID, rescheduleTo(newDay, SLOT_1330.getId(), null, null), ACTOR_ID);

        verify(surveyScheduleValidator).validate(new ScheduleCheck(newDay, SLOT_1330.getId(), OLD_STAFF, DESIRED_START, SURVEY_ID));
        assertThat(survey.getSurveyDate()).isEqualTo(newDay);
        assertThat(survey.getTimeSlot()).isSameAs(SLOT_1330);
        assertThat(survey.getSurveyStaff().getId()).isEqualTo(OLD_STAFF);
        assertThat(survey.getSurveyMethod()).isEqualTo(SurveyMethod.ONSITE);
        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(survey.getNote()).isEqualTo("[2026-11-02 09:00][RESCHEDULE][user#3] "
                + "2026-11-03 08:00-10:00, user#5, ONSITE → 2026-11-05 13:30-15:30, user#5, ONSITE. Lý do: Khách bận buổi sáng");
        verify(bookingSurveyRepository).saveAndFlush(survey);
        verify(staffLookupService, never()).getReference(any());
    }

    @Test
    @DisplayName("reschedule: kèm đổi người và phương thức → validator kiểm tra người mới; lịch nhận người / phương thức mới")
    void testReschedule_ChangeStaffAndMethod() {
        BookingSurvey survey = givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(surveyScheduleValidator.validate(any())).thenReturn(SLOT_0800);
        when(staffLookupService.getReference(NEW_STAFF)).thenReturn(staff(NEW_STAFF));
        givenDetailReturned();

        service.reschedule(SURVEY_ID, rescheduleTo(DAY.plusDays(1), SLOT_0800.getId(), NEW_STAFF, SurveyMethod.ONLINE), ACTOR_ID);

        verify(surveyScheduleValidator).validate(new ScheduleCheck(DAY.plusDays(1), SLOT_0800.getId(), NEW_STAFF, DESIRED_START, SURVEY_ID));
        assertThat(survey.getSurveyStaff().getId()).isEqualTo(NEW_STAFF);
        assertThat(survey.getSurveyMethod()).isEqualTo(SurveyMethod.ONLINE);
        assertThat(survey.getNote()).contains("→ 2026-11-04 08:00-10:00, user#8, ONLINE");
    }

    @Test
    @DisplayName("reschedule: PENDING chưa có ai → vẫn PENDING sau khi có ngày + khung giờ; note ghi '(chưa có ngày)'")
    void testReschedule_PendingNoStaff_StaysPending() {
        BookingSurvey survey = givenSurvey(SurveyStatus.PENDING, null, null, null);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(surveyScheduleValidator.validate(any())).thenReturn(SLOT_0800);
        givenDetailReturned();

        service.reschedule(SURVEY_ID, rescheduleTo(DAY, SLOT_0800.getId(), null, null), ACTOR_ID);

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.PENDING);
        assertThat(survey.getNote()).contains("(chưa có ngày) (chưa có khung giờ), (chưa có), ONSITE → 2026-11-03 08:00-10:00, (chưa có), ONSITE");
    }

    @Test
    @DisplayName("reschedule: PENDING có người, thêm ngày + khung giờ → CONFIRMED")
    void testReschedule_PendingWithStaff_Confirmed() {
        BookingSurvey survey = givenSurvey(SurveyStatus.PENDING, null, null, OLD_STAFF);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(surveyScheduleValidator.validate(any())).thenReturn(SLOT_0800);
        givenDetailReturned();

        service.reschedule(SURVEY_ID, rescheduleTo(DAY, SLOT_0800.getId(), null, null), ACTOR_ID);

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
    }

    @Test
    @DisplayName("reschedule: validator báo trùng → lỗi lan ra, lịch giữ nguyên, không lưu")
    void testReschedule_ValidatorFails() {
        BookingSurvey survey = givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(surveyScheduleValidator.validate(any())).thenThrow(new AppException(ErrorCode.ERR_SURVEY_STAFF_CONFLICT));

        assertError(() -> service.reschedule(SURVEY_ID, rescheduleTo(DAY.plusDays(1), SLOT_1330.getId(), null, null), ACTOR_ID),
                ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
        assertThat(survey.getSurveyDate()).isEqualTo(DAY);
        assertThat(survey.getTimeSlot()).isSameAs(SLOT_0800);
        assertThat(survey.getNote()).isNull();
        verify(bookingSurveyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reschedule: lịch đã IN_PROGRESS → ERR_SURVEY_INVALID_STATUS (đã bắt đầu thì không dời được)")
    void testReschedule_InProgress() {
        givenSurvey(SurveyStatus.IN_PROGRESS, DAY, SLOT_0800, OLD_STAFF);

        assertError(() -> service.reschedule(SURVEY_ID, rescheduleTo(DAY.plusDays(1), SLOT_1330.getId(), null, null), ACTOR_ID),
                ErrorCode.ERR_SURVEY_INVALID_STATUS);
        verifyNoInteractions(surveyScheduleValidator);
    }

    @Test
    @DisplayName("reschedule: đơn đã hủy → ERR_SURVEY_BOOKING_INVALID_STATUS")
    void testReschedule_BookingCancelled() {
        givenSurvey(SurveyStatus.CONFIRMED, DAY, SLOT_0800, OLD_STAFF);
        givenBooking(BookingStatus.CANCELLED);

        assertError(() -> service.reschedule(SURVEY_ID, rescheduleTo(DAY.plusDays(1), SLOT_1330.getId(), null, null), ACTOR_ID),
                ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
    }
}
