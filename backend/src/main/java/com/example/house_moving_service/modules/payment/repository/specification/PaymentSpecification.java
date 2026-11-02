package com.example.house_moving_service.modules.payment.repository.specification;

import com.example.house_moving_service.modules.payment.dto.request.PaymentFilterRequest;
import com.example.house_moving_service.modules.payment.entity.Payment;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class PaymentSpecification {

    public static Specification<Payment> filterPayments(PaymentFilterRequest filter) {
        return (root, query, cb) -> {
            // Eager fetch booking khi không phải count query để tránh N+1 query khi mapping DTO
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("booking", JoinType.LEFT);
            }

            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            // 1. Lọc theo ID đơn hàng
            if (filter.getBookingId() != null) {
                predicates.add(cb.equal(root.get("booking").get("id"), filter.getBookingId()));
            }

            // 2. Tìm kiếm theo mã đơn hàng (không phân biệt hoa/thường, partial match)
            if (filter.getBookingCode() != null && !filter.getBookingCode().trim().isEmpty()) {
                String pattern = "%" + filter.getBookingCode().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("booking").get("bookingCode")), pattern));
            }

            // 3. Lọc theo trạng thái thanh toán (PENDING, SUCCESS, FAILED, CANCELLED)
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // 4. Lọc theo phương thức thanh toán (CASH, BANK_TRANSFER, CARD, E_WALLET)
            if (filter.getPaymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.getPaymentMethod()));
            }

            // 5. Lọc theo loại đợt thanh toán (DEPOSIT, PARTIAL, FINAL, REFUND)
            if (filter.getPaymentType() != null) {
                predicates.add(cb.equal(root.get("paymentType"), filter.getPaymentType()));
            }

            // 6. Tìm theo mã giao dịch / số bút toán
            if (filter.getTransactionCode() != null && !filter.getTransactionCode().trim().isEmpty()) {
                String pattern = "%" + filter.getTransactionCode().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("transactionCode")), pattern));
            }

            // 7. Lọc theo khoảng ngày tạo giao dịch: từ ngày (createdAt >= fromDate)
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }

            // 8. Lọc theo khoảng ngày tạo giao dịch: đến ngày (createdAt <= toDate)
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

