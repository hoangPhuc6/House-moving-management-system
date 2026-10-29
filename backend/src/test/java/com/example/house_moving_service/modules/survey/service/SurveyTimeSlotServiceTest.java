package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.survey.dto.request.SurveyTimeSlotRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.repository.SurveyTimeSlotRepository;
import com.example.house_moving_service.modules.survey.service.impl.SurveyTimeSlotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyTimeSlotServiceTest {

    @Mock
    private SurveyTimeSlotRepository surveyTimeSlotRepository;
    @Mock
    private BookingSurveyRepository bookingSurveyRepository;

    private SurveyTimeSlotServiceImpl surveyTimeSlotService;

    @BeforeEach
    void setUp() {
        surveyTimeSlotService = new SurveyTimeSlotServiceImpl(surveyTimeSlotRepository, bookingSurveyRepository, new SurveyMapper());
    }

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    private SurveyTimeSlot slot(int id, String start, String end, boolean active) {
        return SurveyTimeSlot.builder().id(id).startTime(t(start)).endTime(t(end)).isActive(active).build();
    }

    private SurveyTimeSlotRequest request(String start, String end, Boolean active) {
        return SurveyTimeSlotRequest.builder().startTime(t(start)).endTime(t(end)).isActive(active).build();
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: Tạo hợp lệ, mặc định đang dùng, label dạng 'HH:mm - HH:mm'")
    void testCreate_Success() {
        when(surveyTimeSlotRepository.existsByStartTimeAndEndTime(t("18:00"), t("20:00"))).thenReturn(false);
        when(surveyTimeSlotRepository.existsOverlapping(t("18:00"), t("20:00"), null)).thenReturn(false);
        when(surveyTimeSlotRepository.save(any(SurveyTimeSlot.class))).thenAnswer(inv -> {
            SurveyTimeSlot s = inv.getArgument(0);
            s.setId(9);
            return s;
        });

        SurveyTimeSlotResponse response = surveyTimeSlotService.create(request("18:00", "20:00", null));

        assertThat(response.getId()).isEqualTo(9);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getLabel()).isEqualTo("18:00 - 20:00");
    }

    @Test
    @DisplayName("create: Giờ kết thúc không sau giờ bắt đầu → ERR_INVALID_INPUT")
    void testCreate_EndNotAfterStart() {
        assertThatThrownBy(() -> surveyTimeSlotService.create(request("10:00", "10:00", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_INVALID_INPUT);
        assertThatThrownBy(() -> surveyTimeSlotService.create(request("11:00", "09:00", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_INVALID_INPUT);
        verify(surveyTimeSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: Chồng lấn khung giờ đang dùng → ERR_SURVEY_TIME_SLOT_OVERLAP")
    void testCreate_Overlap() {
        when(surveyTimeSlotRepository.existsByStartTimeAndEndTime(t("09:00"), t("11:00"))).thenReturn(false);
        when(surveyTimeSlotRepository.existsOverlapping(t("09:00"), t("11:00"), null)).thenReturn(true);

        assertThatThrownBy(() -> surveyTimeSlotService.create(request("09:00", "11:00", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP);
        verify(surveyTimeSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: Trùng y hệt khung giờ đã tắt → ERR_SURVEY_TIME_SLOT_OVERLAP (không để DB báo 500)")
    void testCreate_DuplicateOfInactiveSlot() {
        when(surveyTimeSlotRepository.existsByStartTimeAndEndTime(t("08:00"), t("10:00"))).thenReturn(true);

        assertThatThrownBy(() -> surveyTimeSlotService.create(request("08:00", "10:00", false)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP);
    }

    @Test
    @DisplayName("create: Khung giờ tạo ở trạng thái tắt thì không cần kiểm tra chồng lấn")
    void testCreate_InactiveSkipsOverlapCheck() {
        when(surveyTimeSlotRepository.existsByStartTimeAndEndTime(t("09:00"), t("11:00"))).thenReturn(false);
        when(surveyTimeSlotRepository.save(any(SurveyTimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        SurveyTimeSlotResponse response = surveyTimeSlotService.create(request("09:00", "11:00", false));

        assertThat(response.getIsActive()).isFalse();
        verify(surveyTimeSlotRepository, never()).existsOverlapping(any(), any(), any());
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: Đổi giờ khi khung giờ có lịch khảo sát sắp tới → ERR_SURVEY_TIME_SLOT_IN_USE")
    void testUpdate_TimeChangedWhileInUse() {
        when(surveyTimeSlotRepository.findById(1)).thenReturn(Optional.of(slot(1, "08:00", "10:00", true)));
        when(bookingSurveyRepository.existsByTimeSlotIdAndStatusInAndSurveyDateGreaterThanEqual(
                eq(1), eq(SurveyStatus.OCCUPYING_SLOT), any(LocalDate.class))).thenReturn(true);

        assertThatThrownBy(() -> surveyTimeSlotService.update(1, request("08:00", "09:30", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_TIME_SLOT_IN_USE);
        verify(surveyTimeSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: Không đổi giờ (chỉ đổi trạng thái) thì không cần kiểm tra lịch đang dùng")
    void testUpdate_SameTimeDoesNotCheckUsage() {
        SurveyTimeSlot existing = slot(1, "08:00", "10:00", true);
        when(surveyTimeSlotRepository.findById(1)).thenReturn(Optional.of(existing));
        when(surveyTimeSlotRepository.existsByStartTimeAndEndTimeAndIdNot(t("08:00"), t("10:00"), 1)).thenReturn(false);
        when(surveyTimeSlotRepository.save(existing)).thenReturn(existing);

        SurveyTimeSlotResponse response = surveyTimeSlotService.update(1, request("08:00", "10:00", false));

        assertThat(response.getIsActive()).isFalse();
        verify(bookingSurveyRepository, never()).existsByTimeSlotIdAndStatusInAndSurveyDateGreaterThanEqual(any(), any(), any());
    }

    @Test
    @DisplayName("update: Đổi giờ hợp lệ, kiểm tra chồng lấn có loại trừ chính nó")
    void testUpdate_Success_ExcludesSelfFromOverlap() {
        SurveyTimeSlot existing = slot(1, "08:00", "10:00", true);
        when(surveyTimeSlotRepository.findById(1)).thenReturn(Optional.of(existing));
        when(bookingSurveyRepository.existsByTimeSlotIdAndStatusInAndSurveyDateGreaterThanEqual(
                eq(1), eq(SurveyStatus.OCCUPYING_SLOT), any(LocalDate.class))).thenReturn(false);
        when(surveyTimeSlotRepository.existsByStartTimeAndEndTimeAndIdNot(t("07:30"), t("09:30"), 1)).thenReturn(false);
        when(surveyTimeSlotRepository.existsOverlapping(t("07:30"), t("09:30"), 1)).thenReturn(false);
        when(surveyTimeSlotRepository.save(existing)).thenReturn(existing);

        SurveyTimeSlotResponse response = surveyTimeSlotService.update(1, request("07:30", "09:30", null));

        assertThat(response.getLabel()).isEqualTo("07:30 - 09:30");
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("update: Khung giờ không tồn tại → ERR_SURVEY_TIME_SLOT_NOT_FOUND")
    void testUpdate_NotFound() {
        when(surveyTimeSlotRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> surveyTimeSlotService.update(99, request("08:00", "10:00", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_TIME_SLOT_NOT_FOUND);
    }

    // ---------- toggle ----------

    @Test
    @DisplayName("toggle: Tắt khung giờ đang dùng, không cần kiểm tra chồng lấn")
    void testToggle_TurnOff() {
        SurveyTimeSlot existing = slot(1, "08:00", "10:00", true);
        when(surveyTimeSlotRepository.findById(1)).thenReturn(Optional.of(existing));
        when(surveyTimeSlotRepository.save(existing)).thenReturn(existing);

        SurveyTimeSlotResponse response = surveyTimeSlotService.toggle(1);

        assertThat(response.getIsActive()).isFalse();
        verify(surveyTimeSlotRepository, never()).existsOverlapping(any(), any(), any());
    }

    @Test
    @DisplayName("toggle: Bật lại khi đã có khung giờ khác chồng lấn → ERR_SURVEY_TIME_SLOT_OVERLAP")
    void testToggle_TurnOnWithOverlap() {
        when(surveyTimeSlotRepository.findById(1)).thenReturn(Optional.of(slot(1, "08:00", "10:00", false)));
        when(surveyTimeSlotRepository.existsOverlapping(t("08:00"), t("10:00"), 1)).thenReturn(true);

        assertThatThrownBy(() -> surveyTimeSlotService.toggle(1))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP);
        verify(surveyTimeSlotRepository, never()).save(any());
    }

    // ---------- getAll / getActiveSlot ----------

    @Test
    @DisplayName("getAll: activeOnly = true chỉ lấy khung giờ đang dùng, còn lại lấy tất cả")
    void testGetAll() {
        when(surveyTimeSlotRepository.findAllByIsActiveTrueOrderByStartTimeAsc()).thenReturn(List.of(slot(1, "08:00", "10:00", true)));
        when(surveyTimeSlotRepository.findAllByOrderByStartTimeAsc())
                .thenReturn(List.of(slot(1, "08:00", "10:00", true), slot(2, "10:00", "12:00", false)));

        assertThat(surveyTimeSlotService.getAll(true)).hasSize(1);
        assertThat(surveyTimeSlotService.getAll(null)).hasSize(2);
        assertThat(surveyTimeSlotService.getAll(false)).hasSize(2);
    }

    @Test
    @DisplayName("getActiveSlot: Không tồn tại → NOT_FOUND; đã tắt → INACTIVE; đang dùng → trả về")
    void testGetActiveSlot() {
        SurveyTimeSlot active = slot(1, "08:00", "10:00", true);
        when(surveyTimeSlotRepository.findById(1)).thenReturn(Optional.of(active));
        when(surveyTimeSlotRepository.findById(2)).thenReturn(Optional.of(slot(2, "10:00", "12:00", false)));
        when(surveyTimeSlotRepository.findById(99)).thenReturn(Optional.empty());

        assertThat(surveyTimeSlotService.getActiveSlot(1)).isSameAs(active);
        assertThatThrownBy(() -> surveyTimeSlotService.getActiveSlot(2))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_TIME_SLOT_INACTIVE);
        assertThatThrownBy(() -> surveyTimeSlotService.getActiveSlot(99))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_SURVEY_TIME_SLOT_NOT_FOUND);
    }
}
