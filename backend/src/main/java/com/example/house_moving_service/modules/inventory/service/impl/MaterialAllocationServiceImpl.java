package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.inventory.dto.request.AllocateMaterialItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.AllocateMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.request.CancelAllocationRequest;
import com.example.house_moving_service.modules.inventory.dto.request.SettleMaterialItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.SettleMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialAllocationResponse;
import com.example.house_moving_service.modules.inventory.dto.response.ScheduleMaterialsResponse;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.mapper.MaterialAllocationMapper;
import com.example.house_moving_service.modules.inventory.repository.MaterialAllocationRepository;
import com.example.house_moving_service.modules.inventory.service.MaterialAllocationService;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSnapshot;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.service.MovingScheduleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialAllocationServiceImpl implements MaterialAllocationService {

    // Chuyến chưa xong và chưa hủy thì được xuất vật tư
    static final Set<MovingScheduleStatus> ALLOCATABLE = EnumSet.of(
            MovingScheduleStatus.PLANNED, MovingScheduleStatus.CONFIRMED,
            MovingScheduleStatus.RESCHEDULED, MovingScheduleStatus.IN_PROGRESS);

    private final MaterialAllocationRepository allocationRepository;
    private final InventoryLedger inventoryLedger;
    private final MovingScheduleLookupService movingScheduleLookupService;
    private final BookingWorkflowService bookingWorkflowService;
    private final UserLookupService userLookupService;
    private final MaterialAllocationCancellation allocationCancellation;
    private final MaterialAllocationMapper allocationMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ScheduleMaterialsResponse getScheduleMaterials(Integer scheduleId) {
        MovingScheduleSnapshot schedule = movingScheduleLookupService.getSnapshot(scheduleId);
        return allocationMapper.toScheduleResponse(schedule, allocationRepository.findAllByScheduleIdOrderByIdAsc(scheduleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleMaterialsResponse allocate(Integer scheduleId, AllocateMaterialsRequest request, Long actorUserId) {
        List<AllocateMaterialItemRequest> items = request.getItems().stream()
                .sorted(Comparator.comparing(AllocateMaterialItemRequest::getMaterialId))
                .toList();
        InventoryRequestChecks.requireDistinct(items.stream().map(AllocateMaterialItemRequest::getMaterialId).toList(),
                "materialId");
        items.forEach(item -> InventoryRequestChecks.requirePositiveQuantity(item.getQuantity()));

        // Thứ tự khóa: chuyến → dòng cấp phát → vật tư theo materialId tăng dần
        MovingScheduleSnapshot schedule = movingScheduleLookupService.lockForUpdate(scheduleId);
        requireScheduleStatus(schedule, ALLOCATABLE);
        Map<Integer, MaterialAllocation> openByMaterial = allocationRepository
                .findAllByScheduleIdAndStatusForUpdate(scheduleId, AllocationStatus.ALLOCATED).stream()
                .collect(Collectors.toMap(a -> a.getMaterial().getId(), Function.identity()));

        // Kiểm hết mọi món trước rồi mới ghi, để một món lỗi / thiếu tồn thì không ghi gì
        Map<Integer, Material> materials = new LinkedHashMap<>();
        for (AllocateMaterialItemRequest item : items) {
            Material material = inventoryLedger.lockMaterial(item.getMaterialId());
            InventoryRequestChecks.requireActive(material);
            inventoryLedger.requireAvailable(material, item.getQuantity());
            materials.put(material.getId(), material);
        }

        String note = InventoryRequestChecks.trimToNull(request.getNote());
        LocalDateTime now = LocalDateTime.now(clock);
        for (AllocateMaterialItemRequest item : items) {
            Material material = materials.get(item.getMaterialId());
            MaterialAllocation allocation = openByMaterial.get(material.getId());
            if (allocation == null) {
                allocation = MaterialAllocation.builder()
                        .booking(bookingWorkflowService.getReference(schedule.bookingId()))
                        .schedule(movingScheduleLookupService.getReference(scheduleId))
                        .material(material)
                        .quantityAllocated(item.getQuantity())
                        .status(AllocationStatus.ALLOCATED)
                        // Chụp giá lúc cấp; giá vật tư đổi sau không ảnh hưởng
                        .unitPrice(material.getUnitPrice())
                        .note(note)
                        .allocatedBy(actorUserId != null ? userLookupService.getUserReference(actorUserId) : null)
                        .allocatedAt(now)
                        .build();
            } else {
                // Xuất thêm: tăng dòng cũ, giữ unit_price cũ
                try {
                    allocation.setQuantityAllocated(Math.addExact(allocation.getQuantityAllocated(), item.getQuantity()));
                } catch (ArithmeticException ex) {
                    throw new AppException(ErrorCode.ERR_INVENTORY_INVALID_QUANTITY, "số lượng cấp phát vượt giới hạn");
                }
            }
            allocation = allocationRepository.save(allocation);

            inventoryLedger.post(InventoryLedger.Entry.builder()
                    .materialId(material.getId())
                    .type(InventoryTransactionType.EXPORT)
                    .quantity(item.getQuantity())
                    .quantityChange(-item.getQuantity())
                    .allocation(allocation)
                    .note(note)
                    .actorUserId(actorUserId)
                    .build());
            log.info("Allocation id: {} schedule id: {} material id: {} +{} (allocated {})",
                    allocation.getId(), scheduleId, material.getId(), item.getQuantity(), allocation.getQuantityAllocated());
        }

        return allocationMapper.toScheduleResponse(schedule, allocationRepository.findAllByScheduleIdOrderByIdAsc(scheduleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleMaterialsResponse settle(Integer scheduleId, SettleMaterialsRequest request, Long actorUserId) {
        InventoryRequestChecks.requireDistinct(
                request.getItems().stream().map(SettleMaterialItemRequest::getAllocationId).toList(), "allocationId");

        // Thứ tự khóa: chuyến → dòng cấp phát → vật tư theo materialId tăng dần (ledger) → booking sau cùng
        MovingScheduleSnapshot schedule = movingScheduleLookupService.lockForUpdate(scheduleId);
        requireScheduleStatus(schedule, EnumSet.of(MovingScheduleStatus.COMPLETED));
        List<MaterialAllocation> open = allocationRepository
                .findAllByScheduleIdAndStatusForUpdate(scheduleId, AllocationStatus.ALLOCATED);

        Map<Integer, SettleMaterialItemRequest> itemsById = request.getItems().stream()
                .collect(Collectors.toMap(SettleMaterialItemRequest::getAllocationId, Function.identity()));
        Set<Integer> openIds = open.stream().map(MaterialAllocation::getId).collect(Collectors.toSet());
        for (Integer allocationId : itemsById.keySet()) {
            if (!openIds.contains(allocationId)) {
                throw notOpenAllocation(allocationId, scheduleId);
            }
        }
        List<Integer> missing = open.stream().map(MaterialAllocation::getId)
                .filter(id -> !itemsById.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new AppException(ErrorCode.ERR_ALLOCATION_SETTLE_MISMATCH, "thiếu dòng cấp phát " + missing);
        }
        // Khóa vật tư (open đã theo materialId tăng dần) trước khi đọc bất kỳ field nào của vật tư: nếu nạp vật tư bằng
        // SELECT thường trước, lần khóa sau của ledger không đọc lại và tồn kho có thể là số cũ
        for (MaterialAllocation allocation : open) {
            inventoryLedger.lockMaterial(allocation.getMaterial().getId());
        }
        // Kiểm hết mọi dòng trước khi ghi giao dịch nào
        for (MaterialAllocation allocation : open) {
            validateSettleItem(allocation, itemsById.get(allocation.getId()));
        }

        String note = InventoryRequestChecks.trimToNull(request.getNote());
        LocalDateTime now = LocalDateTime.now(clock);
        BigDecimal total = BigDecimal.ZERO;
        // open đã theo materialId tăng dần nên ledger khóa vật tư đúng thứ tự
        for (MaterialAllocation allocation : open) {
            SettleMaterialItemRequest item = itemsById.get(allocation.getId());
            if (item.getQuantityReturned() > 0) {
                inventoryLedger.post(settleEntry(allocation, InventoryTransactionType.RETURN,
                        item.getQuantityReturned(), item.getQuantityReturned(), note, actorUserId));
            }
            if (item.getQuantityDamaged() > 0) {
                // Hư / mất trong chuyến đã trừ tồn lúc EXPORT: chỉ ghi nhận, không trừ thêm
                inventoryLedger.post(settleEntry(allocation, InventoryTransactionType.DAMAGED,
                        item.getQuantityDamaged(), 0, note, actorUserId));
            }

            BigDecimal charged = allocation.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantityUsed()))
                    .setScale(2, RoundingMode.HALF_UP);
            allocation.setQuantityUsed(item.getQuantityUsed());
            allocation.setQuantityReturned(item.getQuantityReturned());
            allocation.setQuantityDamaged(item.getQuantityDamaged());
            allocation.setChargedAmount(charged);
            allocation.setChargedAt(now);
            allocation.setSettledBy(actorUserId != null ? userLookupService.getUserReference(actorUserId) : null);
            allocation.setSettledAt(now);
            allocation.setStatus(AllocationStatus.SETTLED);
            allocationRepository.save(allocation);
            total = total.add(charged);
        }

        // Tiền vật tư cộng vào đơn đúng một lần, sau khi mọi dòng đã chốt
        if (total.signum() > 0) {
            bookingWorkflowService.addToTotalAmount(schedule.bookingId(), total);
        }
        log.info("Schedule id: {} materials settled ({} allocations, charged {}) by user id: {}",
                scheduleId, open.size(), total, actorUserId);
        return allocationMapper.toScheduleResponse(schedule, allocationRepository.findAllByScheduleIdOrderByIdAsc(scheduleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialAllocationResponse cancel(Integer allocationId, CancelAllocationRequest request, Long actorUserId) {
        // Thứ tự khóa: chuyến → dòng cấp phát → vật tư
        Integer scheduleId = allocationRepository.findScheduleIdById(allocationId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_ALLOCATION_NOT_FOUND, allocationId));
        MovingScheduleSnapshot schedule = movingScheduleLookupService.lockForUpdate(scheduleId);
        MaterialAllocation allocation = allocationRepository.findByIdForUpdate(allocationId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_ALLOCATION_NOT_FOUND, allocationId));
        if (allocation.getStatus() != AllocationStatus.ALLOCATED) {
            throw new AppException(ErrorCode.ERR_ALLOCATION_INVALID_STATUS);
        }
        requireScheduleStatus(schedule, MaterialAllocationCancellation.CANCELLABLE);

        String note = request != null ? InventoryRequestChecks.trimToNull(request.getNote()) : null;
        return allocationMapper.toResponse(allocationCancellation.cancel(allocation, actorUserId, note));
    }

    private AppException notOpenAllocation(Integer allocationId, Integer scheduleId) {
        return allocationRepository.findById(allocationId)
                .filter(a -> a.getSchedule().getId().equals(scheduleId))
                .map(a -> new AppException(ErrorCode.ERR_ALLOCATION_INVALID_STATUS))
                .orElseGet(() -> new AppException(ErrorCode.ERR_ALLOCATION_NOT_FOUND, allocationId));
    }

    private static void validateSettleItem(MaterialAllocation allocation, SettleMaterialItemRequest item) {
        long sum = (long) item.getQuantityUsed() + item.getQuantityReturned() + item.getQuantityDamaged();
        if (item.getQuantityUsed() < 0 || item.getQuantityReturned() < 0 || item.getQuantityDamaged() < 0
                || sum != allocation.getQuantityAllocated()) {
            throw new AppException(ErrorCode.ERR_ALLOCATION_SETTLE_MISMATCH, "dòng " + allocation.getId()
                    + ": đã dùng + hoàn + hư (" + sum + ") phải bằng số đã cấp (" + allocation.getQuantityAllocated() + ")");
        }
        if (allocation.getMaterial().getCategory() == MaterialCategory.TOOL && item.getQuantityUsed() > 0) {
            throw new AppException(ErrorCode.ERR_ALLOCATION_TOOL_USED, allocation.getMaterial().getCode());
        }
    }

    private static InventoryLedger.Entry settleEntry(MaterialAllocation allocation, InventoryTransactionType type,
                                                     int quantity, int change, String note, Long actorUserId) {
        return InventoryLedger.Entry.builder()
                .materialId(allocation.getMaterial().getId())
                .type(type)
                .quantity(quantity)
                .quantityChange(change)
                .allocation(allocation)
                .note(note)
                .actorUserId(actorUserId)
                .build();
    }

    static void requireScheduleStatus(MovingScheduleSnapshot schedule, Set<MovingScheduleStatus> allowed) {
        if (!allowed.contains(schedule.status())) {
            throw new AppException(ErrorCode.ERR_ALLOCATION_SCHEDULE_INVALID_STATUS, schedule.status());
        }
    }
}
