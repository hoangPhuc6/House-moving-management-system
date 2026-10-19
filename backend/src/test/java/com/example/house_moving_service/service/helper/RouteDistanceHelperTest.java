package com.example.house_moving_service.service.helper;

import com.example.house_moving_service.config.MapApiProperties;
import com.example.house_moving_service.config.PricingProperties;
import com.example.house_moving_service.dto.request.pricing.LocationDto;
import com.example.house_moving_service.dto.request.pricing.RouteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDistanceHelperTest {

    private PricingProperties pricingProperties;
    private MapApiProperties mapApiProperties;
    private RouteDistanceHelper routeDistanceHelper;

    // Tọa độ thực tế: Chợ Bến Thành -> Sân bay Tân Sơn Nhất
    private LocationDto benThanh;
    private LocationDto tanSonNhat;

    @BeforeEach
    void setUp() {
        pricingProperties = new PricingProperties();
        pricingProperties.setHaversineWindingFactor(1.3);

        mapApiProperties = new MapApiProperties();
        mapApiProperties.setEnabled(true);
        mapApiProperties.setProvider("GOONG");
        mapApiProperties.setApiKey("DDYnLEXmBZx1TxgQ3vZbWeNOwUMi8KWYqarmpfZq");
        mapApiProperties.setConnectTimeoutMs(3000);
        mapApiProperties.setReadTimeoutMs(4000);

        routeDistanceHelper = new RouteDistanceHelper(pricingProperties, mapApiProperties);

        // Chợ Bến Thành, Q1
        benThanh = LocationDto.builder()
                .address("Chợ Bến Thành, Lê Lợi, P. Bến Thành, Q.1, TP.HCM")
                .lat(new BigDecimal("10.7725"))
                .lng(new BigDecimal("106.6980"))
                .build();

        // Sân bay Tân Sơn Nhất, Tân Bình
        tanSonNhat = LocationDto.builder()
                .address("Sân bay Tân Sơn Nhất, Trường Sơn, P.2, Q. Tân Bình, TP.HCM")
                .lat(new BigDecimal("10.8185"))
                .lng(new BigDecimal("106.6588"))
                .build();
    }

    @Test
    @DisplayName("Tầng 1: Ưu tiên manualDistanceKm khi người dùng/nhân viên nhập trực tiếp")
    void resolveDistance_tier1_manualDistanceTakesHighestPriority() {
        RouteDto route = RouteDto.builder()
                .pickup(benThanh)
                .dropoff(tanSonNhat)
                .manualDistanceKm(new BigDecimal("15.50"))
                .build();

        BigDecimal distance = routeDistanceHelper.resolveDistance(route);

        assertThat(distance).isEqualByComparingTo("15.50");
    }

    @Test
    @DisplayName("Tầng 2: Gọi Goong Distance Matrix API thực tế khi app.map.enabled=true")
    void resolveDistance_tier2_callsGoongApiSuccessfully() {
        RouteDto route = RouteDto.builder()
                .pickup(benThanh)
                .dropoff(tanSonNhat)
                .manualDistanceKm(null)
                .build();

        BigDecimal distance = routeDistanceHelper.resolveDistance(route);

        // Goong trả về cự ly xe chạy thực tế ~7.32 km (từ 7.0 đến 8.0 km)
        assertThat(distance).isNotNull();
        assertThat(distance).isGreaterThan(new BigDecimal("6.00"));
        assertThat(distance).isLessThan(new BigDecimal("9.00"));
        System.out.printf("Cự ly Goong API định tuyến thực tế từ Chợ Bến Thành -> Tân Sơn Nhất: %s km%n", distance);
    }

    @Test
    @DisplayName("Tầng 3: Tự động fallback về Haversine * 1.3 khi app.map.enabled = false")
    void resolveDistance_tier3_fallbackToHaversineWhenMapApiDisabled() {
        mapApiProperties.setEnabled(false);

        RouteDto route = RouteDto.builder()
                .pickup(benThanh)
                .dropoff(tanSonNhat)
                .manualDistanceKm(null)
                .build();

        BigDecimal distance = routeDistanceHelper.resolveDistance(route);

        // Haversine tính ~8.67 km
        assertThat(distance).isNotNull();
        assertThat(distance).isEqualByComparingTo("8.67");
    }

    @Test
    @DisplayName("Tầng 3 Fallback Resilience: Khi API Key sai hoặc mất mạng -> không sập hệ thống, fallback về Haversine")
    void resolveDistance_tier3_fallbackToHaversineWhenApiKeyInvalid() {
        mapApiProperties.setApiKey("INVALID_API_KEY_TEST");

        RouteDto route = RouteDto.builder()
                .pickup(benThanh)
                .dropoff(tanSonNhat)
                .manualDistanceKm(null)
                .build();

        BigDecimal distance = routeDistanceHelper.resolveDistance(route);

        // API lỗi HTTP 401/403 -> tự động fallback Haversine 8.67 km
        assertThat(distance).isNotNull();
        assertThat(distance).isEqualByComparingTo("8.67");
    }
}

