package com.example.house_moving_service.modules.survey;

import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Đồng hồ chỉnh được cho test tích hợp luồng khảo sát: tạo lịch "hôm trước", rồi tua tới giờ hẹn để MOVER bắt đầu.
 * Không phụ thuộc giờ chạy test.
 */
@TestConfiguration
public class SurveyTestClockConfig {

    @Bean
    @Primary
    public MutableClock mutableTestClock() {
        return new MutableClock(LocalDateTime.of(2099, 1, 1, 7, 0));
    }

    public static final class MutableClock extends Clock {

        private static final ZoneId ZONE = SurveyClockConfig.BUSINESS_ZONE;
        private volatile Instant instant;

        MutableClock(LocalDateTime start) {
            set(start);
        }

        public void set(LocalDateTime businessTime) {
            this.instant = businessTime.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
