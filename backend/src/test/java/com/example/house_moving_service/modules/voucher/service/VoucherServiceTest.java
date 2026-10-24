package com.example.house_moving_service.modules.voucher.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.voucher.enums.DiscountType;
import com.example.house_moving_service.modules.voucher.repository.VoucherRepository;
import com.example.house_moving_service.modules.voucher.service.impl.VoucherServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    private VoucherServiceImpl voucherService;

    @BeforeEach
    void setUp() {
        voucherService = new VoucherServiceImpl(voucherRepository);
    }

    private Voucher voucher(String code, boolean active, LocalDateTime start, LocalDateTime end, Integer usedCount) {
        return Voucher.builder()
                .id(10)
                .code(code)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50000"))
                .isActive(active)
                .startDate(start)
                .endDate(end)
                .usedCount(usedCount)
                .build();
    }

    private Voucher validVoucher(String code) {
        return voucher(code, true, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 0);
    }

    @Nested
    @DisplayName("getValidVoucher")
    class GetValidVoucherTests {

        @Test
        @DisplayName("Mã không tồn tại -> ERR_VOUCHER_NOT_FOUND")
        void getValidVoucher_notFound_throws() {
            when(voucherRepository.findByCode("KHONGTONTAI")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> voucherService.getValidVoucher("KHONGTONTAI"))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_NOT_FOUND);
        }

        @Test
        @DisplayName("Voucher bị tắt -> ERR_VOUCHER_EXPIRED")
        void getValidVoucher_inactive_throws() {
            Voucher v = voucher("BITHUY", false, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 0);
            when(voucherRepository.findByCode("BITHUY")).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.getValidVoucher("BITHUY"))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_EXPIRED);
        }

        @Test
        @DisplayName("Chưa tới ngày bắt đầu -> ERR_VOUCHER_EXPIRED")
        void getValidVoucher_notStarted_throws() {
            Voucher v = voucher("CHUAMOBAN", true, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(5), 0);
            when(voucherRepository.findByCode("CHUAMOBAN")).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.getValidVoucher("CHUAMOBAN"))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_EXPIRED);
        }

        @Test
        @DisplayName("Đã qua ngày kết thúc -> ERR_VOUCHER_EXPIRED")
        void getValidVoucher_ended_throws() {
            Voucher v = voucher("HETHAN", true, LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(1), 0);
            when(voucherRepository.findByCode("HETHAN")).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.getValidVoucher("HETHAN"))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_EXPIRED);
        }

        @Test
        @DisplayName("Voucher hợp lệ -> trả về voucher")
        void getValidVoucher_valid_returnsVoucher() {
            Voucher v = validVoucher("SALE50");
            when(voucherRepository.findByCode("SALE50")).thenReturn(Optional.of(v));

            assertThat(voucherService.getValidVoucher("SALE50")).isSameAs(v);
        }

        @Test
        @DisplayName("Mã được chuẩn hóa: trim + upper-case trước khi tìm")
        void getValidVoucher_normalizesCode() {
            Voucher v = validVoucher("SALE50");
            when(voucherRepository.findByCode("SALE50")).thenReturn(Optional.of(v));

            assertThat(voucherService.getValidVoucher("  sale50 ")).isSameAs(v);
        }
    }

    @Nested
    @DisplayName("lockValidVoucher")
    class LockValidVoucherTests {

        @Test
        @DisplayName("Dùng findByCodeWithLock, không dùng findByCode")
        void lockValidVoucher_usesLockQuery() {
            Voucher v = validVoucher("SALE50");
            when(voucherRepository.findByCodeWithLock("SALE50")).thenReturn(Optional.of(v));

            assertThat(voucherService.lockValidVoucher(" sale50")).isSameAs(v);
            verify(voucherRepository, never()).findByCode(anyString());
        }

        @Test
        @DisplayName("Mã không tồn tại -> ERR_VOUCHER_NOT_FOUND")
        void lockValidVoucher_notFound_throws() {
            when(voucherRepository.findByCodeWithLock("KHONGTONTAI")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> voucherService.lockValidVoucher("KHONGTONTAI"))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_NOT_FOUND);
        }

        @Test
        @DisplayName("Voucher hết hạn -> ERR_VOUCHER_EXPIRED")
        void lockValidVoucher_expired_throws() {
            Voucher v = voucher("HETHAN", true, LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(1), 0);
            when(voucherRepository.findByCodeWithLock("HETHAN")).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> voucherService.lockValidVoucher("HETHAN"))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_EXPIRED);
        }

        @Test
        @DisplayName("Bắt buộc chạy trong transaction đang mở (Propagation.MANDATORY)")
        void lockValidVoucher_requiresExistingTransaction() throws NoSuchMethodException {
            Transactional tx = VoucherServiceImpl.class
                    .getMethod("lockValidVoucher", String.class)
                    .getAnnotation(Transactional.class);

            assertThat(tx).isNotNull();
            assertThat(tx.propagation()).isEqualTo(Propagation.MANDATORY);
        }
    }

    @Nested
    @DisplayName("getReference / increaseUsage / releaseUsage")
    class UsageTests {

        @Test
        @DisplayName("getReference ủy quyền cho getReferenceById")
        void getReference_delegates() {
            Voucher ref = validVoucher("SALE50");
            when(voucherRepository.getReferenceById(10)).thenReturn(ref);

            assertThat(voucherService.getReference(10)).isSameAs(ref);
        }

        @Test
        @DisplayName("increaseUsage: usedCount null -> 1 và lưu")
        void increaseUsage_nullCount_becomesOne() {
            Voucher v = validVoucher("SALE50");
            v.setUsedCount(null);

            voucherService.increaseUsage(v);

            assertThat(v.getUsedCount()).isEqualTo(1);
            verify(voucherRepository).save(v);
        }

        @Test
        @DisplayName("increaseUsage: 5 -> 6 và lưu")
        void increaseUsage_incrementsByOne() {
            Voucher v = validVoucher("SALE50");
            v.setUsedCount(5);

            voucherService.increaseUsage(v);

            assertThat(v.getUsedCount()).isEqualTo(6);
            verify(voucherRepository).save(v);
        }

        @Test
        @DisplayName("releaseUsage: 3 -> 2 và lưu")
        void releaseUsage_decrementsByOne() {
            Voucher v = validVoucher("SALE50");
            v.setUsedCount(3);
            when(voucherRepository.findById(10)).thenReturn(Optional.of(v));

            voucherService.releaseUsage(10);

            assertThat(v.getUsedCount()).isEqualTo(2);
            verify(voucherRepository).save(v);
        }

        @Test
        @DisplayName("releaseUsage: usedCount = 0 -> giữ 0, không lưu (không để âm)")
        void releaseUsage_zero_doesNotGoNegative() {
            Voucher v = validVoucher("SALE50");
            v.setUsedCount(0);
            when(voucherRepository.findById(10)).thenReturn(Optional.of(v));

            voucherService.releaseUsage(10);

            assertThat(v.getUsedCount()).isZero();
            verify(voucherRepository, never()).save(any());
        }

        @Test
        @DisplayName("releaseUsage: usedCount null -> không làm gì")
        void releaseUsage_nullCount_noop() {
            Voucher v = validVoucher("SALE50");
            v.setUsedCount(null);
            when(voucherRepository.findById(10)).thenReturn(Optional.of(v));

            voucherService.releaseUsage(10);

            assertThat(v.getUsedCount()).isNull();
            verify(voucherRepository, never()).save(any());
        }

        @Test
        @DisplayName("releaseUsage: voucher không tồn tại -> bỏ qua, không lỗi")
        void releaseUsage_missingVoucher_noop() {
            when(voucherRepository.findById(99)).thenReturn(Optional.empty());

            assertThatCode(() -> voucherService.releaseUsage(99)).doesNotThrowAnyException();
            verify(voucherRepository, never()).save(any());
        }
    }
}
