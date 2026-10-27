package com.example.house_moving_service.modules.survey.controller.management;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.entity.Role;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.service.SurveyManagementService;
import com.example.house_moving_service.modules.survey.service.SurveyQueryService;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ManagementSurveyControllerTest {

    private static final String SURVEYS_URL = "/api/v1/management/surveys";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SurveyQueryService surveyQueryService;
    @MockitoBean
    private SurveyManagementService surveyManagementService;

    // @AuthenticationPrincipal cần đúng kiểu CustomUserDetails (giống ManagementBookingControllerTest)
    private static CustomUserDetails principal(long id, RoleCode roleCode) {
        Role role = Role.builder().id(1L).code(roleCode).name(roleCode.name()).build();
        User user = User.builder().id(id).email(roleCode.name().toLowerCase() + "@housemoving.vn")
                .password("encoded_pass").isActive(true).roles(Set.of(role)).build();
        return new CustomUserDetails(user);
    }

    private static SurveySummaryResponse summary(int id) {
        return SurveySummaryResponse.builder().id(id).bookingId(7).bookingCode("BK-0007").status(SurveyStatus.CONFIRMED).build();
    }

    private static PageResponse<SurveySummaryResponse> page(SurveySummaryResponse... items) {
        return PageResponse.<SurveySummaryResponse>builder()
                .pageNo(1).pageSize(10).totalPages(1).totalElements(items.length).last(true).items(List.of(items))
                .build();
    }

    @Test
    @DisplayName("GET danh sách - ADMIN và DISPATCHER → 200; query param được bind vào bộ lọc, mặc định trang 0 cỡ 10, không sort")
    void testSearch_AdminAndDispatcher() throws Exception {
        when(surveyQueryService.search(any(), any())).thenReturn(page(summary(1)));

        for (String role : List.of("ADMIN", "DISPATCHER")) {
            mockMvc.perform(get(SURVEYS_URL)
                            .param("dateFrom", "2026-11-01")
                            .param("status", "CONFIRMED")
                            .param("bookingCode", "BK")
                            .with(user(role.toLowerCase() + "@housemoving.vn").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].id").value(1))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        ArgumentCaptor<SurveyFilterRequest> filter = ArgumentCaptor.forClass(SurveyFilterRequest.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(surveyQueryService, times(2)).search(filter.capture(), pageable.capture());
        assertThat(filter.getValue().getDateFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(filter.getValue().getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(filter.getValue().getBookingCode()).isEqualTo("BK");
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("GET danh sách - MOVER / CUSTOMER → 403")
    void testSearch_Forbidden() throws Exception {
        for (String role : List.of("MOVER", "CUSTOMER")) {
            mockMvc.perform(get(SURVEYS_URL).with(user(role.toLowerCase() + "@test.vn").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(surveyQueryService);
    }

    @Test
    @DisplayName("GET danh sách - chưa đăng nhập → 401")
    void testSearch_Unauthenticated() throws Exception {
        mockMvc.perform(get(SURVEYS_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET chi tiết - DISPATCHER → 200")
    void testGetDetail_Dispatcher() throws Exception {
        when(surveyQueryService.getDetail(5)).thenReturn(SurveyDetailResponse.builder()
                .id(5).bookingCode("BK-0007").status(SurveyStatus.PENDING).note("[2026-10-27 09:00][CREATE][user#3] Gọi trước").build());

        mockMvc.perform(get(SURVEYS_URL + "/5").with(user("dispatcher@housemoving.vn").roles("DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET chi tiết - không tồn tại → 404 ERR_SURVEY_NOT_FOUND")
    void testGetDetail_NotFound() throws Exception {
        when(surveyQueryService.getDetail(99)).thenThrow(new AppException(ErrorCode.ERR_SURVEY_NOT_FOUND, 99));

        mockMvc.perform(get(SURVEYS_URL + "/99").with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SURVEY_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("GET chi tiết - id không phải số → 400 ERR_INVALID_INPUT, không gọi service")
    void testGetDetail_NonNumericId() throws Exception {
        mockMvc.perform(get(SURVEYS_URL + "/abc").with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()));
        verifyNoInteractions(surveyQueryService);
    }

    @Test
    @DisplayName("GET theo booking - ADMIN → 200, mới nhất trước")
    void testGetByBooking() throws Exception {
        when(surveyQueryService.getByBooking(7)).thenReturn(List.of(summary(2), summary(1)));

        mockMvc.perform(get("/api/v1/management/bookings/7/surveys").with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(2));
    }

    @Test
    @DisplayName("GET theo booking - MOVER → 403")
    void testGetByBooking_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/management/bookings/7/surveys").with(user("mover@test.vn").roles("MOVER")))
                .andExpect(status().isForbidden());
    }

    // ---------- Tạo lịch ----------

    private static final String CREATE_BODY = "{\"bookingId\":7,\"surveyMethod\":\"ONSITE\","
            + "\"surveyDate\":\"2026-11-03\",\"timeSlotId\":2,\"staffId\":8}";

    @Test
    @DisplayName("POST - ADMIN và DISPATCHER tạo lịch → 201; id người tạo lấy từ token, body bind đúng")
    void testCreate_AdminAndDispatcher() throws Exception {
        when(surveyManagementService.create(any(CreateSurveyRequest.class), any()))
                .thenReturn(SurveyDetailResponse.builder().id(40).status(SurveyStatus.CONFIRMED).build());

        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.DISPATCHER)) {
            mockMvc.perform(post(SURVEYS_URL)
                            .with(user(principal(role == RoleCode.ADMIN ? 1L : 3L, role)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Tạo lịch khảo sát thành công"))
                    .andExpect(jsonPath("$.data.id").value(40))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }

        ArgumentCaptor<CreateSurveyRequest> request = ArgumentCaptor.forClass(CreateSurveyRequest.class);
        verify(surveyManagementService).create(request.capture(), eq(3L));
        assertThat(request.getValue().getBookingId()).isEqualTo(7);
        assertThat(request.getValue().getSurveyDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(request.getValue().getStaffId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("POST - MOVER → 403, không gọi service")
    void testCreate_Mover_Forbidden() throws Exception {
        mockMvc.perform(post(SURVEYS_URL)
                        .with(user(principal(8L, RoleCode.MOVER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verify(surveyManagementService, never()).create(any(), any());
    }

    @Test
    @DisplayName("POST - thiếu bookingId và surveyMethod → 400, báo lỗi đúng field")
    void testCreate_MissingRequiredFields() throws Exception {
        mockMvc.perform(post(SURVEYS_URL)
                        .with(user(principal(1L, RoleCode.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"surveyDate\":\"2026-11-03\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.errors.bookingId").exists())
                .andExpect(jsonPath("$.errors.surveyMethod").exists());
        verify(surveyManagementService, never()).create(any(), any());
    }

    @Test
    @DisplayName("POST - trùng lịch nhân viên → 409 ERR_SURVEY_STAFF_CONFLICT")
    void testCreate_StaffConflict() throws Exception {
        when(surveyManagementService.create(any(CreateSurveyRequest.class), any()))
                .thenThrow(new AppException(ErrorCode.ERR_SURVEY_STAFF_CONFLICT));

        mockMvc.perform(post(SURVEYS_URL)
                        .with(user(principal(1L, RoleCode.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SURVEY_STAFF_CONFLICT.getCode()));
    }

    // ---------- MOVER và lịch rảnh / bận ----------

    @Test
    @DisplayName("GET /surveys/movers - trả danh sách MOVER, không bị hiểu là /surveys/{id}")
    void testListMovers() throws Exception {
        when(surveyQueryService.listAvailableMovers())
                .thenReturn(List.of(StaffSummary.builder().userId(8L).fullName("Trần Mover").build()));

        mockMvc.perform(get(SURVEYS_URL + "/movers").with(user("dispatcher@housemoving.vn").roles("DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(8))
                .andExpect(jsonPath("$.data[0].fullName").value("Trần Mover"));
        verify(surveyQueryService, never()).getDetail(any());
    }

    @Test
    @DisplayName("GET availability - có date → truyền đúng ngày; không có date → truyền null (service tự lấy hôm nay)")
    void testAvailability() throws Exception {
        when(surveyQueryService.getStaffAvailability(eq(8L), any()))
                .thenReturn(StaffAvailabilityResponse.builder().staffId(8L).date(LocalDate.of(2026, 11, 3)).slots(List.of()).build());

        mockMvc.perform(get(SURVEYS_URL + "/movers/8/availability").param("date", "2026-11-03")
                        .with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffId").value(8));
        mockMvc.perform(get(SURVEYS_URL + "/movers/8/availability")
                        .with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(surveyQueryService).getStaffAvailability(8L, LocalDate.of(2026, 11, 3));
        verify(surveyQueryService).getStaffAvailability(8L, null);
    }

    @Test
    @DisplayName("GET availability - date sai định dạng → 400")
    void testAvailability_InvalidDate() throws Exception {
        mockMvc.perform(get(SURVEYS_URL + "/movers/8/availability").param("date", "03/11/2026")
                        .with(user("admin@housemoving.vn").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verify(surveyQueryService, never()).getStaffAvailability(any(), any());
    }

    @Test
    @DisplayName("GET movers / availability - MOVER → 403")
    void testMoverEndpoints_Forbidden() throws Exception {
        mockMvc.perform(get(SURVEYS_URL + "/movers").with(user("mover@test.vn").roles("MOVER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(SURVEYS_URL + "/movers/8/availability").with(user("mover@test.vn").roles("MOVER")))
                .andExpect(status().isForbidden());
    }
}
