package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleTypeResponse;
import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import com.example.house_moving_service.modules.scheduling.mapper.VehicleMapper;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleTypeRepository;
import com.example.house_moving_service.modules.scheduling.service.VehicleTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleTypeServiceImpl implements VehicleTypeService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final VehicleTypeRepository vehicleTypeRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;

    @Override
    @Transactional
    public VehicleTypeResponse create(CreateVehicleTypeRequest request) {
        String name = request.getName().trim();
        if (vehicleTypeRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_NAME, name);
        }

        VehicleType type = VehicleType.builder()
                .name(name)
                .description(trimToNull(request.getDescription()))
                .maxLoadKg(request.getMaxLoadKg())
                .maxVolumeM3(request.getMaxVolumeM3())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();

        VehicleType saved = vehicleTypeRepository.save(type);
        log.info("Vehicle type created: id={}, name={}", saved.getId(), saved.getName());
        return vehicleMapper.toTypeResponse(saved, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleTypeResponse> getAll(Boolean isActive) {
        List<VehicleType> types = isActive == null
                ? vehicleTypeRepository.findAll(DEFAULT_SORT)
                : vehicleTypeRepository.findAllByIsActive(isActive, DEFAULT_SORT);

        Map<Integer, Long> counts = vehicleRepository.countGroupByType().stream()
                .collect(Collectors.toMap(
                        VehicleRepository.VehicleCountByType::getVehicleTypeId,
                        VehicleRepository.VehicleCountByType::getTotal));

        return types.stream()
                .map(t -> vehicleMapper.toTypeResponse(t, counts.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleTypeResponse getById(Integer id) {
        VehicleType type = findOrThrow(id);
        return vehicleMapper.toTypeResponse(type, vehicleRepository.countByVehicleTypeId(id));
    }

    @Override
    @Transactional
    public VehicleTypeResponse update(Integer id, UpdateVehicleTypeRequest request) {
        VehicleType type = findOrThrow(id);

        String name = request.getName().trim();
        if (vehicleTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AppException(ErrorCode.ERR_DUPLICATE_NAME, name);
        }

        type.setName(name);
        type.setDescription(trimToNull(request.getDescription()));
        type.setMaxLoadKg(request.getMaxLoadKg());
        type.setMaxVolumeM3(request.getMaxVolumeM3());
        if (request.getIsActive() != null) {
            type.setIsActive(request.getIsActive());
        }

        VehicleType saved = vehicleTypeRepository.save(type);
        return vehicleMapper.toTypeResponse(saved, vehicleRepository.countByVehicleTypeId(id));
    }

    @Override
    @Transactional
    public VehicleTypeResponse toggleActive(Integer id) {
        VehicleType type = findOrThrow(id);
        // Ngừng loại xe chỉ chặn việc gán loại này cho xe mới; các xe hiện có giữ nguyên
        type.setIsActive(!Boolean.TRUE.equals(type.getIsActive()));
        VehicleType saved = vehicleTypeRepository.save(type);
        log.info("Vehicle type id={} toggled active={}", id, saved.getIsActive());
        return vehicleMapper.toTypeResponse(saved, vehicleRepository.countByVehicleTypeId(id));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        VehicleType type = findOrThrow(id);
        long vehicleCount = vehicleRepository.countByVehicleTypeId(id);
        if (vehicleCount > 0) {
            throw new AppException(ErrorCode.ERR_VEHICLE_TYPE_IN_USE, vehicleCount);
        }
        vehicleTypeRepository.delete(type);
        log.info("Vehicle type deleted: id={}", id);
    }

    private VehicleType findOrThrow(Integer id) {
        return vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_VEHICLE_TYPE_NOT_FOUND, id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
