package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.inventory.dto.request.AdjustInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryItemRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.InventoryTransactionFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialHistoryFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialHistoryResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.mapper.InventoryTransactionMapper;
import com.example.house_moving_service.modules.inventory.mapper.MaterialMapper;
import com.example.house_moving_service.modules.inventory.repository.InventoryTransactionRepository;
import com.example.house_moving_service.modules.inventory.repository.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
        service = new InventoryServiceImpl(ledger, new InventoryTransactionMapper(), materialRepository, new MaterialMapper(),
                transactionRepository);
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

    private static AdjustInventoryRequest adjustRequest(int materialId, int actual, String note) {
        return AdjustInventoryRequest.builder().materialId(materialId).actualQuantity(actual).note(note).build();
    }

    @Test
    @DisplayName("Kiểm kê thừa: ADJUST quantity = chênh lệch, change dương, tồn = số đếm")
    void adjust_increase() {
        Material a = givenMaterial(1, 10, true);

        InventoryTransactionResponse tx = service.adjust(adjustRequest(1, 13, "  Đếm lại kho  "), ACTOR_ID);

        assertThat(a.getQuantityOnHand()).isEqualTo(13);
        assertThat(tx.type()).isEqualTo(InventoryTransactionType.ADJUST);
        assertThat(tx.quantity()).isEqualTo(3);
        assertThat(tx.quantityChange()).isEqualTo(3);
        assertThat(tx.balanceAfter()).isEqualTo(13);
        assertThat(tx.note()).isEqualTo("Đếm lại kho");
        assertThat(tx.createdBy()).isEqualTo("staff@example.com");
        assertThat(tx.allocationId()).isNull();
    }

    @Test
    @DisplayName("Kiểm kê thiếu (kể cả về 0): ADJUST quantity dương, change âm")
    void adjust_decrease() {
        Material a = givenMaterial(1, 10, true);

        InventoryTransactionResponse tx = service.adjust(adjustRequest(1, 0, "Mất hết"), ACTOR_ID);

        assertThat(a.getQuantityOnHand()).isZero();
        assertThat(tx.quantity()).isEqualTo(10);
        assertThat(tx.quantityChange()).isEqualTo(-10);
        assertThat(tx.balanceAfter()).isZero();
    }

    @Test
    @DisplayName("Kiểm kê vật tư đang tắt vẫn được (tồn là số đếm thực tế)")
    void adjust_inactiveAllowed() {
        Material a = givenMaterial(1, 5, false);

        service.adjust(adjustRequest(1, 4, "Kiểm cuối tháng"), ACTOR_ID);

        assertThat(a.getQuantityOnHand()).isEqualTo(4);
    }

    @Test
    @DisplayName("Kiểm kê không lệch → ERR_INVENTORY_ADJUST_NO_CHANGE, không ghi gì")
    void adjust_noChange() {
        Material a = givenMaterial(1, 10, true);

        assertThatThrownBy(() -> service.adjust(adjustRequest(1, 10, "Đếm lại"), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVENTORY_ADJUST_NO_CHANGE));
        assertThat(a.getQuantityOnHand()).isEqualTo(10);
        verify(transactionRepository, never()).save(any());
        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Kiểm kê thiếu lý do hoặc số âm → ERR_INVALID_INPUT, không khóa vật tư")
    void adjust_invalidInput() {
        assertThatThrownBy(() -> service.adjust(adjustRequest(1, 3, "  "), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVALID_INPUT));
        assertThatThrownBy(() -> service.adjust(adjustRequest(1, -1, "Đếm"), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_INVALID_INPUT));
        verify(materialRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Kiểm kê vật tư không tồn tại → ERR_MATERIAL_NOT_FOUND")
    void adjust_materialNotFound() {
        when(materialRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjust(adjustRequest(99, 1, "Đếm"), ACTOR_ID))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_MATERIAL_NOT_FOUND));
    }

    @Test
    @DisplayName("Tồn thấp: trả theo đúng thứ tự repository, có cờ lowStock")
    void lowStock_mapsRepositoryOrder() {
        Material worse = Material.builder().id(2).code("M-2").name("B").category(MaterialCategory.CONSUMABLE)
                .unit("cái").quantityOnHand(0).minStock(10).isActive(true).build();
        Material mild = Material.builder().id(1).code("M-1").name("A").category(MaterialCategory.TOOL)
                .unit("cái").quantityOnHand(4).minStock(5).isActive(true).build();
        when(materialRepository.findActiveLowStock()).thenReturn(List.of(worse, mild));

        List<MaterialResponse> result = service.getLowStock();

        assertThat(result).extracting(MaterialResponse::id).containsExactly(2, 1);
        assertThat(result).allSatisfy(m -> assertThat(m.lowStock()).isTrue());
    }

    private static InventoryTransactionRepository.TypeTotal total(InventoryTransactionType type, long sum) {
        return new InventoryTransactionRepository.TypeTotal() {
            @Override
            public InventoryTransactionType getType() {
                return type;
            }

            @Override
            public Long getTotal() {
                return sum;
            }
        };
    }

    @Test
    @DisplayName("Lịch sử một món: kèm tồn hiện tại, totals đủ 5 loại, khoảng ngày tính hết ngày to, mới nhất trước")
    @SuppressWarnings("unchecked")
    void history_totalsAndDefaultSort() {
        Material a = givenMaterial(1, 42, true);
        when(materialRepository.findById(1)).thenReturn(Optional.of(a));
        InventoryTransaction tx = InventoryTransaction.builder().id(77).material(a)
                .transactionType(InventoryTransactionType.IMPORT).quantity(50).quantityChange(50).balanceAfter(50)
                .createdAt(NOW).build();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(transactionRepository.findAll(any(Specification.class), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(tx), inv.getArgument(1), 1));
        when(transactionRepository.sumQuantityByType(eq(1), any(), any())).thenReturn(List.of(
                total(InventoryTransactionType.IMPORT, 50), total(InventoryTransactionType.EXPORT, 8)));

        MaterialHistoryResponse result = service.getMaterialHistory(1, MaterialHistoryFilterRequest.builder()
                .from(LocalDate.of(2026, 11, 1)).to(LocalDate.of(2026, 11, 17)).build(), PageRequest.of(0, 10));

        assertThat(result.material().id()).isEqualTo(1);
        assertThat(result.material().quantityOnHand()).isEqualTo(42);
        assertThat(result.totals()).containsOnlyKeys(InventoryTransactionType.values());
        assertThat(result.totals()).containsEntry(InventoryTransactionType.IMPORT, 50L)
                .containsEntry(InventoryTransactionType.EXPORT, 8L)
                .containsEntry(InventoryTransactionType.RETURN, 0L)
                .containsEntry(InventoryTransactionType.DAMAGED, 0L)
                .containsEntry(InventoryTransactionType.ADJUST, 0L);
        assertThat(result.transactions().getItems()).extracting(InventoryTransactionResponse::id).containsExactly(77);
        // to tính hết ngày: mốc trên là đầu ngày hôm sau
        verify(transactionRepository).sumQuantityByType(1, LocalDate.of(2026, 11, 1).atStartOfDay(),
                LocalDate.of(2026, 11, 18).atStartOfDay());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("Lịch sử lọc theo loại: totals chỉ tính loại đó, loại khác = 0")
    @SuppressWarnings("unchecked")
    void history_typeFilterTotals() {
        Material a = givenMaterial(1, 10, true);
        when(materialRepository.findById(1)).thenReturn(Optional.of(a));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));
        when(transactionRepository.sumQuantityByType(eq(1), any(), any())).thenReturn(List.of(
                total(InventoryTransactionType.IMPORT, 50), total(InventoryTransactionType.EXPORT, 8)));

        MaterialHistoryResponse result = service.getMaterialHistory(1,
                MaterialHistoryFilterRequest.builder().type(InventoryTransactionType.EXPORT).build(), PageRequest.of(0, 10));

        assertThat(result.totals()).containsEntry(InventoryTransactionType.EXPORT, 8L)
                .containsEntry(InventoryTransactionType.IMPORT, 0L);
    }

    @Test
    @DisplayName("Lịch sử vật tư không tồn tại → ERR_MATERIAL_NOT_FOUND, không truy vấn giao dịch")
    void history_materialNotFound() {
        when(materialRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMaterialHistory(99, null, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ERR_MATERIAL_NOT_FOUND));
        verify(transactionRepository, never()).sumQuantityByType(any(), any(), any());
    }

    @Test
    @DisplayName("Tra cứu giao dịch: giữ sort client truyền")
    @SuppressWarnings("unchecked")
    void searchTransactions_keepsClientSort() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(transactionRepository.findAll(any(Specification.class), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));

        PageResponse<InventoryTransactionResponse> result = service.searchTransactions(
                InventoryTransactionFilterRequest.builder().bookingId(7).build(),
                PageRequest.of(1, 5, Sort.by("quantity")));

        assertThat(result.getItems()).isEmpty();
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("quantity"));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    }
}
