package com.example.house_moving_service.service.impl;

import com.example.house_moving_service.dto.request.CreateServiceRequest;
import com.example.house_moving_service.dto.request.UpdateServiceRequest;
import com.example.house_moving_service.dto.request.UpdateServiceStatusRequest;
import com.example.house_moving_service.dto.response.ServiceResponse;
import com.example.house_moving_service.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.entity.Service;
import com.example.house_moving_service.entity.ServiceType;
import com.example.house_moving_service.enums.ServiceStatus;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.repository.ServiceRepository;
import com.example.house_moving_service.repository.ServiceTypeRepository;
import com.example.house_moving_service.service.ServiceCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceCatalogServiceImpl implements ServiceCatalogService {

    private final ServiceRepository serviceRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    @Override
    @Transactional
    public ServiceResponse create(CreateServiceRequest req) {
        ServiceType serviceType = serviceTypeRepository.findById(req.getServiceTypeId())
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_TYPE_NOT_FOUND, req.getServiceTypeId()));

        if (!Boolean.TRUE.equals(serviceType.getIsActive())) {
            throw new AppException(ErrorCode.ERR_SERVICE_TYPE_INACTIVE);
        }

        String trimmedName = req.getName().trim();
        if (serviceRepository.existsByNameAndServiceTypeId(trimmedName, req.getServiceTypeId())) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_NAME, trimmedName);
        }

        Service service = Service.builder()
                .serviceType(serviceType)
                .name(trimmedName)
                .description(req.getDescription())
                .basePrice(req.getBasePrice())
                .status(ServiceStatus.ACTIVE)
                .build();

        Service saved = serviceRepository.save(service);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ServiceResponse update(Integer id, UpdateServiceRequest req) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, id));

        if (!service.getServiceType().getId().equals(req.getServiceTypeId())) {
            ServiceType newServiceType = serviceTypeRepository.findById(req.getServiceTypeId())
                    .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_TYPE_NOT_FOUND, req.getServiceTypeId()));

            if (!Boolean.TRUE.equals(newServiceType.getIsActive())) {
                throw new AppException(ErrorCode.ERR_SERVICE_TYPE_INACTIVE);
            }
            service.setServiceType(newServiceType);
        }

        String trimmedName = req.getName().trim();
        if (serviceRepository.existsByNameAndServiceTypeIdAndIdNot(trimmedName, req.getServiceTypeId(), id)) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_NAME, trimmedName);
        }

        service.setName(trimmedName);
        service.setDescription(req.getDescription());
        service.setBasePrice(req.getBasePrice());

        Service updated = serviceRepository.save(service);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public ServiceResponse updateStatus(Integer id, UpdateServiceStatusRequest req) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, id));

        ServiceStatus newStatus = req.toServiceStatus();
        if (newStatus == ServiceStatus.ACTIVE && !Boolean.TRUE.equals(service.getServiceType().getIsActive())) {
            throw new AppException(ErrorCode.ERR_SERVICE_TYPE_INACTIVE);
        }

        service.setStatus(newStatus);
        Service updated = serviceRepository.save(service);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void softDelete(Integer id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, id));

        service.setStatus(ServiceStatus.INACTIVE);
        serviceRepository.save(service);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponse getById(Integer id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, id));
        return mapToResponse(service);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponse getActiveServiceById(Integer id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, id));

        if (service.getStatus() != ServiceStatus.ACTIVE || !Boolean.TRUE.equals(service.getServiceType().getIsActive())) {
            throw new AppException(ErrorCode.ERR_SERVICE_INACTIVE);
        }
        return mapToResponse(service);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> getAll(Integer typeId, ServiceStatus status) {
        List<Service> services;
        if (typeId != null && status != null) {
            services = serviceRepository.findAllByServiceTypeIdAndStatus(typeId, status);
        } else if (typeId != null) {
            services = serviceRepository.findAllByServiceTypeId(typeId);
        } else if (status != null) {
            services = serviceRepository.findAllByStatus(status);
        } else {
            services = serviceRepository.findAll();
        }
        return services.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> getActiveCatalog() {
        return serviceRepository.findAllByStatusAndServiceType_IsActiveTrue(ServiceStatus.ACTIVE).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> getActiveCatalogGroupedByType() {
        List<ServiceType> activeTypes = serviceTypeRepository.findAllByIsActive(true);

        return activeTypes.stream()
                .map(type -> {
                    List<ServiceResponse> activeServices = serviceRepository
                            .findAllByServiceTypeIdAndStatus(type.getId(), ServiceStatus.ACTIVE)
                            .stream()
                            .map(this::mapToResponse)
                            .toList();

                    return ServiceTypeResponse.builder()
                            .id(type.getId())
                            .name(type.getName())
                            .description(type.getDescription())
                            .isActive(type.getIsActive())
                            .services(activeServices)
                            .build();
                })
                .toList();
    }

    private ServiceResponse mapToResponse(Service entity) {
        return ServiceResponse.builder()
                .id(entity.getId())
                .serviceTypeId(entity.getServiceType() != null ? entity.getServiceType().getId() : null)
                .serviceTypeName(entity.getServiceType() != null ? entity.getServiceType().getName() : null)
                .name(entity.getName())
                .description(entity.getDescription())
                .basePrice(entity.getBasePrice())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

