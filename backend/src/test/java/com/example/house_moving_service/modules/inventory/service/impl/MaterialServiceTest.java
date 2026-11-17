package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.inventory.dto.request.CreateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialStatusRequest;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.mapper.MaterialMapper;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    private static final int MATERIAL_ID = 3;

    @Mock
    private MaterialRepository materialRepository;

    private MaterialServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MaterialServiceImpl(materialRepository, new MaterialMapper());
        lenient().when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Material existing() {
        return Material.builder().id(MATERIAL_ID).code("BOX-L").name("Thùng carton lớn")
                .category(MaterialCategory.CONSUMABLE).unit("cái").unitPrice(new BigDecimal("15000"))
                .quantityOnHand(40).minStock(10).isActive(true).build();
    }

    @Test
    @DisplayName("Tạo mới: chuẩn hóa mã, tồn = 0, đang hoạt động, unitPrice / minStock mặc định 0")
    void create_success() {
        when(materialRepository.existsByCodeIgnoreCase("TAPE-01")).thenReturn(false);

        MaterialResponse res = service.create(CreateMaterialRequest.builder()
                .code("  tape-01 ").name(" Băng keo ").category(MaterialCategory.CONSUMABLE).unit(" cuộn ")
                .description("   ").build());

        ArgumentCaptor<Material> captor = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(captor.capture());
        Material saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("TAPE-01");
        assertThat(saved.getName()).isEqualTo("Băng keo");
        assertThat(saved.getUnit()).isEqualTo("cuộn");
        assertThat(saved.getQuantityOnHand()).isZero();
        assertThat(saved.getMinStock()).isZero();
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("0");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getIsActive()).isTrue();
        assertThat(res.lowStock()).isFalse();
    }

    @Test
    @DisplayName("Tạo trùng code (không phân biệt hoa thường) → ERR_MATERIAL_CODE_DUPLICATE, không lưu")
    void create_duplicateCode() {
        when(materialRepository.existsByCodeIgnoreCase("BOX-L")).thenReturn(true);

        assertThatThrownBy(() -> service.create(CreateMaterialRequest.builder()
                .code("box-l").name("Thùng").category(MaterialCategory.CONSUMABLE).unit("cái").build()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ERR_MATERIAL_CODE_DUPLICATE);
        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa: đổi name / unit / unitPrice / minStock / description, giữ nguyên code, category, tồn")
    void update_keepsCodeCategoryAndStock() {
        Material material = existing();
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));

        MaterialResponse res = service.update(MATERIAL_ID, UpdateMaterialRequest.builder()
                .name("Thùng carton cỡ L").unit("thùng").unitPrice(new BigDecimal("18000")).minStock(50)
                .description("60x40x40").build());

        assertThat(res.code()).isEqualTo("BOX-L");
        assertThat(res.category()).isEqualTo(MaterialCategory.CONSUMABLE);
        assertThat(res.quantityOnHand()).isEqualTo(40);
        assertThat(res.name()).isEqualTo("Thùng carton cỡ L");
        assertThat(res.unit()).isEqualTo("thùng");
        assertThat(res.unitPrice()).isEqualByComparingTo("18000");
        assertThat(res.minStock()).isEqualTo(50);
        assertThat(res.description()).isEqualTo("60x40x40");
        // tồn 40 < tối thiểu 50
        assertThat(res.lowStock()).isTrue();
        assertThat(material.getCode()).isEqualTo("BOX-L");
        assertThat(material.getCategory()).isEqualTo(MaterialCategory.CONSUMABLE);
    }

    @Test
    @DisplayName("Sửa vật tư không tồn tại → ERR_MATERIAL_NOT_FOUND")
    void update_notFound() {
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(MATERIAL_ID, UpdateMaterialRequest.builder()
                .name("x").unit("cái").unitPrice(BigDecimal.ZERO).minStock(0).build()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ERR_MATERIAL_NOT_FOUND);
    }

    @Test
    @DisplayName("Xem chi tiết vật tư không tồn tại → ERR_MATERIAL_NOT_FOUND")
    void getById_notFound() {
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(MATERIAL_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ERR_MATERIAL_NOT_FOUND);
    }

    @Test
    @DisplayName("Tắt vật tư: isActive = false, tồn giữ nguyên; đặt lại cùng trạng thái thì không lưu")
    void updateStatus() {
        Material material = existing();
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));

        MaterialResponse res = service.updateStatus(MATERIAL_ID, new UpdateMaterialStatusRequest(false));
        assertThat(res.isActive()).isFalse();
        assertThat(res.quantityOnHand()).isEqualTo(40);
        verify(materialRepository).save(material);

        service.updateStatus(MATERIAL_ID, new UpdateMaterialStatusRequest(false));
        verify(materialRepository).save(material); // vẫn chỉ 1 lần
    }
}
