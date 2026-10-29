package com.example.house_moving_service.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế chạy tác vụ định kỳ (@Scheduled) cho toàn ứng dụng.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
