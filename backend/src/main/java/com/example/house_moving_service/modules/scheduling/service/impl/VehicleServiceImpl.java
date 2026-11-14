package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleStatusRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.VehicleFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.VehicleMapper;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleTypeRepository;
import com.example.house_moving_service.modules.scheduling.repository.specification.VehicleSpecification;
import com.example.house_moving_service.modules.scheduling.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    private final VehicleMapper vehicleMapper;

    @Override
    @Transactional
    public VehicleResponse create(CreateVehicleRequest request) {
        String plate = normalizePlate(request.getLicensePlate());
        if (vehicleRepository.existsByLicensePlateIgnoreCase(plate)) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_LICENSE_PLATE, plate);
        }

        VehicleType type = findActiveType(request.getVehicleTypeId());

        VehicleStatus status = request.getStatus() != null ? request.getStatus() : VehicleStatus.AVAILABLE;
        if (status == VehicleStatus.IN_USE) {
            throw new AppException(ErrorCode.ERR_VEHICLE_INVALID_STATUS,
                    "IN_USE do hệ thống tự cập nhật khi chuyến bắt đầu, không thể đặt thủ công");
        }

        Vehicle vehicle = Vehicle.builder()
                .licensePlate(plate)
                .vehicleType(type)
                // Không nhập thể tích thùng thì lấy theo thể tích tối đa của loại xe
                .capacityM3(request.getCapacityM3() != null ? request.getCapacityM3() : type.getMaxVolumeM3())
                .status(status)
                .note(trimToNull(request.getNote()))
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle created: id={}, plate={}, typeId={}", saved.getId(), plate, type.getId());
        return vehicleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> search(VehicleFilterRequest filter, Pageable pageable) {
        return PageResponse.from(vehicleRepository
                .findAll(VehicleSpecification.filter(filter), pageable)
                .map(vehicleMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getById(Integer id) {
        return vehicleMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public VehicleResponse update(Integer id, UpdateVehicleRequest request) {
        Vehicle vehicle = findOrThrow(id);

        String plate = normalizePlate(request.getLicensePlate());
        if (vehicleRepository.existsByLicensePlateIgnoreCaseAndIdNot(plate, id)) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_LICENSE_PLATE, plate);
        }

        // Giữ nguyên loại xe thì không cần loại đó còn hoạt động; đổi sang loại khác thì loại mới phải đang hoạt động
        if (!Objects.equals(vehicle.getVehicleType().getId(), request.getVehicleTypeId())) {
            vehicle.setVehicleType(findActiveType(request.getVehicleTypeId()));
        }

        vehicle.setLicensePlate(plate);
        vehicle.setCapacityM3(request.getCapacityM3() != null
                ? request.getCapacityM3()
                : vehicle.getVehicleType().getMaxVolumeM3());
        vehicle.setNote(trimToNull(request.getNote()));

        Vehicle saved = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleResponse updateStatus(Integer id, UpdateVehicleStatusRequest request) {
        Vehicle vehicle = findOrThrow(id);
        changeStatus(vehicle, request.getStatus());
        if (request.getNote() != null) {
            vehicle.setNote(trimToNull(request.getNote()));
        }
        Vehicle saved = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(Integer id) {
        Vehicle vehicle = findOrThrow(id);
        changeStatus(vehicle, VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
    }

    /**
     * Quy tắc đổi trạng thái xe:
     * <ul>
     *   <li>IN_USE chỉ do hệ thống đặt khi chuyến bắt đầu (FieldMovingScheduleService), admin không đặt tay.</li>
     *   <li>Xe đang chạy chuyến IN_PROGRESS thì không đổi được trạng thái.</li>
     *   <li>Chuyển MAINTENANCE / INACTIVE khi xe còn được phân công cho chuyến chưa xong → phải đổi xe cho chuyến đó trước.</li>
     * </ul>
     */
    private void changeStatus(Vehicle vehicle, VehicleStatus target) {
        if (target == VehicleStatus.IN_USE) {
            throw new AppException(ErrorCode.ERR_VEHICLE_INVALID_STATUS,
                    "IN_USE do hệ thống tự cập nhật khi chuyến bắt đầu, không thể đặt thủ công");
        }
        if (vehicle.getStatus() == target) {
            return;
        }
        if (vehicleAssignmentRepository.isOnTripInProgress(vehicle.getId())) {
            throw new AppException(ErrorCode.ERR_VEHICLE_BUSY, "đổi trạng thái khi xe đang chạy chuyến");
        }
        if ((target == VehicleStatus.MAINTENANCE || target == VehicleStatus.INACTIVE)
                && vehicleAssignmentRepository.hasUnfinishedAssignment(vehicle.getId())) {
            throw new AppException(ErrorCode.ERR_VEHICLE_BUSY,
                    "chuyển sang " + target + ". Hãy đổi xe khác cho các chuyến đó trước");
        }

        log.info("Vehicle id={} status {} -> {}", vehicle.getId(), vehicle.getStatus(), target);
        vehicle.setStatus(target);
    }

    private Vehicle findOrThrow(Integer id) {
        return vehicleRepository.findWithTypeById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_VEHICLE_NOT_FOUND, id));
    }

    private VehicleType findActiveType(Integer typeId) {
        VehicleType type = vehicleTypeRepository.findById(typeId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_VEHICLE_TYPE_NOT_FOUND, typeId));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new AppException(ErrorCode.ERR_VEHICLE_TYPE_INACTIVE, type.getName());
        }
        return type;
    }

    // " 51c-123.45 " -> "51C-123.45"; gộp nhiều khoảng trắng thành một
    static String normalizePlate(String raw) {
        return raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
