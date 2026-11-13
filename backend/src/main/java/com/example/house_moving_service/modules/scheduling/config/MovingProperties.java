package com.example.house_moving_service.modules.scheduling.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.moving")
@Getter
@Setter
public class MovingProperties {

    /**
     * Giám sát viên được bấm bắt đầu chuyến sớm tối đa bao nhiêu phút trước giờ hẹn (scheduled_start_at). Mặc định 60;
     * 0 = chỉ được bắt đầu từ đúng giờ. Bắt đầu muộn không bị chặn (chỉ cần đúng ngày của giờ hẹn).
     */
    private int earlyStartMinutes = 60;
}
