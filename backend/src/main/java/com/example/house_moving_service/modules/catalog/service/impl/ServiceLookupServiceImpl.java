package com.example.house_moving_service.modules.catalog.service.impl;

import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;

// Bean riêng, tách khỏi ServiceCatalogServiceImpl. Không gắn @Transactional để tham gia transaction của module gọi
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceLookupServiceImpl implements ServiceLookupService {

    private final ServiceRepository serviceRepository;

    @Override
    public List<Service> findAllByIds(Collection<Integer> ids) {
        return serviceRepository.findAllByIdIn(ids);
    }

    @Override
    public Service getReference(Integer id) {
        return serviceRepository.getReferenceById(id);
    }
}
