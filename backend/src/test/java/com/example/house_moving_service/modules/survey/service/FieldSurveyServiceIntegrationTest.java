package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.repository.StaffRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.booking.dto.response.BookingItemSnapshot;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.survey.dto.request.LocationAccessRequest;
import com.example.house_moving_service.modules.survey.dto.request.RouteAccessRequest;
import com.example.house_moving_service.modules.survey.dto.request.SurveyItemRequest;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Luồng MOVER trên DB thật với "bây giờ" cố định (không phụ thuộc giờ chạy test); mọi thứ rollback sau test
@SpringBootTest
@Transactional
class FieldSurveyServiceIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2099, 3, 10);

    @TestConfiguration
    static class FixedClockConfig {
        // 08:30 ngày khảo sát: khung 08:00 đã tới giờ
        @Bean
        @Primary
        Clock fixedTestClock() {
            return Clock.fixed(ZonedDateTime.of(DAY, LocalTime.of(8, 30), SurveyClockConfig.BUSINESS_ZONE).toInstant(),
                    SurveyClockConfig.BUSINESS_ZONE);
        }
    }

    @Autowired
    private FieldSurveyService fieldSurveyService;
    @Autowired
    private BookingSurveyRepository bookingSurveyRepository;
    @Autowired
    private SurveyTimeSlotRepository surveyTimeSlotRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;
    @Autowired
    private BookingWorkflowService bookingWorkflowService;
    @Autowired
    private QuotationDraftService quotationDraftService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StaffRepository staffRepository;

    private Booking booking;
    private Long moverId;
    private BookingSurvey survey;

    @BeforeEach
    void setUp() {
        booking = bookingRepository.findAll(PageRequest.of(0, 1)).getContent().get(0);
        bookingSurveyRepository.deleteAll(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(booking.getId()));
        booking.setStatus(BookingStatus.PENDING_SURVEY);
        bookingRepository.saveAndFlush(booking);

        moverId = userRepository.findByEmail("mover1@housemoving.vn").orElseThrow().getId();
        SurveyTimeSlot slot0800 = surveyTimeSlotRepository.findAllByOrderByStartTimeAsc().get(0);
        // Dựng lịch CONFIRMED thẳng qua repository (không qua validator, vì "hôm nay" của validator cũng là DAY)
        survey = bookingSurveyRepository.saveAndFlush(BookingSurvey.builder()
                .booking(booking).surveyMethod(SurveyMethod.ONSITE).status(SurveyStatus.CONFIRMED)
                .surveyDate(DAY).timeSlot(slot0800).surveyStaff(staffRepository.getReferenceById(moverId))
                .build());
    }

    @Test
    @DisplayName("Bắt đầu: lịch IN_PROGRESS, bản nháp có đúng các dòng khách khai, booking SURVEYING + 1 dòng lịch sử, booking_items giữ nguyên")
    void testStart_FullFlow() {
        int declaredCount = bookingWorkflowService.getItems(booking.getId()).size();
        int historyBefore = bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(booking.getId()).size();

        FieldSurveyResponse result = fieldSurveyService.start(survey.getId(), moverId);

        assertThat(result.getStatus()).isEqualTo(SurveyStatus.IN_PROGRESS);
        assertThat(result.getActualStartAt()).isEqualTo(DAY.atTime(8, 30));
        assertThat(result.getDraftItems()).hasSize(declaredCount);
        assertThat(result.getDraftItems()).allSatisfy(item -> assertThat(item.bookingItemId()).isNotNull());
        assertThat(quotationDraftService.findCurrentDraft(booking.getId())).isPresent();

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.SURVEYING);
        assertThat(bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(booking.getId()))
                .hasSize(historyBefore + 1);
        assertThat(bookingWorkflowService.getItems(booking.getId())).hasSize(declaredCount);
    }

    @Test
    @DisplayName("Sửa / thêm / xóa đồ trên bản nháp → bản nháp đổi, booking_items (đồ khách khai) giữ nguyên")
    void testItems_DoNotChangeDeclaredItems() {
        List<BookingItemSnapshot> declaredBefore = bookingWorkflowService.getItems(booking.getId());
        FieldSurveyResponse started = fieldSurveyService.start(survey.getId(), moverId);
        QuotationItemDto copied = started.getDraftItems().get(0);

        fieldSurveyService.updateItem(survey.getId(), copied.id(), SurveyItemRequest.builder()
                .itemName(copied.itemName()).quantity(copied.quantity() + 5).build(), moverId);
        QuotationItemDto added = fieldSurveyService.addItem(survey.getId(),
                SurveyItemRequest.builder().itemName("Sofa").quantity(1).build(), moverId);

        List<QuotationItemDto> draft = fieldSurveyService.getMySurvey(survey.getId(), moverId).getDraftItems();
        assertThat(draft).hasSize(declaredBefore.size() + 1);
        assertThat(draft).filteredOn(i -> i.id().equals(copied.id()))
                .singleElement().extracting(QuotationItemDto::quantity).isEqualTo(copied.quantity() + 5);
        assertThat(added.bookingItemId()).isNull();

        fieldSurveyService.deleteItem(survey.getId(), added.id(), moverId);
        assertThat(fieldSurveyService.getMySurvey(survey.getId(), moverId).getDraftItems()).hasSize(declaredBefore.size());
        assertThat(bookingWorkflowService.getItems(booking.getId())).isEqualTo(declaredBefore);
    }

    @Test
    @DisplayName("Cập nhật điều kiện ra vào → booking_routes lưu chuỗi mới, getRoute parse lại đúng; note của lịch có ROUTE_UPDATE")
    void testUpdateRouteAccess_PersistedAndParsedBack() {
        fieldSurveyService.start(survey.getId(), moverId);

        FieldSurveyResponse result = fieldSurveyService.updateRouteAccess(survey.getId(), RouteAccessRequest.builder()
                .pickup(LocationAccessRequest.builder().floorNumber(7).hasElevator(true).walkingDistanceMeters(35)
                        .extraNote("Cổng sau").build())
                .dropoff(LocationAccessRequest.builder().floorNumber(2).hasElevator(false).walkingDistanceMeters(0).build())
                .build(), moverId);

        BookingRouteResponse route = bookingWorkflowService.getRoute(booking.getId());
        assertThat(route.getPickupAccess()).isEqualTo(new RouteAccessInfo(7, true, 35, "Cổng sau"));
        assertThat(route.getDropoffAccess()).isEqualTo(new RouteAccessInfo(2, false, 0, null));
        assertThat(result.getRoute().getPickupAccess()).isEqualTo(route.getPickupAccess());
        assertThat(result.getNote()).contains("[ROUTE_UPDATE][user#" + moverId + "]")
                .contains("→ Tầng 7, có thang máy, hẻm 35m (Cổng sau)");
    }
}
