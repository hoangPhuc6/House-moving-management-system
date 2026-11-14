package com.example.house_moving_service.modules.scheduling.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "app.surcharge")
@Getter
@Setter
public class SurchargeProperties {

    /** Số lượng tối đa của một khoản phụ phí (phần chênh lệch so với lúc khách khai). Mặc định 100. */
    private int maxQuantity = 100;

    /** Đơn giá tối đa của một khoản phụ phí (VNĐ). Mặc định 50.000.000. */
    private BigDecimal maxUnitPrice = new BigDecimal("50000000");
}
