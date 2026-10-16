package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.entity.ServiceType;
import com.example.house_moving_service.exception.AppException;
import com.example.house_moving_service.exception.ErrorCode;
import com.example.house_moving_service.repository.ServiceTypeRepository;
import com.example.house_moving_service.service.impl.ServiceTypeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTypeServiceTest {

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @InjectMocks
    private ServiceTypeServiceImpl serviceTypeService;

    @Test
    @DisplayName("create: Tạo loại dịch vụ thành công")
    void testCreate_success() {
        CreateServiceTypeRequest req = CreateServiceTypeRequest.builder()
                .name("Nhóm xe tải")
                .description("Vận chuyển xe tải")
                .isActive(true)
                .build();

        when(serviceTypeRepository.existsByName("Nhóm xe tải")).thenReturn(false);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(invocation -> {
            ServiceType st = invocation.getArgument(0);
            st.setId(1);
            return st;
        });

        ServiceTypeResponse response = serviceTypeService.create(req);
        assertNotNull(response);
        assertEquals(1, response.getId());
        assertEquals("Nhóm xe tải", response.getName());
        assertTrue(response.getIsActive());
    }

    @Test
    @DisplayName("create: Ném ERR_DUPLICATE_NAME khi tên loại dịch vụ đã tồn tại")
    void testCreate_duplicateName() {
        CreateServiceTypeRequest req = CreateServiceTypeRequest.builder()
                .name("Nhóm xe tải")
                .build();

        when(serviceTypeRepository.existsByName("Nhóm xe tải")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.create(req));
        assertEquals(ErrorCode.ERR_DUPLICATE_NAME, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Nhóm xe tải"));
    }

    @Test
    @DisplayName("update: Cập nhật thành công")
    void testUpdate_success() {
        ServiceType existing = ServiceType.builder()
                .id(1)
                .name("Tên cũ")
                .description("Mô tả cũ")
                .isActive(true)
                .build();

        UpdateServiceTypeRequest req = UpdateServiceTypeRequest.builder()
                .name("Tên mới")
                .description("Mô tả mới")
                .isActive(false)
                .build();

        when(serviceTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.existsByNameAndIdNot("Tên mới", 1)).thenReturn(false);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse res = serviceTypeService.update(1, req);
        assertEquals("Tên mới", res.getName());
        assertFalse(res.getIsActive());
    }

    @Test
    @DisplayName("update: Ném ERR_SERVICE_TYPE_NOT_FOUND khi ID không tồn tại")
    void testUpdate_notFound() {
        UpdateServiceTypeRequest req = UpdateServiceTypeRequest.builder()
                .name("Tên mới")
                .isActive(true)
                .build();

        when(serviceTypeRepository.findById(999)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.update(999, req));
        assertEquals(ErrorCode.ERR_SERVICE_TYPE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("toggleActive: Đảo ngược trạng thái isActive thành công")
    void testToggleActive_success() {
        ServiceType existing = ServiceType.builder()
                .id(1)
                .name("Nhóm 1")
                .isActive(true)
                .build();

        when(serviceTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse res = serviceTypeService.toggleActive(1);
        assertFalse(res.getIsActive());
        verify(serviceTypeRepository).save(existing);
    }
}

