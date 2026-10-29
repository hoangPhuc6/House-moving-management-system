package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.common.storage.FileStorageService;
import com.example.house_moving_service.common.storage.StorageException;
import com.example.house_moving_service.common.storage.StorageProperties;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.survey.config.SurveyProperties;
import com.example.house_moving_service.modules.survey.dto.response.SurveyAttachmentResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyAttachment;
import com.example.house_moving_service.modules.survey.enums.AttachmentType;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.repository.SurveyAttachmentRepository;
import com.example.house_moving_service.modules.survey.service.SurveyAttachmentService;
import com.example.house_moving_service.modules.survey.service.impl.SurveyAttachmentPolicy.Format;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SurveyAttachmentServiceImpl implements SurveyAttachmentService {

    private final BookingSurveyRepository bookingSurveyRepository;
    private final SurveyAttachmentRepository surveyAttachmentRepository;
    private final SurveyAttachmentPolicy attachmentPolicy;
    private final UserLookupService userLookupService;
    private final StaffLookupService staffLookupService;
    // Không bắt buộc: chưa cấu hình app.storage.provider thì không có bean, upload báo ERR_FILE_STORAGE
    private final ObjectProvider<FileStorageService> fileStorageService;
    private final StorageProperties storageProperties;
    private final SurveyProperties surveyProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyAttachmentResponse upload(Integer surveyId, MultipartFile file, Long userId, boolean isManagement) {
        // Khóa lịch: đếm số tệp và lưu trong cùng một lượt, hai lần upload đồng thời không cùng vượt giới hạn
        BookingSurvey survey = loadForUpdate(surveyId);
        if (isManagement) {
            if (survey.getStatus() == SurveyStatus.CANCELLED) {
                throw new AppException(ErrorCode.ERR_SURVEY_INVALID_STATUS);
            }
        } else {
            requireOwner(survey, userId);
            if (survey.getStatus() != SurveyStatus.IN_PROGRESS) {
                throw new AppException(ErrorCode.ERR_SURVEY_INVALID_STATUS);
            }
        }

        Format format = attachmentPolicy.resolve(file.getContentType(), file.getOriginalFilename(), file.getSize(), readHeader(file));
        ensureWithinLimits(surveyId, format.type());
        FileStorageService storage = requireStorage();

        String key = "surveys/" + survey.getBooking().getId() + "/" + surveyId + "/" + UUID.randomUUID() + "." + format.defaultExtension();
        try (InputStream data = file.getInputStream()) {
            storage.upload(key, data, file.getSize(), format.contentType());
        } catch (IOException | StorageException e) {
            log.error("Upload attachment failed for survey id: {} key: {}", surveyId, key, e);
            throw new AppException(ErrorCode.ERR_FILE_STORAGE);
        }
        // File đã lên nơi lưu trữ trước DB: nếu transaction rollback thì xóa file vừa upload để không mồ côi
        runAfterRollback(() -> deleteQuietly(storage, key));

        SurveyAttachment saved = surveyAttachmentRepository.saveAndFlush(SurveyAttachment.builder()
                .survey(survey)
                .fileUrl(key)
                .fileType(format.type())
                .uploadedBy(userLookupService.getUserReference(userId))
                .build());

        log.info("Uploaded attachment id: {} ({}) to survey id: {} by user id: {}", saved.getId(), format.type(), surveyId, userId);
        return toResponse(saved, userId, new HashMap<>());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SurveyAttachmentResponse> list(Integer surveyId, Long userId, boolean isManagement) {
        BookingSurvey survey = bookingSurveyRepository.findById(surveyId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_NOT_FOUND, surveyId));
        if (!isManagement) {
            requireOwner(survey, userId);
        }
        // Một lịch có tới vài chục tệp nhưng thường chỉ vài người tải lên: tra tên mỗi người một lần
        Map<Long, String> uploaderNames = new HashMap<>();
        return surveyAttachmentRepository.findAllBySurveyIdOrderByCreatedAtAsc(surveyId).stream()
                .map(attachment -> toResponse(attachment, uploaderId(attachment), uploaderNames))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer surveyId, Integer attachmentId, Long userId, boolean isManagement) {
        BookingSurvey survey = loadForUpdate(surveyId);
        if (!isManagement) {
            requireOwner(survey, userId);
        }
        // Đã từng hoàn tất (kể cả sau đó bị khảo sát lại) thì tệp là bằng chứng của kết quả đó
        if (survey.getActualEndAt() != null || survey.getStatus() == SurveyStatus.COMPLETED) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
        SurveyAttachment attachment = surveyAttachmentRepository.findByIdAndSurveyId(attachmentId, surveyId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_ATTACHMENT_NOT_FOUND, attachmentId));
        if (!isManagement && !userId.equals(uploaderId(attachment))) {
            throw new AppException(ErrorCode.ERR_SURVEY_FORBIDDEN);
        }

        String key = attachment.getFileUrl();
        surveyAttachmentRepository.delete(attachment);
        // Xóa file sau commit: DB rollback thì file vẫn còn, không có dòng nào trỏ tới file đã mất
        FileStorageService storage = fileStorageService.getIfAvailable();
        if (storage != null) {
            runAfterCommit(() -> deleteQuietly(storage, key));
        }
        log.info("Deleted attachment id: {} of survey id: {} by user id: {}", attachmentId, surveyId, userId);
    }

    private BookingSurvey loadForUpdate(Integer surveyId) {
        return bookingSurveyRepository.findByIdForUpdate(surveyId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_NOT_FOUND, surveyId));
    }

    private void requireOwner(BookingSurvey survey, Long userId) {
        if (survey.getSurveyStaff() == null || !survey.getSurveyStaff().getId().equals(userId)) {
            throw new AppException(ErrorCode.ERR_SURVEY_FORBIDDEN);
        }
    }

    private void ensureWithinLimits(Integer surveyId, AttachmentType type) {
        if (surveyAttachmentRepository.countBySurveyId(surveyId) >= surveyProperties.getMaxAttachments()) {
            throw new AppException(ErrorCode.ERR_SURVEY_ATTACHMENT_LIMIT, "tối đa " + surveyProperties.getMaxAttachments() + " tệp");
        }
        if (type == AttachmentType.VIDEO
                && surveyAttachmentRepository.countBySurveyIdAndFileType(surveyId, AttachmentType.VIDEO)
                >= surveyProperties.getMaxVideoAttachments()) {
            throw new AppException(ErrorCode.ERR_SURVEY_ATTACHMENT_LIMIT, "tối đa " + surveyProperties.getMaxVideoAttachments() + " video");
        }
    }

    private FileStorageService requireStorage() {
        FileStorageService storage = fileStorageService.getIfAvailable();
        if (storage == null) {
            log.error("No file storage configured (app.storage.provider is empty)");
            throw new AppException(ErrorCode.ERR_FILE_STORAGE);
        }
        return storage;
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SurveyAttachmentPolicy.HEADER_LENGTH);
        } catch (IOException e) {
            throw new AppException(ErrorCode.ERR_FILE_STORAGE);
        }
    }

    private SurveyAttachmentResponse toResponse(SurveyAttachment attachment, Long uploaderId, Map<Long, String> uploaderNames) {
        String uploaderName = uploaderId == null ? null : uploaderNames.computeIfAbsent(uploaderId,
                id -> staffLookupService.findSummary(id).map(StaffSummary::fullName).orElse(null));
        return SurveyAttachmentResponse.builder()
                .id(attachment.getId())
                .fileType(attachment.getFileType())
                .contentType(attachmentPolicy.contentTypeOfKey(attachment.getFileUrl()).orElse(null))
                .accessUrl(accessUrl(attachment.getFileUrl()))
                .uploadedById(uploaderId)
                .uploadedByName(uploaderName)
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    // Chưa cấu hình nơi lưu trữ, hoặc nơi lưu trữ lỗi: vẫn trả danh sách, chỉ thiếu URL xem
    private String accessUrl(String key) {
        FileStorageService storage = fileStorageService.getIfAvailable();
        if (storage == null) {
            return null;
        }
        try {
            return storage.generateAccessUrl(key, Duration.ofMinutes(storageProperties.getUrlTtlMinutes()));
        } catch (StorageException e) {
            log.warn("Cannot generate access URL for key: {}", key, e);
            return null;
        }
    }

    private static Long uploaderId(SurveyAttachment attachment) {
        return attachment.getUploadedBy() != null ? attachment.getUploadedBy().getId() : null;
    }

    private static void deleteQuietly(FileStorageService storage, String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // File mồ côi trên nơi lưu trữ: vô hại (không dòng DB nào trỏ tới), chỉ ghi log để dọn sau
            log.warn("Cannot delete stored file: {}", key, e);
        }
    }

    private static void runAfterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }

    private static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
