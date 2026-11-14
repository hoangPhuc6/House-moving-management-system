package com.example.house_moving_service.modules.scheduling.repository.specification;

import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeFilterRequest;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SurchargeSpecification {

    /** Không truyền status thì lọc PROPOSED (khoản đang chờ điều phối viên xử lý). */
    public static Specification<BookingSurcharge> filterSurcharges(SurchargeFilterRequest filter) {
        return (root, query, cb) -> {
            // Fetch đơn và dịch vụ khi không phải count query để lấy mã đơn / tên dịch vụ mà không bị N+1
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("booking", JoinType.INNER);
                root.fetch("service", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            SurchargeStatus status = filter != null && filter.getStatus() != null ? filter.getStatus() : SurchargeStatus.PROPOSED;
            predicates.add(cb.equal(root.get("status"), status));

            if (filter != null) {
                if (filter.getSurchargeType() != null) {
                    predicates.add(cb.equal(root.get("surchargeType"), filter.getSurchargeType()));
                }
                if (filter.getBookingId() != null) {
                    predicates.add(cb.equal(root.get("booking").get("id"), filter.getBookingId()));
                }
                // Khoảng ngày đề xuất, tính cả ngày cuối
                if (filter.getFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("requestedAt"), filter.getFrom().atStartOfDay()));
                }
                if (filter.getTo() != null) {
                    predicates.add(cb.lessThan(root.get("requestedAt"), filter.getTo().plusDays(1).atStartOfDay()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
