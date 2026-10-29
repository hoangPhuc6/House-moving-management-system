package com.example.house_moving_service.modules.survey.controller.admin;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.survey.dto.request.SurveyTimeSlotRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.service.SurveyTimeSlotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSurveyTimeSlotControllerTest {

    private static final String ADMIN_URL = "/api/v1/admin/survey-time-slots";
    private static final String MANAGEMENT_URL = "/api/v1/management/survey-time-slots";
    private static final String VALID_BODY = "{\"startTime\":\"18:00\",\"endTime\":\"20:00\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SurveyTimeSlotService surveyTimeSlotService;

    private SurveyTimeSlotResponse response(int id, String start, String end, boolean active) {
        return SurveyTimeSlotResponse.builder()
                .id(id).startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end))
                .isActive(active).label(start + " - " + end)
                .build();
    }

    // ---------- Admin ----------

    @Test
    @DisplayName("POST - ADMIN tạo khung giờ → 201, giờ trả về dạng HH:mm")
    void testCreate_Admin_Created() throws Exception {
        when(surveyTimeSlotService.create(any(SurveyTimeSlotRequest.class))).thenReturn(response(9, "18:00", "20:00", true));

        mockMvc.perform(post(ADMIN_URL)
                        .with(user("admin@housemoving.vn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo khung giờ khảo sát thành công"))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.startTime").value("18:00"))
                .andExpect(jsonPath("$.data.label").value("18:00 - 20:00"));
    }

    @Test
    @DisplayName("POST - Thiếu startTime → 400, báo lỗi đúng field")
    void testCreate_MissingStartTime_BadRequest() throws Exception {
        mockMvc.perform(post(ADMIN_URL)
                        .with(user("admin@housemoving.vn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endTime\":\"20:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.errors.startTime").exists());
        verify(surveyTimeSlotService, never()).create(any());
    }

    @Test
    @DisplayName("POST - Service báo chồng lấn → 409 ERR_SURVEY_TIME_SLOT_OVERLAP")
    void testCreate_Overlap_Conflict() throws Exception {
        when(surveyTimeSlotService.create(any(SurveyTimeSlotRequest.class)))
                .thenThrow(new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP));

        mockMvc.perform(post(ADMIN_URL)
                        .with(user("admin@housemoving.vn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP.getCode()));
    }

    @Test
    @DisplayName("POST - DISPATCHER / MOVER gọi API admin → 403")
    void testCreate_NonAdmin_Forbidden() throws Exception {
        for (String role : List.of("DISPATCHER", "MOVER")) {
            mockMvc.perform(post(ADMIN_URL)
                            .with(user(role.toLowerCase() + "@housemoving.vn").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isForbidden());
        }
        verify(surveyTimeSlotService, never()).create(any());
    }

    @Test
    @DisplayName("GET - ADMIN xem tất cả khung giờ, truyền activeOnly xuống service")
    void testGetAll_Admin() throws Exception {
        when(surveyTimeSlotService.getAll(null))
                .thenReturn(List.of(response(1, "08:00", "10:00", true), response(2, "10:00", "12:00", false)));

        mockMvc.perform(get(ADMIN_URL).with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].isActive").value(false));
    }

    @Test
    @DisplayName("PUT - ADMIN cập nhật → 200")
    void testUpdate_Admin() throws Exception {
        when(surveyTimeSlotService.update(eq(1), any(SurveyTimeSlotRequest.class))).thenReturn(response(1, "18:00", "20:00", true));

        mockMvc.perform(put(ADMIN_URL + "/1")
                        .with(user("admin@housemoving.vn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật khung giờ khảo sát thành công"));
    }

    @Test
    @DisplayName("PATCH toggle - ADMIN bật / tắt → 200")
    void testToggle_Admin() throws Exception {
        when(surveyTimeSlotService.toggle(1)).thenReturn(response(1, "08:00", "10:00", false));

        mockMvc.perform(patch(ADMIN_URL + "/1/toggle").with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    // ---------- Management ----------

    @Test
    @DisplayName("GET management - DISPATCHER và ADMIN xem khung giờ đang dùng → 200")
    void testManagementGetActive_Staff() throws Exception {
        when(surveyTimeSlotService.getAll(true)).thenReturn(List.of(response(1, "08:00", "10:00", true)));

        for (String role : List.of("DISPATCHER", "ADMIN")) {
            mockMvc.perform(get(MANAGEMENT_URL).with(user(role.toLowerCase() + "@housemoving.vn").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].label").value("08:00 - 10:00"));
        }
    }

    @Test
    @DisplayName("GET management - MOVER / CUSTOMER → 403")
    void testManagementGetActive_Forbidden() throws Exception {
        for (String role : List.of("MOVER", "CUSTOMER")) {
            mockMvc.perform(get(MANAGEMENT_URL).with(user(role.toLowerCase() + "@test.vn").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }
}
