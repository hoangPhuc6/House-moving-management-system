package com.example.house_moving_service.modules.catalog.service.impl;

import com.example.house_moving_service.modules.catalog.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.catalog.repository.ServiceTypeRepository;
import com.example.house_moving_service.modules.catalog.service.ServiceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTypeServiceImpl implements ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceRepository serviceRepository;

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
                .feeCategory(req.getFeeCategory() != null ? req.getFeeCategory() : FeeCategory.TRANSPORT)
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

        boolean deactivating = Boolean.TRUE.equals(serviceType.getIsActive()) && !Boolean.TRUE.equals(req.getIsActive());
        boolean changingCategory = req.getFeeCategory() != null && req.getFeeCategory() != serviceType.getFeeCategory();
        if (deactivating) {
            ensureNoSystemService(serviceType, "ngừng kích hoạt loại dịch vụ đang chứa nó");
        }
        if (changingCategory) {
            ensureNoSystemService(serviceType, "đổi nhóm phí của loại dịch vụ đang chứa nó");
        }

        serviceType.setName(trimmedName);
        serviceType.setDescription(req.getDescription());
        serviceType.setIsActive(req.getIsActive());
        if (req.getFeeCategory() != null) {
            serviceType.setFeeCategory(req.getFeeCategory());
        }

        ServiceType updated = serviceTypeRepository.save(serviceType);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public ServiceTypeResponse toggleActive(Integer id) {
        ServiceType serviceType = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_TYPE_NOT_FOUND, id));

        if (Boolean.TRUE.equals(serviceType.getIsActive())) {
            ensureNoSystemService(serviceType, "ngừng kích hoạt loại dịch vụ đang chứa nó");
        }
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

    // Tắt loại dịch vụ / đổi nhóm phí làm dịch vụ phụ phí bên trong ngừng tự cộng hoặc cộng sai nhóm
    private void ensureNoSystemService(ServiceType serviceType, String action) {
        if (serviceRepository.existsByServiceTypeIdAndCodeIsNotNull(serviceType.getId())) {
            throw new AppException(ErrorCode.ERR_SERVICE_SYSTEM_PROTECTED, action);
        }
    }

    private ServiceTypeResponse mapToResponse(ServiceType entity) {
        return ServiceTypeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .feeCategory(entity.getFeeCategory())
                .build();
    }
}

