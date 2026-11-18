package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.repository.InventoryTransactionRepository;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryLedgerTest {

    private static final int MATERIAL_ID = 11;
    private static final long ACTOR_ID = 5L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private UserLookupService userLookupService;

    private InventoryLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new InventoryLedger(materialRepository, transactionRepository, userLookupService, CLOCK);
        lenient().when(userLookupService.getUserReference(anyLong()))
                .thenAnswer(inv -> User.builder().id(inv.getArgument(0)).build());
        lenient().when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Material material(int onHand) {
        return Material.builder().id(MATERIAL_ID).code("BOX-L").name("Thùng carton lớn")
                .category(MaterialCategory.CONSUMABLE).unit("cái").quantityOnHand(onHand).build();
    }

    private void givenMaterial(Material m) {
        when(materialRepository.findByIdForUpdate(MATERIAL_ID)).thenReturn(Optional.of(m));
    }

    private static MaterialAllocation allocation() {
        Booking booking = Booking.builder().id(7).build();
        MovingSchedule schedule = MovingSchedule.builder().id(50).booking(booking).build();
        return MaterialAllocation.builder().id(99).booking(booking).schedule(schedule).build();
    }

    @Test
    @DisplayName("IMPORT cộng tồn, ghi giao dịch có balance_after, unit_cost, người tạo và giờ theo Clock")
    void import_increasesStock() {
        Material m = material(10);
        givenMaterial(m);

        InventoryTransaction tx = ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.IMPORT).quantity(5).quantityChange(5)
                .unitCost(new BigDecimal("12000")).note("  nhập lô 1  ").actorUserId(ACTOR_ID).build());

        assertThat(m.getQuantityOnHand()).isEqualTo(15);
        assertThat(tx.getBalanceAfter()).isEqualTo(15);
        assertThat(tx.getQuantity()).isEqualTo(5);
        assertThat(tx.getQuantityChange()).isEqualTo(5);
        assertThat(tx.getUnitCost()).isEqualByComparingTo("12000");
        assertThat(tx.getNote()).isEqualTo("nhập lô 1");
        assertThat(tx.getCreatedBy().getId()).isEqualTo(ACTOR_ID);
        assertThat(tx.getCreatedAt()).isEqualTo(NOW);
        assertThat(tx.getBooking()).isNull();
        verify(materialRepository).save(m);
        verify(transactionRepository).save(tx);
    }

    @Test
    @DisplayName("EXPORT trừ tồn, gắn allocation và lấy booking / schedule từ dòng cấp phát")
    void export_decreasesStock() {
        Material m = material(10);
        givenMaterial(m);
        MaterialAllocation allocation = allocation();

        InventoryTransaction tx = ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.EXPORT).quantity(4).quantityChange(-4)
                .allocation(allocation).actorUserId(ACTOR_ID).build());

        assertThat(m.getQuantityOnHand()).isEqualTo(6);
        assertThat(tx.getBalanceAfter()).isEqualTo(6);
        assertThat(tx.getQuantity()).isEqualTo(4);
        assertThat(tx.getQuantityChange()).isEqualTo(-4);
        assertThat(tx.getAllocation()).isSameAs(allocation);
        assertThat(tx.getBooking().getId()).isEqualTo(7);
        assertThat(tx.getSchedule().getId()).isEqualTo(50);
    }

    @Test
    @DisplayName("Trừ đúng bằng tồn còn lại → tồn 0, hợp lệ")
    void export_toZero() {
        Material m = material(3);
        givenMaterial(m);

        InventoryTransaction tx = ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.DAMAGED).quantity(3).quantityChange(-3)
                .note("ướt hỏng trong kho").build());

        assertThat(m.getQuantityOnHand()).isZero();
        assertThat(tx.getBalanceAfter()).isZero();
        assertThat(tx.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("Trừ vượt tồn → ERR_INVENTORY_INSUFFICIENT kèm details, không đổi tồn, không ghi giao dịch")
    void export_insufficient() {
        Material m = material(2);
        givenMaterial(m);

        assertThatThrownBy(() -> ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.EXPORT).quantity(5).quantityChange(-5)
                .allocation(allocation()).build()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.ERR_INVENTORY_INSUFFICIENT);
                    assertThat(app.getDetails()).isEqualTo(Map.of("materialId", MATERIAL_ID, "onHand", 2, "requested", 5));
                });

        assertThat(m.getQuantityOnHand()).isEqualTo(2);
        verify(materialRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("DAMAGED với change 0 (hư ngoài chuyến) không đổi tồn nhưng vẫn ghi giao dịch")
    void damaged_zeroChange_keepsStock() {
        Material m = material(8);
        givenMaterial(m);

        InventoryTransaction tx = ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.DAMAGED).quantity(2).quantityChange(0)
                .allocation(allocation()).build());

        assertThat(m.getQuantityOnHand()).isEqualTo(8);
        assertThat(tx.getBalanceAfter()).isEqualTo(8);
        assertThat(tx.getQuantity()).isEqualTo(2);
        assertThat(tx.getQuantityChange()).isZero();
        verify(materialRepository, never()).save(any());
        verify(transactionRepository).save(tx);
    }

    @Test
    @DisplayName("ADJUST giảm ghi đúng balance_after")
    void adjust_decrease() {
        Material m = material(10);
        givenMaterial(m);

        InventoryTransaction tx = ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.ADJUST).quantity(3).quantityChange(-3)
                .note("kiểm kê thiếu").build());

        assertThat(m.getQuantityOnHand()).isEqualTo(7);
        assertThat(tx.getBalanceAfter()).isEqualTo(7);
    }

    @Test
    @DisplayName("ADJUST không có lý do → ERR_INVALID_INPUT, không khóa / ghi gì")
    void adjust_requiresNote() {
        assertThatThrownBy(() -> ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.ADJUST).quantity(1).quantityChange(1)
                .note("   ").build()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ERR_INVALID_INPUT);
        verify(materialRepository, never()).findByIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Số lượng ≤ 0 → ERR_INVENTORY_INVALID_QUANTITY")
    void quantity_mustBePositive() {
        assertThatThrownBy(() -> ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.IMPORT).quantity(0).quantityChange(0).build()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ERR_INVENTORY_INVALID_QUANTITY);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sai dấu quantity_change hoặc EXPORT thiếu allocation → lỗi lập trình, không ghi gì")
    void invalidEntry_rejected() {
        assertThatThrownBy(() -> ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.IMPORT).quantity(2).quantityChange(-2).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.EXPORT).quantity(2).quantityChange(-2).build()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vật tư không tồn tại → ERR_MATERIAL_NOT_FOUND")
    void materialNotFound() {
        when(materialRepository.findByIdForUpdate(MATERIAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.IMPORT).quantity(1).quantityChange(1).build()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ERR_MATERIAL_NOT_FOUND);
    }

    @Test
    @DisplayName("Cộng tràn int → ERR_INVENTORY_INVALID_QUANTITY, không đổi tồn")
    void overflow_rejected() {
        Material m = material(Integer.MAX_VALUE);
        givenMaterial(m);

        assertThatThrownBy(() -> ledger.post(InventoryLedger.Entry.builder()
                .materialId(MATERIAL_ID).type(InventoryTransactionType.IMPORT).quantity(1).quantityChange(1).build()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ERR_INVENTORY_INVALID_QUANTITY);
        assertThat(m.getQuantityOnHand()).isEqualTo(Integer.MAX_VALUE);
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository, never()).save(captor.capture());
    }
}
