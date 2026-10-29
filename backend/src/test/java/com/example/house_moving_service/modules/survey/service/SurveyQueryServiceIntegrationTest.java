package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.repository.StaffRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
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

// Chạy trên DB thật: sort theo timeSlot.startTime phải chạy được cùng fetch join của specification và query đếm
@SpringBootTest
@Transactional
class SurveyQueryServiceIntegrationTest {

    // Ngày xa trong tương lai để không đụng dữ liệu nhập tay trên DB dev
    private static final LocalDate DAY = LocalDate.of(2099, 2, 10);

    @Autowired
    private SurveyQueryService surveyQueryService;
    @Autowired
    private BookingSurveyRepository bookingSurveyRepository;
    @Autowired
    private SurveyTimeSlotRepository surveyTimeSlotRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StaffRepository staffRepository;

    private Booking booking1;
    private Booking booking2;
    private Staff mover1;
    private Staff mover2;
    private SurveyTimeSlot slot0800;
    private SurveyTimeSlot slot1000;

    @BeforeEach
    void setUp() {
        List<Booking> bookings = bookingRepository.findAll(PageRequest.of(0, 2)).getContent();
        booking1 = bookings.get(0);
        booking2 = bookings.get(1);
        bookingSurveyRepository.deleteAll(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(booking1.getId()));
        bookingSurveyRepository.deleteAll(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(booking2.getId()));
        bookingSurveyRepository.flush();

        mover1 = staffRepository.getReferenceById(userRepository.findByEmail("mover1@housemoving.vn").orElseThrow().getId());
        mover2 = staffRepository.getReferenceById(userRepository.findByEmail("mover2@housemoving.vn").orElseThrow().getId());

        List<SurveyTimeSlot> slots = surveyTimeSlotRepository.findAllByOrderByStartTimeAsc();
        slot0800 = slots.get(0);
        slot1000 = slots.get(1);
    }

    private BookingSurvey survey(Booking booking, SurveyStatus status, Staff staff, LocalDate date, SurveyTimeSlot slot) {
        return bookingSurveyRepository.save(BookingSurvey.builder()
                .booking(booking).surveyMethod(SurveyMethod.ONSITE).status(status)
                .surveyStaff(staff).surveyDate(date).timeSlot(slot)
                .build());
    }

    @Test
    @DisplayName("search không truyền sort: sắp theo ngày rồi giờ bắt đầu; tổng số đúng; có địa chỉ lấy hàng và nhân viên")
    void testSearch_DefaultSortOnRealDb() {
        BookingSurvey b1NextDay = survey(booking1, SurveyStatus.CANCELLED, mover1, DAY.plusDays(1), slot0800);
        BookingSurvey b1At10 = survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot1000);
        BookingSurvey b2At08 = survey(booking2, SurveyStatus.CONFIRMED, mover2, DAY, slot0800);
        bookingSurveyRepository.flush();

        SurveyFilterRequest filter = SurveyFilterRequest.builder().dateFrom(DAY).dateTo(DAY.plusDays(1)).build();
        PageResponse<SurveySummaryResponse> page = surveyQueryService.search(filter, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getItems()).extracting(SurveySummaryResponse::getId)
                .containsExactly(b2At08.getId(), b1At10.getId(), b1NextDay.getId());
        SurveySummaryResponse first = page.getItems().get(0);
        assertThat(first.getPickupAddress()).isNotBlank();
        assertThat(first.getStaff().getFullName()).isNotBlank();
        assertThat(first.getTimeSlot().getLabel()).isEqualTo("08:00 - 10:00");
    }

    @Test
    @DisplayName("search phân trang: trang 2 cỡ 2 trả phần tử còn lại, totalElements vẫn là tổng")
    void testSearch_Pagination() {
        survey(booking1, SurveyStatus.CANCELLED, mover1, DAY.plusDays(1), slot0800);
        survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot1000);
        BookingSurvey last = survey(booking2, SurveyStatus.CONFIRMED, mover2, DAY.plusDays(2), slot0800);
        bookingSurveyRepository.flush();

        SurveyFilterRequest filter = SurveyFilterRequest.builder().dateFrom(DAY).dateTo(DAY.plusDays(2)).build();
        PageResponse<SurveySummaryResponse> page = surveyQueryService.search(filter, PageRequest.of(1, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getItems()).extracting(SurveySummaryResponse::getId).containsExactly(last.getId());
    }
}
