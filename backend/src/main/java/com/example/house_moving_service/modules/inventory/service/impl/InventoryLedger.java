package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.repository.InventoryTransactionRepository;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nơi DUY NHẤT đổi materials.quantity_on_hand. Mỗi lần đổi: khóa dòng vật tư, kiểm tồn sau giao dịch ≥ 0, cập nhật
 * tồn và ghi đúng một dòng inventory_transactions kèm balance_after. Chỉ dùng nội bộ module inventory.
 * <p>
 * Phải chạy trong transaction của service gọi (MANDATORY) để khóa giữ đến lúc commit và lỗi thì rollback cả request.
 * Service gọi nhiều vật tư trong một request thì gọi theo materialId tăng dần (thứ tự khóa của M8).
 * booking / schedule của giao dịch lấy từ dòng cấp phát (allocation), không nhận từ request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryLedger {

    private final MaterialRepository materialRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserLookupService userLookupService;
    private final Clock clock;

    /**
     * Một lần đổi tồn kho.
     *
     * @param quantity       số lượng nghiệp vụ, luôn dương
     * @param quantityChange thay đổi thật của tồn, phải khớp loại: IMPORT / RETURN +quantity, EXPORT −quantity,
     *                       DAMAGED 0 hoặc −quantity, ADJUST ±quantity
     * @param allocation     bắt buộc với EXPORT / RETURN; null với giao dịch trong kho (IMPORT, ADJUST, DAMAGED trong kho)
     * @param note           bắt buộc với ADJUST
     */
    @Builder
    public record Entry(
            Integer materialId,
            InventoryTransactionType type,
            int quantity,
            int quantityChange,
            BigDecimal unitCost,
            MaterialAllocation allocation,
            String note,
            Long actorUserId
    ) {
    }

    /** Khóa dòng vật tư (SELECT ... FOR UPDATE) cho service cần đọc vật tư trước khi ghi sổ; không có thì ERR_MATERIAL_NOT_FOUND. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Material lockMaterial(Integer materialId) {
        if (materialId == null) {
            throw new AppException(ErrorCode.ERR_MATERIAL_NOT_FOUND, (Object) null);
        }
        return materialRepository.findByIdForUpdate(materialId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MATERIAL_NOT_FOUND, materialId));
    }

    /** Ghi một giao dịch và cập nhật tồn kho; trả dòng giao dịch đã lưu. Không đủ tồn thì không đổi gì. */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryTransaction post(Entry entry) {
        validate(entry);
        Material material = lockMaterial(entry.materialId());

        int onHand = material.getQuantityOnHand();
        int balanceAfter;
        try {
            balanceAfter = Math.addExact(onHand, entry.quantityChange());
        } catch (ArithmeticException ex) {
            throw new AppException(ErrorCode.ERR_INVENTORY_INVALID_QUANTITY, "tồn kho vượt giới hạn cho phép");
        }
        if (balanceAfter < 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("materialId", material.getId());
            details.put("onHand", onHand);
            details.put("requested", -entry.quantityChange());
            throw AppException.withDetails(ErrorCode.ERR_INVENTORY_INSUFFICIENT, details,
                    material.getCode() + " (còn " + onHand + ", cần " + (-entry.quantityChange()) + ")");
        }

        if (balanceAfter != onHand) {
            material.setQuantityOnHand(balanceAfter);
            materialRepository.save(material);
        }

        MaterialAllocation allocation = entry.allocation();
        InventoryTransaction transaction = InventoryTransaction.builder()
                .material(material)
                .transactionType(entry.type())
                .quantity(entry.quantity())
                .quantityChange(entry.quantityChange())
                .balanceAfter(balanceAfter)
                .unitCost(entry.unitCost())
                .allocation(allocation)
                .booking(allocation != null ? allocation.getBooking() : null)
                .schedule(allocation != null ? allocation.getSchedule() : null)
                .note(trimToNull(entry.note()))
                .createdBy(entry.actorUserId() != null ? userLookupService.getUserReference(entry.actorUserId()) : null)
                .createdAt(LocalDateTime.now(clock))
                .build();
        InventoryTransaction saved = transactionRepository.save(transaction);

        log.info("Inventory materialId={} type={} change={} balance={} (allocationId={}, actor={})",
                material.getId(), entry.type(), entry.quantityChange(), balanceAfter,
                allocation != null ? allocation.getId() : null, entry.actorUserId());
        return saved;
    }

    private static void validate(Entry entry) {
        if (entry == null || entry.type() == null) {
            throw new IllegalArgumentException("Thiếu loại giao dịch kho");
        }
        if (entry.quantity() <= 0) {
            throw new AppException(ErrorCode.ERR_INVENTORY_INVALID_QUANTITY, "số lượng phải lớn hơn 0");
        }
        // Sai dấu là lỗi lập trình ở service gọi, không phải lỗi dữ liệu người dùng
        if (!entry.type().allowsChange(entry.quantity(), entry.quantityChange())) {
            throw new IllegalArgumentException("quantityChange " + entry.quantityChange() + " không hợp lệ với "
                    + entry.type() + " quantity " + entry.quantity());
        }
        if (entry.type().requiresAllocation() && entry.allocation() == null) {
            throw new IllegalArgumentException(entry.type() + " bắt buộc gắn dòng cấp phát");
        }
        if (entry.type() == InventoryTransactionType.ADJUST && trimToNull(entry.note()) == null) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "kiểm kê (ADJUST) bắt buộc ghi lý do");
        }
        if (entry.unitCost() != null && entry.unitCost().signum() < 0) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "giá nhập không được âm");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
