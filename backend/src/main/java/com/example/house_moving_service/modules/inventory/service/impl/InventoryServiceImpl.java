package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.mapper.InventoryTransactionMapper;
import com.example.house_moving_service.modules.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryLedger inventoryLedger;
    private final InventoryTransactionMapper transactionMapper;

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
}
