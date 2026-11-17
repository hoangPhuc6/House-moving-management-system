package com.example.house_moving_service.modules.inventory.controller.admin;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.inventory.dto.request.CreateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.service.MaterialService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMaterialControllerTest {

    private static final String URL = "/api/v1/admin/materials";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private MaterialService materialService;

    private static CreateMaterialRequest validRequest() {
        return CreateMaterialRequest.builder().code("BOX-L").name("Thùng carton lớn")
                .category(MaterialCategory.CONSUMABLE).unit("cái").unitPrice(new BigDecimal("15000")).minStock(10).build();
    }

    @Test
    @DisplayName("POST - ADMIN tạo vật tư → 201, tồn 0")
    void create_success() throws Exception {
        when(materialService.create(any(CreateMaterialRequest.class))).thenReturn(MaterialResponse.builder()
                .id(1).code("BOX-L").category(MaterialCategory.CONSUMABLE).quantityOnHand(0).minStock(10)
                .lowStock(true).isActive(true).build());

        mockMvc.perform(post(URL)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.code").value("BOX-L"))
                .andExpect(jsonPath("$.data.quantityOnHand").value(0))
                .andExpect(jsonPath("$.data.lowStock").value(true));
    }

    @Test
    @DisplayName("POST - thiếu code / name / category / unit → 400")
    void create_validation() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - trùng code → 409 mã 9002")
    void create_duplicate() throws Exception {
        when(materialService.create(any(CreateMaterialRequest.class)))
                .thenThrow(new AppException(ErrorCode.ERR_MATERIAL_CODE_DUPLICATE, "BOX-L"));

        mockMvc.perform(post(URL)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(9002));
    }

    @Test
    @DisplayName("POST - DISPATCHER không được quản lý danh mục vật tư → 403")
    void create_forbiddenForDispatcher() throws Exception {
        mockMvc.perform(post(URL)
                        .with(user("dispatcher@example.com").roles("DISPATCHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }
}
