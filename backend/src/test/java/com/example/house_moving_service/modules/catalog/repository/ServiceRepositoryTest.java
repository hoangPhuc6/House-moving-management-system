package com.example.house_moving_service.modules.catalog.repository;

import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ServiceRepositoryTest {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findAllByIdInAndStatus: Lấy đúng các dịch vụ ACTIVE và nạp eager ServiceType trong 1 query")
    void testFindAllByIdInAndStatus_success() {
        // Given
        ServiceType serviceType = serviceTypeRepository.save(ServiceType.builder()
                .name("Nhóm vận chuyển test")
                .description("Mô tả nhóm vận chuyển")
                .isActive(true)
                .build());

        Service activeService1 = serviceRepository.save(Service.builder()
                .serviceType(serviceType)
                .name("Xe tải 1.5T test")
                .description("Gói cơ bản")
                .basePrice(new BigDecimal("500000.00"))
                .status(ServiceStatus.ACTIVE)
                .build());

        Service inactiveService = serviceRepository.save(Service.builder()
                .serviceType(serviceType)
                .name("Xe tải 2.5T test ngừng cung cấp")
                .description("Ngừng hoạt động")
                .basePrice(new BigDecimal("700000.00"))
                .status(ServiceStatus.INACTIVE)
                .build());

        Service activeService2 = serviceRepository.save(Service.builder()
                .serviceType(serviceType)
                .name("Xe tải 5T test")
                .description("Gói xe tải lớn")
                .basePrice(new BigDecimal("1200000.00"))
                .status(ServiceStatus.ACTIVE)
                .build());

        List<Integer> queryIds = List.of(activeService1.getId(), inactiveService.getId(), activeService2.getId());

        // When
        List<Service> results = serviceRepository.findAllByIdInAndStatus(queryIds, ServiceStatus.ACTIVE);

        // Then
        assertNotNull(results);
        assertEquals(2, results.size(), "Chỉ 2 dịch vụ ACTIVE được trả về, dịch vụ INACTIVE phải bị loại");

        List<Integer> resultIds = results.stream().map(Service::getId).toList();
        assertTrue(resultIds.contains(activeService1.getId()));
        assertTrue(resultIds.contains(activeService2.getId()));
        assertFalse(resultIds.contains(inactiveService.getId()));

        // Kiểm tra eager fetch quan hệ serviceType không sinh LazyInitializationException
        results.forEach(s -> {
            assertNotNull(s.getServiceType());
            assertEquals("Nhóm vận chuyển test", s.getServiceType().getName());
        });
    }

    @Test
    @DisplayName("findAllByStatus: Lấy danh sách dịch vụ theo trạng thái ACTIVE")
    void testFindAllByStatus() {
        ServiceType serviceType = serviceTypeRepository.save(ServiceType.builder()
                .name("Nhóm nhân công test")
                .description("Bốc xếp")
                .isActive(true)
                .build());

        serviceRepository.save(Service.builder()
                .serviceType(serviceType)
                .name("Bốc xếp tầng trệt test")
                .basePrice(new BigDecimal("200000.00"))
                .status(ServiceStatus.ACTIVE)
                .build());

        List<Service> activeServices = serviceRepository.findAllByStatus(ServiceStatus.ACTIVE);
        assertNotNull(activeServices);
        assertFalse(activeServices.isEmpty());
        activeServices.forEach(s -> assertEquals(ServiceStatus.ACTIVE, s.getStatus()));
    }

    @Test
    @DisplayName("findAllByServiceTypeIdAndStatus: Lọc chính xác theo nhóm dịch vụ và trạng thái")
    void testFindAllByServiceTypeIdAndStatus() {
        ServiceType type1 = serviceTypeRepository.save(ServiceType.builder()
                .name("Nhóm 1")
                .isActive(true)
                .build());

        ServiceType type2 = serviceTypeRepository.save(ServiceType.builder()
                .name("Nhóm 2")
                .isActive(true)
                .build());

        serviceRepository.save(Service.builder()
                .serviceType(type1)
                .name("Dịch vụ thuộc nhóm 1")
                .basePrice(new BigDecimal("100000.00"))
                .status(ServiceStatus.ACTIVE)
                .build());

        serviceRepository.save(Service.builder()
                .serviceType(type2)
                .name("Dịch vụ thuộc nhóm 2")
                .basePrice(new BigDecimal("200000.00"))
                .status(ServiceStatus.ACTIVE)
                .build());

        List<Service> group1Services = serviceRepository.findAllByServiceTypeIdAndStatus(type1.getId(), ServiceStatus.ACTIVE);
        assertNotNull(group1Services);
        assertEquals(1, group1Services.size());
        assertEquals("Dịch vụ thuộc nhóm 1", group1Services.getFirst().getName());
        assertEquals(type1.getId(), group1Services.getFirst().getServiceType().getId());
    }

    // Cột code không ghi qua JPA (insertable/updatable = false) → gán bằng native SQL như migration
    private void assignCode(Integer serviceId, String code) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE services SET code = :code WHERE id = :id")
                .setParameter("code", code)
                .setParameter("id", serviceId)
                .executeUpdate();
        entityManager.clear();
    }

    private Service saveService(ServiceType type, String name) {
        return serviceRepository.save(Service.builder()
                .serviceType(type)
                .name(name)
                .basePrice(new BigDecimal("10000.00"))
                .status(ServiceStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("findByCode: Tìm được dịch vụ hệ thống theo mã, kèm loại dịch vụ và nhóm phí")
    void testFindByCode() {
        ServiceType type = serviceTypeRepository.save(ServiceType.builder()
                .name("Nhóm địa hình test").isActive(true).feeCategory(FeeCategory.TERRAIN).build());
        Service service = saveService(type, "Phụ phí test có mã");
        assignCode(service.getId(), "TEST_CODE_FIND");

        Optional<Service> found = serviceRepository.findByCode("TEST_CODE_FIND");
        assertTrue(found.isPresent());
        assertEquals(service.getId(), found.get().getId());
        assertEquals(FeeCategory.TERRAIN, found.get().getServiceType().getFeeCategory());
        assertTrue(serviceRepository.findByCode("TEST_CODE_KHONG_CO").isEmpty());
    }

    @Test
    @DisplayName("existsByServiceTypeIdAndCodeIsNotNull: Chỉ true khi loại có dịch vụ mang mã")
    void testExistsByServiceTypeIdAndCodeIsNotNull() {
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().name("Nhóm có mã test").isActive(true).build());
        Service service = saveService(type, "Dịch vụ thường test");

        assertFalse(serviceRepository.existsByServiceTypeIdAndCodeIsNotNull(type.getId()));
        assignCode(service.getId(), "TEST_CODE_EXISTS");
        assertTrue(serviceRepository.existsByServiceTypeIdAndCodeIsNotNull(type.getId()));
    }

    @Test
    @DisplayName("code: Hai dịch vụ không được trùng mã (unique index)")
    void testCodeUnique() {
        ServiceType type = serviceTypeRepository.save(ServiceType.builder().name("Nhóm unique test").isActive(true).build());
        Service s1 = saveService(type, "Dịch vụ unique 1");
        Service s2 = saveService(type, "Dịch vụ unique 2");
        assignCode(s1.getId(), "TEST_CODE_UNIQUE");

        assertThrows(Exception.class, () -> assignCode(s2.getId(), "TEST_CODE_UNIQUE"));
    }

    @Test
    @DisplayName("fee_category: Insert không truyền cột thì DB mặc định TRANSPORT; dữ liệu seed được gán đúng nhóm")
    void testFeeCategoryDefaultAndSeed() {
        Number id = (Number) entityManager.createNativeQuery(
                        "INSERT INTO service_types (name, is_active) VALUES ('Nhóm default test', true) RETURNING id")
                .getSingleResult();
        entityManager.clear();

        ServiceType inserted = serviceTypeRepository.findById(id.intValue()).orElseThrow();
        assertEquals(FeeCategory.TRANSPORT, inserted.getFeeCategory());

        serviceTypeRepository.findById(5).ifPresent(t -> assertEquals(FeeCategory.TERRAIN, t.getFeeCategory()));
    }
}
