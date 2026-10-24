package com.example.house_moving_service.modules.booking.repository.specification;

import com.example.house_moving_service.modules.booking.dto.request.BookingManagementFilterRequest;
import com.example.house_moving_service.modules.booking.entity.Booking;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class BookingSpecification {
    public static Specification<Booking> filterBookings(BookingManagementFilterRequest filter) {
        return (root, query, cb) -> {
            // Eager fetch route khi không phải count query để tránh N+1 query khi mapping toSummaryResponse
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("route", JoinType.LEFT);
            }

            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            // 1. Lọc theo trạng thái đơn hàng
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // 2. Tìm theo mã đơn hàng (không phân biệt hoa/thường, partial match)
            if (filter.getBookingCode() != null && !filter.getBookingCode().trim().isEmpty()) {
                String codePattern = "%" + filter.getBookingCode().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("bookingCode")), codePattern));
            }

            // 3. Tìm theo số điện thoại người liên hệ
            if (filter.getCustomerPhone() != null && !filter.getCustomerPhone().trim().isEmpty()) {
                String phonePattern = "%" + filter.getCustomerPhone().trim() + "%";
                predicates.add(cb.like(root.get("contactPhone"), phonePattern));
            }

            // 4. Tìm theo tên người liên hệ (không phân biệt hoa/thường)
            if (filter.getCustomerName() != null && !filter.getCustomerName().trim().isEmpty()) {
                String namePattern = "%" + filter.getCustomerName().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("contactName")), namePattern));
            }

            // 5. Lọc theo khoảng ngày tạo đơn (từ ngày: createdAt >= fromDate)
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }

            // 6. Lọc theo khoảng ngày tạo đơn (đến ngày: createdAt <= toDate)
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

