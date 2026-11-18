package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.inventory.dto.request.AdjustInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.InventoryTransactionFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialHistoryFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialHistoryResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.mapper.InventoryTransactionMapper;
import com.example.house_moving_service.modules.inventory.mapper.MaterialMapper;
import com.example.house_moving_service.modules.inventory.repository.InventoryTransactionRepository;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
import com.example.house_moving_service.modules.inventory.repository.specification.InventoryTransactionSpecification;
import com.example.house_moving_service.modules.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    // Mới nhất trước; id phá hòa khi trùng created_at
    static final Sort DEFAULT_TRANSACTION_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    // Mốc thay cho from / to không truyền khi tính tổng (query tổng cần đủ hai mốc)
    private static final LocalDateTime MIN_TIME = LocalDate.of(1970, 1, 1).atStartOfDay();
    private static final LocalDateTime MAX_TIME = LocalDate.of(9999, 1, 1).atStartOfDay();

    private final InventoryLedger inventoryLedger;
    private final InventoryTransactionMapper transactionMapper;
    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;
    private final InventoryTransactionRepository transactionRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InventoryTransactionResponse> importStock(ImportInventoryRequest request, Long actorUserId) {
        List<ImportInventoryItemRequest> items = request.getItems().stream()
                .sorted(Comparator.comparing(ImportInventoryItemRequest::getMaterialId))
                .toList();
        InventoryRequestChecks.requireDistinct(items.stream().map(ImportInventoryItemRequest::getMaterialId).toList(),
                "materialId");
        items.forEach(item -> InventoryRequestChecks.requirePositiveQuantity(item.getQuantity()));

        // Khóa và kiểm hết mọi món trước (materialId tăng dần) rồi mới ghi, để một món lỗi thì không món nào được nhập
        for (ImportInventoryItemRequest item : items) {
            InventoryRequestChecks.requireActive(inventoryLedger.lockMaterial(item.getMaterialId()));
        }

        String note = InventoryRequestChecks.trimToNull(request.getNote());
        List<InventoryTransactionResponse> result = new ArrayList<>(items.size());
        for (ImportInventoryItemRequest item : items) {
            result.add(transactionMapper.toResponse(inventoryLedger.post(InventoryLedger.Entry.builder()
                    .materialId(item.getMaterialId())
                    .type(InventoryTransactionType.IMPORT)
                    .quantity(item.getQuantity())
                    .quantityChange(item.getQuantity())
                    .unitCost(item.getUnitCost())
                    .note(note)
                    .actorUserId(actorUserId)
                    .build())));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryTransactionResponse writeOffDamaged(DamageInventoryRequest request, Long actorUserId) {
        InventoryRequestChecks.requirePositiveQuantity(request.getQuantity());
        String note = InventoryRequestChecks.trimToNull(request.getNote());
        if (note == null) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "xuất hủy bắt buộc ghi lý do");
        }
        InventoryRequestChecks.requireActive(inventoryLedger.lockMaterial(request.getMaterialId()));

        // Ledger tự chặn vượt tồn (ERR_INVENTORY_INSUFFICIENT)
        return transactionMapper.toResponse(inventoryLedger.post(InventoryLedger.Entry.builder()
                .materialId(request.getMaterialId())
                .type(InventoryTransactionType.DAMAGED)
                .quantity(request.getQuantity())
                .quantityChange(-request.getQuantity())
                .note(note)
                .actorUserId(actorUserId)
                .build()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryTransactionResponse adjust(AdjustInventoryRequest request, Long actorUserId) {
        if (request.getActualQuantity() == null || request.getActualQuantity() < 0) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "số lượng thực tế không được âm");
        }
        String note = InventoryRequestChecks.trimToNull(request.getNote());
        if (note == null) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "kiểm kê bắt buộc ghi lý do");
        }
        // Khóa trước rồi mới đọc tồn để chênh lệch tính trên số mới nhất. Vật tư đang tắt vẫn kiểm kê được
        Material material = inventoryLedger.lockMaterial(request.getMaterialId());
        // actual ≥ 0 và onHand ≥ 0 nên hiệu luôn nằm trong int
        int difference = request.getActualQuantity() - material.getQuantityOnHand();
        if (difference == 0) {
            throw new AppException(ErrorCode.ERR_INVENTORY_ADJUST_NO_CHANGE);
        }

        return transactionMapper.toResponse(inventoryLedger.post(InventoryLedger.Entry.builder()
                .materialId(material.getId())
                .type(InventoryTransactionType.ADJUST)
                .quantity(Math.abs(difference))
                .quantityChange(difference)
                .note(note)
                .actorUserId(actorUserId)
                .build()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialResponse> getLowStock() {
        return materialRepository.findActiveLowStock().stream().map(materialMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialHistoryResponse getMaterialHistory(Integer materialId, MaterialHistoryFilterRequest filter,
                                                      Pageable pageable) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MATERIAL_NOT_FOUND, materialId));
        MaterialHistoryFilterRequest f = filter != null ? filter : new MaterialHistoryFilterRequest();

        PageResponse<InventoryTransactionResponse> transactions = searchTransactions(
                InventoryTransactionFilterRequest.builder()
                        .materialId(materialId).type(f.getType()).from(f.getFrom()).to(f.getTo())
                        .build(),
                pageable);

        // Đủ mọi loại để client không phải kiểm thiếu khóa; lọc theo loại thì các loại khác = 0
        Map<InventoryTransactionType, Long> totals = new EnumMap<>(InventoryTransactionType.class);
        for (InventoryTransactionType type : InventoryTransactionType.values()) {
            totals.put(type, 0L);
        }
        LocalDateTime from = f.getFrom() != null ? f.getFrom().atStartOfDay() : MIN_TIME;
        LocalDateTime toExclusive = f.getTo() != null ? f.getTo().plusDays(1).atStartOfDay() : MAX_TIME;
        for (InventoryTransactionRepository.TypeTotal row
                : transactionRepository.sumQuantityByType(materialId, from, toExclusive)) {
            if (f.getType() == null || f.getType() == row.getType()) {
                totals.put(row.getType(), row.getTotal() != null ? row.getTotal() : 0L);
            }
        }

        return MaterialHistoryResponse.builder()
                .material(materialMapper.toResponse(material))
                .totals(totals)
                .transactions(transactions)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> searchTransactions(InventoryTransactionFilterRequest filter,
                                                                         Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_TRANSACTION_SORT);
        return PageResponse.from(transactionRepository
                .findAll(InventoryTransactionSpecification.filter(filter), sorted)
                .map(transactionMapper::toResponse));
    }
}
