package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyManagementServiceCreateTest {

    private static final int BOOKING_ID = 7;
    private static final long STAFF_ID = 8L;
    private static final long ACTOR_ID = 3L;
    private static final int SLOT_ID = 2;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate SURVEY_DATE = TODAY.plusDays(1);
    private static final LocalDateTime DESIRED_START = TODAY.plusDays(5).atTime(8, 0);
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

    private SurveyManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SurveyManagementServiceImpl(bookingSurveyRepository, bookingWorkflowService, staffLookupService,
                surveyScheduleValidator, surveyQueryService, CLOCK);
    }

    private void givenBooking(BookingStatus status) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(BookingSnapshot.builder()
                .id(BOOKING_ID).bookingCode("BK-0007").status(status).desiredStartAt(DESIRED_START).build());
    }

    private void givenNoActiveSurvey() {
        when(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(BOOKING_ID, SurveyStatus.ACTIVE))
                .thenReturn(Optional.empty());
    }

    private void givenSaveSucceeds() {
        when(bookingWorkflowService.getReference(BOOKING_ID)).thenReturn(Booking.builder().id(BOOKING_ID).build());
        when(bookingSurveyRepository.saveAndFlush(any(BookingSurvey.class))).thenAnswer(inv -> {
            BookingSurvey s = inv.getArgument(0);
            s.setId(40);
            return s;
        });
        when(surveyQueryService.getDetail(40)).thenReturn(SurveyDetailResponse.builder().id(40).build());
    }

    private static CreateSurveyRequest fullRequest() {
        return CreateSurveyRequest.builder()
                .bookingId(BOOKING_ID).surveyMethod(SurveyMethod.ONSITE)
                .surveyDate(SURVEY_DATE).timeSlotId(SLOT_ID).staffId(STAFF_ID)
                .build();
    }

    private BookingSurvey captureSaved() {
        ArgumentCaptor<BookingSurvey> captor = ArgumentCaptor.forClass(BookingSurvey.class);
        verify(bookingSurveyRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Đủ ngày + khung giờ + nhân viên → CONFIRMED, gắn khung giờ do validator trả về, trả chi tiết lịch mới")
    void testCreate_Full_Confirmed() {
        givenBooking(BookingStatus.PENDING_SURVEY);
        givenNoActiveSurvey();
        SurveyTimeSlot slot = SurveyTimeSlot.builder().id(SLOT_ID).build();
        when(surveyScheduleValidator.validate(any())).thenReturn(slot);
        when(staffLookupService.getReference(STAFF_ID)).thenReturn(Staff.builder().id(STAFF_ID).build());
        givenSaveSucceeds();

        SurveyDetailResponse result = service.create(fullRequest(), ACTOR_ID);

        assertThat(result.getId()).isEqualTo(40);
        BookingSurvey saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(saved.getTimeSlot()).isSameAs(slot);
        assertThat(saved.getSurveyStaff().getId()).isEqualTo(STAFF_ID);
        assertThat(saved.getSurveyDate()).isEqualTo(SURVEY_DATE);
        assertThat(saved.getNote()).isNull();
    }

    @Test
    @DisplayName("Validator nhận đúng dữ liệu: ngày, khung giờ, nhân viên, ngày chuyển nhà; excludeSurveyId = null (tạo mới)")
    void testCreate_ValidatorReceivesScheduleCheck() {
        givenBooking(BookingStatus.PENDING);
        givenNoActiveSurvey();
        when(staffLookupService.getReference(STAFF_ID)).thenReturn(Staff.builder().id(STAFF_ID).build());
        givenSaveSucceeds();

        service.create(fullRequest(), ACTOR_ID);

        verify(surveyScheduleValidator).validate(new ScheduleCheck(SURVEY_DATE, SLOT_ID, STAFF_ID, DESIRED_START, null));
    }

    @Test
    @DisplayName("Thiếu nhân viên → PENDING; không lấy reference nhân viên")
    void testCreate_NoStaff_Pending() {
        givenBooking(BookingStatus.PENDING);
        givenNoActiveSurvey();
        when(surveyScheduleValidator.validate(any())).thenReturn(SurveyTimeSlot.builder().id(SLOT_ID).build());
        givenSaveSucceeds();
        CreateSurveyRequest request = fullRequest();
        request.setStaffId(null);

        service.create(request, ACTOR_ID);

        BookingSurvey saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(SurveyStatus.PENDING);
        assertThat(saved.getSurveyStaff()).isNull();
        verify(staffLookupService, never()).getReference(anyLong());
    }

    @Test
    @DisplayName("Chỉ có phương thức (chưa xếp lịch) → PENDING, ngày / khung giờ / nhân viên đều trống")
    void testCreate_NothingScheduled_Pending() {
        givenBooking(BookingStatus.PENDING);
        givenNoActiveSurvey();
        givenSaveSucceeds();
        CreateSurveyRequest request = CreateSurveyRequest.builder()
                .bookingId(BOOKING_ID).surveyMethod(SurveyMethod.PHONE).build();

        service.create(request, ACTOR_ID);

        BookingSurvey saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(SurveyStatus.PENDING);
        assertThat(saved.getSurveyDate()).isNull();
        assertThat(saved.getTimeSlot()).isNull();
    }

    @Test
    @DisplayName("Có ghi chú → nối vào note với tag CREATE, người tạo và giờ Việt Nam")
    void testCreate_WithNote() {
        givenBooking(BookingStatus.PENDING);
        givenNoActiveSurvey();
        givenSaveSucceeds();
        CreateSurveyRequest request = CreateSurveyRequest.builder()
                .bookingId(BOOKING_ID).surveyMethod(SurveyMethod.ONSITE).note("  Gọi trước 30 phút  ").build();

        service.create(request, ACTOR_ID);

        assertThat(captureSaved().getNote()).isEqualTo("[2026-11-02 09:00][CREATE][user#3] Gọi trước 30 phút");
    }

    @Test
    @DisplayName("Booking không ở PENDING / PENDING_SURVEY → ERR_SURVEY_BOOKING_INVALID_STATUS, không lưu")
    void testCreate_InvalidBookingStatus() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.CANCELLED, BookingStatus.SURVEYING, BookingStatus.QUOTED}) {
            givenBooking(status);

            assertThatThrownBy(() -> service.create(fullRequest(), ACTOR_ID))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
        verifyNoInteractions(bookingSurveyRepository, surveyScheduleValidator);
    }

    @Test
    @DisplayName("Booking đã có lịch khảo sát đang hoạt động → ERR_SURVEY_ACTIVE_EXISTS, không chạy validator")
    void testCreate_ActiveSurveyExists() {
        givenBooking(BookingStatus.PENDING_SURVEY);
        when(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(BOOKING_ID, SurveyStatus.ACTIVE))
                .thenReturn(Optional.of(BookingSurvey.builder().id(1).build()));

        assertThatThrownBy(() -> service.create(fullRequest(), ACTOR_ID))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_ACTIVE_EXISTS);
        verifyNoInteractions(surveyScheduleValidator);
    }

    @Test
    @DisplayName("Validator ném lỗi (vd. trùng lịch) → lỗi lan ra, không lưu, không đổi trạng thái booking")
    void testCreate_ValidatorFails() {
        givenBooking(BookingStatus.PENDING);
        givenNoActiveSurvey();
        when(surveyScheduleValidator.validate(any())).thenThrow(new AppException(ErrorCode.ERR_SURVEY_STAFF_CONFLICT));

        assertThatThrownBy(() -> service.create(fullRequest(), ACTOR_ID))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
        verify(bookingSurveyRepository, never()).saveAndFlush(any());
        verify(bookingWorkflowService, never()).changeStatus(anyInt(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("Tạo thành công không đổi trạng thái booking")
    void testCreate_DoesNotChangeBookingStatus() {
        givenBooking(BookingStatus.PENDING);
        givenNoActiveSurvey();
        givenSaveSucceeds();

        service.create(CreateSurveyRequest.builder().bookingId(BOOKING_ID).surveyMethod(SurveyMethod.ONSITE).build(), ACTOR_ID);

        verify(bookingWorkflowService, never()).changeStatus(anyInt(), any(), any(), any(), any());
    }
}
