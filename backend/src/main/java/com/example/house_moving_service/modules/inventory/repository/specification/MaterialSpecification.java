package com.example.house_moving_service.modules.inventory.repository.specification;

import com.example.house_moving_service.modules.inventory.dto.request.MaterialFilterRequest;
import com.example.house_moving_service.modules.inventory.entity.Material;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MaterialSpecification {

    public static Specification<Material> filter(MaterialFilterRequest filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), filter.getCategory()));
            }
            if (filter.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), filter.getIsActive()));
            }
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + escapeLike(filter.getKeyword().trim().toUpperCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.upper(root.get("code")), pattern, '\\'),
                        cb.like(cb.upper(root.get("name")), pattern, '\\')));
            }
            if (filter.getLowStock() != null) {
                // Tồn thấp: quantity_on_hand < min_stock (so sánh hai cột của cùng dòng)
                predicates.add(Boolean.TRUE.equals(filter.getLowStock())
                        ? cb.lessThan(root.get("quantityOnHand"), root.get("minStock"))
                        : cb.greaterThanOrEqualTo(root.get("quantityOnHand"), root.get("minStock")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Người dùng gõ % hoặc _ thì tìm đúng ký tự đó, không coi là wildcard
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
