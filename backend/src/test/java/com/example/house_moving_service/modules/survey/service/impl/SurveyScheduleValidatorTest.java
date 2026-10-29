package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.StaffExternalScheduleChecker;
import com.example.house_moving_service.modules.survey.service.SurveyTimeSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyScheduleValidatorTest {

    // "Bây giờ" cố định: 2026-11-02 09:00 giờ Việt Nam
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(TODAY, LocalTime.of(9, 0), SurveyClockConfig.BUSINESS_ZONE).toInstant(),
            SurveyClockConfig.BUSINESS_ZONE);

    private static final long STAFF_ID = 8L;
    private static final int SLOT_ID = 2;

    @Mock
    private SurveyTimeSlotService surveyTimeSlotService;
    @Mock
    private StaffLookupService staffLookupService;
    @Mock
    private BookingSurveyRepository bookingSurveyRepository;
    @Mock
    private ObjectProvider<StaffExternalScheduleChecker> externalScheduleChecker;
    @Mock
    private StaffExternalScheduleChecker checker;

    private SurveyScheduleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SurveyScheduleValidator(surveyTimeSlotService, staffLookupService, bookingSurveyRepository,
                externalScheduleChecker, CLOCK);
    }

    private static SurveyTimeSlot slot(String start, String end) {
        return SurveyTimeSlot.builder().id(SLOT_ID)
                .startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end)).isActive(true).build();
    }

    private static ScheduleCheck full(LocalDate date, Integer excludeId) {
        return new ScheduleCheck(date, SLOT_ID, STAFF_ID, null, excludeId);
    }

    private void givenSlot(String start, String end) {
        when(surveyTimeSlotService.getActiveSlot(SLOT_ID)).thenReturn(slot(start, end));
    }

    private void givenValidMover() {
        when(staffLookupService.isActiveStaffWithRole(STAFF_ID, RoleCode.MOVER)).thenReturn(true);
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- đủ dữ liệu ----------

    @Test
    @DisplayName("Đủ dữ liệu, hợp lệ, không có bean checker → qua, trả về khung giờ")
    void testValidate_FullAndValid_NoChecker() {
        givenSlot("13:30", "15:30");
        givenValidMover();
        when(externalScheduleChecker.getIfAvailable()).thenReturn(null);

        SurveyTimeSlot result = validator.validate(full(TODAY.plusDays(1), null));

        assertThat(result.getId()).isEqualTo(SLOT_ID);
        verify(bookingSurveyRepository).existsSurveySlotConflict(
                STAFF_ID, TODAY.plusDays(1), SLOT_ID, SurveyStatus.OCCUPYING_SLOT, null);
    }

    @Test
    @DisplayName("Ngày khảo sát đã qua → ERR_SURVEY_INVALID_DATE")
    void testValidate_PastDate() {
        givenSlot("08:00", "10:00");

        assertError(() -> validator.validate(full(TODAY.minusDays(1), null)), ErrorCode.ERR_SURVEY_INVALID_DATE);
        verifyNoInteractions(staffLookupService, bookingSurveyRepository);
    }

    @Test
    @DisplayName("Hôm nay nhưng khung giờ đã bắt đầu (08:00 < 09:00) → ERR_SURVEY_INVALID_DATE")
    void testValidate_TodaySlotStarted() {
        givenSlot("08:00", "10:00");

        assertError(() -> validator.validate(full(TODAY, null)), ErrorCode.ERR_SURVEY_INVALID_DATE);
    }

    @Test
    @DisplayName("Hôm nay, khung giờ bắt đầu đúng 09:00 (= bây giờ) → coi như đã bắt đầu")
    void testValidate_TodaySlotStartsNow() {
        givenSlot("09:00", "11:00");

        assertError(() -> validator.validate(full(TODAY, null)), ErrorCode.ERR_SURVEY_INVALID_DATE);
    }

    @Test
    @DisplayName("Hôm nay, khung giờ chưa bắt đầu (10:00 > 09:00) → qua")
    void testValidate_TodaySlotNotStarted() {
        givenSlot("10:00", "12:00");
        givenValidMover();

        assertThatCode(() -> validator.validate(full(TODAY, null))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ngày khảo sát trùng hoặc sau ngày chuyển nhà → ERR_SURVEY_INVALID_DATE")
    void testValidate_NotBeforeMovingDate() {
        givenSlot("08:00", "10:00");
        LocalDateTime movingAt = TODAY.plusDays(3).atTime(7, 0);

        assertError(() -> validator.validate(new ScheduleCheck(TODAY.plusDays(3), SLOT_ID, STAFF_ID, movingAt, null)),
                ErrorCode.ERR_SURVEY_INVALID_DATE);
        assertError(() -> validator.validate(new ScheduleCheck(TODAY.plusDays(4), SLOT_ID, STAFF_ID, movingAt, null)),
                ErrorCode.ERR_SURVEY_INVALID_DATE);
    }

    @Test
    @DisplayName("Ngày khảo sát trước ngày chuyển nhà 1 ngày → qua")
    void testValidate_BeforeMovingDate() {
        givenSlot("08:00", "10:00");
        givenValidMover();
        LocalDateTime movingAt = TODAY.plusDays(3).atTime(7, 0);

        assertThatCode(() -> validator.validate(new ScheduleCheck(TODAY.plusDays(2), SLOT_ID, STAFF_ID, movingAt, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Khung giờ đã tắt → lỗi từ SurveyTimeSlotService lan ra")
    void testValidate_InactiveSlot() {
        when(surveyTimeSlotService.getActiveSlot(SLOT_ID))
                .thenThrow(new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_INACTIVE));

        assertError(() -> validator.validate(full(TODAY.plusDays(1), null)), ErrorCode.ERR_SURVEY_TIME_SLOT_INACTIVE);
    }

    @Test
    @DisplayName("Nhân viên không phải MOVER đang làm việc → ERR_SURVEY_STAFF_INVALID")
    void testValidate_InvalidStaff() {
        givenSlot("08:00", "10:00");
        when(staffLookupService.isActiveStaffWithRole(STAFF_ID, RoleCode.MOVER)).thenReturn(false);

        assertError(() -> validator.validate(full(TODAY.plusDays(1), null)), ErrorCode.ERR_SURVEY_STAFF_INVALID);
        verifyNoInteractions(bookingSurveyRepository);
    }

    @Test
    @DisplayName("Trùng lịch khảo sát khác → ERR_SURVEY_STAFF_CONFLICT, không hỏi lịch chuyển nhà")
    void testValidate_SurveyConflict() {
        givenSlot("08:00", "10:00");
        givenValidMover();
        when(bookingSurveyRepository.existsSurveySlotConflict(anyLong(), any(), anyInt(), any(), any())).thenReturn(true);

        assertError(() -> validator.validate(full(TODAY.plusDays(1), null)), ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
        verifyNoInteractions(externalScheduleChecker);
    }

    @Test
    @DisplayName("Đổi lịch: excludeSurveyId được truyền xuống repository")
    void testValidate_ExcludeIdPassedDown() {
        givenSlot("08:00", "10:00");
        givenValidMover();

        validator.validate(full(TODAY.plusDays(1), 15));

        verify(bookingSurveyRepository).existsSurveySlotConflict(
                STAFF_ID, TODAY.plusDays(1), SLOT_ID, SurveyStatus.OCCUPYING_SLOT, 15);
    }

    @Test
    @DisplayName("Checker báo bận chuyển nhà → ERR_SURVEY_STAFF_BUSY; khoảng giờ = [ngày + giờ bắt đầu, ngày + giờ kết thúc)")
    void testValidate_BusyElsewhere() {
        givenSlot("13:30", "15:30");
        givenValidMover();
        when(externalScheduleChecker.getIfAvailable()).thenReturn(checker);
        LocalDate date = TODAY.plusDays(1);
        when(checker.isBusy(STAFF_ID, date.atTime(13, 30), date.atTime(15, 30))).thenReturn(true);

        assertError(() -> validator.validate(full(date, null)), ErrorCode.ERR_SURVEY_STAFF_BUSY);
    }

    @Test
    @DisplayName("Checker báo rảnh → qua")
    void testValidate_CheckerFree() {
        givenSlot("13:30", "15:30");
        givenValidMover();
        when(externalScheduleChecker.getIfAvailable()).thenReturn(checker);
        when(checker.isBusy(eq(STAFF_ID), any(), any())).thenReturn(false);

        assertThatCode(() -> validator.validate(full(TODAY.plusDays(1), null))).doesNotThrowAnyException();
    }

    // ---------- thiếu dữ liệu ----------

    @Test
    @DisplayName("Có ngày + khung giờ, chưa có nhân viên → chỉ kiểm tra ngày và khung giờ")
    void testValidate_NoStaff() {
        givenSlot("08:00", "10:00");

        SurveyTimeSlot result = validator.validate(new ScheduleCheck(TODAY.plusDays(1), SLOT_ID, null, null, null));

        assertThat(result).isNotNull();
        verifyNoInteractions(staffLookupService, bookingSurveyRepository, externalScheduleChecker);
    }

    @Test
    @DisplayName("Chỉ có nhân viên → chỉ kiểm tra nhân viên hợp lệ, không kiểm tra trùng lịch")
    void testValidate_StaffOnly() {
        givenValidMover();

        SurveyTimeSlot result = validator.validate(new ScheduleCheck(null, null, STAFF_ID, null, null));

        assertThat(result).isNull();
        verifyNoInteractions(surveyTimeSlotService, bookingSurveyRepository, externalScheduleChecker);
    }

    @Test
    @DisplayName("Chỉ có ngày: hôm nay vẫn qua (không có khung giờ để so giờ), ngày đã qua thì lỗi")
    void testValidate_DateOnly() {
        assertThatCode(() -> validator.validate(new ScheduleCheck(TODAY, null, null, null, null)))
                .doesNotThrowAnyException();
        assertError(() -> validator.validate(new ScheduleCheck(TODAY.minusDays(1), null, null, null, null)),
                ErrorCode.ERR_SURVEY_INVALID_DATE);
        verifyNoInteractions(surveyTimeSlotService);
    }

    @Test
    @DisplayName("Có nhân viên + khung giờ nhưng chưa có ngày → không kiểm tra trùng lịch")
    void testValidate_NoDate() {
        givenSlot("08:00", "10:00");
        givenValidMover();

        validator.validate(new ScheduleCheck(null, SLOT_ID, STAFF_ID, null, null));

        verify(bookingSurveyRepository, never()).existsSurveySlotConflict(anyLong(), any(), anyInt(), any(), any());
        verifyNoInteractions(externalScheduleChecker);
    }
}
