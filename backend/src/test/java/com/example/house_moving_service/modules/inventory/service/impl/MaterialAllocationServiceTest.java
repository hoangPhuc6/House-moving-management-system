package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.inventory.dto.request.AllocateMaterialItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.AllocateMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.request.CancelAllocationRequest;
import com.example.house_moving_service.modules.inventory.dto.request.SettleMaterialItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.SettleMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialAllocationResponse;
import com.example.house_moving_service.modules.inventory.dto.response.ScheduleMaterialsResponse;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.mapper.MaterialAllocationMapper;
import com.example.house_moving_service.modules.inventory.repository.InventoryTransactionRepository;
import com.example.house_moving_service.modules.inventory.repository.MaterialAllocationRepository;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSnapshot;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.service.MovingScheduleLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dùng InventoryLedger thật (repository mock) để kiểm đúng số tồn và giao dịch được ghi. */
@ExtendWith(MockitoExtension.class)
class MaterialAllocationServiceTest {

    static final int SCHEDULE_ID = 50;
    static final int BOOKING_ID = 7;
    static final long ACTOR_ID = 5L;
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 9, 0);
    static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    MaterialAllocationRepository allocationRepository;
    @Mock
    MaterialRepository materialRepository;
    @Mock
    InventoryTransactionRepository transactionRepository;
    @Mock
    MovingScheduleLookupService movingScheduleLookupService;
    @Mock
    BookingWorkflowService bookingWorkflowService;
    @Mock
    UserLookupService userLookupService;

    MaterialAllocationServiceImpl service;
    final Booking booking = Booking.builder().id(BOOKING_ID).bookingCode("BK-001").build();
    final MovingSchedule schedule = MovingSchedule.builder().id(SCHEDULE_ID).booking(booking).build();
    // Dòng cấp phát đã lưu (giả lập bảng) và giao dịch đã ghi
    final List<MaterialAllocation> allocations = new ArrayList<>();
    final List<InventoryTransaction> transactions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        InventoryLedger ledger = new InventoryLedger(materialRepository, transactionRepository, userLookupService, CLOCK);
        service = new MaterialAllocationServiceImpl(allocationRepository, ledger, movingScheduleLookupService,
                bookingWorkflowService, userLookupService, new MaterialAllocationCancellation(allocationRepository, ledger),
                new MaterialAllocationMapper(), CLOCK);
        lenient().when(userLookupService.getUserReference(anyLong()))
                .thenAnswer(inv -> User.builder().id(inv.getArgument(0)).email("dispatcher@example.com").build());
        lenient().when(bookingWorkflowService.getReference(BOOKING_ID)).thenReturn(booking);
        lenient().when(movingScheduleLookupService.getReference(SCHEDULE_ID)).thenReturn(schedule);
        lenient().when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(inv -> {
            transactions.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(allocationRepository.save(any(MaterialAllocation.class))).thenAnswer(inv -> {
            MaterialAllocation a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(900 + allocations.size());
                allocations.add(a);
            }
            return a;
        });
        lenient().when(allocationRepository.findAllByScheduleIdOrderByIdAsc(SCHEDULE_ID)).thenAnswer(inv -> allocations);
        lenient().when(allocationRepository.findAllByScheduleIdAndStatusForUpdate(anyInt(), any()))
                .thenAnswer(inv -> allocations.stream().filter(a -> a.getStatus() == inv.getArgument(1)).toList());
    }

    void givenSchedule(MovingScheduleStatus status) {
        MovingScheduleSnapshot snapshot = MovingScheduleSnapshot.builder()
                .id(SCHEDULE_ID).bookingId(BOOKING_ID).status(status).build();
        lenient().when(movingScheduleLookupService.lockForUpdate(SCHEDULE_ID)).thenReturn(snapshot);
        lenient().when(movingScheduleLookupService.getSnapshot(SCHEDULE_ID)).thenReturn(snapshot);
    }

    Material givenMaterial(int id, MaterialCategory category, int onHand, String unitPrice) {
        Material m = Material.builder().id(id).code("M-" + id).name("Vật tư " + id).category(category).unit("cái")
                .quantityOnHand(onHand).unitPrice(new BigDecimal(unitPrice)).isActive(true).build();
        lenient().when(materialRepository.findByIdForUpdate(id)).thenReturn(Optional.of(m));
        return m;
    }

    MaterialAllocation existingAllocation(Material material, int allocated, String unitPrice) {
        MaterialAllocation a = MaterialAllocation.builder().id(800 + allocations.size()).booking(booking)
                .schedule(schedule).material(material).quantityAllocated(allocated).status(AllocationStatus.ALLOCATED)
                .unitPrice(new BigDecimal(unitPrice)).allocatedAt(NOW.minusDays(1)).build();
        allocations.add(a);
        return a;
    }

    static AllocateMaterialsRequest allocateRequest(AllocateMaterialItemRequest... items) {
        return AllocateMaterialsRequest.builder().items(List.of(items)).note("Chuẩn bị chuyến").build();
    }

    static AllocateMaterialItemRequest item(int materialId, int quantity) {
        return AllocateMaterialItemRequest.builder().materialId(materialId).quantity(quantity).build();
    }

    static void assertError(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("Xuất mới: tạo dòng ALLOCATED chụp giá, ghi EXPORT trừ tồn, booking lấy từ chuyến")
    void allocate_new() {
        givenSchedule(MovingScheduleStatus.CONFIRMED);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 100, "15000");
        Material trolley = givenMaterial(2, MaterialCategory.TOOL, 4, "0");

        ScheduleMaterialsResponse response = service.allocate(SCHEDULE_ID,
                allocateRequest(item(2, 1), item(1, 20)), ACTOR_ID);

        assertThat(box.getQuantityOnHand()).isEqualTo(80);
        assertThat(trolley.getQuantityOnHand()).isEqualTo(3);
        assertThat(allocations).hasSize(2);
        MaterialAllocation boxAllocation = allocations.get(0);
        assertThat(boxAllocation.getMaterial()).isSameAs(box);
        assertThat(boxAllocation.getBooking()).isSameAs(booking);
        assertThat(boxAllocation.getSchedule()).isSameAs(schedule);
        assertThat(boxAllocation.getQuantityAllocated()).isEqualTo(20);
        assertThat(boxAllocation.getUnitPrice()).isEqualByComparingTo("15000");
        assertThat(boxAllocation.getAllocatedBy().getId()).isEqualTo(ACTOR_ID);
        assertThat(boxAllocation.getAllocatedAt()).isEqualTo(NOW);

        assertThat(transactions).hasSize(2).allSatisfy(tx -> {
            assertThat(tx.getTransactionType()).isEqualTo(InventoryTransactionType.EXPORT);
            assertThat(tx.getBooking()).isSameAs(booking);
            assertThat(tx.getSchedule()).isSameAs(schedule);
        });
        assertThat(transactions.get(0).getAllocation()).isSameAs(boxAllocation);
        assertThat(transactions.get(0).getQuantityChange()).isEqualTo(-20);
        assertThat(transactions.get(0).getBalanceAfter()).isEqualTo(80);

        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.allocations()).extracting(MaterialAllocationResponse::materialId).containsExactly(1, 2);
        // Chỉ CONSUMABLE tính tiền dự kiến: 20 × 15000
        assertThat(response.estimatedTotal()).isEqualByComparingTo("300000");
        assertThat(response.chargedTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Xuất thêm cùng món: tăng dòng ALLOCATED cũ, giữ giá cũ dù giá vật tư đã đổi")
    void allocate_extraKeepsOldPrice() {
        givenSchedule(MovingScheduleStatus.IN_PROGRESS);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 50, "18000");
        MaterialAllocation existing = existingAllocation(box, 10, "15000");

        ScheduleMaterialsResponse response = service.allocate(SCHEDULE_ID, allocateRequest(item(1, 5)), ACTOR_ID);

        assertThat(allocations).containsExactly(existing);
        assertThat(existing.getQuantityAllocated()).isEqualTo(15);
        assertThat(existing.getUnitPrice()).isEqualByComparingTo("15000");
        assertThat(box.getQuantityOnHand()).isEqualTo(45);
        assertThat(transactions).singleElement().satisfies(tx -> {
            assertThat(tx.getQuantity()).isEqualTo(5);
            assertThat(tx.getAllocation()).isSameAs(existing);
        });
        assertThat(response.estimatedTotal()).isEqualByComparingTo("225000");
    }

    @Test
    @DisplayName("Chuyến COMPLETED → ERR_ALLOCATION_SCHEDULE_INVALID_STATUS, không khóa vật tư, không ghi gì")
    void allocate_completedScheduleBlocked() {
        givenSchedule(MovingScheduleStatus.COMPLETED);

        Throwable thrown = catchThrowable(
                () -> service.allocate(SCHEDULE_ID, allocateRequest(item(1, 5)), ACTOR_ID));

        assertError(thrown, ErrorCode.ERR_ALLOCATION_SCHEDULE_INVALID_STATUS);
        verify(materialRepository, never()).findByIdForUpdate(anyInt());
        verify(allocationRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Một món thiếu tồn → ERR_INVENTORY_INSUFFICIENT, không tạo dòng cấp phát, không ghi giao dịch")
    void allocate_insufficientWritesNothing() {
        givenSchedule(MovingScheduleStatus.PLANNED);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 100, "15000");
        Material tape = givenMaterial(2, MaterialCategory.CONSUMABLE, 3, "20000");

        assertThatThrownBy(() -> service.allocate(SCHEDULE_ID, allocateRequest(item(1, 10), item(2, 5)), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVENTORY_INSUFFICIENT);
                    assertThat(ex.getDetails()).isEqualTo(Map.of("materialId", 2, "onHand", 3, "requested", 5));
                });

        assertThat(box.getQuantityOnHand()).isEqualTo(100);
        assertThat(tape.getQuantityOnHand()).isEqualTo(3);
        verify(allocationRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vật tư đang tắt → ERR_MATERIAL_INACTIVE")
    void allocate_inactiveMaterial() {
        givenSchedule(MovingScheduleStatus.CONFIRMED);
        givenMaterial(1, MaterialCategory.CONSUMABLE, 100, "15000").setIsActive(false);

        assertError(catchThrowable(
                () -> service.allocate(SCHEDULE_ID, allocateRequest(item(1, 1)), ACTOR_ID)),
                ErrorCode.ERR_MATERIAL_INACTIVE);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Trùng materialId → ERR_INVALID_INPUT; quantity ≤ 0 → ERR_INVENTORY_INVALID_QUANTITY")
    void allocate_invalidItems() {
        assertError(catchThrowable(
                () -> service.allocate(SCHEDULE_ID, allocateRequest(item(1, 1), item(1, 2)), ACTOR_ID)),
                ErrorCode.ERR_INVALID_INPUT);
        assertError(catchThrowable(
                () -> service.allocate(SCHEDULE_ID, allocateRequest(item(1, -1)), ACTOR_ID)),
                ErrorCode.ERR_INVENTORY_INVALID_QUANTITY);
        verify(movingScheduleLookupService, never()).lockForUpdate(anyInt());
    }

    // ---------- Chốt chuyến ----------

    static SettleMaterialItemRequest settleItem(MaterialAllocation a, int used, int returned, int damaged) {
        return SettleMaterialItemRequest.builder().allocationId(a.getId())
                .quantityUsed(used).quantityReturned(returned).quantityDamaged(damaged).build();
    }

    static SettleMaterialsRequest settleRequest(SettleMaterialItemRequest... items) {
        return SettleMaterialsRequest.builder().items(List.of(items)).note("Chốt sau chuyến").build();
    }

    @Test
    @DisplayName("Chốt đúng: RETURN cộng tồn, DAMAGED change 0, tính tiền, gọi addToTotalAmount đúng 1 lần với tổng đúng")
    void settle_success() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 80, "18000");
        Material trolley = givenMaterial(2, MaterialCategory.TOOL, 3, "0");
        MaterialAllocation boxAllocation = existingAllocation(box, 20, "15000");
        MaterialAllocation trolleyAllocation = existingAllocation(trolley, 2, "0");

        ScheduleMaterialsResponse response = service.settle(SCHEDULE_ID, settleRequest(
                settleItem(trolleyAllocation, 0, 1, 1), settleItem(boxAllocation, 15, 3, 2)), ACTOR_ID);

        assertThat(box.getQuantityOnHand()).isEqualTo(83);
        assertThat(trolley.getQuantityOnHand()).isEqualTo(4);
        assertThat(transactions).extracting(InventoryTransaction::getTransactionType, InventoryTransaction::getQuantity,
                        InventoryTransaction::getQuantityChange, InventoryTransaction::getBalanceAfter)
                .containsExactly(
                        tuple(InventoryTransactionType.RETURN, 3, 3, 83),
                        tuple(InventoryTransactionType.DAMAGED, 2, 0, 83),
                        tuple(InventoryTransactionType.RETURN, 1, 1, 4),
                        tuple(InventoryTransactionType.DAMAGED, 1, 0, 4));
        assertThat(transactions).allSatisfy(tx -> assertThat(tx.getAllocation()).isNotNull());

        assertThat(boxAllocation.getStatus()).isEqualTo(AllocationStatus.SETTLED);
        assertThat(boxAllocation.getQuantityUsed()).isEqualTo(15);
        assertThat(boxAllocation.getQuantityReturned()).isEqualTo(3);
        assertThat(boxAllocation.getQuantityDamaged()).isEqualTo(2);
        // Tính theo giá chụp lúc cấp (15000), không theo giá hiện tại (18000)
        assertThat(boxAllocation.getChargedAmount()).isEqualByComparingTo("225000");
        assertThat(boxAllocation.getChargedAt()).isEqualTo(NOW);
        assertThat(boxAllocation.getSettledAt()).isEqualTo(NOW);
        assertThat(boxAllocation.getSettledBy().getId()).isEqualTo(ACTOR_ID);
        assertThat(trolleyAllocation.getStatus()).isEqualTo(AllocationStatus.SETTLED);
        assertThat(trolleyAllocation.getChargedAmount()).isEqualByComparingTo("0");

        verify(bookingWorkflowService, times(1)).addToTotalAmount(eq(BOOKING_ID),
                argThat(amount -> amount.compareTo(new BigDecimal("225000")) == 0));
        assertThat(response.chargedTotal()).isEqualByComparingTo("225000");
        assertThat(response.estimatedTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Chốt thiếu dòng ALLOCATED → ERR_ALLOCATION_SETTLE_MISMATCH, không ghi gì")
    void settle_missingAllocation() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        MaterialAllocation a = existingAllocation(givenMaterial(1, MaterialCategory.CONSUMABLE, 80, "15000"), 20, "15000");
        existingAllocation(givenMaterial(2, MaterialCategory.TOOL, 3, "0"), 2, "0");

        assertError(catchThrowable(() -> service.settle(SCHEDULE_ID, settleRequest(settleItem(a, 20, 0, 0)), ACTOR_ID)),
                ErrorCode.ERR_ALLOCATION_SETTLE_MISMATCH);
        assertThat(a.getStatus()).isEqualTo(AllocationStatus.ALLOCATED);
        verify(transactionRepository, never()).save(any());
        verify(bookingWorkflowService, never()).addToTotalAmount(anyInt(), any());
    }

    @Test
    @DisplayName("Chốt lệch số (used + returned + damaged ≠ allocated) → ERR_ALLOCATION_SETTLE_MISMATCH")
    void settle_quantityMismatch() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        MaterialAllocation a = existingAllocation(givenMaterial(1, MaterialCategory.CONSUMABLE, 80, "15000"), 20, "15000");

        assertError(catchThrowable(() -> service.settle(SCHEDULE_ID, settleRequest(settleItem(a, 10, 5, 4)), ACTOR_ID)),
                ErrorCode.ERR_ALLOCATION_SETTLE_MISMATCH);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("TOOL có quantityUsed > 0 → ERR_ALLOCATION_TOOL_USED")
    void settle_toolUsed() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        MaterialAllocation a = existingAllocation(givenMaterial(2, MaterialCategory.TOOL, 3, "0"), 2, "0");

        assertError(catchThrowable(() -> service.settle(SCHEDULE_ID, settleRequest(settleItem(a, 1, 1, 0)), ACTOR_ID)),
                ErrorCode.ERR_ALLOCATION_TOOL_USED);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chuyến chưa COMPLETED → ERR_ALLOCATION_SCHEDULE_INVALID_STATUS")
    void settle_scheduleNotCompleted() {
        givenSchedule(MovingScheduleStatus.IN_PROGRESS);
        MaterialAllocation a = existingAllocation(givenMaterial(1, MaterialCategory.CONSUMABLE, 80, "15000"), 20, "15000");

        assertError(catchThrowable(() -> service.settle(SCHEDULE_ID, settleRequest(settleItem(a, 20, 0, 0)), ACTOR_ID)),
                ErrorCode.ERR_ALLOCATION_SCHEDULE_INVALID_STATUS);
        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tổng tiền 0 (chỉ hoàn kho) → không gọi addToTotalAmount")
    void settle_zeroTotalSkipsBooking() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 80, "15000");
        MaterialAllocation a = existingAllocation(box, 20, "15000");

        service.settle(SCHEDULE_ID, settleRequest(settleItem(a, 0, 20, 0)), ACTOR_ID);

        assertThat(box.getQuantityOnHand()).isEqualTo(100);
        assertThat(a.getStatus()).isEqualTo(AllocationStatus.SETTLED);
        assertThat(a.getChargedAmount()).isEqualByComparingTo("0");
        verify(bookingWorkflowService, never()).addToTotalAmount(anyInt(), any());
    }

    @Test
    @DisplayName("Chốt dòng không thuộc chuyến → ERR_ALLOCATION_NOT_FOUND")
    void settle_foreignAllocation() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        MaterialAllocation a = existingAllocation(givenMaterial(1, MaterialCategory.CONSUMABLE, 80, "15000"), 20, "15000");
        when(allocationRepository.findById(777)).thenReturn(Optional.empty());

        assertError(catchThrowable(() -> service.settle(SCHEDULE_ID, settleRequest(settleItem(a, 20, 0, 0),
                        SettleMaterialItemRequest.builder().allocationId(777).quantityUsed(1).quantityReturned(0)
                                .quantityDamaged(0).build()), ACTOR_ID)),
                ErrorCode.ERR_ALLOCATION_NOT_FOUND);
    }

    // ---------- Hủy cấp phát ----------

    void givenLockable(MaterialAllocation a) {
        lenient().when(allocationRepository.findScheduleIdById(a.getId())).thenReturn(Optional.of(SCHEDULE_ID));
        lenient().when(allocationRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
    }

    @Test
    @DisplayName("Hủy cấp phát: RETURN đủ số đã cấp, dòng CANCELLED, không tính tiền")
    void cancel_returnsFullQuantity() {
        givenSchedule(MovingScheduleStatus.PLANNED);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 90, "15000");
        MaterialAllocation a = existingAllocation(box, 10, "15000");
        givenLockable(a);

        MaterialAllocationResponse response = service.cancel(a.getId(),
                CancelAllocationRequest.builder().note("Khách đổi lịch").build(), ACTOR_ID);

        assertThat(box.getQuantityOnHand()).isEqualTo(100);
        assertThat(a.getStatus()).isEqualTo(AllocationStatus.CANCELLED);
        assertThat(a.getQuantityReturned()).isEqualTo(10);
        assertThat(a.getChargedAmount()).isNull();
        assertThat(transactions).singleElement().satisfies(tx -> {
            assertThat(tx.getTransactionType()).isEqualTo(InventoryTransactionType.RETURN);
            assertThat(tx.getQuantityChange()).isEqualTo(10);
            assertThat(tx.getAllocation()).isSameAs(a);
            assertThat(tx.getNote()).isEqualTo("Khách đổi lịch");
        });
        assertThat(response.status()).isEqualTo(AllocationStatus.CANCELLED);
        verify(bookingWorkflowService, never()).addToTotalAmount(anyInt(), any());
    }

    @Test
    @DisplayName("Hủy dòng đã SETTLED → ERR_ALLOCATION_INVALID_STATUS, không hoàn kho")
    void cancel_settledBlocked() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 90, "15000");
        MaterialAllocation a = existingAllocation(box, 10, "15000");
        a.setStatus(AllocationStatus.SETTLED);
        givenLockable(a);

        assertError(catchThrowable(() -> service.cancel(a.getId(), null, ACTOR_ID)),
                ErrorCode.ERR_ALLOCATION_INVALID_STATUS);
        assertThat(box.getQuantityOnHand()).isEqualTo(90);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy cấp phát khi chuyến IN_PROGRESS → ERR_ALLOCATION_SCHEDULE_INVALID_STATUS")
    void cancel_inProgressBlocked() {
        givenSchedule(MovingScheduleStatus.IN_PROGRESS);
        MaterialAllocation a = existingAllocation(givenMaterial(1, MaterialCategory.CONSUMABLE, 90, "15000"), 10, "15000");
        givenLockable(a);

        assertError(catchThrowable(() -> service.cancel(a.getId(), null, ACTOR_ID)),
                ErrorCode.ERR_ALLOCATION_SCHEDULE_INVALID_STATUS);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy dòng không tồn tại → ERR_ALLOCATION_NOT_FOUND")
    void cancel_notFound() {
        when(allocationRepository.findScheduleIdById(404)).thenReturn(Optional.empty());

        assertError(catchThrowable(() -> service.cancel(404, null, ACTOR_ID)), ErrorCode.ERR_ALLOCATION_NOT_FOUND);
    }

    @Test
    @DisplayName("Xem cấp phát: trả mọi dòng (mọi trạng thái) theo thứ tự, tổng dự kiến chỉ tính CONSUMABLE ALLOCATED, "
            + "không khóa và không ghi gì")
    void getScheduleMaterials_listsAllStatuses() {
        givenSchedule(MovingScheduleStatus.COMPLETED);
        Material box = givenMaterial(1, MaterialCategory.CONSUMABLE, 80, "15000");
        Material tape = givenMaterial(2, MaterialCategory.CONSUMABLE, 30, "20000");
        Material trolley = givenMaterial(3, MaterialCategory.TOOL, 3, "50000");
        Material wrap = givenMaterial(4, MaterialCategory.CONSUMABLE, 10, "30000");
        existingAllocation(box, 20, "15000");
        existingAllocation(trolley, 1, "50000");
        MaterialAllocation settled = existingAllocation(tape, 5, "20000");
        settled.setStatus(AllocationStatus.SETTLED);
        settled.setQuantityUsed(4);
        settled.setQuantityReturned(1);
        settled.setChargedAmount(new BigDecimal("80000"));
        MaterialAllocation cancelled = existingAllocation(wrap, 2, "30000");
        cancelled.setStatus(AllocationStatus.CANCELLED);

        ScheduleMaterialsResponse response = service.getScheduleMaterials(SCHEDULE_ID);

        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.scheduleStatus()).isEqualTo(MovingScheduleStatus.COMPLETED);
        assertThat(response.allocations())
                .extracting(MaterialAllocationResponse::materialId, MaterialAllocationResponse::status)
                .containsExactly(
                        tuple(1, AllocationStatus.ALLOCATED),
                        tuple(3, AllocationStatus.ALLOCATED),
                        tuple(2, AllocationStatus.SETTLED),
                        tuple(4, AllocationStatus.CANCELLED));
        // Dự kiến: chỉ carton (CONSUMABLE, ALLOCATED) 20 × 15000; TOOL / SETTLED / CANCELLED không tính
        assertThat(response.estimatedTotal()).isEqualByComparingTo("300000");
        assertThat(response.chargedTotal()).isEqualByComparingTo("80000");

        verify(movingScheduleLookupService, never()).lockForUpdate(anyInt());
        verify(allocationRepository, never()).findAllByScheduleIdAndStatusForUpdate(anyInt(), any());
        verify(allocationRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xem cấp phát chuyến chưa có dòng nào → danh sách rỗng, tổng 0")
    void getScheduleMaterials_empty() {
        givenSchedule(MovingScheduleStatus.PLANNED);

        ScheduleMaterialsResponse response = service.getScheduleMaterials(SCHEDULE_ID);

        assertThat(response.allocations()).isEmpty();
        assertThat(response.estimatedTotal()).isEqualByComparingTo("0");
        assertThat(response.chargedTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Xem cấp phát chuyến không tồn tại → ERR_MOVING_SCHEDULE_NOT_FOUND, không truy vấn dòng cấp phát")
    void getScheduleMaterials_scheduleNotFound() {
        when(movingScheduleLookupService.getSnapshot(404))
                .thenThrow(new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, 404));

        assertError(catchThrowable(() -> service.getScheduleMaterials(404)), ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND);
        verify(allocationRepository, never()).findAllByScheduleIdOrderByIdAsc(anyInt());
    }
}
