package com.example.house_moving_service.modules.catalog.service;

import com.example.house_moving_service.modules.catalog.dto.request.CreateServiceRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceStatusRequest;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceResponse;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceRequest;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.enums.SystemServiceCode;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.catalog.repository.ServiceTypeRepository;
import com.example.house_moving_service.modules.catalog.service.impl.ServiceCatalogServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @InjectMocks
    private ServiceCatalogServiceImpl serviceCatalogService;

    @Test
    @DisplayName("create: Tạo dịch vụ thành công khi nhóm cha active")
    void testCreate_success() {
        ServiceType parent = ServiceType.builder()
                .id(1)
                .name("Nhóm 1")
                .isActive(true)
                .build();

        CreateServiceRequest req = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ A")
                .description("Mô tả A")
                .basePrice(new BigDecimal("100000.00"))
                .build();

        when(serviceTypeRepository.findById(1)).thenReturn(Optional.of(parent));
        when(serviceRepository.existsByNameAndServiceTypeId("Dịch vụ A", 1)).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenAnswer(inv -> {
            Service s = inv.getArgument(0);
            s.setId(10);
            return s;
        });

        ServiceResponse res = serviceCatalogService.create(req);
        assertNotNull(res);
        assertEquals(10, res.getId());
        assertEquals(ServiceStatus.ACTIVE, res.getStatus());
        assertEquals("Dịch vụ A", res.getName());
    }

    @Test
    @DisplayName("create: Ném ERR_SERVICE_TYPE_INACTIVE khi nhóm dịch vụ cha bị vô hiệu hóa")
    void testCreate_parentInactive_throwsException() {
        ServiceType parent = ServiceType.builder()
                .id(1)
                .name("Nhóm 1")
                .isActive(false) // Bị tắt
                .build();

        CreateServiceRequest req = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ A")
                .basePrice(new BigDecimal("100000.00"))
                .build();

        when(serviceTypeRepository.findById(1)).thenReturn(Optional.of(parent));

        AppException ex = assertThrows(AppException.class, () -> serviceCatalogService.create(req));
        assertEquals(ErrorCode.ERR_SERVICE_TYPE_INACTIVE, ex.getErrorCode());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: Ném ERR_DUPLICATE_NAME khi trùng tên trong cùng nhóm")
    void testCreate_duplicateName_throwsException() {
        ServiceType parent = ServiceType.builder()
                .id(1)
                .name("Nhóm 1")
                .isActive(true)
                .build();

        CreateServiceRequest req = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ A")
                .basePrice(new BigDecimal("100000.00"))
                .build();

        when(serviceTypeRepository.findById(1)).thenReturn(Optional.of(parent));
        when(serviceRepository.existsByNameAndServiceTypeId("Dịch vụ A", 1)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> serviceCatalogService.create(req));
        assertEquals(ErrorCode.ERR_DUPLICATE_NAME, ex.getErrorCode());
    }

    @Test
    @DisplayName("softDelete: Đặt status = INACTIVE, tuyệt đối không gọi deleteById")
    void testSoftDelete_setsInactive() {
        Service existing = Service.builder()
                .id(5)
                .name("Dịch vụ cũ")
                .status(ServiceStatus.ACTIVE)
                .build();

        when(serviceRepository.findById(5)).thenReturn(Optional.of(existing));

        serviceCatalogService.softDelete(5);

        assertEquals(ServiceStatus.INACTIVE, existing.getStatus());
        verify(serviceRepository).save(existing);
        verify(serviceRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("updateStatus: Bật ACTIVE cho dịch vụ khi nhóm cha INACTIVE sẽ ném ERR_SERVICE_TYPE_INACTIVE")
    void testUpdateStatus_parentInactive() {
        ServiceType parentInactive = ServiceType.builder()
                .id(1)
                .name("Nhóm tắt")
                .isActive(false)
                .build();

        Service service = Service.builder()
                .id(7)
                .serviceType(parentInactive)
                .status(ServiceStatus.INACTIVE)
                .build();

        when(serviceRepository.findById(7)).thenReturn(Optional.of(service));

        UpdateServiceStatusRequest req = UpdateServiceStatusRequest.builder()
                .status("ACTIVE")
                .build();

        AppException ex = assertThrows(AppException.class, () -> serviceCatalogService.updateStatus(7, req));
        assertEquals(ErrorCode.ERR_SERVICE_TYPE_INACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("getById: Ném ERR_SERVICE_NOT_FOUND khi ID không tồn tại")
    void testGetById_notFound() {
        when(serviceRepository.findById(999)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> serviceCatalogService.getById(999));
        assertEquals(ErrorCode.ERR_SERVICE_NOT_FOUND, ex.getErrorCode());
        assertEquals("Không tìm thấy dịch vụ với ID: 999", ex.getMessage());
    }

    private Service systemService(ServiceType type) {
        return Service.builder()
                .id(20)
                .serviceType(type)
                .name("Phụ phí khuân vác thang bộ (mỗi lầu)")
                .basePrice(new BigDecimal("50000.00"))
                .status(ServiceStatus.ACTIVE)
                .code(SystemServiceCode.SURCHARGE_STAIRS)
                .build();
    }

    private ServiceType terrainType() {
        return ServiceType.builder().id(5).name("Địa hình").isActive(true).feeCategory(FeeCategory.TERRAIN).build();
    }

    @Test
    @DisplayName("updateStatus: Ngừng kích hoạt dịch vụ hệ thống thì ném ERR_SERVICE_SYSTEM_PROTECTED")
    void testUpdateStatus_systemServiceProtected() {
        Service service = systemService(terrainType());
        when(serviceRepository.findById(20)).thenReturn(Optional.of(service));

        UpdateServiceStatusRequest req = UpdateServiceStatusRequest.builder().status("INACTIVE").build();

        AppException ex = assertThrows(AppException.class, () -> serviceCatalogService.updateStatus(20, req));
        assertEquals(ErrorCode.ERR_SERVICE_SYSTEM_PROTECTED, ex.getErrorCode());
        assertEquals(ServiceStatus.ACTIVE, service.getStatus());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("softDelete: Xóa dịch vụ hệ thống thì ném ERR_SERVICE_SYSTEM_PROTECTED")
    void testSoftDelete_systemServiceProtected() {
        Service service = systemService(terrainType());
        when(serviceRepository.findById(20)).thenReturn(Optional.of(service));

        AppException ex = assertThrows(AppException.class, () -> serviceCatalogService.softDelete(20));
        assertEquals(ErrorCode.ERR_SERVICE_SYSTEM_PROTECTED, ex.getErrorCode());
        assertEquals(ServiceStatus.ACTIVE, service.getStatus());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: Sửa giá dịch vụ hệ thống thì cho phép, mã vẫn giữ nguyên")
    void testUpdate_systemServicePriceChange_allowed() {
        Service service = systemService(terrainType());
        when(serviceRepository.findById(20)).thenReturn(Optional.of(service));
        when(serviceRepository.existsByNameAndServiceTypeIdAndIdNot(service.getName(), 5, 20)).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateServiceRequest req = UpdateServiceRequest.builder()
                .serviceTypeId(5)
                .name(service.getName())
                .basePrice(new BigDecimal("60000.00"))
                .build();

        ServiceResponse res = serviceCatalogService.update(20, req);
        assertEquals(new BigDecimal("60000.00"), res.getBasePrice());
        assertEquals(SystemServiceCode.SURCHARGE_STAIRS, res.getCode());
    }

    @Test
    @DisplayName("update: Chuyển dịch vụ hệ thống sang loại khác nhóm phí thì ném ERR_SERVICE_SYSTEM_PROTECTED")
    void testUpdate_systemServiceChangeTypeOtherCategory_throws() {
        Service service = systemService(terrainType());
        ServiceType laborType = ServiceType.builder().id(2).name("Nhân công").isActive(true).feeCategory(FeeCategory.LABOR).build();
        when(serviceRepository.findById(20)).thenReturn(Optional.of(service));
        when(serviceTypeRepository.findById(2)).thenReturn(Optional.of(laborType));

        UpdateServiceRequest req = UpdateServiceRequest.builder()
                .serviceTypeId(2)
                .name(service.getName())
                .basePrice(new BigDecimal("50000.00"))
                .build();

        AppException ex = assertThrows(AppException.class, () -> serviceCatalogService.update(20, req));
        assertEquals(ErrorCode.ERR_SERVICE_SYSTEM_PROTECTED, ex.getErrorCode());
        assertEquals(5, service.getServiceType().getId());
    }

    @Test
    @DisplayName("update: Chuyển dịch vụ hệ thống sang loại cùng nhóm phí thì cho phép")
    void testUpdate_systemServiceChangeTypeSameCategory_allowed() {
        Service service = systemService(terrainType());
        ServiceType otherTerrain = ServiceType.builder().id(9).name("Địa hình 2").isActive(true).feeCategory(FeeCategory.TERRAIN).build();
        when(serviceRepository.findById(20)).thenReturn(Optional.of(service));
        when(serviceTypeRepository.findById(9)).thenReturn(Optional.of(otherTerrain));
        when(serviceRepository.existsByNameAndServiceTypeIdAndIdNot(service.getName(), 9, 20)).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateServiceRequest req = UpdateServiceRequest.builder()
                .serviceTypeId(9)
                .name(service.getName())
                .basePrice(new BigDecimal("50000.00"))
                .build();

        ServiceResponse res = serviceCatalogService.update(20, req);
        assertEquals(9, res.getServiceTypeId());
    }
}
