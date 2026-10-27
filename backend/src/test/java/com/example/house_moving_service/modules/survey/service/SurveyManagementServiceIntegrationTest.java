package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.service.BookingManagementService;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.survey.dto.request.AssignSurveyStaffRequest;
import com.example.house_moving_service.modules.survey.dto.request.CancelSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.FailSurveyRequest;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.dto.request.RescheduleSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.repository.SurveyTimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Luồng tạo lịch trên DB thật: validator + lưu + dựng chi tiết; mọi thứ rollback sau test
@SpringBootTest
@Transactional
class SurveyManagementServiceIntegrationTest {

    // Ngày xa trong tương lai: luôn sau "hôm nay", không đụng dữ liệu nhập tay trên DB dev
    private static final LocalDate DAY = LocalDate.of(2099, 3, 10);

    @Autowired
    private SurveyManagementService surveyManagementService;
    @Autowired
    private BookingSurveyRepository bookingSurveyRepository;
    @Autowired
    private SurveyTimeSlotRepository surveyTimeSlotRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingManagementService bookingManagementService;
    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    private Booking booking1;
    private Booking booking2;
    private Long moverId;
    private Long actorId;
    private SurveyTimeSlot slot;

    @BeforeEach
    void setUp() {
        List<Booking> bookings = bookingRepository.findAll(PageRequest.of(0, 2)).getContent();
        booking1 = bookings.get(0);
        booking2 = bookings.get(1);
        for (Booking b : bookings) {
            bookingSurveyRepository.deleteAll(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(b.getId()));
            // Đưa về trạng thái cho phép tạo lịch, bỏ ngày chuyển nhà để không vướng quy tắc "khảo sát trước ngày chuyển"
            b.setStatus(BookingStatus.PENDING_SURVEY);
            b.setDesiredStartAt(null);
        }
        bookingRepository.saveAllAndFlush(bookings);

        moverId = userRepository.findByEmail("mover1@housemoving.vn").orElseThrow().getId();
        actorId = userRepository.findByEmail("admin@housemoving.vn").orElseThrow().getId();
        slot = surveyTimeSlotRepository.findAllByIsActiveTrueOrderByStartTimeAsc().get(0);
    }

    private CreateSurveyRequest request(Booking booking) {
        return CreateSurveyRequest.builder()
                .bookingId(booking.getId()).surveyMethod(SurveyMethod.ONSITE)
                .surveyDate(DAY).timeSlotId(slot.getId()).staffId(moverId)
                .note("Khách dặn gọi trước")
                .build();
    }

    @Test
    @DisplayName("Tạo lịch đủ dữ liệu → CONFIRMED, chi tiết có mã đơn, khung giờ, nhân viên, tuyến đường, ghi chú CREATE")
    void testCreate_Confirmed() {
        SurveyDetailResponse detail = surveyManagementService.create(request(booking1), actorId);

        assertThat(detail.getId()).isNotNull();
        assertThat(detail.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(detail.getBookingCode()).isEqualTo(booking1.getBookingCode());
        assertThat(detail.getTimeSlot().getId()).isEqualTo(slot.getId());
        assertThat(detail.getStaff().getUserId()).isEqualTo(moverId);
        assertThat(detail.getRoute()).isNotNull();
        assertThat(detail.getNote()).contains("[CREATE][user#" + actorId + "] Khách dặn gọi trước");
        assertThat(detail.getPreviousSurveys()).isEmpty();
    }

    @Test
    @DisplayName("Cùng MOVER, cùng ngày, cùng khung giờ cho đơn thứ hai → ERR_SURVEY_STAFF_CONFLICT")
    void testCreate_SameStaffSlot_Conflict() {
        surveyManagementService.create(request(booking1), actorId);

        assertThatThrownBy(() -> surveyManagementService.create(request(booking2), actorId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
    }

    @Test
    @DisplayName("Đơn đã có lịch đang hoạt động → ERR_SURVEY_ACTIVE_EXISTS")
    void testCreate_ActiveExists() {
        surveyManagementService.create(request(booking1), actorId);
        CreateSurveyRequest second = CreateSurveyRequest.builder()
                .bookingId(booking1.getId()).surveyMethod(SurveyMethod.PHONE).build();

        assertThatThrownBy(() -> surveyManagementService.create(second, actorId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_ACTIVE_EXISTS);
    }

    // ---------- 4.6.4: phân công / đổi lịch ----------

    @Test
    @DisplayName("Dời lịch của chính nó sang cùng khung giờ (chỉ đổi phương thức) → không bị báo trùng với chính nó")
    void testReschedule_SameSlotItself_NoConflict() {
        SurveyDetailResponse created = surveyManagementService.create(request(booking1), actorId);

        SurveyDetailResponse moved = surveyManagementService.reschedule(created.getId(), RescheduleSurveyRequest.builder()
                .surveyDate(DAY).timeSlotId(slot.getId()).surveyMethod(SurveyMethod.ONLINE)
                .reason("Khách muốn gọi video").build(), actorId);

        assertThat(moved.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(moved.getSurveyMethod()).isEqualTo(SurveyMethod.ONLINE);
        assertThat(moved.getNote()).contains("[RESCHEDULE]").contains("Lý do: Khách muốn gọi video");
    }

    @Test
    @DisplayName("Dời lịch sang khung giờ MOVER đã có lịch khác → ERR_SURVEY_STAFF_CONFLICT, lịch giữ nguyên")
    void testReschedule_IntoOccupiedSlot_Conflict() {
        surveyManagementService.create(request(booking1), actorId);
        CreateSurveyRequest other = request(booking2);
        other.setSurveyDate(DAY.plusDays(1));
        SurveyDetailResponse second = surveyManagementService.create(other, actorId);

        assertThatThrownBy(() -> surveyManagementService.reschedule(second.getId(), RescheduleSurveyRequest.builder()
                .surveyDate(DAY).timeSlotId(slot.getId()).reason("Khách đổi ngày").build(), actorId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
    }

    @Test
    @DisplayName("Lịch PENDING chưa có người, phân công MOVER → CONFIRMED, chi tiết có nhân viên")
    void testAssign_PendingToConfirmed() {
        CreateSurveyRequest noStaff = request(booking1);
        noStaff.setStaffId(null);
        SurveyDetailResponse created = surveyManagementService.create(noStaff, actorId);
        assertThat(created.getStatus()).isEqualTo(SurveyStatus.PENDING);

        SurveyDetailResponse assigned = surveyManagementService.assign(created.getId(),
                AssignSurveyStaffRequest.builder().staffId(moverId).build(), actorId);

        assertThat(assigned.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(assigned.getStaff().getUserId()).isEqualTo(moverId);
        assertThat(assigned.getNote()).contains("[ASSIGN][user#" + actorId + "] Phân công: (chưa có) → user#" + moverId);
    }

    // ---------- 4.6.5: hủy / thất bại / hủy theo đơn ----------

    @Test
    @DisplayName("Hủy đơn qua API booking → listener hủy lịch khảo sát (kể cả đang IN_PROGRESS) trong cùng transaction")
    void testBookingCancelled_SurveyCancelledByListener() {
        SurveyDetailResponse created = surveyManagementService.create(request(booking1), actorId);
        BookingSurvey survey = bookingSurveyRepository.findById(created.getId()).orElseThrow();
        survey.setStatus(SurveyStatus.IN_PROGRESS);
        bookingSurveyRepository.saveAndFlush(survey);

        bookingManagementService.cancelBookingByManagement(booking1.getId(),
                CancelBookingRequest.builder().reason("Khách đổi ý").build(), actorId);
        bookingSurveyRepository.flush();

        BookingSurvey after = bookingSurveyRepository.findById(created.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SurveyStatus.CANCELLED);
        assertThat(after.getNote()).contains("[CANCEL][user#" + actorId + "] Đơn hàng bị hủy: Khách đổi ý (đang khảo sát dở)");
    }

    @Test
    @DisplayName("Thất bại khi booking SURVEYING → survey RESURVEY_REQUIRED, booking PENDING_SURVEY có lịch sử; tạo được lịch mới")
    void testFail_BookingBackToPendingSurvey_ThenCreateAgain() {
        SurveyDetailResponse created = surveyManagementService.create(request(booking1), actorId);
        BookingSurvey survey = bookingSurveyRepository.findById(created.getId()).orElseThrow();
        survey.setStatus(SurveyStatus.IN_PROGRESS);
        bookingSurveyRepository.saveAndFlush(survey);
        booking1.setStatus(BookingStatus.SURVEYING);
        bookingRepository.saveAndFlush(booking1);
        int historyBefore = bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(booking1.getId()).size();

        SurveyDetailResponse failed = surveyManagementService.fail(created.getId(),
                FailSurveyRequest.builder().reason("Khách vắng nhà").build(), actorId);

        assertThat(failed.getStatus()).isEqualTo(SurveyStatus.RESURVEY_REQUIRED);
        assertThat(bookingRepository.findById(booking1.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING_SURVEY);
        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(booking1.getId());
        assertThat(history).hasSize(historyBefore + 1);
        assertThat(history.get(history.size() - 1).getNewStatus()).isEqualTo(BookingStatus.PENDING_SURVEY);

        // Lịch thất bại không còn "đang hoạt động" nên tạo lịch mới cho cùng đơn được
        CreateSurveyRequest again = request(booking1);
        again.setSurveyDate(DAY.plusDays(1));
        SurveyDetailResponse next = surveyManagementService.create(again, actorId);
        assertThat(next.getPreviousSurveys()).extracting(s -> s.getId()).containsExactly(created.getId());
    }

    @Test
    @DisplayName("Hủy lịch CONFIRMED → CANCELLED, khung giờ của MOVER được giải phóng cho đơn khác")
    void testCancel_FreesStaffSlot() {
        SurveyDetailResponse created = surveyManagementService.create(request(booking1), actorId);

        surveyManagementService.cancel(created.getId(), CancelSurveyRequest.builder().reason("Xếp nhầm").build(), actorId);

        SurveyDetailResponse other = surveyManagementService.create(request(booking2), actorId);
        assertThat(other.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
    }
}
