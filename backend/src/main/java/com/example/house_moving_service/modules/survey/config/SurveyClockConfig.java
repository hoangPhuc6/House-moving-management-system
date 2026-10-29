package com.example.house_moving_service.modules.survey.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// "Hôm nay" / "bây giờ" khi kiểm tra lịch khảo sát luôn theo giờ Việt Nam, không theo múi giờ máy chủ.
// Inject Clock thay vì gọi LocalDate.now() để test cố định được thời gian.
@Configuration
public class SurveyClockConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock surveyClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
