package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleStatusRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.VehicleFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import org.springframework.data.domain.Pageable;

/** Quản lý đội xe (Admin). */
public interface VehicleService {

    VehicleResponse create(CreateVehicleRequest request);

    PageResponse<VehicleResponse> search(VehicleFilterRequest filter, Pageable pageable);

    VehicleResponse getById(Integer id);

    VehicleResponse update(Integer id, UpdateVehicleRequest request);

    VehicleResponse updateStatus(Integer id, UpdateVehicleStatusRequest request);

    /** Xóa mềm: chuyển xe sang INACTIVE để giữ lịch sử các chuyến đã chạy. */
    void softDelete(Integer id);
}
