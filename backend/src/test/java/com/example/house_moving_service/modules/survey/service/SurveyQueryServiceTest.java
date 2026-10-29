package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse.SlotAvailability;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SlotBusyReason;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.impl.SurveyQueryServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
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
class SurveyQueryServiceTest {

    private static final int BOOKING_ID = 7;
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
    private QuotationDraftService quotationDraftService;
    @Mock
    private StaffLookupService staffLookupService;
    @Mock
    private SurveyTimeSlotService surveyTimeSlotService;
    @Mock
    private ObjectProvider<StaffExternalScheduleChecker> externalScheduleChecker;
    @Mock
    private StaffExternalScheduleChecker checker;

    private SurveyQueryServiceImpl service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new SurveyQueryServiceImpl(bookingSurveyRepository, bookingWorkflowService, quotationDraftService,
                staffLookupService, surveyTimeSlotService, externalScheduleChecker, new SurveyMapper(), CLOCK);
        booking = Booking.builder().id(BOOKING_ID).bookingCode("BK-0007")
                .contactName("Nguyễn Văn A").contactPhone("0901234567").build();
        booking.setRoute(BookingRoute.builder().pickupAddress("12 Lê Lợi, Q1").build());
    }

    private BookingSurvey survey(int id, SurveyStatus status) {
        return BookingSurvey.builder().id(id).booking(booking).surveyMethod(SurveyMethod.ONSITE).status(status).build();
    }

    private BookingSurvey scheduledSurvey(int id) {
        BookingSurvey s = survey(id, SurveyStatus.CONFIRMED);
        s.setSurveyDate(LocalDate.of(2026, 11, 3));
        s.setTimeSlot(SurveyTimeSlot.builder().id(1).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0))
                .isActive(true).build());
        s.setSurveyStaff(Staff.builder().id(8L).staffCode("MV001").fullName("Trần Mover").build());
        return s;
    }

    // ---------- getDetail ----------

    @Test
    @DisplayName("getDetail: không tồn tại → ERR_SURVEY_NOT_FOUND")
    void testGetDetail_NotFound() {
        when(bookingSurveyRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(99))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_NOT_FOUND);
    }

    @Test
    @DisplayName("getDetail: lần khảo sát mới nhất → có bản nháp + dòng báo giá; previousSurveys không chứa chính nó")
    void testGetDetail_Latest_HasDraft() {
        BookingSurvey latest = scheduledSurvey(2);
        BookingSurvey old = survey(1, SurveyStatus.RESURVEY_REQUIRED);
        when(bookingSurveyRepository.findById(2)).thenReturn(Optional.of(latest));
        when(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of(latest, old));
        BookingRouteResponse route = BookingRouteResponse.builder().pickupAddress("12 Lê Lợi, Q1").build();
        when(bookingWorkflowService.getRoute(BOOKING_ID)).thenReturn(route);
        QuotationDraftDto draft = QuotationDraftDto.builder().id(30).bookingId(BOOKING_ID).version(2)
                .status(QuotationStatus.DRAFT).build();
        when(quotationDraftService.findCurrentDraft(BOOKING_ID)).thenReturn(Optional.of(draft));
        when(quotationDraftService.listItems(30)).thenReturn(List.of(QuotationItemDto.builder().id(1).itemName("Tủ lạnh").build()));

        SurveyDetailResponse detail = service.getDetail(2);

        assertThat(detail.getId()).isEqualTo(2);
        assertThat(detail.getBookingCode()).isEqualTo("BK-0007");
        assertThat(detail.getPickupAddress()).isEqualTo("12 Lê Lợi, Q1");
        assertThat(detail.getTimeSlot().getLabel()).isEqualTo("08:00 - 10:00");
        assertThat(detail.getStaff().getStaffCode()).isEqualTo("MV001");
        assertThat(detail.getRoute()).isSameAs(route);
        assertThat(detail.getCurrentDraft().id()).isEqualTo(30);
        assertThat(detail.getDraftItems()).extracting(QuotationItemDto::itemName).containsExactly("Tủ lạnh");
        assertThat(detail.getPreviousSurveys()).extracting(SurveySummaryResponse::getId).containsExactly(1);
    }

    @Test
    @DisplayName("getDetail: lần khảo sát cũ → không hiện bản nháp, không hỏi quotation")
    void testGetDetail_Old_NoDraft() {
        BookingSurvey latest = scheduledSurvey(2);
        BookingSurvey old = survey(1, SurveyStatus.RESURVEY_REQUIRED);
        when(bookingSurveyRepository.findById(1)).thenReturn(Optional.of(old));
        when(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of(latest, old));

        SurveyDetailResponse detail = service.getDetail(1);

        assertThat(detail.getCurrentDraft()).isNull();
        assertThat(detail.getDraftItems()).isNull();
        assertThat(detail.getPreviousSurveys()).extracting(SurveySummaryResponse::getId).containsExactly(2);
        verifyNoInteractions(quotationDraftService);
    }

    @Test
    @DisplayName("getDetail: mới nhất nhưng chưa có bản nháp → currentDraft null, không gọi listItems")
    void testGetDetail_Latest_NoDraftYet() {
        BookingSurvey only = survey(5, SurveyStatus.PENDING);
        when(bookingSurveyRepository.findById(5)).thenReturn(Optional.of(only));
        when(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of(only));
        when(quotationDraftService.findCurrentDraft(BOOKING_ID)).thenReturn(Optional.empty());

        SurveyDetailResponse detail = service.getDetail(5);

        assertThat(detail.getCurrentDraft()).isNull();
        assertThat(detail.getPreviousSurveys()).isEmpty();
        verify(quotationDraftService, never()).listItems(any());
    }

    @Test
    @DisplayName("getDetail: lịch PENDING chưa có ngày / khung giờ / nhân viên → các field null, không NPE")
    void testGetDetail_PendingWithoutSchedule() {
        BookingSurvey pending = survey(5, SurveyStatus.PENDING);
        when(bookingSurveyRepository.findById(5)).thenReturn(Optional.of(pending));
        when(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of(pending));

        SurveyDetailResponse detail = service.getDetail(5);

        assertThat(detail.getSurveyDate()).isNull();
        assertThat(detail.getTimeSlot()).isNull();
        assertThat(detail.getStaff()).isNull();
    }

    // ---------- search ----------

    @Test
    @DisplayName("search: không truyền sort → sắp theo ngày khảo sát rồi giờ bắt đầu, giữ trang và cỡ trang")
    @SuppressWarnings("unchecked")
    void testSearch_DefaultSort() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(bookingSurveyRepository.findAll(any(Specification.class), captor.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(scheduledSurvey(2)), inv.getArgument(1), 1));

        PageResponse<SurveySummaryResponse> page = service.search(new SurveyFilterRequest(), PageRequest.of(1, 5));

        Pageable used = captor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(1);
        assertThat(used.getPageSize()).isEqualTo(5);
        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Order.asc("surveyDate"), Sort.Order.asc("timeSlot.startTime")));
        assertThat(page.getItems()).extracting(SurveySummaryResponse::getId).containsExactly(2);
    }

    @Test
    @DisplayName("search: có truyền sort → giữ nguyên sort của người gọi")
    @SuppressWarnings("unchecked")
    void testSearch_CustomSort() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(bookingSurveyRepository.findAll(any(Specification.class), captor.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));
        Pageable requested = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt")));

        service.search(null, requested);

        assertThat(captor.getValue()).isSameAs(requested);
    }

    // ---------- getByBooking ----------

    @Test
    @DisplayName("getByBooking: trả mọi lần khảo sát theo thứ tự repository (mới nhất trước)")
    void testGetByBooking() {
        when(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(BOOKING_ID))
                .thenReturn(List.of(scheduledSurvey(2), survey(1, SurveyStatus.CANCELLED)));

        List<SurveySummaryResponse> result = service.getByBooking(BOOKING_ID);

        assertThat(result).extracting(SurveySummaryResponse::getId).containsExactly(2, 1);
        verify(bookingWorkflowService).getSnapshot(BOOKING_ID);
    }

    @Test
    @DisplayName("getByBooking: booking không tồn tại → lỗi từ BookingWorkflowService lan ra, không truy vấn khảo sát")
    void testGetByBooking_BookingNotFound() {
        when(bookingWorkflowService.getSnapshot(404)).thenThrow(new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, 404));

        assertThatThrownBy(() -> service.getByBooking(404))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
        verifyNoInteractions(bookingSurveyRepository);
    }

    // ---------- listAvailableMovers / getStaffAvailability ----------

    private static SurveyTimeSlotResponse slotResponse(int id, String start, String end) {
        return SurveyTimeSlotResponse.builder().id(id).startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end))
                .isActive(true).label(start + " - " + end).build();
    }

    private void givenMover() {
        when(staffLookupService.isActiveStaffWithRole(STAFF_ID, RoleCode.MOVER)).thenReturn(true);
        when(staffLookupService.findSummary(STAFF_ID))
                .thenReturn(Optional.of(StaffSummary.builder().userId(STAFF_ID).fullName("Trần Mover").build()));
        when(surveyTimeSlotService.getAll(true)).thenReturn(List.of(
                slotResponse(1, "08:00", "10:00"), slotResponse(2, "10:00", "12:00"), slotResponse(3, "13:30", "15:30")));
    }

    private BookingSurvey surveyAtSlot(int surveyId, int slotId) {
        BookingSurvey s = survey(surveyId, SurveyStatus.CONFIRMED);
        s.setTimeSlot(SurveyTimeSlot.builder().id(slotId).build());
        return s;
    }

    @Test
    @DisplayName("listAvailableMovers: lấy MOVER đang làm việc từ identity")
    void testListAvailableMovers() {
        List<StaffSummary> movers = List.of(StaffSummary.builder().userId(STAFF_ID).fullName("Trần Mover").build());
        when(staffLookupService.findActiveByRole(RoleCode.MOVER)).thenReturn(movers);

        assertThat(service.listAvailableMovers()).isSameAs(movers);
    }

    @Test
    @DisplayName("availability: khung giờ có lịch khảo sát → bận SURVEY kèm surveyId; còn lại rảnh; không có bean checker")
    void testAvailability_SurveyBusy_NoChecker() {
        givenMover();
        LocalDate date = TODAY.plusDays(1);
        when(bookingSurveyRepository.findByStaffAndDateRange(STAFF_ID, date, date, SurveyStatus.OCCUPYING_SLOT))
                .thenReturn(List.of(surveyAtSlot(12, 1)));
        when(externalScheduleChecker.getIfAvailable()).thenReturn(null);

        StaffAvailabilityResponse result = service.getStaffAvailability(STAFF_ID, date);

        assertThat(result.getFullName()).isEqualTo("Trần Mover");
        assertThat(result.getDate()).isEqualTo(date);
        assertThat(result.getSlots()).extracting(SlotAvailability::isAvailable).containsExactly(false, true, true);
        SlotAvailability busy = result.getSlots().get(0);
        assertThat(busy.getReason()).isEqualTo(SlotBusyReason.SURVEY);
        assertThat(busy.getSurveyId()).isEqualTo(12);
        assertThat(result.getSlots().get(1).getReason()).isNull();
        assertThat(result.getSlots().get(1).getSurveyId()).isNull();
    }

    @Test
    @DisplayName("availability: checker báo bận việc khác → OTHER_SCHEDULE; hỏi đúng khoảng giờ; slot đã bận khảo sát thì không hỏi checker")
    void testAvailability_OtherSchedule() {
        givenMover();
        LocalDate date = TODAY.plusDays(1);
        when(bookingSurveyRepository.findByStaffAndDateRange(STAFF_ID, date, date, SurveyStatus.OCCUPYING_SLOT))
                .thenReturn(List.of(surveyAtSlot(12, 1)));
        when(externalScheduleChecker.getIfAvailable()).thenReturn(checker);
        when(checker.isBusy(STAFF_ID, date.atTime(10, 0), date.atTime(12, 0))).thenReturn(false);
        when(checker.isBusy(STAFF_ID, date.atTime(13, 30), date.atTime(15, 30))).thenReturn(true);

        StaffAvailabilityResponse result = service.getStaffAvailability(STAFF_ID, date);

        assertThat(result.getSlots()).extracting(SlotAvailability::getReason)
                .containsExactly(SlotBusyReason.SURVEY, null, SlotBusyReason.OTHER_SCHEDULE);
        verify(checker, never()).isBusy(STAFF_ID, date.atTime(8, 0), date.atTime(10, 0));
    }

    @Test
    @DisplayName("availability: không truyền ngày → hôm nay theo giờ Việt Nam")
    void testAvailability_DefaultToday() {
        givenMover();
        when(bookingSurveyRepository.findByStaffAndDateRange(STAFF_ID, TODAY, TODAY, SurveyStatus.OCCUPYING_SLOT))
                .thenReturn(List.of());

        assertThat(service.getStaffAvailability(STAFF_ID, null).getDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("availability: nhân viên không phải MOVER đang làm việc → ERR_SURVEY_STAFF_INVALID")
    void testAvailability_InvalidStaff() {
        when(staffLookupService.isActiveStaffWithRole(STAFF_ID, RoleCode.MOVER)).thenReturn(false);

        assertThatThrownBy(() -> service.getStaffAvailability(STAFF_ID, TODAY))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_STAFF_INVALID);
        verifyNoInteractions(bookingSurveyRepository, surveyTimeSlotService);
    }
}
