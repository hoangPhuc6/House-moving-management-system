package com.example.house_moving_service.modules.inventory.repository.specification;

import com.example.house_moving_service.modules.inventory.dto.request.InventoryTransactionFilterRequest;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InventoryTransactionSpecification {

    public static Specification<InventoryTransaction> filter(InventoryTransactionFilterRequest filter) {
        return (root, query, cb) -> {
            // Fetch vật tư, đơn, người thao tác khi không phải count query để map response không bị N+1
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("material", JoinType.INNER);
                root.fetch("booking", JoinType.LEFT);
                root.fetch("createdBy", JoinType.LEFT);
            }
            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();
            if (filter.getMaterialId() != null) {
                predicates.add(cb.equal(root.get("material").get("id"), filter.getMaterialId()));
            }
            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("transactionType"), filter.getType()));
            }
            if (filter.getBookingId() != null) {
                predicates.add(cb.equal(root.get("booking").get("id"), filter.getBookingId()));
            }
            if (filter.getScheduleId() != null) {
                predicates.add(cb.equal(root.get("schedule").get("id"), filter.getScheduleId()));
            }
            // Khoảng ngày tạo, tính cả ngày cuối
            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom().atStartOfDay()));
            }
            if (filter.getTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.getTo().plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
