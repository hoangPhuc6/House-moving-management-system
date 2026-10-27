package com.example.house_moving_service.modules.survey.repository.specification;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingSurveySpecification {

    public static Specification<BookingSurvey> filterSurveys(SurveyFilterRequest filter) {
        return (root, query, cb) -> {
            Join<BookingSurvey, Booking> booking;
            // Fetch khi không phải count query để tránh N+1 khi map danh sách (booking, khung giờ, nhân viên)
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                Fetch<BookingSurvey, Booking> fetchedBooking = root.fetch("booking", JoinType.INNER);
                booking = (Join<BookingSurvey, Booking>) fetchedBooking;
                root.fetch("timeSlot", JoinType.LEFT);
                root.fetch("surveyStaff", JoinType.LEFT);
            } else {
                booking = root.join("booking", JoinType.INNER);
            }

            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            // 1. Khoảng ngày khảo sát
            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("surveyDate"), filter.getDateFrom()));
            }
            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("surveyDate"), filter.getDateTo()));
            }

            // 2. Nhân viên khảo sát
            if (filter.getStaffId() != null) {
                predicates.add(cb.equal(root.get("surveyStaff").get("id"), filter.getStaffId()));
            }

            // 3. Trạng thái và phương thức khảo sát
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getMethod() != null) {
                predicates.add(cb.equal(root.get("surveyMethod"), filter.getMethod()));
            }

            // 4. Mã đơn hàng (không phân biệt hoa/thường, partial match)
            if (filter.getBookingCode() != null && !filter.getBookingCode().trim().isEmpty()) {
                String codePattern = "%" + filter.getBookingCode().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(booking.get("bookingCode")), codePattern));
            }

            // 5. Số điện thoại người liên hệ của đơn
            if (filter.getCustomerPhone() != null && !filter.getCustomerPhone().trim().isEmpty()) {
                String phonePattern = "%" + filter.getCustomerPhone().trim() + "%";
                predicates.add(cb.like(booking.get("contactPhone"), phonePattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
