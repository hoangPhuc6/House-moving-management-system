package com.example.house_moving_service.modules.catalog.service;

import com.example.house_moving_service.modules.catalog.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.catalog.repository.ServiceTypeRepository;
import com.example.house_moving_service.modules.catalog.service.impl.ServiceTypeServiceImpl;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTypeServiceTest {

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private ServiceRepository serviceRepository;

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

    @Test
    @DisplayName("create: Không gửi feeCategory thì mặc định TRANSPORT")
    void testCreate_defaultFeeCategory() {
        CreateServiceTypeRequest req = CreateServiceTypeRequest.builder().name("Nhóm mới").build();
        when(serviceTypeRepository.existsByName("Nhóm mới")).thenReturn(false);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse res = serviceTypeService.create(req);
        assertEquals(FeeCategory.TRANSPORT, res.getFeeCategory());
    }

    @Test
    @DisplayName("create: Gửi feeCategory thì lưu đúng nhóm phí")
    void testCreate_explicitFeeCategory() {
        CreateServiceTypeRequest req = CreateServiceTypeRequest.builder()
                .name("Vật tư").feeCategory(FeeCategory.MATERIAL).build();
        when(serviceTypeRepository.existsByName("Vật tư")).thenReturn(false);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse res = serviceTypeService.create(req);
        assertEquals(FeeCategory.MATERIAL, res.getFeeCategory());
    }

    @Test
    @DisplayName("update: Không gửi feeCategory thì giữ nhóm phí hiện tại")
    void testUpdate_keepFeeCategoryWhenNull() {
        ServiceType existing = ServiceType.builder()
                .id(2).name("Nhân công").isActive(true).feeCategory(FeeCategory.LABOR).build();
        UpdateServiceTypeRequest req = UpdateServiceTypeRequest.builder()
                .name("Nhân công").isActive(true).build();
        when(serviceTypeRepository.findById(2)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.existsByNameAndIdNot("Nhân công", 2)).thenReturn(false);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse res = serviceTypeService.update(2, req);
        assertEquals(FeeCategory.LABOR, res.getFeeCategory());
    }

    @Test
    @DisplayName("update: Đổi nhóm phí của loại không chứa dịch vụ hệ thống thì cho phép")
    void testUpdate_changeFeeCategory_allowed() {
        ServiceType existing = ServiceType.builder()
                .id(3).name("Kỹ thuật").isActive(true).feeCategory(FeeCategory.TRANSPORT).build();
        UpdateServiceTypeRequest req = UpdateServiceTypeRequest.builder()
                .name("Kỹ thuật").isActive(true).feeCategory(FeeCategory.TECHNICAL).build();
        when(serviceTypeRepository.findById(3)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.existsByNameAndIdNot("Kỹ thuật", 3)).thenReturn(false);
        when(serviceRepository.existsByServiceTypeIdAndCodeIsNotNull(3)).thenReturn(false);
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse res = serviceTypeService.update(3, req);
        assertEquals(FeeCategory.TECHNICAL, res.getFeeCategory());
    }

    @Test
    @DisplayName("update: Đổi nhóm phí của loại chứa dịch vụ hệ thống thì ném ERR_SERVICE_SYSTEM_PROTECTED")
    void testUpdate_changeFeeCategory_systemServiceProtected() {
        ServiceType existing = ServiceType.builder()
                .id(5).name("Địa hình").isActive(true).feeCategory(FeeCategory.TERRAIN).build();
        UpdateServiceTypeRequest req = UpdateServiceTypeRequest.builder()
                .name("Địa hình").isActive(true).feeCategory(FeeCategory.LABOR).build();
        when(serviceTypeRepository.findById(5)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.existsByNameAndIdNot("Địa hình", 5)).thenReturn(false);
        when(serviceRepository.existsByServiceTypeIdAndCodeIsNotNull(5)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.update(5, req));
        assertEquals(ErrorCode.ERR_SERVICE_SYSTEM_PROTECTED, ex.getErrorCode());
        assertEquals(FeeCategory.TERRAIN, existing.getFeeCategory());
        verify(serviceTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: Tắt loại chứa dịch vụ hệ thống thì ném ERR_SERVICE_SYSTEM_PROTECTED")
    void testUpdate_deactivate_systemServiceProtected() {
        ServiceType existing = ServiceType.builder()
                .id(5).name("Địa hình").isActive(true).feeCategory(FeeCategory.TERRAIN).build();
        UpdateServiceTypeRequest req = UpdateServiceTypeRequest.builder()
                .name("Địa hình").isActive(false).build();
        when(serviceTypeRepository.findById(5)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.existsByNameAndIdNot("Địa hình", 5)).thenReturn(false);
        when(serviceRepository.existsByServiceTypeIdAndCodeIsNotNull(5)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.update(5, req));
        assertEquals(ErrorCode.ERR_SERVICE_SYSTEM_PROTECTED, ex.getErrorCode());
        assertTrue(existing.getIsActive());
    }

    @Test
    @DisplayName("toggleActive: Tắt loại chứa dịch vụ hệ thống thì ném ERR_SERVICE_SYSTEM_PROTECTED")
    void testToggleActive_systemServiceProtected() {
        ServiceType existing = ServiceType.builder().id(5).name("Địa hình").isActive(true).build();
        when(serviceTypeRepository.findById(5)).thenReturn(Optional.of(existing));
        when(serviceRepository.existsByServiceTypeIdAndCodeIsNotNull(5)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceTypeService.toggleActive(5));
        assertEquals(ErrorCode.ERR_SERVICE_SYSTEM_PROTECTED, ex.getErrorCode());
        assertTrue(existing.getIsActive());
        verify(serviceTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggleActive: Bật lại loại đang tắt thì không cần kiểm tra dịch vụ hệ thống")
    void testToggleActive_reactivate_noSystemCheck() {
        ServiceType existing = ServiceType.builder().id(6).name("Nhóm tắt").isActive(false).build();
        when(serviceTypeRepository.findById(6)).thenReturn(Optional.of(existing));
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceTypeResponse res = serviceTypeService.toggleActive(6);
        assertTrue(res.getIsActive());
        verify(serviceRepository, never()).existsByServiceTypeIdAndCodeIsNotNull(any());
    }
}
