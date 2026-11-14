package com.example.house_moving_service.modules.scheduling.controller.admin;

import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.service.VehicleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminVehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private VehicleService vehicleService;

    @Test
    @DisplayName("POST /api/v1/admin/vehicles - ADMIN tạo xe → 201")
    void create_success() throws Exception {
        CreateVehicleRequest request = CreateVehicleRequest.builder()
                .licensePlate("51C-123.45").vehicleTypeId(1).build();
        when(vehicleService.create(any(CreateVehicleRequest.class))).thenReturn(VehicleResponse.builder()
                .id(1).licensePlate("51C-123.45").vehicleTypeId(1).status(VehicleStatus.AVAILABLE).build());

        mockMvc.perform(post("/api/v1/admin/vehicles")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.licensePlate").value("51C-123.45"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/vehicles - thiếu biển số và loại xe → 400")
    void create_validation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vehicles")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors.licensePlate").exists())
                .andExpect(jsonPath("$.errors.vehicleTypeId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/admin/vehicles - DISPATCHER không được thêm xe → 403")
    void create_forbiddenForDispatcher() throws Exception {
        CreateVehicleRequest request = CreateVehicleRequest.builder()
                .licensePlate("51C-123.45").vehicleTypeId(1).build();

        mockMvc.perform(post("/api/v1/admin/vehicles")
                        .with(user("dispatcher@example.com").roles("DISPATCHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
