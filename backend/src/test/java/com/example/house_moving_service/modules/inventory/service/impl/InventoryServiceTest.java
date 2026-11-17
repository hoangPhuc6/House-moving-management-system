package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.mapper.InventoryTransactionMapper;
import com.example.house_moving_service.modules.inventory.repository.InventoryTransactionRepository;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dùng InventoryLedger thật (repository mock) để kiểm đúng số tồn sau giao dịch. */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final long ACTOR_ID = 5L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private UserLookupService userLookupService;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        InventoryLedger ledger = new InventoryLedger(materialRepository, transactionRepository, userLookupService, CLOCK);
        service = new InventoryServiceImpl(ledger, new InventoryTransactionMapper());
        lenient().when(userLookupService.getUserReference(anyLong()))
                .thenAnswer(inv -> User.builder().id(inv.getArgument(0)).email("staff@example.com").build());
        lenient().when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Material givenMaterial(int id, int onHand, boolean active) {
        Material m = Material.builder().id(id).code("M-" + id).name("Vật tư " + id)
                .category(MaterialCategory.CONSUMABLE).unit("cái").quantityOnHand(onHand).isActive(active).build();
        lenient().when(materialRepository.findByIdForUpdate(id)).thenReturn(Optional.of(m));
        return m;
    }

    private static ImportInventoryItemRequest item(int materialId, int quantity, String unitCost) {
        return ImportInventoryItemRequest.builder().materialId(materialId).quantity(quantity)
                .unitCost(new BigDecimal(unitCost)).build();
    }

    private static ImportInventoryRequest request(ImportInventoryItemRequest... items) {
        return ImportInventoryRequest.builder().items(List.of(items)).note("  Phiếu PN-01  ").build();
    }

    @Test
    @DisplayName("Nhập nhiều món: cộng đúng tồn từng món, mỗi món một IMPORT, kết quả theo materialId tăng dần")
    void import_multipleItems() {
        Material a = givenMaterial(1, 10, true);
        Material b = givenMaterial(2, 0, true);

        List<InventoryTransactionResponse> result = service.importStock(
                request(item(2, 30, "5000"), item(1, 5, "12000.50")), ACTOR_ID);

        assertThat(a.getQuantityOnHand()).isEqualTo(15);
        assertThat(b.getQuantityOnHand()).isEqualTo(30);
        assertThat(result).extracting(InventoryTransactionResponse::materialId).containsExactly(1, 2);
        assertThat(result).allSatisfy(tx -> {
            assertThat(tx.type()).isEqualTo(InventoryTransactionType.IMPORT);
            assertThat(tx.note()).isEqualTo("Phiếu PN-01");
            assertThat(tx.createdBy()).isEqualTo("staff@example.com");
            assertThat(tx.createdAt()).isEqualTo(NOW);
        });
        assertThat(result.get(0).quantityChange()).isEqualTo(5);
        assertThat(result.get(0).balanceAfter()).isEqualTo(15);
        assertThat(result.get(0).unitCost()).isEqualByComparingTo("12000.50");
        assertThat(result.get(1).balanceAfter()).isEqualTo(30);
    }

    @Test
    @DisplayName("Một món đang tắt → cả phiếu bị chặn, không món nào được nhập")
    void import_inactiveBlocksWholeRequest() {
        Material a = givenMaterial(1, 10, true);
        Material b = givenMaterial(2, 4, false);

        assertThatThrownBy(() -> service.importStock(request(item(1, 5, "1000"), item(2, 3, "1000")), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_MATERIAL_INACTIVE));

        assertThat(a.getQuantityOnHand()).isEqualTo(10);
        assertThat(b.getQuantityOnHand()).isEqualTo(4);
        verify(transactionRepository, never()).save(any());
        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Trùng materialId trong phiếu → ERR_INVALID_INPUT")
    void import_duplicateMaterial() {
        assertThatThrownBy(() -> service.importStock(request(item(1, 5, "1000"), item(1, 2, "1000")), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVALID_INPUT));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("quantity ≤ 0 → ERR_INVENTORY_INVALID_QUANTITY")
    void import_nonPositiveQuantity() {
        assertThatThrownBy(() -> service.importStock(request(item(1, 0, "1000")), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVENTORY_INVALID_QUANTITY));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xuất hủy trong kho: DAMAGED trừ đúng tồn, không gắn chuyến")
    void writeOff_success() {
        Material a = givenMaterial(1, 10, true);

        InventoryTransactionResponse tx = service.writeOffDamaged(DamageInventoryRequest.builder()
                .materialId(1).quantity(4).note("Thùng bị ướt").build(), ACTOR_ID);

        assertThat(a.getQuantityOnHand()).isEqualTo(6);
        assertThat(tx.type()).isEqualTo(InventoryTransactionType.DAMAGED);
        assertThat(tx.quantity()).isEqualTo(4);
        assertThat(tx.quantityChange()).isEqualTo(-4);
        assertThat(tx.balanceAfter()).isEqualTo(6);
        assertThat(tx.allocationId()).isNull();
        assertThat(tx.note()).isEqualTo("Thùng bị ướt");
    }

    @Test
    @DisplayName("Xuất hủy vượt tồn → ERR_INVENTORY_INSUFFICIENT, tồn giữ nguyên, không ghi giao dịch")
    void writeOff_exceedsStock() {
        Material a = givenMaterial(1, 3, true);

        assertThatThrownBy(() -> service.writeOffDamaged(DamageInventoryRequest.builder()
                .materialId(1).quantity(5).note("Hư").build(), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVENTORY_INSUFFICIENT));

        assertThat(a.getQuantityOnHand()).isEqualTo(3);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xuất hủy thiếu lý do → ERR_INVALID_INPUT")
    void writeOff_blankNote() {
        assertThatThrownBy(() -> service.writeOffDamaged(DamageInventoryRequest.builder()
                .materialId(1).quantity(1).note("   ").build(), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVALID_INPUT));
    }

    @Test
    @DisplayName("Vật tư không tồn tại → ERR_MATERIAL_NOT_FOUND")
    void import_materialNotFound() {
        when(materialRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importStock(request(item(99, 1, "1000")), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_MATERIAL_NOT_FOUND));
    }
}
