package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleTypeResponse;
import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import com.example.house_moving_service.modules.scheduling.mapper.VehicleMapper;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleTypeServiceImplTest {

    @Mock
    private VehicleTypeRepository vehicleTypeRepository;
    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleTypeServiceImpl vehicleTypeService;

    @BeforeEach
    void setUp() {
        vehicleTypeService = new VehicleTypeServiceImpl(vehicleTypeRepository, vehicleRepository, new VehicleMapper());
    }

    private VehicleType type(boolean active) {
        return VehicleType.builder().id(1).name("Xe tải 2 tấn")
                .maxLoadKg(new BigDecimal("2000")).maxVolumeM3(new BigDecimal("12")).isActive(active).build();
    }

    @Test
    @DisplayName("create: tạo loại xe thành công, mặc định đang hoạt động")
    void create_success() {
        when(vehicleTypeRepository.existsByNameIgnoreCase("Xe tải 2 tấn")).thenReturn(false);
        when(vehicleTypeRepository.save(any(VehicleType.class))).thenAnswer(inv -> {
            VehicleType t = inv.getArgument(0);
            t.setId(1);
            return t;
        });

        VehicleTypeResponse res = vehicleTypeService.create(CreateVehicleTypeRequest.builder()
                .name("  Xe tải 2 tấn ").maxLoadKg(new BigDecimal("2000")).build());

        assertEquals(1, res.id());
        assertEquals("Xe tải 2 tấn", res.name());
        assertTrue(res.isActive());
        assertEquals(0, res.vehicleCount());
    }

    @Test
    @DisplayName("create: trùng tên → ERR_DUPLICATE_NAME")
    void create_duplicate() {
        when(vehicleTypeRepository.existsByNameIgnoreCase("Xe tải 2 tấn")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> vehicleTypeService.create(
                CreateVehicleTypeRequest.builder().name("Xe tải 2 tấn").build()));

        assertEquals(ErrorCode.ERR_DUPLICATE_NAME, ex.getErrorCode());
        verify(vehicleTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAll: gắn số xe của từng loại")
    void getAll_withCounts() {
        VehicleType other = VehicleType.builder().id(2).name("Xe tải 5 tấn").isActive(true).build();
        when(vehicleTypeRepository.findAll(any(Sort.class))).thenReturn(List.of(type(true), other));
        VehicleRepository.VehicleCountByType count = new VehicleRepository.VehicleCountByType() {
            public Integer getVehicleTypeId() { return 1; }
            public Long getTotal() { return 3L; }
        };
        when(vehicleRepository.countGroupByType()).thenReturn(List.of(count));

        List<VehicleTypeResponse> res = vehicleTypeService.getAll(null);

        assertEquals(3, res.get(0).vehicleCount());
        assertEquals(0, res.get(1).vehicleCount());
    }

    @Test
    @DisplayName("update: không truyền isActive thì giữ nguyên trạng thái")
    void update_keepActive() {
        VehicleType t = type(false);
        when(vehicleTypeRepository.findById(1)).thenReturn(Optional.of(t));
        when(vehicleTypeRepository.existsByNameIgnoreCaseAndIdNot("Xe tải 2.5 tấn", 1)).thenReturn(false);
        when(vehicleTypeRepository.save(t)).thenReturn(t);

        VehicleTypeResponse res = vehicleTypeService.update(1, UpdateVehicleTypeRequest.builder()
                .name("Xe tải 2.5 tấn").maxLoadKg(new BigDecimal("2500")).build());

        assertEquals("Xe tải 2.5 tấn", res.name());
        assertFalse(res.isActive());
    }

    @Test
    @DisplayName("toggleActive: đảo trạng thái hoạt động")
    void toggleActive() {
        VehicleType t = type(true);
        when(vehicleTypeRepository.findById(1)).thenReturn(Optional.of(t));
        when(vehicleTypeRepository.save(t)).thenReturn(t);

        assertFalse(vehicleTypeService.toggleActive(1).isActive());
    }

    @Test
    @DisplayName("delete: còn xe thuộc loại → ERR_VEHICLE_TYPE_IN_USE")
    void delete_inUse() {
        when(vehicleTypeRepository.findById(1)).thenReturn(Optional.of(type(true)));
        when(vehicleRepository.countByVehicleTypeId(1)).thenReturn(2L);

        AppException ex = assertThrows(AppException.class, () -> vehicleTypeService.delete(1));

        assertEquals(ErrorCode.ERR_VEHICLE_TYPE_IN_USE, ex.getErrorCode());
        verify(vehicleTypeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: chưa có xe → xóa hẳn")
    void delete_success() {
        VehicleType t = type(true);
        when(vehicleTypeRepository.findById(1)).thenReturn(Optional.of(t));
        when(vehicleRepository.countByVehicleTypeId(1)).thenReturn(0L);

        vehicleTypeService.delete(1);

        verify(vehicleTypeRepository).delete(t);
    }

    @Test
    @DisplayName("getById: không tồn tại → ERR_VEHICLE_TYPE_NOT_FOUND")
    void getById_notFound() {
        when(vehicleTypeRepository.findById(9)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> vehicleTypeService.getById(9));

        assertEquals(ErrorCode.ERR_VEHICLE_TYPE_NOT_FOUND, ex.getErrorCode());
    }
}
