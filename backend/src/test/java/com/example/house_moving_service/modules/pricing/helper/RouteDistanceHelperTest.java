package com.example.house_moving_service.modules.pricing.helper;

import com.example.house_moving_service.modules.pricing.config.MapApiProperties;
import com.example.house_moving_service.modules.pricing.config.PricingProperties;
import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RouteDistanceHelperTest {

    private PricingProperties pricingProperties;
    private MapApiProperties mapApiProperties;
    private RouteDistanceHelper routeDistanceHelper;
    // Giả lập Goong, không gọi mạng thật
    private HttpClient httpClient;

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
        mapApiProperties.setApiKey("TEST_API_KEY");
        mapApiProperties.setConnectTimeoutMs(3000);
        mapApiProperties.setReadTimeoutMs(4000);

        httpClient = mock(HttpClient.class);
        routeDistanceHelper = new RouteDistanceHelper(pricingProperties, mapApiProperties, httpClient);

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
        verifyNoInteractions(httpClient);
    }

    @SuppressWarnings("unchecked")
    private void stubGoong(int statusCode, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(statusCode).when(response).statusCode();
        doReturn(body).when(response).body();
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Tầng 2: Lấy cự ly từ Goong Distance Matrix (giả lập) khi app.map.enabled=true")
    void resolveDistance_tier2_callsGoongApiSuccessfully() throws Exception {
        stubGoong(200, "{\"rows\":[{\"elements\":[{\"status\":\"OK\",\"distance\":{\"value\":7320}}]}]}");

        RouteDto route = RouteDto.builder()
                .pickup(benThanh)
                .dropoff(tanSonNhat)
                .manualDistanceKm(null)
                .build();

        BigDecimal distance = routeDistanceHelper.resolveDistance(route);

        // 7320 m → 7.32 km
        assertThat(distance).isEqualByComparingTo("7.32");
        verify(httpClient).send(argThat(req -> req.uri().toString().contains("api_key=TEST_API_KEY")), any());
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
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Tầng 3 Fallback Resilience: Khi API Key sai hoặc mất mạng -> không sập hệ thống, fallback về Haversine")
    void resolveDistance_tier3_fallbackToHaversineWhenApiKeyInvalid() throws Exception {
        stubGoong(403, "{\"error\":\"invalid api key\"}");

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

    @Test
    @DisplayName("Tầng 3 Fallback Resilience: Mất mạng (IOException) -> fallback về Haversine")
    void resolveDistance_tier3_fallbackToHaversineWhenNetworkFails() throws Exception {
        doThrow(new IOException("connect timed out")).when(httpClient).send(any(HttpRequest.class), any());

        RouteDto route = RouteDto.builder().pickup(benThanh).dropoff(tanSonNhat).build();

        assertThat(routeDistanceHelper.resolveDistance(route)).isEqualByComparingTo("8.67");
    }
}
