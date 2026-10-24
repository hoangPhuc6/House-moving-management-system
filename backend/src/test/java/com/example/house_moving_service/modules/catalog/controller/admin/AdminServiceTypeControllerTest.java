package com.example.house_moving_service.modules.catalog.controller.admin;

import com.example.house_moving_service.modules.catalog.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.modules.catalog.service.ServiceTypeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class AdminServiceTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ServiceTypeService serviceTypeService;

    @Test
    @DisplayName("POST /api/v1/admin/service-types - Success with ADMIN role returns 201")
    void createServiceType_Success() throws Exception {
        CreateServiceTypeRequest request = CreateServiceTypeRequest.builder()
                .name("Xe tải vận chuyển")
                .description("Dịch vụ phương tiện chuyển nhà")
                .build();

        ServiceTypeResponse response = ServiceTypeResponse.builder()
                .id(1)
                .name("Xe tải vận chuyển")
                .description("Dịch vụ phương tiện chuyển nhà")
                .isActive(true)
                .build();

        when(serviceTypeService.create(any(CreateServiceTypeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/service-types")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Tạo loại dịch vụ thành công"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Xe tải vận chuyển"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/service-types - Validation failure (blank name) returns 400")
    void createServiceType_ValidationFailure() throws Exception {
        CreateServiceTypeRequest request = CreateServiceTypeRequest.builder()
                .name("")
                .description("Mô tả")
                .build();

        mockMvc.perform(post("/api/v1/admin/service-types")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("POST /api/v1/admin/service-types - Non-admin role (CUSTOMER) returns 403 Forbidden")
    void createServiceType_ForbiddenForCustomer() throws Exception {
        CreateServiceTypeRequest request = CreateServiceTypeRequest.builder()
                .name("Dịch vụ test")
                .build();

        mockMvc.perform(post("/api/v1/admin/service-types")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("POST /api/v1/admin/service-types - Unauthenticated returns 401 Unauthorized")
    void createServiceType_Unauthorized() throws Exception {
        CreateServiceTypeRequest request = CreateServiceTypeRequest.builder()
                .name("Dịch vụ test")
                .build();

        mockMvc.perform(post("/api/v1/admin/service-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("GET /api/v1/admin/service-types - Success with ADMIN role returns 200")
    void getAllServiceTypes_Success() throws Exception {
        List<ServiceTypeResponse> list = List.of(
                ServiceTypeResponse.builder().id(1).name("Loại 1").isActive(true).build(),
                ServiceTypeResponse.builder().id(2).name("Loại 2").isActive(false).build()
        );

        when(serviceTypeService.getAll()).thenReturn(list);

        mockMvc.perform(get("/api/v1/admin/service-types")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/admin/service-types/{id} - Success with ADMIN role returns 200")
    void getServiceTypeById_Success() throws Exception {
        ServiceTypeResponse response = ServiceTypeResponse.builder().id(1).name("Loại 1").isActive(true).build();
        when(serviceTypeService.getById(1)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/service-types/1")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/service-types/{id} - Success with ADMIN role returns 200")
    void updateServiceType_Success() throws Exception {
        UpdateServiceTypeRequest request = UpdateServiceTypeRequest.builder()
                .name("Loại cập nhật")
                .description("Mô tả mới")
                .isActive(true)
                .build();

        ServiceTypeResponse response = ServiceTypeResponse.builder()
                .id(1)
                .name("Loại cập nhật")
                .description("Mô tả mới")
                .isActive(true)
                .build();

        when(serviceTypeService.update(eq(1), any(UpdateServiceTypeRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/service-types/1")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.name").value("Loại cập nhật"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/service-types/{id}/toggle-active - Success returns 200")
    void toggleActiveServiceType_Success() throws Exception {
        ServiceTypeResponse response = ServiceTypeResponse.builder().id(1).name("Loại 1").isActive(false).build();
        when(serviceTypeService.toggleActive(1)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/service-types/1/toggle-active")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }
}
