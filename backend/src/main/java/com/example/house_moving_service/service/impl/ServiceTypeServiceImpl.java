package com.example.house_moving_service.service.impl;

import com.example.house_moving_service.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.entity.ServiceType;
import com.example.house_moving_service.exception.AppException;
import com.example.house_moving_service.exception.ErrorCode;
import com.example.house_moving_service.repository.ServiceTypeRepository;
import com.example.house_moving_service.service.ServiceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTypeServiceImpl implements ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;

    @Override
    @Transactional
    public ServiceTypeResponse create(CreateServiceTypeRequest req) {
        String trimmedName = req.getName().trim();
        if (serviceTypeRepository.existsByName(trimmedName)) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_NAME, trimmedName);
        }

        ServiceType serviceType = ServiceType.builder()
                .name(trimmedName)
                .description(req.getDescription())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();

        ServiceType saved = serviceTypeRepository.save(serviceType);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ServiceTypeResponse update(Integer id, UpdateServiceTypeRequest req) {
        ServiceType serviceType = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_TYPE_NOT_FOUND, id));

        String trimmedName = req.getName().trim();
        if (serviceTypeRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_NAME, trimmedName);
        }

        serviceType.setName(trimmedName);
        serviceType.setDescription(req.getDescription());
        serviceType.setIsActive(req.getIsActive());

        ServiceType updated = serviceTypeRepository.save(serviceType);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public ServiceTypeResponse toggleActive(Integer id) {
        ServiceType serviceType = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_TYPE_NOT_FOUND, id));

        serviceType.setIsActive(!Boolean.TRUE.equals(serviceType.getIsActive()));
        ServiceType updated = serviceTypeRepository.save(serviceType);
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceTypeResponse getById(Integer id) {
        ServiceType serviceType = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_TYPE_NOT_FOUND, id));
        return mapToResponse(serviceType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> getAll() {
        return serviceTypeRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> getAllActive() {
        return serviceTypeRepository.findAllByIsActive(true).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ServiceTypeResponse mapToResponse(ServiceType entity) {
        return ServiceTypeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .build();
    }
}

