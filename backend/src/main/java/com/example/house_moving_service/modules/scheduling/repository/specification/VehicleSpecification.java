package com.example.house_moving_service.modules.scheduling.repository.specification;

import com.example.house_moving_service.modules.scheduling.dto.request.VehicleFilterRequest;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VehicleSpecification {

    public static Specification<Vehicle> filter(VehicleFilterRequest filter) {
        return (root, query, cb) -> {
            // Fetch loại xe khi không phải count query để map response không bị N+1
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("vehicleType", JoinType.INNER);
            }

            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + escapeLike(filter.getKeyword().trim().toUpperCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.upper(root.get("licensePlate")), pattern, '\\'));
            }
            if (filter.getVehicleTypeId() != null) {
                predicates.add(cb.equal(root.get("vehicleType").get("id"), filter.getVehicleTypeId()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Người dùng gõ % hoặc _ thì tìm đúng ký tự đó, không coi là wildcard
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
