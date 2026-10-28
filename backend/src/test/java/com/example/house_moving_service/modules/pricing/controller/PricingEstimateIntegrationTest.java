package com.example.house_moving_service.modules.pricing.controller;

import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gọi API thật POST /api/v1/pricing/estimate: PricingService, PricingEngine và DB thật (seed + V12), không mock.
 * Đơn giá seed: xe 1.5 tấn 450.000đ, vượt km 15.000đ, thang bộ 50.000đ/lầu, hẻm sâu 100.000đ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PricingEstimateIntegrationTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    private CustomUserDetails customer;
    private Service truck;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByEmail("customer1@gmail.com").orElseThrow();
        customer = new CustomUserDetails(user);
        truck = serviceRepository.findByName("Xe tải 1.5 Tấn (Nội thành)").orElseThrow();
    }

    private String body(String startAt, String distanceKm, int pickupFloor, boolean pickupElevator,
                        int dropoffWalkingMeters, String serviceItemsJson, String voucherCode) {
        String voucher = voucherCode == null ? "" : ",\"voucherCode\":\"" + voucherCode + "\"";
        return """
                {
                  "schedule": {"desiredStartAt": "%s", "desiredEndAt": "%s"},
                  "route": {
                    "pickup": {"address": "123 Lê Lợi, Q1", "lat": 10.776889, "lng": 106.700806,
                               "floorNumber": %d, "hasElevator": %s, "walkingDistanceMeters": 0},
                    "dropoff": {"address": "456 NTMK, Q3", "lat": 10.782622, "lng": 106.695844,
                                "floorNumber": 0, "hasElevator": false, "walkingDistanceMeters": %d},
                    "manualDistanceKm": %s
                  },
                  "serviceItems": %s%s
                }
                """.formatted(startAt, LocalDateTime.parse(startAt).plusHours(4).format(ISO),
                pickupFloor, pickupElevator, dropoffWalkingMeters, distanceKm, serviceItemsJson, voucher);
    }

    private String inThreeDays() {
        return LocalDateTime.now().plusDays(3).withNano(0).format(ISO);
    }

    private ResultActions estimate(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/pricing/estimate")
                .with(user(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("Đơn đơn giản: 8 km, tầng trệt → chỉ giá cơ bản, không phụ phí")
    void basicPrice() throws Exception {
        estimate(body(inThreeDays(), "8", 0, false, 0,
                "[{\"serviceId\":%d,\"quantity\":1}]".formatted(truck.getId()), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isEstimated").value(true))
                .andExpect(jsonPath("$.data.chargeableExtraKm").value(0))
                .andExpect(jsonPath("$.data.breakdownItems", hasSize(1)))
                .andExpect(jsonPath("$.data.categoryFees.baseTransportFee").value("450000.00"))
                .andExpect(jsonPath("$.data.financialSummary.finalTotal").value("450000.00"));
    }

    @Test
    @DisplayName("15 km, điểm lấy tầng 5 không thang máy, điểm giao đi bộ 80 m → đủ 3 phụ phí, từng khoản có giải thích")
    void autoSurcharges() throws Exception {
        estimate(body(inThreeDays(), "15", 5, false, 80,
                "[{\"serviceId\":%d,\"quantity\":1}]".formatted(truck.getId()), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.includedKm").value(10.0))
                .andExpect(jsonPath("$.data.chargeableExtraKm").value(5))
                .andExpect(jsonPath("$.data.breakdownItems", hasSize(4)))
                .andExpect(jsonPath("$.data.breakdownItems[0].autoApplied").value(false))
                .andExpect(jsonPath("$.data.breakdownItems[1].note").value("Vượt 5 km (15 km, đã gồm 10 km)"))
                .andExpect(jsonPath("$.data.breakdownItems[1].autoApplied").value(true))
                .andExpect(jsonPath("$.data.breakdownItems[2].note").value("Điểm lấy hàng: tầng 5, không thang máy (4 tầng)"))
                .andExpect(jsonPath("$.data.breakdownItems[3].note").value("Điểm giao hàng: đi bộ 80 m từ chỗ đậu xe"))
                // 450.000 + 75.000 vận chuyển; 200.000 + 100.000 địa hình
                .andExpect(jsonPath("$.data.categoryFees.baseTransportFee").value("525000.00"))
                .andExpect(jsonPath("$.data.categoryFees.terrainSurchargeFee").value("300000.00"))
                .andExpect(jsonPath("$.data.financialSummary.subtotal").value("825000.00"));
    }

    @Test
    @DisplayName("Dịch vụ bổ sung (nhân công, vật tư) cộng đúng nhóm phí")
    void additionalServices() throws Exception {
        Service labor = serviceRepository.findAll().stream()
                .filter(s -> s.getServiceType().getId() == 2 && s.getCode() == null && s.getStatus() == ServiceStatus.ACTIVE)
                .findFirst().orElseThrow();
        Service material = serviceRepository.findAll().stream()
                .filter(s -> s.getServiceType().getId() == 4 && s.getCode() == null && s.getStatus() == ServiceStatus.ACTIVE)
                .findFirst().orElseThrow();
        String items = "[{\"serviceId\":%d,\"quantity\":1},{\"serviceId\":%d,\"quantity\":2},{\"serviceId\":%d,\"quantity\":10}]"
                .formatted(truck.getId(), labor.getId(), material.getId());

        estimate(body(inThreeDays(), "8", 0, false, 0, items, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryFees.laborFee")
                        .value(labor.getBasePrice().multiply(java.math.BigDecimal.valueOf(2)).setScale(2).toPlainString()))
                .andExpect(jsonPath("$.data.categoryFees.materialFee")
                        .value(material.getBasePrice().multiply(java.math.BigDecimal.TEN).setScale(2).toPlainString()));
    }

    @Test
    @DisplayName("Dữ liệu không hợp lệ: số lượng 1.5 / 0, dịch vụ không tồn tại, lịch quá gần → 400")
    void invalidInput() throws Exception {
        estimate(body(inThreeDays(), "8", 0, false, 0, "[{\"serviceId\":%d,\"quantity\":1.5}]".formatted(truck.getId()), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()));
        estimate(body(inThreeDays(), "8", 0, false, 0, "[{\"serviceId\":%d,\"quantity\":0}]".formatted(truck.getId()), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()));
        estimate(body(inThreeDays(), "8", 0, false, 0, "[{\"serviceId\":999999,\"quantity\":1}]", null))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SERVICE_NOT_FOUND.getCode()));
        estimate(body(LocalDateTime.now().plusMinutes(30).withNano(0).format(ISO), "8", 0, false, 0,
                "[{\"serviceId\":%d,\"quantity\":1}]".formatted(truck.getId()), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_SCHEDULE.getCode()));
    }

    @Test
    @DisplayName("Dịch vụ đã ngừng hoạt động không dùng được cho đơn mới → ERR_SERVICE_INACTIVE")
    void inactiveService() throws Exception {
        truck.setStatus(ServiceStatus.INACTIVE);
        serviceRepository.saveAndFlush(truck);

        estimate(body(inThreeDays(), "8", 0, false, 0,
                "[{\"serviceId\":%d,\"quantity\":1}]".formatted(truck.getId()), null))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SERVICE_INACTIVE.getCode()));
    }
}
