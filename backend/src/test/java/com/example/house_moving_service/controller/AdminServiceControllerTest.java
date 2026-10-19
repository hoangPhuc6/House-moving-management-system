package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.CreateServiceRequest;
import com.example.house_moving_service.dto.request.UpdateServiceRequest;
import com.example.house_moving_service.dto.request.UpdateServiceStatusRequest;
import com.example.house_moving_service.dto.response.ServiceResponse;
import com.example.house_moving_service.enums.ServiceStatus;
import com.example.house_moving_service.service.ServiceCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
class AdminServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ServiceCatalogService serviceCatalogService;

    @Test
    @DisplayName("POST /api/v1/admin/services - Success with ADMIN role returns 201")
    void createService_Success() throws Exception {
        CreateServiceRequest request = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Xe tải 1.5 tấn")
                .description("Phù hợp chuyển nhà trọ")
                .basePrice(new BigDecimal("500000.00"))
                .build();

        ServiceResponse response = ServiceResponse.builder()
                .id(1)
                .serviceTypeId(1)
                .serviceTypeName("Xe tải")
                .name("Xe tải 1.5 tấn")
                .description("Phù hợp chuyển nhà trọ")
                .basePrice(new BigDecimal("500000.00"))
                .status(ServiceStatus.ACTIVE)
                .build();

        when(serviceCatalogService.create(any(CreateServiceRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/services")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Tạo dịch vụ thành công"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.basePrice").value(500000.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/services - Validation failure (price <= 0) returns 400")
    void createService_InvalidPrice_ReturnsBadRequest() throws Exception {
        CreateServiceRequest request = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ lỗi giá")
                .basePrice(BigDecimal.ZERO)
                .build();

        mockMvc.perform(post("/api/v1/admin/services")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors.basePrice").exists());
    }

    @Test
    @DisplayName("POST /api/v1/admin/services - Non-admin role (CUSTOMER) returns 403 Forbidden")
    void createService_ForbiddenForCustomer() throws Exception {
        CreateServiceRequest request = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ test")
                .basePrice(new BigDecimal("100000.00"))
                .build();

        mockMvc.perform(post("/api/v1/admin/services")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("GET /api/v1/admin/services - Filter by typeId and status returns 200")
    void getAllServices_WithFilters_Returns200() throws Exception {
        List<ServiceResponse> list = List.of(
                ServiceResponse.builder()
                        .id(1)
                        .serviceTypeId(1)
                        .name("Xe 1")
                        .basePrice(new BigDecimal("300000.00"))
                        .status(ServiceStatus.ACTIVE)
                        .build()
        );

        when(serviceCatalogService.getAll(1, ServiceStatus.ACTIVE)).thenReturn(list);

        mockMvc.perform(get("/api/v1/admin/services")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("typeId", "1")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/services/{id} - Success returns 200")
    void getServiceById_Success() throws Exception {
        ServiceResponse response = ServiceResponse.builder()
                .id(1)
                .serviceTypeId(1)
                .name("Xe 1")
                .basePrice(new BigDecimal("300000.00"))
                .status(ServiceStatus.ACTIVE)
                .build();

        when(serviceCatalogService.getById(1)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/services/1")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/services/{id} - Success returns 200")
    void updateService_Success() throws Exception {
        UpdateServiceRequest request = UpdateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Xe cập nhật")
                .basePrice(new BigDecimal("600000.00"))
                .build();

        ServiceResponse response = ServiceResponse.builder()
                .id(1)
                .serviceTypeId(1)
                .name("Xe cập nhật")
                .basePrice(new BigDecimal("600000.00"))
                .status(ServiceStatus.ACTIVE)
                .build();

        when(serviceCatalogService.update(eq(1), any(UpdateServiceRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/services/1")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.name").value("Xe cập nhật"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/services/{id}/status - Success returns 200")
    void updateServiceStatus_Success() throws Exception {
        UpdateServiceStatusRequest request = UpdateServiceStatusRequest.builder()
                .status("INACTIVE")
                .build();

        ServiceResponse response = ServiceResponse.builder()
                .id(1)
                .serviceTypeId(1)
                .name("Xe 1")
                .status(ServiceStatus.INACTIVE)
                .build();

        when(serviceCatalogService.updateStatus(eq(1), any(UpdateServiceStatusRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/services/1/status")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/services/{id} - Soft-delete returns 200 OK")
    void softDeleteService_Success() throws Exception {
        doNothing().when(serviceCatalogService).softDelete(1);

        mockMvc.perform(delete("/api/v1/admin/services/1")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Xóa dịch vụ thành công (đã chuyển sang INACTIVE)"));
    }
}

