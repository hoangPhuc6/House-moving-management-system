package com.example.house_moving_service.modules.survey.dto.response;

import com.example.house_moving_service.modules.survey.enums.AttachmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Một tệp đính kèm khảo sát. accessUrl có hạn (mặc định 15 phút), sinh lại mỗi lần đọc
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyAttachmentResponse {

    private Integer id;
    private AttachmentType fileType;
    private String contentType;

    // null khi chưa cấu hình nơi lưu trữ
    private String accessUrl;

    private Long uploadedById;
    private String uploadedByName;
    private LocalDateTime createdAt;
}
