package com.example.house_moving_service.modules.survey.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.survey")
@Getter
@Setter
public class SurveyProperties {

    /**
     * MOVER được bấm bắt đầu khảo sát sớm tối đa bao nhiêu phút trước giờ bắt đầu khung giờ. Mặc định 60 phút;
     * 0 = chỉ được bắt đầu từ đúng giờ. Không giới hạn bắt đầu muộn (chỉ cần đúng ngày khảo sát).
     */
    private int earlyStartMinutes = 60;
}
