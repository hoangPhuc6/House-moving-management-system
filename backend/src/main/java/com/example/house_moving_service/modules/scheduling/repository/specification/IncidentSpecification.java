package com.example.house_moving_service.modules.scheduling.repository.specification;

import com.example.house_moving_service.modules.scheduling.dto.request.IncidentFilterRequest;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IncidentSpecification {

    public static Specification<Incident> filterIncidents(IncidentFilterRequest filter) {
        return (root, query, cb) -> {
            // Fetch đơn khi không phải count query để lấy mã đơn mà không bị N+1
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("booking", JoinType.INNER);
            }

            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getIncidentType() != null) {
                predicates.add(cb.equal(root.get("incidentType"), filter.getIncidentType()));
            }
            if (filter.getBookingId() != null) {
                predicates.add(cb.equal(root.get("booking").get("id"), filter.getBookingId()));
            }
            if (filter.getScheduleId() != null) {
                predicates.add(cb.equal(root.get("schedule").get("id"), filter.getScheduleId()));
            }

            // Khoảng ngày báo sự cố, tính cả ngày cuối
            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reportedAt"), filter.getFrom().atStartOfDay()));
            }
            if (filter.getTo() != null) {
                predicates.add(cb.lessThan(root.get("reportedAt"), filter.getTo().plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
