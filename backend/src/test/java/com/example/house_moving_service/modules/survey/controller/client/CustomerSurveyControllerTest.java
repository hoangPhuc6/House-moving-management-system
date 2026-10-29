package com.example.house_moving_service.modules.survey.controller.client;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.entity.Role;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.survey.dto.response.CustomerSurveyResponse;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.service.CustomerSurveyService;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerSurveyControllerTest {

    private static final String URL = "/api/v1/bookings/7/surveys";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerSurveyService customerSurveyService;

    // @AuthenticationPrincipal cần đúng kiểu CustomUserDetails
    private static CustomUserDetails principal(long id, RoleCode roleCode) {
        Role role = Role.builder().id(1L).code(roleCode).name(roleCode.name()).build();
        User user = User.builder().id(id).email(roleCode.name().toLowerCase() + "@test.vn")
                .password("encoded_pass").isActive(true).roles(Set.of(role)).build();
        return new CustomUserDetails(user);
    }

    @Test
    @DisplayName("CUSTOMER xem lịch khảo sát đơn của mình → 200; id người gọi lấy từ token")
    void testCustomer_Ok() throws Exception {
        when(customerSurveyService.getByBooking(7, 21L)).thenReturn(List.of(
                CustomerSurveyResponse.builder().id(40).status(SurveyStatus.CONFIRMED).timeSlotLabel("08:00 - 10:00").build()));

        mockMvc.perform(get(URL).with(user(principal(21L, RoleCode.CUSTOMER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(40))
                .andExpect(jsonPath("$.data[0].timeSlotLabel").value("08:00 - 10:00"))
                .andExpect(jsonPath("$.data[0].note").doesNotExist());
    }

    @Test
    @DisplayName("Đơn của khách khác → 403 ERR_BOOKING_FORBIDDEN")
    void testCustomer_NotOwner() throws Exception {
        when(customerSurveyService.getByBooking(7, 22L)).thenThrow(new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN));

        mockMvc.perform(get(URL).with(user(principal(22L, RoleCode.CUSTOMER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_BOOKING_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("MOVER / DISPATCHER / ADMIN gọi API của khách → 403; chưa đăng nhập → 401")
    void testOtherRoles_Forbidden() throws Exception {
        for (RoleCode role : List.of(RoleCode.MOVER, RoleCode.DISPATCHER, RoleCode.ADMIN)) {
            mockMvc.perform(get(URL).with(user(principal(1L, role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(customerSurveyService);
    }
}
