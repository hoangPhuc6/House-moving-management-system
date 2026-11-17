package com.example.house_moving_service.modules.inventory.repository;

import com.example.house_moving_service.modules.inventory.dto.request.MaterialFilterRequest;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.inventory.repository.specification.MaterialSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Chạy trên database thật (Flyway V17) và rollback sau mỗi test; mã có tiền tố riêng để không lẫn dữ liệu có sẵn
@SpringBootTest
@Transactional
class MaterialRepositoryTest {

    private static final String PREFIX = "ZZTEST-M8-";

    @Autowired
    private MaterialRepository materialRepository;

    @BeforeEach
    void setUp() {
        save("LOW", MaterialCategory.CONSUMABLE, 2, 10, true);     // tồn thấp
        save("EQUAL", MaterialCategory.CONSUMABLE, 10, 10, true);  // bằng mức tối thiểu: không thấp
        save("OK", MaterialCategory.TOOL, 30, 5, true);
        save("LOW-OFF", MaterialCategory.TOOL, 0, 3, false);       // tồn thấp nhưng đang tắt
    }

    private void save(String suffix, MaterialCategory category, int onHand, int minStock, boolean active) {
        materialRepository.save(Material.builder()
                .code(PREFIX + suffix).name("Vat tu test " + suffix).category(category).unit("cái")
                .unitPrice(BigDecimal.ZERO).quantityOnHand(onHand).minStock(minStock).isActive(active).build());
    }

    private List<String> codes(MaterialFilterRequest filter) {
        filter.setKeyword(PREFIX);
        return materialRepository.findAll(MaterialSpecification.filter(filter)).stream()
                .map(Material::getCode).sorted().toList();
    }

    @Test
    @DisplayName("lowStock = true chỉ lấy vật tư có tồn < tồn tối thiểu")
    void filter_lowStockTrue() {
        assertThat(codes(MaterialFilterRequest.builder().lowStock(true).build()))
                .containsExactly(PREFIX + "LOW", PREFIX + "LOW-OFF");
    }

    @Test
    @DisplayName("lowStock = false lấy vật tư có tồn ≥ tồn tối thiểu")
    void filter_lowStockFalse() {
        assertThat(codes(MaterialFilterRequest.builder().lowStock(false).build()))
                .containsExactly(PREFIX + "EQUAL", PREFIX + "OK");
    }

    @Test
    @DisplayName("Kết hợp lowStock + isActive + category")
    void filter_combined() {
        assertThat(codes(MaterialFilterRequest.builder().lowStock(true).isActive(true).build()))
                .containsExactly(PREFIX + "LOW");
        assertThat(codes(MaterialFilterRequest.builder().category(MaterialCategory.TOOL).build()))
                .containsExactly(PREFIX + "LOW-OFF", PREFIX + "OK");
    }

    @Test
    @DisplayName("keyword tìm theo một phần mã hoặc tên, không phân biệt hoa thường")
    void filter_keyword() {
        List<String> byName = materialRepository.findAll(MaterialSpecification.filter(
                        MaterialFilterRequest.builder().keyword("vat TU test equal").build())).stream()
                .map(Material::getCode).toList();
        assertThat(byName).containsExactly(PREFIX + "EQUAL");

        assertThat(materialRepository.existsByCodeIgnoreCase(PREFIX.toLowerCase() + "ok")).isTrue();
    }
}
