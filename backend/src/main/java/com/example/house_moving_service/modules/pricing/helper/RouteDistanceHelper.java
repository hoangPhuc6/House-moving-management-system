package com.example.house_moving_service.modules.pricing.helper;

import com.example.house_moving_service.modules.pricing.config.MapApiProperties;
import com.example.house_moving_service.modules.pricing.config.PricingProperties;
import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Component
@Slf4j
public class RouteDistanceHelper {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final PricingProperties pricingProperties;
    private final MapApiProperties mapApiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public RouteDistanceHelper(PricingProperties pricingProperties, MapApiProperties mapApiProperties) {
        this(pricingProperties, mapApiProperties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .build());
    }

    // Cho test truyền HttpClient giả lập, không gọi mạng thật
    RouteDistanceHelper(PricingProperties pricingProperties, MapApiProperties mapApiProperties, HttpClient httpClient) {
        this.pricingProperties = pricingProperties;
        this.mapApiProperties = mapApiProperties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = httpClient;
    }

    /**
     * Phân giải cự ly di chuyển qua 3 tầng:
     * 1. Nhập thủ công (manualDistanceKm > 0)
     * 2. Gọi Map Routing API ngoài (nếu bật cấu hình)
     * 3. Fallback tính cự ly Haversine * winding factor in-memory
     */
    public BigDecimal resolveDistance(RouteDto route) {
        if (route == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Tầng 1: Thủ công
        if (route.getManualDistanceKm() != null && route.getManualDistanceKm().compareTo(BigDecimal.ZERO) > 0) {
            return route.getManualDistanceKm().setScale(2, RoundingMode.HALF_UP);
        }

        // Tầng 2: External Map API (nếu được kích hoạt)
        if (mapApiProperties.isEnabled() && mapApiProperties.getApiKey() != null && !mapApiProperties.getApiKey().isBlank()) {
            try {
                BigDecimal apiDistance = callExternalMapApi(route.getPickup(), route.getDropoff());
                if (apiDistance != null && apiDistance.compareTo(BigDecimal.ZERO) > 0) {
                    return apiDistance.setScale(2, RoundingMode.HALF_UP);
                }
            } catch (Exception e) {
                log.warn("External map API routing failed or timed out. Falling back to Haversine calculation. Error: {}", e.getMessage());
            }
        }

        // Tầng 3: In-memory Haversine formula fallback
        return calculateHaversineDistance(route.getPickup(), route.getDropoff());
    }

    /**
     * Tính cự ly đường chim bay theo công thức Haversine và nhân hệ số đường bộ
     */
    public BigDecimal calculateHaversineDistance(LocationDto pickup, LocationDto dropoff) {
        if (pickup == null || dropoff == null || pickup.getLat() == null || pickup.getLng() == null
                || dropoff.getLat() == null || dropoff.getLng() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        double lat1 = Math.toRadians(pickup.getLat().doubleValue());
        double lon1 = Math.toRadians(pickup.getLng().doubleValue());
        double lat2 = Math.toRadians(dropoff.getLat().doubleValue());
        double lon2 = Math.toRadians(dropoff.getLng().doubleValue());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distanceKm = EARTH_RADIUS_KM * c * pricingProperties.getHaversineWindingFactor();

        if (distanceKm < 0.1) {
            distanceKm = 0.1;
        }

        return BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal callExternalMapApi(LocationDto pickup, LocationDto dropoff) {
        if (!"GOONG".equalsIgnoreCase(mapApiProperties.getProvider())) {
            log.debug("Unsupported map provider: {}", mapApiProperties.getProvider());
            return null;
        }

        if (pickup.getLat() == null || pickup.getLng() == null || dropoff.getLat() == null || dropoff.getLng() == null) {
            return null;
        }

        String uri = String.format(Locale.US,
                "https://rsapi.goong.io/DistanceMatrix?origins=%f,%f&destinations=%f,%f&vehicle=car&api_key=%s",
                pickup.getLat().doubleValue(),
                pickup.getLng().doubleValue(),
                dropoff.getLat().doubleValue(),
                dropoff.getLng().doubleValue(),
                mapApiProperties.getApiKey().trim()
        );

        try {
            int readTimeout = mapApiProperties.getReadTimeoutMs() > 0 ? mapApiProperties.getReadTimeoutMs() : 3000;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofMillis(readTimeout))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode element = root.path("rows").path(0).path("elements").path(0);
                String status = element.path("status").asText();

                if ("OK".equalsIgnoreCase(status)) {
                    long distanceMeters = element.path("distance").path("value").asLong();
                    BigDecimal distanceKm = BigDecimal.valueOf(distanceMeters)
                            .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
                    log.info("Goong DistanceMatrix API successfully resolved route distance: {} km ({} meters)",
                            distanceKm, distanceMeters);
                    return distanceKm;
                } else {
                    log.warn("Goong API returned non-OK element status: {}", status);
                }
            } else {
                log.warn("Goong API returned HTTP status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to call Goong Distance Matrix API: {}", e.getMessage());
        }

        return null;
    }
}

