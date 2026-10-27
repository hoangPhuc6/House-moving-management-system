package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.survey.dto.request.AssignSurveyStaffRequest;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
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
}
