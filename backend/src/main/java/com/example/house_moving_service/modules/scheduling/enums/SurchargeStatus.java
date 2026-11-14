package com.example.house_moving_service.modules.scheduling.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * PROPOSED ──send (điều phối)──► WAITING_CUSTOMER ──accept (khách)──► ACCEPTED (──collect──► đã thu tiền)
 * PROPOSED ──reject (điều phối)──► REJECTED · PROPOSED ──cancel (người đề xuất)──► CANCELLED
 * WAITING_CUSTOMER ──reject (khách)──► REJECTED_BY_CUSTOMER
 * WAITING_CUSTOMER / REJECTED_BY_CUSTOMER ──withdraw (điều phối)──► CANCELLED
 * Trạng thái cuối: ACCEPTED, REJECTED, CANCELLED.
 */
public enum SurchargeStatus {
    PROPOSED,
    WAITING_CUSTOMER,
    ACCEPTED,
    REJECTED,
    REJECTED_BY_CUSTOMER,
    CANCELLED;

    // Còn khoản ở các trạng thái này thì đội thi công chưa được bắt đầu chuyến (ERR_MOVING_PENDING_ADJUSTMENT)
    public static final Set<SurchargeStatus> BLOCKING_START = EnumSet.of(PROPOSED, WAITING_CUSTOMER, REJECTED_BY_CUSTOMER);

    // Điều phối viên được rút lại để thương lượng lại
    public static final Set<SurchargeStatus> WITHDRAWABLE = EnumSet.of(WAITING_CUSTOMER, REJECTED_BY_CUSTOMER);

    // Khách thấy được: bỏ khoản nội bộ chưa gửi (PROPOSED) và khoản đã bác / đã hủy
    public static final Set<SurchargeStatus> VISIBLE_TO_CUSTOMER = EnumSet.of(WAITING_CUSTOMER, ACCEPTED, REJECTED_BY_CUSTOMER);
}
