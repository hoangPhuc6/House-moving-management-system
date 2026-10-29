package com.example.house_moving_service.modules.survey;

import com.example.house_moving_service.common.enums.PropertyType;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.request.CreateBookingRequest;
import com.example.house_moving_service.modules.booking.service.BookingService;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import com.example.house_moving_service.modules.pricing.dto.request.ScheduleDto;
import com.example.house_moving_service.modules.pricing.dto.request.ServiceItemDto;
import com.example.house_moving_service.modules.survey.SurveyTestClockConfig.MutableClock;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.repository.SurveyTimeSlotRepository;
import com.example.house_moving_service.modules.survey.service.SurveyManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 4.10 — IT10: 2 request đồng thời gán cùng MOVER vào cùng ngày + khung giờ cho 2 booking.
 * Cần transaction thật (không @Transactional) để 2 thread đua nhau; dữ liệu tự tạo và tự xóa sau test.
 */
@SpringBootTest
@Import(SurveyTestClockConfig.class)
class SurveyConcurrencyIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2099, 6, 1);

    @Autowired private MutableClock clock;
    @Autowired private SurveyManagementService managementService;
    @Autowired private BookingService bookingService;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private SurveyTimeSlotRepository timeSlotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Integer> createdBookingIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock.set(DAY.minusDays(1).atTime(9, 0));
    }

    @AfterEach
    void cleanUp() {
        for (Integer id : createdBookingIds) {
            jdbcTemplate.update("DELETE FROM booking_surveys WHERE booking_id = ?", id);
            jdbcTemplate.update("DELETE FROM booking_status_history WHERE booking_id = ?", id);
            jdbcTemplate.update("DELETE FROM booking_services WHERE booking_id = ?", id);
            jdbcTemplate.update("DELETE FROM booking_items WHERE booking_id = ?", id);
            jdbcTemplate.update("DELETE FROM booking_vouchers WHERE booking_id = ?", id);
            jdbcTemplate.update("DELETE FROM booking_routes WHERE booking_id = ?", id);
            jdbcTemplate.update("DELETE FROM bookings WHERE id = ?", id);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_surveys WHERE booking_id = ?", Integer.class, id))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bookings WHERE id = ?", Integer.class, id)).isZero();
        }
    }

    // Đơn mới của customer1, ngày chuyển sau ngày khảo sát
    private Integer newBooking(Long customerUserId, Integer truckId) {
        LocalDateTime start = DAY.plusDays(9).atTime(8, 0);
        LocationDto location = LocationDto.builder().address("1 Lê Lợi, Q1").lat(new BigDecimal("10.7769"))
                .lng(new BigDecimal("106.7008")).propertyType(PropertyType.HOUSE)
                .floorNumber(0).hasElevator(false).walkingDistanceMeters(0).build();
        Integer id = bookingService.createBooking(CreateBookingRequest.builder()
                .contactName("Test đồng thời").contactPhone("0912345678")
                .schedule(ScheduleDto.builder().desiredStartAt(start).desiredEndAt(start.plusHours(4)).build())
                .route(RouteDto.builder().pickup(location).dropoff(location).manualDistanceKm(new BigDecimal("5")).build())
                .serviceItems(List.of(ServiceItemDto.builder().serviceId(truckId).quantity(1).build()))
                .build(), customerUserId).getId();
        createdBookingIds.add(id);
        return id;
    }

    @Test
    @DisplayName("IT10 — 2 thread cùng gán mover1 vào cùng khung cho 2 booking → đúng 1 thành công, thread kia bị từ chối")
    void it10_raceOnSameStaffSlot() throws Exception {
        Long customerId = userRepository.findByEmail("customer1@gmail.com").orElseThrow().getId();
        Long dispatcherId = userRepository.findByEmail("dispatcher@housemoving.vn").orElseThrow().getId();
        Long moverId = userRepository.findByEmail("mover1@housemoving.vn").orElseThrow().getId();
        Integer truckId = serviceRepository.findByName("Xe tải 1.5 Tấn (Nội thành)").orElseThrow().getId();
        SurveyTimeSlot slot = timeSlotRepository.findAllByIsActiveTrueOrderByStartTimeAsc().getFirst();
        List<Integer> bookingIds = List.of(newBooking(customerId, truckId), newBooking(customerId, truckId));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> results = new ArrayList<>();
        for (Integer bookingId : bookingIds) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return managementService.create(CreateSurveyRequest.builder()
                            .bookingId(bookingId).surveyMethod(SurveyMethod.ONSITE)
                            .surveyDate(DAY).timeSlotId(slot.getId()).staffId(moverId)
                            .build(), dispatcherId);
                } catch (RuntimeException ex) {
                    return ex;
                }
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> result : results) {
            outcomes.add(result.get());
        }
        List<Object> failures = outcomes.stream().filter(RuntimeException.class::isInstance).toList();
        assertThat(failures).as("đúng 1 request bị từ chối: %s", outcomes).hasSize(1);
        // Thua ở bước kiểm tra trùng (AppException) hoặc ở unique index một phần khi 2 bên cùng qua bước kiểm tra
        assertThat(failures.getFirst()).satisfiesAnyOf(
                ex -> assertThat(ex).isInstanceOf(AppException.class)
                        .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ERR_SURVEY_STAFF_CONFLICT),
                ex -> assertThat((Throwable) ex).isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("uq_booking_surveys_staff_slot"));

        Integer saved = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_surveys
                WHERE survey_staff_id = ? AND survey_date = ? AND survey_time_slot_id = ? AND status = 'CONFIRMED'
                """, Integer.class, moverId, DAY, slot.getId());
        assertThat(saved).isEqualTo(1);
    }
}
