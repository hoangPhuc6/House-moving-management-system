package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
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
}
