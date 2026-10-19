package com.example.house_moving_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "app.pricing")
@Getter
@Setter
public class PricingProperties {

    /**
     * Thời gian đặt trước tối thiểu (tiếng). Mặc định là 2 giờ.
     */
    private int minLeadHours = 2;

    /**
     * Cự ly cơ sở đã bao gồm trong gói xe vận chuyển (km). Mặc định là 10.0 km.
     */
    private BigDecimal baseTransportIncludedKm = new BigDecimal("10.0");

    /**
     * Hệ số uốn lượn đường bộ khi tính dự phòng Haversine. Mặc định là 1.3.
     */
    private double haversineWindingFactor = 1.3;
}

