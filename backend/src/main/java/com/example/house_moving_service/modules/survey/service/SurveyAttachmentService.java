package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.survey.dto.response.SurveyAttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Tệp đính kèm của lịch khảo sát. isManagement = true khi gọi từ vùng quản lý (ADMIN / DISPATCHER),
 * false khi gọi từ vùng MOVER (chỉ lịch mình được phân công).
 */
public interface SurveyAttachmentService {

    /**
     * MOVER: lịch của mình, đang IN_PROGRESS. Quản lý: lịch chưa CANCELLED (kể cả đã COMPLETED, để bổ sung ảnh khách gửi).
     * Kiểm tra loại / đuôi / dung lượng / nội dung file và giới hạn số tệp, rồi lưu lên nơi lưu trữ.
     */
    SurveyAttachmentResponse upload(Integer surveyId, MultipartFile file, Long userId, boolean isManagement);

    List<SurveyAttachmentResponse> list(Integer surveyId, Long userId, boolean isManagement);

    /**
     * Lịch đã từng hoàn tất thì không ai xóa được (tệp là bằng chứng cho báo giá). MOVER chỉ xóa tệp do mình tải lên.
     * Xóa dòng DB trong transaction, xóa file trên nơi lưu trữ sau khi commit.
     */
    void delete(Integer surveyId, Integer attachmentId, Long userId, boolean isManagement);
}
