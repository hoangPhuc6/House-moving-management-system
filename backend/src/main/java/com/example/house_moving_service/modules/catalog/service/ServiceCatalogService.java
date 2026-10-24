package com.example.house_moving_service.modules.catalog.service;

import com.example.house_moving_service.modules.catalog.dto.request.CreateServiceRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceStatusRequest;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceResponse;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;

import java.util.List;

public interface ServiceCatalogService {

    ServiceResponse create(CreateServiceRequest req);

    ServiceResponse update(Integer id, UpdateServiceRequest req);

    ServiceResponse updateStatus(Integer id, UpdateServiceStatusRequest req);

    void softDelete(Integer id);

    ServiceResponse getById(Integer id);

    ServiceResponse getActiveServiceById(Integer id);

    List<ServiceResponse> getAll(Integer typeId, ServiceStatus status);

    List<ServiceResponse> getActiveCatalog();

    List<ServiceTypeResponse> getActiveCatalogGroupedByType();
}

