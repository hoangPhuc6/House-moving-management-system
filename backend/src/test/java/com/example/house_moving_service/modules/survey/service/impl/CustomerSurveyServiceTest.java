package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.survey.dto.response.CustomerSurveyResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
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

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSurveyServiceTest {

    private static final int BOOKING_ID = 7;
    private static final long OWNER_ID = 21L;
    private static final long OTHER_CUSTOMER_ID = 22L;

    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private BookingSurveyRepository bookingSurveyRepository;

    private CustomerSurveyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerSurveyServiceImpl(bookingWorkflowService, bookingSurveyRepository, new SurveyMapper());
    }

    private void givenBookingOwnedBy(Long customerUserId) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID))
                .thenReturn(BookingSnapshot.builder().id(BOOKING_ID).customerUserId(customerUserId).build());
    }

    private static BookingSurvey confirmedSurvey() {
        return BookingSurvey.builder()
                .id(40).surveyMethod(SurveyMethod.ONSITE).status(SurveyStatus.CONFIRMED)
                .surveyDate(LocalDate.of(2026, 11, 3))
                .timeSlot(SurveyTimeSlot.builder().id(1).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).build())
                .surveyStaff(Staff.builder().id(8L).staffCode("MV001").fullName("Trần Mover").build())
                .note("[2026-10-27 09:00][ASSIGN][user#3] Phân công: (chưa có) → user#8")
                .build();
    }

    @Test
    @DisplayName("Chủ đơn xem được: ngày, khung giờ dạng chữ, tên nhân viên, trạng thái; theo thứ tự repository (mới nhất trước)")
    void testOwnerSeesSurveys() {
        givenBookingOwnedBy(OWNER_ID);
        BookingSurvey pending = BookingSurvey.builder().id(41).surveyMethod(SurveyMethod.PHONE).status(SurveyStatus.PENDING).build();
        when(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of(pending, confirmedSurvey()));

        List<CustomerSurveyResponse> result = service.getByBooking(BOOKING_ID, OWNER_ID);

        assertThat(result).extracting(CustomerSurveyResponse::getId).containsExactly(41, 40);
        CustomerSurveyResponse confirmed = result.get(1);
        assertThat(confirmed.getTimeSlotLabel()).isEqualTo("08:00 - 10:00");
        assertThat(confirmed.getStaffName()).isEqualTo("Trần Mover");
        assertThat(confirmed.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        CustomerSurveyResponse notScheduled = result.get(0);
        assertThat(notScheduled.getSurveyDate()).isNull();
        assertThat(notScheduled.getTimeSlotLabel()).isNull();
        assertThat(notScheduled.getStaffName()).isNull();
    }

    @Test
    @DisplayName("Khách khác / đơn không có khách → ERR_BOOKING_FORBIDDEN, không truy vấn lịch khảo sát")
    void testNotOwner_Forbidden() {
        givenBookingOwnedBy(OTHER_CUSTOMER_ID);
        assertThatThrownBy(() -> service.getByBooking(BOOKING_ID, OWNER_ID))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);

        givenBookingOwnedBy(null);
        assertThatThrownBy(() -> service.getByBooking(BOOKING_ID, OWNER_ID))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);

        verifyNoInteractions(bookingSurveyRepository);
    }

    @Test
    @DisplayName("Đơn không tồn tại → lỗi ERR_BOOKING_NOT_FOUND từ booking lan ra")
    void testBookingNotFound() {
        when(bookingWorkflowService.getSnapshot(404)).thenThrow(new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, 404));

        assertThatThrownBy(() -> service.getByBooking(404, OWNER_ID))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }

    @Test
    @DisplayName("DTO trả cho khách không có trường ghi chú nội bộ, tệp đính kèm, mã / id / SĐT nhân viên")
    void testNoInternalFields() {
        List<String> fields = Arrays.stream(CustomerSurveyResponse.class.getDeclaredFields()).map(Field::getName).toList();

        assertThat(fields).doesNotContain("note", "attachments", "staffCode", "staffId", "staffPhone", "staff");
        assertThat(fields).contains("staffName", "timeSlotLabel", "status");
    }
}
