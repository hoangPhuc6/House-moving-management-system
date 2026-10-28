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

    /**
     * Số tệp đính kèm tối đa của một lần khảo sát (MOVER và quản lý dùng chung hạn mức). Nhà phố nhiều tầng cần khoảng
     * 30–50 ảnh; giới hạn chỉ để chặn dùng sai (bấm nhầm chọn cả thư viện ảnh).
     */
    private int maxAttachments = 50;

    /** Trong số đó, tối đa bao nhiêu video (mỗi video tới 50 MB, gấp nhiều lần ảnh). Ảnh và PDF không giới hạn riêng. */
    private int maxVideoAttachments = 5;
}
