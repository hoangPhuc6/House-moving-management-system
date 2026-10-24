package com.example.house_moving_service.modules.pricing.dto;

import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import com.example.house_moving_service.modules.pricing.dto.request.ScheduleDto;
import com.example.house_moving_service.modules.pricing.dto.request.ServiceItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.AppliedVoucherDto;
import com.example.house_moving_service.modules.pricing.dto.response.BreakdownItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.CategoryFeesDto;
import com.example.house_moving_service.modules.pricing.dto.response.FinancialSummaryDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.common.enums.PropertyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingDtoValidationTest {

    private static Validator validator;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private PricingCalculationRequest createValidRequest() {
        ScheduleDto schedule = ScheduleDto.builder()
                .desiredStartAt(LocalDateTime.of(2026, 10, 20, 8, 0, 0))
                .desiredEndAt(LocalDateTime.of(2026, 10, 20, 12, 0, 0))
                .build();

        LocationDto pickup = LocationDto.builder()
                .address("123 Lê Văn Việt, TP. Thủ Đức")
                .lat(new BigDecimal("10.8456000"))
                .lng(new BigDecimal("106.7789000"))
                .propertyType(PropertyType.HOUSE)
                .floorNumber(2)
                .hasElevator(false)
                .walkingDistanceMeters(30)
                .build();

        LocationDto dropoff = LocationDto.builder()
                .address("456 Nguyễn Thị Thập, Quận 7")
                .lat(new BigDecimal("10.7412000"))
                .lng(new BigDecimal("106.7098000"))
                .propertyType(PropertyType.APARTMENT)
                .floorNumber(9)
                .hasElevator(true)
                .walkingDistanceMeters(0)
                .build();

        RouteDto route = RouteDto.builder()
                .pickup(pickup)
                .dropoff(dropoff)
                .manualDistanceKm(new BigDecimal("12.5"))
                .build();

        ServiceItemDto item1 = ServiceItemDto.builder()
                .serviceId(1)
                .quantity(new BigDecimal("1.0"))
                .build();

        ServiceItemDto item2 = ServiceItemDto.builder()
                .serviceId(3)
                .quantity(new BigDecimal("2.0"))
                .build();

        return PricingCalculationRequest.builder()
                .schedule(schedule)
                .route(route)
                .serviceItems(List.of(item1, item2))
                .voucherCode("GIAM50K")
                .build();
    }

    @Test
    @DisplayName("PricingCalculationRequest: Hợp lệ khi đầy đủ thông tin chuẩn")
    void testValidRequest() {
        PricingCalculationRequest request = createValidRequest();
        Set<ConstraintViolation<PricingCalculationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should produce 0 violations");
    }

    @Test
    @DisplayName("PricingCalculationRequest: Vi phạm khi thiếu schedule hoặc route")
    void testMissingScheduleAndRoute() {
        PricingCalculationRequest request = PricingCalculationRequest.builder()
                .serviceItems(List.of(ServiceItemDto.builder().serviceId(1).quantity(BigDecimal.ONE).build()))
                .build();

        Set<ConstraintViolation<PricingCalculationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("schedule")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("route")));
    }

    @Test
    @DisplayName("PricingCalculationRequest: Vi phạm khi serviceItems rỗng")
    void testEmptyServiceItems() {
        PricingCalculationRequest request = createValidRequest();
        request.setServiceItems(Collections.emptyList());

        Set<ConstraintViolation<PricingCalculationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("serviceItems")));
    }

    @Test
    @DisplayName("ScheduleDto: Vi phạm khi thiếu thời gian bắt đầu hoặc kết thúc")
    void testInvalidSchedule() {
        ScheduleDto schedule = ScheduleDto.builder().build();
        Set<ConstraintViolation<ScheduleDto>> violations = validator.validate(schedule);
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("LocationDto: Vi phạm khi địa chỉ trống hoặc thiếu tọa độ")
    void testInvalidLocation() {
        LocationDto location = LocationDto.builder()
                .address("   ")
                .lat(null)
                .lng(null)
                .hasElevator(null)
                .floorNumber(-1)
                .walkingDistanceMeters(-10)
                .build();

        Set<ConstraintViolation<LocationDto>> violations = validator.validate(location);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("address")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("lat")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("lng")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("hasElevator")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("floorNumber")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("walkingDistanceMeters")));
    }

    @Test
    @DisplayName("RouteDto: Vi phạm khi khoảng cách thủ công âm")
    void testNegativeManualDistanceKm() {
        RouteDto route = RouteDto.builder()
                .pickup(LocationDto.builder().address("A").lat(BigDecimal.ONE).lng(BigDecimal.ONE).hasElevator(true).build())
                .dropoff(LocationDto.builder().address("B").lat(BigDecimal.ONE).lng(BigDecimal.ONE).hasElevator(true).build())
                .manualDistanceKm(new BigDecimal("-5.0"))
                .build();

        Set<ConstraintViolation<RouteDto>> violations = validator.validate(route);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("manualDistanceKm")));
    }

    @Test
    @DisplayName("ServiceItemDto: Vi phạm khi serviceId null hoặc quantity <= 0")
    void testInvalidServiceItem() {
        ServiceItemDto item = ServiceItemDto.builder()
                .serviceId(null)
                .quantity(new BigDecimal("0.0"))
                .build();

        Set<ConstraintViolation<ServiceItemDto>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("serviceId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("quantity")));
    }

    @Test
    @DisplayName("Cascading Validation: Bắt lỗi nested location khi validate root request")
    void testCascadingValidation() {
        PricingCalculationRequest request = createValidRequest();
        request.getRoute().getPickup().setAddress(""); // blank

        Set<ConstraintViolation<PricingCalculationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("route.pickup.address")));
    }

    @Test
    @DisplayName("Response Serialization: Kiểm tra serialize PricingCalculationResponse thành JSON chuẩn")
    void testResponseSerialization() throws Exception {
        PricingCalculationResponse response = PricingCalculationResponse.builder()
                .isEstimated(true)
                .calculatedDistanceKm(new BigDecimal("12.50"))
                .currency("VND")
                .financialSummary(FinancialSummaryDto.builder()
                        .subtotal(new BigDecimal("1400000.00"))
                        .discountAmount(new BigDecimal("50000.00"))
                        .finalTotal(new BigDecimal("1350000.00"))
                        .build())
                .categoryFees(CategoryFeesDto.builder()
                        .baseTransportFee(new BigDecimal("550000.00"))
                        .laborFee(new BigDecimal("500000.00"))
                        .technicalFee(BigDecimal.ZERO)
                        .materialFee(new BigDecimal("250000.00"))
                        .terrainSurchargeFee(new BigDecimal("100000.00"))
                        .build())
                .breakdownItems(List.of(
                        BreakdownItemDto.builder()
                                .serviceId(1)
                                .serviceTypeId(1)
                                .serviceTypeName("Cước xe")
                                .serviceName("Xe 1.5 tấn")
                                .quantity(new BigDecimal("1.0"))
                                .unitPrice(new BigDecimal("500000.00"))
                                .totalPrice(new BigDecimal("500000.00"))
                                .note("Chặng cơ bản")
                                .build()
                ))
                .appliedVoucher(AppliedVoucherDto.builder()
                        .voucherId(12)
                        .code("GIAM50K")
                        .title("Ưu đãi khách mới")
                        .discountType("FIXED")
                        .discountValue(new BigDecimal("50000.00"))
                        .discountAmount(new BigDecimal("50000.00"))
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.contains("\"isEstimated\":true"));
        assertTrue(json.contains("\"currency\":\"VND\""));
        assertTrue(json.contains("\"finalTotal\":\"1350000.00\""));
        assertTrue(json.contains("\"baseTransportFee\":\"550000.00\""));
        assertTrue(json.contains("\"code\":\"GIAM50K\""));
    }
}

