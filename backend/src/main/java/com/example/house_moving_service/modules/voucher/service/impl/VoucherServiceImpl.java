package com.example.house_moving_service.modules.voucher.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.voucher.repository.VoucherRepository;
import com.example.house_moving_service.modules.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;

    @Override
    public Voucher getValidVoucher(String code) {
        String normalizedCode = normalize(code);
        Voucher voucher = voucherRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_VOUCHER_NOT_FOUND, normalizedCode));
        ensureActive(voucher, normalizedCode);
        return voucher;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Voucher lockValidVoucher(String code) {
        String normalizedCode = normalize(code);
        Voucher voucher = voucherRepository.findByCodeWithLock(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_VOUCHER_NOT_FOUND, normalizedCode));
        ensureActive(voucher, normalizedCode);
        return voucher;
    }

    @Override
    public Voucher getReference(Integer id) {
        return voucherRepository.getReferenceById(id);
    }

    @Override
    public void increaseUsage(Voucher voucher) {
        int currentUsedCount = voucher.getUsedCount() != null ? voucher.getUsedCount() : 0;
        voucher.setUsedCount(currentUsedCount + 1);
        voucherRepository.save(voucher);
    }

    @Override
    public void releaseUsage(Integer voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId).orElse(null);
        if (voucher != null && voucher.getUsedCount() != null && voucher.getUsedCount() > 0) {
            voucher.setUsedCount(voucher.getUsedCount() - 1);
            voucherRepository.save(voucher);
            log.info("Reverted usedCount for voucher: {}", voucher.getCode());
        }
    }

    private String normalize(String code) {
        return code.trim().toUpperCase();
    }

    // Kiểm tra hiệu lực thời gian và trạng thái
    private void ensureActive(Voucher voucher, String code) {
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(voucher.getIsActive())
                || now.isBefore(voucher.getStartDate())
                || now.isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.ERR_VOUCHER_EXPIRED, code);
        }
    }
}
