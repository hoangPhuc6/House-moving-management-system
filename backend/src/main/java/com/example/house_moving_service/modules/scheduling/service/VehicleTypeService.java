package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleTypeResponse;

import java.util.List;

/** Quản lý loại xe (Admin). */
public interface VehicleTypeService {

    VehicleTypeResponse create(CreateVehicleTypeRequest request);

    /** @param isActive null = lấy tất cả; true / false = lọc theo trạng thái */
    List<VehicleTypeResponse> getAll(Boolean isActive);

    VehicleTypeResponse getById(Integer id);

    VehicleTypeResponse update(Integer id, UpdateVehicleTypeRequest request);

    VehicleTypeResponse toggleActive(Integer id);

    /** Xóa hẳn loại xe; chỉ được khi chưa có xe nào thuộc loại này. */
    void delete(Integer id);
}
