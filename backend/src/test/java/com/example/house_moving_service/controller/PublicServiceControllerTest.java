package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.response.ServiceResponse;
import com.example.house_moving_service.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.enums.ServiceStatus;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.service.ServiceCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceCatalogService serviceCatalogService;

    @Test
    @DisplayName("GET /api/v1/services - Unauthenticated returns 401 Unauthorized")
    void getActiveCatalog_UnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("GET /api/v1/services - Authenticated customer returns active services list (200 OK)")
    void getActiveCatalog_FlatList_Success() throws Exception {
        List<ServiceResponse> list = List.of(
                ServiceResponse.builder()
                        .id(1)
                        .name("Cước xe tải 1.5 tấn")
                        .basePrice(new BigDecimal("500000.00"))
                        .status(ServiceStatus.ACTIVE)
                        .build()
        );

        when(serviceCatalogService.getActiveCatalog()).thenReturn(list);

        mockMvc.perform(get("/api/v1/services")
                        .with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Cước xe tải 1.5 tấn"));
    }

    @Test
    @DisplayName("GET /api/v1/services?grouped=true - Authenticated returns grouped services (200 OK)")
    void getActiveCatalog_Grouped_Success() throws Exception {
        List<ServiceTypeResponse> list = List.of(
                ServiceTypeResponse.builder()
                        .id(1)
                        .name("Cước xe")
                        .isActive(true)
                        .services(List.of(
                                ServiceResponse.builder()
                                        .id(1)
                                        .name("Xe 1.5 tấn")
                                        .basePrice(new BigDecimal("500000.00"))
                                        .status(ServiceStatus.ACTIVE)
                                        .build()
                        ))
                        .build()
        );

        when(serviceCatalogService.getActiveCatalogGroupedByType()).thenReturn(list);

        mockMvc.perform(get("/api/v1/services")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .param("grouped", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].services.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/services/{id} - Authenticated returns single active service (200 OK)")
    void getActiveServiceById_Success() throws Exception {
        ServiceResponse response = ServiceResponse.builder()
                .id(1)
                .name("Xe tải 1.5 tấn")
                .basePrice(new BigDecimal("500000.00"))
                .status(ServiceStatus.ACTIVE)
                .build();

        when(serviceCatalogService.getActiveServiceById(1)).thenReturn(response);

        mockMvc.perform(get("/api/v1/services/1")
                        .with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Xe tải 1.5 tấn"));
    }

    @Test
    @DisplayName("GET /api/v1/services/{id} - Inactive service throws ERR_SERVICE_INACTIVE (400 Bad Request)")
    void getActiveServiceById_InactiveService_ThrowsBadRequest() throws Exception {
        when(serviceCatalogService.getActiveServiceById(99))
                .thenThrow(new AppException(ErrorCode.ERR_SERVICE_INACTIVE));

        mockMvc.perform(get("/api/v1/services/99")
                        .with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004))
                .andExpect(jsonPath("$.message").value("Dịch vụ đã ngừng hoạt động"));
    }
}

