package com.example.house_moving_service.modules.voucher.service;

import com.example.house_moving_service.modules.voucher.entity.Voucher;

/**
 * API công khai của module voucher — module khác (pricing, booking) chỉ thao tác voucher qua interface này.
 */
public interface VoucherService {

    /**
     * Tìm voucher theo mã (đã chuẩn hóa trim + upper-case) và kiểm tra còn hiệu lực.
     *
     * @throws com.example.house_moving_service.common.exception.AppException
     *         ERR_VOUCHER_NOT_FOUND nếu không tồn tại, ERR_VOUCHER_EXPIRED nếu bị tắt hoặc ngoài thời gian hiệu lực
     */
    Voucher getValidVoucher(String code);

    /**
     * Giống {@link #getValidVoucher(String)} nhưng khóa dòng voucher (pessimistic lock) đến hết transaction,
     * dùng khi tạo đơn để tránh nhiều đơn cùng dùng lượt cuối. Bắt buộc gọi trong transaction đang mở.
     */
    Voucher lockValidVoucher(String code);

    Voucher getReference(Integer id);

    /** Tăng used_count thêm 1 (null coi như 0). */
    void increaseUsage(Voucher voucher);

    /** Hoàn lại 1 lượt dùng khi hủy đơn; không làm used_count âm, bỏ qua nếu voucher không tồn tại. */
    void releaseUsage(Integer voucherId);
}
