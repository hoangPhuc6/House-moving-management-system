package com.example.house_moving_service.modules.scheduling.repository.specification;

import com.example.house_moving_service.modules.scheduling.dto.request.MovingScheduleFilterRequest;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MovingScheduleSpecification {

    public static Specification<MovingSchedule> filter(MovingScheduleFilterRequest filter) {
        return (root, query, cb) -> {
            // Fetch đơn khi không phải count query để lấy mã đơn mà không bị N+1
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("booking", JoinType.INNER);
            }
            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            // Chuyến giao với khoảng ngày [from 00:00, to + 1 ngày 00:00)
            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThan(root.get("scheduledEndAt"), filter.getFrom().atStartOfDay()));
            }
            if (filter.getTo() != null) {
                predicates.add(cb.lessThan(root.get("scheduledStartAt"), filter.getTo().plusDays(1).atStartOfDay()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getBookingId() != null) {
                predicates.add(cb.equal(root.get("booking").get("id"), filter.getBookingId()));
            }
            if (filter.getVehicleId() != null && query != null) {
                Subquery<Integer> sub = query.subquery(Integer.class);
                Root<ScheduleVehicleAssignment> a = sub.from(ScheduleVehicleAssignment.class);
                sub.select(a.get("id")).where(
                        cb.equal(a.get("movingSchedule"), root),
                        cb.equal(a.get("vehicle").get("id"), filter.getVehicleId()),
                        cb.notEqual(a.get("status"), AssignmentStatus.CANCELLED));
                predicates.add(cb.exists(sub));
            }
            if (filter.getStaffId() != null && query != null) {
                Subquery<Integer> sub = query.subquery(Integer.class);
                Root<ScheduleStaffAssignment> a = sub.from(ScheduleStaffAssignment.class);
                sub.select(a.get("id")).where(
                        cb.equal(a.get("movingSchedule"), root),
                        cb.equal(a.get("staff").get("id"), filter.getStaffId()),
                        cb.notEqual(a.get("status"), AssignmentStatus.CANCELLED));
                predicates.add(cb.exists(sub));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
