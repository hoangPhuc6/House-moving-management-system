package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.dto.response.ServiceTypeResponse;

import java.util.List;

public interface ServiceTypeService {

    ServiceTypeResponse create(CreateServiceTypeRequest req);

    ServiceTypeResponse update(Integer id, UpdateServiceTypeRequest req);

    ServiceTypeResponse toggleActive(Integer id);

    ServiceTypeResponse getById(Integer id);

    List<ServiceTypeResponse> getAll();

    List<ServiceTypeResponse> getAllActive();
}

