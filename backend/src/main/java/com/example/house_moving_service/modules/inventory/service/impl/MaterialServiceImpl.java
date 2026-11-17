package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.inventory.dto.request.CreateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialStatusRequest;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.mapper.MaterialMapper;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
import com.example.house_moving_service.modules.inventory.repository.specification.MaterialSpecification;
import com.example.house_moving_service.modules.inventory.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("code"), Sort.Order.asc("id"));

    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> search(MaterialFilterRequest filter, Pageable pageable) {
        // Không truyền sort thì sắp theo mã vật tư
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return PageResponse.from(materialRepository
                .findAll(MaterialSpecification.filter(filter), sorted)
                .map(materialMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialResponse getById(Integer id) {
        return materialMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialResponse create(CreateMaterialRequest request) {
        String code = normalizeCode(request.getCode());
        if (materialRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.ERR_MATERIAL_CODE_DUPLICATE, code);
        }

        // Tồn ban đầu luôn = 0: muốn có hàng phải nhập kho để có giao dịch IMPORT đối soát
        Material material = Material.builder()
                .code(code)
                .name(request.getName().trim())
                .category(request.getCategory())
                .unit(request.getUnit().trim())
                .unitPrice(request.getUnitPrice() != null ? request.getUnitPrice() : BigDecimal.ZERO)
                .quantityOnHand(0)
                .minStock(request.getMinStock() != null ? request.getMinStock() : 0)
                .description(trimToNull(request.getDescription()))
                .isActive(true)
                .build();

        Material saved = materialRepository.save(material);
        log.info("Material created: id={}, code={}, category={}", saved.getId(), code, saved.getCategory());
        return materialMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialResponse update(Integer id, UpdateMaterialRequest request) {
        Material material = findOrThrow(id);

        // Không đụng code, category, quantityOnHand
        if (material.getUnitPrice() == null || material.getUnitPrice().compareTo(request.getUnitPrice()) != 0) {
            log.info("Material id={} unitPrice {} -> {}", id, material.getUnitPrice(), request.getUnitPrice());
        }
        material.setName(request.getName().trim());
        material.setUnit(request.getUnit().trim());
        material.setUnitPrice(request.getUnitPrice());
        material.setMinStock(request.getMinStock());
        material.setDescription(trimToNull(request.getDescription()));

        return materialMapper.toResponse(materialRepository.save(material));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialResponse updateStatus(Integer id, UpdateMaterialStatusRequest request) {
        Material material = findOrThrow(id);
        if (!Objects.equals(material.getIsActive(), request.getIsActive())) {
            log.info("Material id={} isActive {} -> {}", id, material.getIsActive(), request.getIsActive());
            material.setIsActive(request.getIsActive());
            material = materialRepository.save(material);
        }
        return materialMapper.toResponse(material);
    }

    private Material findOrThrow(Integer id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MATERIAL_NOT_FOUND, id));
    }

    // " box-l " -> "BOX-L"
    static String normalizeCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
