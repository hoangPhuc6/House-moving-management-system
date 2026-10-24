package com.example.house_moving_service.modules.catalog.service;

import com.example.house_moving_service.modules.catalog.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceTypeResponse;

import java.util.List;

public interface ServiceTypeService {

    ServiceTypeResponse create(CreateServiceTypeRequest req);

    ServiceTypeResponse update(Integer id, UpdateServiceTypeRequest req);

    ServiceTypeResponse toggleActive(Integer id);

    ServiceTypeResponse getById(Integer id);

    List<ServiceTypeResponse> getAll();

    List<ServiceTypeResponse> getAllActive();
}

