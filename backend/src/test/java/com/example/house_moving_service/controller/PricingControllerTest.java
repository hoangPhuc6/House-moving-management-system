package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.pricing.LocationDto;
import com.example.house_moving_service.dto.request.pricing.PricingCalculationRequest;
import com.example.house_moving_service.dto.request.pricing.RouteDto;
import com.example.house_moving_service.dto.request.pricing.ScheduleDto;
import com.example.house_moving_service.dto.request.pricing.ServiceItemDto;
import com.example.house_moving_service.dto.response.pricing.BreakdownItemDto;
import com.example.house_moving_service.dto.response.pricing.CategoryFeesDto;
import com.example.house_moving_service.dto.response.pricing.FinancialSummaryDto;
import com.example.house_moving_service.dto.response.pricing.PricingCalculationResponse;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.common.enums.PropertyType;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import com.example.house_moving_service.service.PricingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PricingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private PricingService pricingService;

    private CustomUserDetails createMockUserDetails(Long id, String email) {
        User user = User.builder()
                .id(id)
                .email(email)
                .password("encoded_pass")
                .isActive(true)
                .build();
        return new CustomUserDetails(user);
    }

    private PricingCalculationRequest createValidRequest() {
        LocalDateTime now = LocalDateTime.now();
        return PricingCalculationRequest.builder()
                .schedule(ScheduleDto.builder()
                        .desiredStartAt(now.plusHours(5))
                        .desiredEndAt(now.plusHours(8))
                        .build())
                .route(RouteDto.builder()
                        .pickup(LocationDto.builder()
                                .address("123 Lê Lợi, Q1, TP.HCM")
                                .lat(new BigDecimal("10.776889"))
                                .lng(new BigDecimal("106.700806"))
                                .propertyType(PropertyType.HOUSE)
                                .floorNumber(0)
                                .hasElevator(true)
                                .walkingDistanceMeters(0)
                                .build())
                        .dropoff(LocationDto.builder()
                                .address("456 Nguyễn Thị Minh Khai, Q3, TP.HCM")
                                .lat(new BigDecimal("10.782622"))
                                .lng(new BigDecimal("106.695844"))
                                .propertyType(PropertyType.APARTMENT)
                                .floorNumber(2)
                                .hasElevator(false)
                                .walkingDistanceMeters(20)
                                .build())
                        .manualDistanceKm(new BigDecimal("15.00"))
                        .build())
                .serviceItems(List.of(
                        ServiceItemDto.builder().serviceId(1).quantity(new BigDecimal("1")).build()
                ))
                .voucherCode("GIAM50K")
                .build();
    }

    private PricingCalculationResponse createMockResponse() {
        return PricingCalculationResponse.builder()
                .isEstimated(true)
                .calculatedDistanceKm(new BigDecimal("15.00"))
                .currency("VND")
                .financialSummary(FinancialSummaryDto.builder()
                        .subtotal(new BigDecimal("1000000.00"))
                        .discountAmount(new BigDecimal("50000.00"))
                        .finalTotal(new BigDecimal("950000.00"))
                        .build())
                .categoryFees(CategoryFeesDto.builder()
                        .baseTransportFee(new BigDecimal("500000.00"))
                        .laborFee(new BigDecimal("500000.00"))
                        .technicalFee(BigDecimal.ZERO)
                        .materialFee(BigDecimal.ZERO)
                        .terrainSurchargeFee(BigDecimal.ZERO)
                        .build())
                .breakdownItems(List.of(
                        BreakdownItemDto.builder()
                                .serviceId(1)
                                .serviceName("Xe tải 1.5 Tấn")
                                .quantity(new BigDecimal("1"))
                                .unitPrice(new BigDecimal("500000.00"))
                                .totalPrice(new BigDecimal("500000.00"))
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Không gửi token -> Bị chặn 401 Unauthorized")
    void estimatePrice_Unauthorized_WhenNoToken_Returns401() throws Exception {
        PricingCalculationRequest request = createValidRequest();

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Gửi token hợp lệ -> Trả về 200 OK kèm kết quả phân rã")
    void estimatePrice_Success_WithToken_Returns200() throws Exception {
        PricingCalculationRequest request = createValidRequest();
        PricingCalculationResponse response = createMockResponse();

        CustomUserDetails userDetails = createMockUserDetails(10L, "customer@example.com");

        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(10L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.isEstimated").value(true))
                .andExpect(jsonPath("$.data.calculatedDistanceKm").value(15.00))
                .andExpect(jsonPath("$.data.financialSummary.subtotal").value(1000000.00))
                .andExpect(jsonPath("$.data.financialSummary.discountAmount").value(50000.00))
                .andExpect(jsonPath("$.data.financialSummary.finalTotal").value(950000.00));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Thiếu trường bắt buộc (serviceItems rỗng) -> Bị chặn 400 Bad Request")
    void estimatePrice_BadRequest_WhenServiceItemsEmpty_Returns400() throws Exception {
        PricingCalculationRequest request = createValidRequest();
        request.setServiceItems(Collections.emptyList()); // Rỗng

        CustomUserDetails userDetails = createMockUserDetails(10L, "customer@example.com");

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Lịch hẹn quá gần (< 2 tiếng) -> Trả về 400 ERR_INVALID_SCHEDULE")
    void estimatePrice_BadRequest_WhenScheduleTooClose_Returns400() throws Exception {
        PricingCalculationRequest request = createValidRequest();
        CustomUserDetails userDetails = createMockUserDetails(10L, "customer@example.com");

        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.ERR_INVALID_SCHEDULE, 2));

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_SCHEDULE.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Dịch vụ bị INACTIVE -> Trả về 400 ERR_SERVICE_INACTIVE")
    void estimatePrice_BadRequest_WhenServiceInactive_Returns400() throws Exception {
        PricingCalculationRequest request = createValidRequest();
        CustomUserDetails userDetails = createMockUserDetails(10L, "customer@example.com");

        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.ERR_SERVICE_INACTIVE, "Xe tải"));

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SERVICE_INACTIVE.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Dịch vụ không tồn tại -> Trả về 404 ERR_SERVICE_NOT_FOUND")
    void estimatePrice_NotFound_WhenServiceNotFound_Returns404() throws Exception {
        PricingCalculationRequest request = createValidRequest();
        CustomUserDetails userDetails = createMockUserDetails(10L, "customer@example.com");

        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, 999));

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SERVICE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Mã voucher không tồn tại -> Trả về 404 ERR_VOUCHER_NOT_FOUND")
    void estimatePrice_NotFound_WhenVoucherNotFound_Returns404() throws Exception {
        PricingCalculationRequest request = createValidRequest();
        CustomUserDetails userDetails = createMockUserDetails(10L, "customer@example.com");

        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.ERR_VOUCHER_NOT_FOUND, "INVALID_VOUCHER"));

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VOUCHER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/estimate - Voucher hết hạn -> Trả về 400 ERR_VOUCHER_EXPIRED")
    void estimatePrice_BadRequest_WhenVoucherExpired_Returns400() throws Exception {
        PricingCalculationRequest request = createValidRequest();
        CustomUserDetails userDetails = createMockUserDetails(10L, "customer@example.com");

        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.ERR_VOUCHER_EXPIRED, "GIAM50K"));

        mockMvc.perform(post("/api/v1/pricing/estimate")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VOUCHER_EXPIRED.getCode()));
    }
}

