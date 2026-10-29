package com.example.house_moving_service.modules.survey.repository;

import com.example.house_moving_service.modules.survey.entity.SurveyAttachment;
import com.example.house_moving_service.modules.survey.enums.AttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyAttachmentRepository extends JpaRepository<SurveyAttachment, Integer> {

    List<SurveyAttachment> findAllBySurveyIdOrderByCreatedAtAsc(Integer surveyId);

    long countBySurveyId(Integer surveyId);

    // Đếm theo loại (giới hạn số video của một lần khảo sát)
    long countBySurveyIdAndFileType(Integer surveyId, AttachmentType fileType);

    // Chỉ lấy tệp thuộc đúng lần khảo sát đó (chống truyền id tệp của khảo sát khác)
    Optional<SurveyAttachment> findByIdAndSurveyId(Integer id, Integer surveyId);
}
