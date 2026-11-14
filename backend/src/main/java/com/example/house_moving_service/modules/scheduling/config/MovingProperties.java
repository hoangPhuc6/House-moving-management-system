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

    /** Nhân viên còn báo được sự cố của chuyến đã COMPLETED trong bao nhiêu giờ sau actual_end_at. Mặc định 24. */
    private int incidentReportWindowHours = 24;

    /**
     * Khách còn tự báo được sự cố của đơn đã COMPLETED trong bao nhiêu ngày sau khi chuyến cuối kết thúc
     * (actual_end_at muộn nhất). Mặc định 7.
     */
    private int customerIncidentReportWindowDays = 7;

    /** Một chuyến dài tối đa bao nhiêu giờ (scheduled_end_at - scheduled_start_at). Chuyển nhiều ngày thì tách nhiều chuyến. Mặc định 24. */
    private int maxScheduleHours = 24;
}
