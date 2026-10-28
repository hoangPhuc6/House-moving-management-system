package com.example.house_moving_service.modules.booking.service;

import com.example.house_moving_service.common.enums.PropertyType;
import com.example.house_moving_service.modules.booking.dto.request.CreateBookingRequest;
import com.example.house_moving_service.modules.booking.dto.response.BookingDetailResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingServiceResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingServiceRepository;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.enums.SystemServiceCode;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import com.example.house_moving_service.modules.pricing.dto.request.ScheduleDto;
import com.example.house_moving_service.modules.pricing.dto.request.ServiceItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.pricing.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy trên PostgreSQL thật (dữ liệu seed + migration V12): tạo đơn với phụ phí tự động,
 * kiểm tra booking_services lưu đủ các dòng và tổng khớp kết quả tính giá.
 */
@SpringBootTest
@Transactional
class BookingPricingIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingServiceRepository bookingServiceRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    private Long customerUserId;
    private Service truck;
    private Service extraKm;
    private Service stairs;

    @BeforeEach
    void setUp() {
        customerUserId = userRepository.findByEmail("customer1@gmail.com").orElseThrow().getId();
        truck = serviceRepository.findByName("Xe tải 1.5 Tấn (Nội thành)").orElseThrow();
        // Migration V12 phải gán đủ mã cho dịch vụ phụ phí
        extraKm = serviceRepository.findByCode(SystemServiceCode.SURCHARGE_EXTRA_KM).orElseThrow();
        stairs = serviceRepository.findByCode(SystemServiceCode.SURCHARGE_STAIRS).orElseThrow();
        assertThat(serviceRepository.findByCode(SystemServiceCode.SURCHARGE_DEEP_ALLEY)).isPresent();
    }

    private CreateBookingRequest request() {
        LocalDateTime start = LocalDateTime.now().plusDays(3).withNano(0);
        return CreateBookingRequest.builder()
                .contactName("Nguyễn Văn A")
                .contactPhone("0912345678")
                .schedule(ScheduleDto.builder().desiredStartAt(start).desiredEndAt(start.plusHours(4)).build())
                .route(RouteDto.builder()
                        .pickup(LocationDto.builder()
                                .address("123 Lê Lợi, Q1, TP.HCM")
                                .lat(new BigDecimal("10.776889")).lng(new BigDecimal("106.700806"))
                                .propertyType(PropertyType.APARTMENT)
                                .floorNumber(5).hasElevator(false).walkingDistanceMeters(0)
                                .build())
                        .dropoff(LocationDto.builder()
                                .address("456 Nguyễn Thị Minh Khai, Q3, TP.HCM")
                                .lat(new BigDecimal("10.782622")).lng(new BigDecimal("106.695844"))
                                .propertyType(PropertyType.HOUSE)
                                .floorNumber(0).hasElevator(false).walkingDistanceMeters(0)
                                .build())
                        // Nhập tay → không gọi bản đồ
                        .manualDistanceKm(new BigDecimal("15.00"))
                        .build())
                .serviceItems(List.of(ServiceItemDto.builder().serviceId(truck.getId()).quantity(2).build()))
                .build();
    }

    @Test
    @DisplayName("Tạo đơn tầng 5 không thang máy, 15 km → lưu dòng thang bộ (4) và vượt km (5); tổng khớp giá tính")
    void createBooking_storesAutoSurcharges() {
        CreateBookingRequest request = request();
        PricingCalculationResponse pricing = pricingService.calculatePrice(PricingCalculationRequest.builder()
                .schedule(request.getSchedule())
                .route(request.getRoute())
                .serviceItems(request.getServiceItems())
                .build());

        BookingDetailResponse created = bookingService.createBooking(request, customerUserId);

        Booking booking = bookingRepository.findById(created.getId()).orElseThrow();
        assertThat(booking.getTotalAmount()).isEqualByComparingTo(pricing.getFinancialSummary().getFinalTotal());

        Map<Integer, com.example.house_moving_service.modules.booking.entity.BookingService> stored =
                bookingServiceRepository.findAllByBookingId(booking.getId()).stream()
                        .collect(Collectors.toMap(bs -> bs.getService().getId(), Function.identity()));
        assertThat(stored).containsOnlyKeys(truck.getId(), extraKm.getId(), stairs.getId());

        // Số lượng lưu đúng, không bị cắt
        assertThat(stored.get(truck.getId()).getQuantity()).isEqualTo(2);
        assertThat(stored.get(extraKm.getId()).getQuantity()).isEqualTo(5);
        assertThat(stored.get(extraKm.getId()).getNote()).isEqualTo("Vượt 5 km (15 km, đã gồm 10 km)");
        assertThat(stored.get(stairs.getId()).getQuantity()).isEqualTo(4);
        assertThat(stored.get(stairs.getId()).getNote()).isEqualTo("Điểm lấy hàng: tầng 5, không thang máy (4 tầng)");

        // Tổng các dòng (đơn giá snapshot × số lượng) = tạm tính của Pricing
        BigDecimal storedSum = stored.values().stream()
                .map(bs -> bs.getPriceApplied().multiply(BigDecimal.valueOf(bs.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(storedSum).isEqualByComparingTo(pricing.getFinancialSummary().getSubtotal());

        // Response chi tiết đơn đánh dấu dòng phụ phí
        assertThat(created.getServices())
                .filteredOn(BookingServiceResponse::isAutoApplied)
                .extracting(BookingServiceResponse::getServiceId)
                .containsExactlyInAnyOrder(extraKm.getId(), stairs.getId());
        assertThat(created.getServices())
                .filteredOn(s -> s.getServiceId().equals(truck.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.isAutoApplied()).isFalse());
    }
}
