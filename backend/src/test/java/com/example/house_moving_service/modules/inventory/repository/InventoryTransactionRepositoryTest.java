package com.example.house_moving_service.modules.inventory.repository;

import com.example.house_moving_service.modules.inventory.dto.request.InventoryTransactionFilterRequest;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.repository.specification.InventoryTransactionSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Chạy trên database thật (Flyway V17) và rollback sau mỗi test; vật tư có mã tiền tố riêng để không lẫn dữ liệu có sẵn
@SpringBootTest
@Transactional
class InventoryTransactionRepositoryTest {

    private static final String PREFIX = "ZZTEST-M8-TX-";

    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private InventoryTransactionRepository transactionRepository;

    private Material box;
    private Material tape;

    @BeforeEach
    void setUp() {
        box = saveMaterial("BOX");
        tape = saveMaterial("TAPE");
        // box: IMPORT 50 (01/11), DAMAGED -2 (10/11 cuối ngày), ADJUST +3 (20/11)
        saveTx(box, InventoryTransactionType.IMPORT, 50, 50, 50, LocalDateTime.of(2026, 11, 1, 8, 0), null);
        saveTx(box, InventoryTransactionType.DAMAGED, 2, -2, 48, LocalDateTime.of(2026, 11, 10, 23, 59), "Ướt");
        saveTx(box, InventoryTransactionType.ADJUST, 3, 3, 51, LocalDateTime.of(2026, 11, 20, 9, 0), "Kiểm kê");
        // tape: IMPORT 10 (05/11) — không được lẫn vào lịch sử của box
        saveTx(tape, InventoryTransactionType.IMPORT, 10, 10, 10, LocalDateTime.of(2026, 11, 5, 8, 0), null);
    }

    private Material saveMaterial(String suffix) {
        return materialRepository.save(Material.builder()
                .code(PREFIX + suffix).name("Vat tu test " + suffix).category(MaterialCategory.CONSUMABLE).unit("cái")
                .unitPrice(BigDecimal.ZERO).quantityOnHand(0).minStock(0).isActive(true).build());
    }

    private void saveTx(Material material, InventoryTransactionType type, int quantity, int change, int balance,
                        LocalDateTime createdAt, String note) {
        transactionRepository.save(InventoryTransaction.builder()
                .material(material).transactionType(type).quantity(quantity).quantityChange(change)
                .balanceAfter(balance).note(note).createdAt(createdAt).build());
    }

    private List<InventoryTransactionType> types(InventoryTransactionFilterRequest filter) {
        return transactionRepository.findAll(InventoryTransactionSpecification.filter(filter)).stream()
                .sorted((x, y) -> x.getCreatedAt().compareTo(y.getCreatedAt()))
                .map(InventoryTransaction::getTransactionType).toList();
    }

    @Test
    @DisplayName("Lọc theo materialId chỉ trả giao dịch của vật tư đó")
    void filter_materialOnly() {
        assertThat(types(InventoryTransactionFilterRequest.builder().materialId(box.getId()).build()))
                .containsExactly(InventoryTransactionType.IMPORT, InventoryTransactionType.DAMAGED,
                        InventoryTransactionType.ADJUST);
    }

    @Test
    @DisplayName("Lọc theo loại và khoảng ngày; to tính hết ngày")
    void filter_typeAndDateRange() {
        assertThat(types(InventoryTransactionFilterRequest.builder().materialId(box.getId())
                .type(InventoryTransactionType.IMPORT).build()))
                .containsExactly(InventoryTransactionType.IMPORT);
        // 23:59 ngày 10/11 vẫn nằm trong to = 10/11
        assertThat(types(InventoryTransactionFilterRequest.builder().materialId(box.getId())
                .from(LocalDate.of(2026, 11, 2)).to(LocalDate.of(2026, 11, 10)).build()))
                .containsExactly(InventoryTransactionType.DAMAGED);
    }

    @Test
    @DisplayName("sumQuantityByType cộng đúng theo loại trong khoảng, chỉ của vật tư đã cho")
    void sumQuantityByType_range() {
        Map<InventoryTransactionType, Long> all = transactionRepository.sumQuantityByType(box.getId(),
                        LocalDate.of(1970, 1, 1).atStartOfDay(), LocalDate.of(9999, 1, 1).atStartOfDay()).stream()
                .collect(Collectors.toMap(InventoryTransactionRepository.TypeTotal::getType,
                        InventoryTransactionRepository.TypeTotal::getTotal));
        assertThat(all).containsOnly(Map.entry(InventoryTransactionType.IMPORT, 50L),
                Map.entry(InventoryTransactionType.DAMAGED, 2L), Map.entry(InventoryTransactionType.ADJUST, 3L));

        Map<InventoryTransactionType, Long> early = transactionRepository.sumQuantityByType(box.getId(),
                        LocalDate.of(2026, 11, 1).atStartOfDay(), LocalDate.of(2026, 11, 11).atStartOfDay()).stream()
                .collect(Collectors.toMap(InventoryTransactionRepository.TypeTotal::getType,
                        InventoryTransactionRepository.TypeTotal::getTotal));
        assertThat(early).containsOnly(Map.entry(InventoryTransactionType.IMPORT, 50L),
                Map.entry(InventoryTransactionType.DAMAGED, 2L));
    }
}
