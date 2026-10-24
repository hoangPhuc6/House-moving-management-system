package com.example.house_moving_service.modules.catalog.service;

import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.catalog.service.impl.ServiceLookupServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceLookupServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private ServiceLookupServiceImpl serviceLookupService;

    @Test
    @DisplayName("findAllByIds: trả đúng kết quả của findAllByIdIn")
    void testFindAllByIds_delegatesToRepository() {
        Set<Integer> ids = Set.of(1, 2);
        List<Service> services = List.of(
                Service.builder().id(1).name("Xe tải 1.5 tấn").build(),
                Service.builder().id(2).name("Bốc xếp").build());
        when(serviceRepository.findAllByIdIn(ids)).thenReturn(services);

        assertSame(services, serviceLookupService.findAllByIds(ids));
        verify(serviceRepository).findAllByIdIn(ids);
    }

    @Test
    @DisplayName("getReference: trả đúng proxy của getReferenceById")
    void testGetReference_delegatesToRepository() {
        Service proxy = Service.builder().id(5).build();
        when(serviceRepository.getReferenceById(5)).thenReturn(proxy);

        assertSame(proxy, serviceLookupService.getReference(5));
    }
}
