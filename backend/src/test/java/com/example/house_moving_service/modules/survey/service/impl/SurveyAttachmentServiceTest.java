package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.common.storage.FileStorageService;
import com.example.house_moving_service.common.storage.StorageException;
import com.example.house_moving_service.common.storage.StorageProperties;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.survey.config.SurveyProperties;
import com.example.house_moving_service.modules.survey.dto.response.SurveyAttachmentResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyAttachment;
import com.example.house_moving_service.modules.survey.enums.AttachmentType;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.repository.SurveyAttachmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyAttachmentServiceTest {

    private static final int SURVEY_ID = 40;
    private static final int BOOKING_ID = 7;
    private static final long MOVER_ID = 8L;
    private static final long OTHER_MOVER_ID = 9L;
    private static final long DISPATCHER_ID = 3L;

    @Mock
    private BookingSurveyRepository bookingSurveyRepository;
    @Mock
    private SurveyAttachmentRepository surveyAttachmentRepository;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private StaffLookupService staffLookupService;
    @Mock
    private ObjectProvider<FileStorageService> storageProvider;
    @Mock
    private FileStorageService storage;

    private SurveyProperties surveyProperties;
    private SurveyAttachmentServiceImpl service;

    @BeforeEach
    void setUp() {
        surveyProperties = new SurveyProperties();
        service = new SurveyAttachmentServiceImpl(bookingSurveyRepository, surveyAttachmentRepository,
                new SurveyAttachmentPolicy(), userLookupService, staffLookupService, storageProvider,
                new StorageProperties(), surveyProperties);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private BookingSurvey givenSurvey(SurveyStatus status, Long staffId) {
        BookingSurvey survey = BookingSurvey.builder()
                .id(SURVEY_ID).booking(Booking.builder().id(BOOKING_ID).build())
                .surveyMethod(SurveyMethod.ONSITE).status(status)
                .surveyStaff(staffId != null ? Staff.builder().id(staffId).build() : null)
                .build();
        when(bookingSurveyRepository.findByIdForUpdate(SURVEY_ID)).thenReturn(Optional.of(survey));
        return survey;
    }

    private static MockMultipartFile file(String name, String contentType, byte[] header) {
        byte[] content = Arrays.copyOf(header, 1024);
        return new MockMultipartFile("file", name, contentType, content);
    }

    private static MockMultipartFile jpeg() {
        return file("phong_khach.jpg", "image/jpeg", SurveyAttachmentPolicyTest.JPEG);
    }

    private static MockMultipartFile mp4() {
        return file("quay_nha.mp4", "video/mp4", SurveyAttachmentPolicyTest.MP4);
    }

    private void givenSaveSucceeds() {
        when(userLookupService.getUserReference(anyLong())).thenAnswer(inv -> User.builder().id(inv.getArgument(0)).build());
        when(surveyAttachmentRepository.saveAndFlush(any(SurveyAttachment.class))).thenAnswer(inv -> {
            SurveyAttachment a = inv.getArgument(0);
            a.setId(100);
            a.setCreatedAt(LocalDateTime.of(2026, 11, 2, 9, 0));
            return a;
        });
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- upload ----------

    @Test
    @DisplayName("MOVER upload ảnh hợp lệ khi IN_PROGRESS → lưu file với key surveys/{booking}/{survey}/{uuid}.jpg, fileType IMAGE")
    void testUpload_MoverImage() {
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storage.generateAccessUrl(anyString(), any(Duration.class))).thenReturn("http://minio/signed");
        when(staffLookupService.findSummary(MOVER_ID)).thenReturn(Optional.of(StaffSummary.builder().fullName("Trần Mover").build()));
        givenSaveSucceeds();

        SurveyAttachmentResponse result = service.upload(SURVEY_ID, jpeg(), MOVER_ID, false);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), any(), eq(1024L), eq("image/jpeg"));
        assertThat(key.getValue()).matches("surveys/7/40/[0-9a-f-]{36}\\.jpg");
        ArgumentCaptor<SurveyAttachment> saved = ArgumentCaptor.forClass(SurveyAttachment.class);
        verify(surveyAttachmentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFileUrl()).isEqualTo(key.getValue());
        assertThat(saved.getValue().getFileType()).isEqualTo(AttachmentType.IMAGE);
        assertThat(result.getContentType()).isEqualTo("image/jpeg");
        assertThat(result.getAccessUrl()).isEqualTo("http://minio/signed");
        assertThat(result.getUploadedByName()).isEqualTo("Trần Mover");
    }

    @Test
    @DisplayName("Quản lý upload cho lịch đã COMPLETED → được (bổ sung ảnh khách gửi)")
    void testUpload_ManagementOnCompleted() {
        givenSurvey(SurveyStatus.COMPLETED, MOVER_ID);
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        givenSaveSucceeds();

        service.upload(SURVEY_ID, file("ban_ve.pdf", "application/pdf", SurveyAttachmentPolicyTest.PDF), DISPATCHER_ID, true);

        verify(storage).upload(anyString(), any(), anyLong(), eq("application/pdf"));
    }

    @Test
    @DisplayName("Quản lý upload cho lịch CANCELLED → ERR_SURVEY_INVALID_STATUS")
    void testUpload_ManagementOnCancelled() {
        givenSurvey(SurveyStatus.CANCELLED, MOVER_ID);

        assertError(() -> service.upload(SURVEY_ID, jpeg(), DISPATCHER_ID, true), ErrorCode.ERR_SURVEY_INVALID_STATUS);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("MOVER upload khi lịch CONFIRMED / COMPLETED → ERR_SURVEY_INVALID_STATUS; lịch người khác → ERR_SURVEY_FORBIDDEN")
    void testUpload_MoverRules() {
        givenSurvey(SurveyStatus.CONFIRMED, MOVER_ID);
        assertError(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false), ErrorCode.ERR_SURVEY_INVALID_STATUS);

        givenSurvey(SurveyStatus.COMPLETED, MOVER_ID);
        assertError(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false), ErrorCode.ERR_SURVEY_INVALID_STATUS);

        givenSurvey(SurveyStatus.IN_PROGRESS, OTHER_MOVER_ID);
        assertError(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false), ErrorCode.ERR_SURVEY_FORBIDDEN);

        verifyNoInteractions(storage, surveyAttachmentRepository);
    }

    @Test
    @DisplayName("File .exe đổi tên .jpg → ERR_FILE_INVALID_TYPE, không lên nơi lưu trữ")
    void testUpload_RenamedExecutable() {
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);

        assertError(() -> service.upload(SURVEY_ID, file("anh.jpg", "image/jpeg", SurveyAttachmentPolicyTest.EXE), MOVER_ID, false),
                ErrorCode.ERR_FILE_INVALID_TYPE);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("Đã có 50 tệp → ERR_SURVEY_ATTACHMENT_LIMIT ('tối đa 50 tệp')")
    void testUpload_TotalLimit() {
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(surveyAttachmentRepository.countBySurveyId(SURVEY_ID)).thenReturn(50L);

        assertThatThrownBy(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_ATTACHMENT_LIMIT)
                .hasMessageContaining("tối đa 50 tệp");
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("Đã có 5 video → thêm video bị chặn ('tối đa 5 video'), nhưng thêm ảnh vẫn được")
    void testUpload_VideoLimit() {
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(surveyAttachmentRepository.countBySurveyId(SURVEY_ID)).thenReturn(12L);
        when(surveyAttachmentRepository.countBySurveyIdAndFileType(SURVEY_ID, AttachmentType.VIDEO)).thenReturn(5L);

        assertThatThrownBy(() -> service.upload(SURVEY_ID, mp4(), MOVER_ID, false))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_ATTACHMENT_LIMIT)
                .hasMessageContaining("tối đa 5 video");

        when(storageProvider.getIfAvailable()).thenReturn(storage);
        givenSaveSucceeds();
        service.upload(SURVEY_ID, jpeg(), MOVER_ID, false);
        verify(storage, times(1)).upload(anyString(), any(), anyLong(), eq("image/jpeg"));
    }

    @Test
    @DisplayName("Giới hạn đọc từ cấu hình: max-attachments = 3 → đã có 3 tệp thì bị chặn")
    void testUpload_LimitFromConfig() {
        surveyProperties.setMaxAttachments(3);
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(surveyAttachmentRepository.countBySurveyId(SURVEY_ID)).thenReturn(3L);

        assertError(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false), ErrorCode.ERR_SURVEY_ATTACHMENT_LIMIT);
    }

    @Test
    @DisplayName("Chưa cấu hình nơi lưu trữ → ERR_FILE_STORAGE; nơi lưu trữ lỗi → ERR_FILE_STORAGE, không lưu DB")
    void testUpload_StorageMissingOrFailing() {
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(storageProvider.getIfAvailable()).thenReturn(null);
        assertError(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false), ErrorCode.ERR_FILE_STORAGE);

        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storage.upload(anyString(), any(), anyLong(), anyString())).thenThrow(new StorageException("down", null));
        assertError(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false), ErrorCode.ERR_FILE_STORAGE);

        verify(surveyAttachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("DB lỗi sau khi file đã lên nơi lưu trữ → transaction rollback thì file vừa upload bị xóa")
    void testUpload_DbFails_FileDeletedOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(userLookupService.getUserReference(MOVER_ID)).thenReturn(User.builder().id(MOVER_ID).build());
        when(surveyAttachmentRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> service.upload(SURVEY_ID, jpeg(), MOVER_ID, false)).hasMessage("DB down");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), any(), anyLong(), anyString());
        verify(storage, never()).delete(anyString());

        // Giả lập transaction kết thúc bằng rollback
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        callbacks.forEach(c -> c.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete(key.getValue());
    }

    // ---------- list ----------

    @Test
    @DisplayName("list: quản lý xem mọi lịch; tên người tải lên tra mỗi người một lần; chưa cấu hình lưu trữ → accessUrl null")
    void testList_ManagementWithoutStorage() {
        BookingSurvey survey = BookingSurvey.builder().id(SURVEY_ID).surveyStaff(Staff.builder().id(MOVER_ID).build()).build();
        when(bookingSurveyRepository.findById(SURVEY_ID)).thenReturn(Optional.of(survey));
        User mover = User.builder().id(MOVER_ID).build();
        when(surveyAttachmentRepository.findAllBySurveyIdOrderByCreatedAtAsc(SURVEY_ID)).thenReturn(List.of(
                SurveyAttachment.builder().id(1).fileUrl("surveys/7/40/a.jpg").fileType(AttachmentType.IMAGE).uploadedBy(mover).build(),
                SurveyAttachment.builder().id(2).fileUrl("surveys/7/40/b.mp4").fileType(AttachmentType.VIDEO).uploadedBy(mover).build()));
        when(staffLookupService.findSummary(MOVER_ID)).thenReturn(Optional.of(StaffSummary.builder().fullName("Trần Mover").build()));
        when(storageProvider.getIfAvailable()).thenReturn(null);

        List<SurveyAttachmentResponse> result = service.list(SURVEY_ID, DISPATCHER_ID, true);

        assertThat(result).extracting(SurveyAttachmentResponse::getContentType).containsExactly("image/jpeg", "video/mp4");
        assertThat(result).allSatisfy(r -> assertThat(r.getAccessUrl()).isNull());
        assertThat(result).allSatisfy(r -> assertThat(r.getUploadedByName()).isEqualTo("Trần Mover"));
        verify(staffLookupService, times(1)).findSummary(MOVER_ID);
    }

    @Test
    @DisplayName("list: MOVER xem lịch của người khác → ERR_SURVEY_FORBIDDEN")
    void testList_MoverNotOwner() {
        when(bookingSurveyRepository.findById(SURVEY_ID)).thenReturn(Optional.of(
                BookingSurvey.builder().id(SURVEY_ID).surveyStaff(Staff.builder().id(OTHER_MOVER_ID).build()).build()));

        assertError(() -> service.list(SURVEY_ID, MOVER_ID, false), ErrorCode.ERR_SURVEY_FORBIDDEN);
    }

    // ---------- delete ----------

    private SurveyAttachment givenAttachment(long uploaderId) {
        SurveyAttachment attachment = SurveyAttachment.builder().id(100).fileUrl("surveys/7/40/a.jpg")
                .fileType(AttachmentType.IMAGE).uploadedBy(User.builder().id(uploaderId).build()).build();
        when(surveyAttachmentRepository.findByIdAndSurveyId(100, SURVEY_ID)).thenReturn(Optional.of(attachment));
        return attachment;
    }

    @Test
    @DisplayName("delete: MOVER xóa tệp của mình khi IN_PROGRESS → xóa DB, file chỉ bị xóa sau khi commit")
    void testDelete_MoverOwnFile_FileDeletedAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        SurveyAttachment attachment = givenAttachment(MOVER_ID);
        when(storageProvider.getIfAvailable()).thenReturn(storage);

        service.delete(SURVEY_ID, 100, MOVER_ID, false);

        verify(surveyAttachmentRepository).delete(attachment);
        verify(storage, never()).delete(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete("surveys/7/40/a.jpg");
    }

    @Test
    @DisplayName("delete: xóa file sau commit bị lỗi → chỉ ghi log, không ném lỗi")
    void testDelete_StorageFailsAfterCommit_Swallowed() {
        TransactionSynchronizationManager.initSynchronization();
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        givenAttachment(MOVER_ID);
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        doThrow(new StorageException("down", null)).when(storage).delete(anyString());

        service.delete(SURVEY_ID, 100, MOVER_ID, false);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete("surveys/7/40/a.jpg");
    }

    @Test
    @DisplayName("delete: MOVER xóa tệp người khác tải lên → ERR_SURVEY_FORBIDDEN; quản lý xóa được")
    void testDelete_OtherUploader() {
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        SurveyAttachment attachment = givenAttachment(DISPATCHER_ID);
        assertError(() -> service.delete(SURVEY_ID, 100, MOVER_ID, false), ErrorCode.ERR_SURVEY_FORBIDDEN);

        service.delete(SURVEY_ID, 100, DISPATCHER_ID, true);
        verify(surveyAttachmentRepository).delete(attachment);
    }

    @Test
    @DisplayName("delete: lịch COMPLETED, hoặc đã từng hoàn tất rồi bị khảo sát lại → không ai xóa được")
    void testDelete_CompletedOrOnceCompleted() {
        givenSurvey(SurveyStatus.COMPLETED, MOVER_ID);
        assertError(() -> service.delete(SURVEY_ID, 100, DISPATCHER_ID, true), ErrorCode.ERR_SURVEY_INVALID_STATUS);

        BookingSurvey resurveyed = givenSurvey(SurveyStatus.RESURVEY_REQUIRED, MOVER_ID);
        resurveyed.setActualEndAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        assertError(() -> service.delete(SURVEY_ID, 100, DISPATCHER_ID, true), ErrorCode.ERR_SURVEY_INVALID_STATUS);

        verify(surveyAttachmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: tệp không thuộc lịch này → ERR_SURVEY_ATTACHMENT_NOT_FOUND")
    void testDelete_NotFound() {
        givenSurvey(SurveyStatus.IN_PROGRESS, MOVER_ID);
        when(surveyAttachmentRepository.findByIdAndSurveyId(999, SURVEY_ID)).thenReturn(Optional.empty());

        assertError(() -> service.delete(SURVEY_ID, 999, DISPATCHER_ID, true), ErrorCode.ERR_SURVEY_ATTACHMENT_NOT_FOUND);
    }
}
