package com.example.house_moving_service.modules.survey.controller.field;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.entity.Role;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.survey.dto.request.SurveyItemRequest;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.service.FieldSurveyService;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FieldSurveyControllerTest {

    private static final String URL = "/api/v1/field/surveys";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FieldSurveyService fieldSurveyService;

    // @AuthenticationPrincipal cần đúng kiểu CustomUserDetails (giống ManagementBookingControllerTest)
    private static CustomUserDetails principal(long id, RoleCode roleCode) {
        Role role = Role.builder().id(1L).code(roleCode).name(roleCode.name()).build();
        User user = User.builder().id(id).email(roleCode.name().toLowerCase() + "@housemoving.vn")
                .password("encoded_pass").isActive(true).roles(Set.of(role)).build();
        return new CustomUserDetails(user);
    }

    @Test
    @DisplayName("GET danh sách - MOVER → 200; id người gọi lấy từ token, query param bind đúng")
    void testGetMySurveys_Mover() throws Exception {
        when(fieldSurveyService.getMySurveys(8L, LocalDate.of(2026, 11, 2), null, SurveyStatus.CONFIRMED))
                .thenReturn(List.of(SurveySummaryResponse.builder().id(40).bookingCode("BK-0007").build()));

        mockMvc.perform(get(URL).param("from", "2026-11-02").param("status", "CONFIRMED")
                        .with(user(principal(8L, RoleCode.MOVER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bookingCode").value("BK-0007"));
        verify(fieldSurveyService).getMySurveys(8L, LocalDate.of(2026, 11, 2), null, SurveyStatus.CONFIRMED);
    }

    @Test
    @DisplayName("GET danh sách - ADMIN / DISPATCHER / CUSTOMER → 403 (vùng FIELD chỉ cho MOVER)")
    void testGetMySurveys_OtherRolesForbidden() throws Exception {
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.DISPATCHER, RoleCode.CUSTOMER)) {
            mockMvc.perform(get(URL).with(user(principal(1L, role))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(fieldSurveyService);
    }

    @Test
    @DisplayName("GET danh sách - chưa đăng nhập → 401")
    void testGetMySurveys_Unauthenticated() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET danh sách - status không hợp lệ → 400")
    void testGetMySurveys_InvalidStatus() throws Exception {
        mockMvc.perform(get(URL).param("status", "DONE").with(user(principal(8L, RoleCode.MOVER))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fieldSurveyService);
    }

    @Test
    @DisplayName("GET chi tiết - MOVER → 200")
    void testGetMySurvey_Mover() throws Exception {
        when(fieldSurveyService.getMySurvey(40, 8L))
                .thenReturn(FieldSurveyResponse.builder().id(40).status(SurveyStatus.CONFIRMED).build());

        mockMvc.perform(get(URL + "/40").with(user(principal(8L, RoleCode.MOVER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(40));
    }

    @Test
    @DisplayName("GET chi tiết - lịch của người khác → 403 ERR_SURVEY_FORBIDDEN")
    void testGetMySurvey_NotOwner() throws Exception {
        when(fieldSurveyService.getMySurvey(any(), any())).thenThrow(new AppException(ErrorCode.ERR_SURVEY_FORBIDDEN));

        mockMvc.perform(get(URL + "/41").with(user(principal(8L, RoleCode.MOVER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SURVEY_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("PATCH start - MOVER → 200; DISPATCHER → 403")
    void testStart() throws Exception {
        when(fieldSurveyService.start(40, 8L))
                .thenReturn(FieldSurveyResponse.builder().id(40).status(SurveyStatus.IN_PROGRESS).build());

        mockMvc.perform(patch(URL + "/40/start").with(user(principal(8L, RoleCode.MOVER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã bắt đầu khảo sát"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mockMvc.perform(patch(URL + "/40/start").with(user(principal(3L, RoleCode.DISPATCHER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH start - quá sớm → 400 ERR_SURVEY_INVALID_DATE")
    void testStart_TooEarly() throws Exception {
        when(fieldSurveyService.start(any(), any()))
                .thenThrow(new AppException(ErrorCode.ERR_SURVEY_INVALID_DATE, "quá sớm so với giờ hẹn"));

        mockMvc.perform(patch(URL + "/40/start").with(user(principal(8L, RoleCode.MOVER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SURVEY_INVALID_DATE.getCode()));
    }

    // ---------- Đồ đạc ----------

    private static final String ITEM_BODY = "{\"itemName\":\"Sofa\",\"quantity\":1,\"unitPrice\":5000000}";

    @Test
    @DisplayName("POST items - MOVER → 201 trả dòng mới; field giá gửi kèm bị bỏ qua")
    void testAddItem() throws Exception {
        when(fieldSurveyService.addItem(eq(40), any(SurveyItemRequest.class), eq(8L)))
                .thenReturn(QuotationItemDto.builder().id(5).itemName("Sofa").quantity(1).build());

        mockMvc.perform(post(URL + "/40/items").with(user(principal(8L, RoleCode.MOVER)))
                        .contentType(MediaType.APPLICATION_JSON).content(ITEM_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Đã thêm đồ đạc"))
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("POST items - thiếu tên / số lượng 0 → 400, báo đúng field")
    void testAddItem_Validation() throws Exception {
        mockMvc.perform(post(URL + "/40/items").with(user(principal(8L, RoleCode.MOVER)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemName\":\" \",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.itemName").exists())
                .andExpect(jsonPath("$.errors.quantity").exists());
        verify(fieldSurveyService, never()).addItem(any(), any(), any());
    }

    @Test
    @DisplayName("PUT items/{itemId} → 200; DELETE items/{itemId} → 200 kèm thông báo")
    void testUpdateAndDeleteItem() throws Exception {
        when(fieldSurveyService.updateItem(eq(40), eq(5), any(SurveyItemRequest.class), eq(8L)))
                .thenReturn(QuotationItemDto.builder().id(5).quantity(2).build());

        mockMvc.perform(put(URL + "/40/items/5").with(user(principal(8L, RoleCode.MOVER)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemName\":\"Sofa\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(2));
        mockMvc.perform(delete(URL + "/40/items/5").with(user(principal(8L, RoleCode.MOVER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa đồ đạc"));
        verify(fieldSurveyService).deleteItem(40, 5, 8L);
    }

    @Test
    @DisplayName("Đồ đạc - DISPATCHER gọi vùng FIELD → 403")
    void testItems_DispatcherForbidden() throws Exception {
        mockMvc.perform(post(URL + "/40/items").with(user(principal(3L, RoleCode.DISPATCHER)))
                        .contentType(MediaType.APPLICATION_JSON).content(ITEM_BODY))
                .andExpect(status().isForbidden());
    }
}
