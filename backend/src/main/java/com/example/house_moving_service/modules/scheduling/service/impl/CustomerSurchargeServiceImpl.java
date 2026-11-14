package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.scheduling.dto.request.CustomerRejectSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.mapper.SurchargeMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.service.CustomerSurchargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerSurchargeServiceImpl implements CustomerSurchargeService {

    private final BookingSurchargeRepository surchargeRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final SurchargeSupport surchargeSupport;
    private final SurchargeMapper surchargeMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CustomerSurchargeSummaryResponse getSurcharges(Integer bookingId, Long customerUserId) {
        requireOwner(bookingId, customerUserId, ErrorCode.ERR_BOOKING_FORBIDDEN);
        return surchargeMapper.toCustomerSummary(surchargeRepository.findAllByBookingIdOrderByRequestedAtAscIdAsc(bookingId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerSurchargeResponse accept(Integer bookingId, Integer id, Long customerUserId) {
        requireOwner(bookingId, customerUserId);
        // Khóa dòng phụ phí rồi khóa dòng đơn, cùng thứ tự với mọi luồng ghi khác để tránh deadlock
        BookingSurcharge surcharge = loadWaitingForUpdate(bookingId, id);
        BookingSnapshot locked = bookingWorkflowService.lockForUpdate(bookingId);
        if (locked.status() == BookingStatus.CANCELLED) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_BOOKING_INVALID_STATUS);
        }

        BigDecimal newTotal = bookingWorkflowService.addToTotalAmount(bookingId, surcharge.getAmount());
        surcharge.setStatus(SurchargeStatus.ACCEPTED);
        surcharge.setCustomerRespondedAt(LocalDateTime.now(clock));
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} accepted by customer user id: {}, booking id: {} total amount now {}",
                id, customerUserId, bookingId, newTotal);
        return surchargeMapper.toCustomerResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerSurchargeResponse reject(Integer bookingId, Integer id, CustomerRejectSurchargeRequest request,
                                            Long customerUserId) {
        requireOwner(bookingId, customerUserId);
        BookingSurcharge surcharge = loadWaitingForUpdate(bookingId, id);
        surcharge.setStatus(SurchargeStatus.REJECTED_BY_CUSTOMER);
        surcharge.setCustomerRespondedAt(LocalDateTime.now(clock));
        surcharge.setCustomerNote(request.getCustomerNote().trim());
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} rejected by customer user id: {} (booking id: {})", id, customerUserId, bookingId);
        return surchargeMapper.toCustomerResponse(saved);
    }

    // Thao tác (xác nhận / từ chối) trên đơn của người khác → ERR_SURCHARGE_FORBIDDEN
    private void requireOwner(Integer bookingId, Long customerUserId) {
        requireOwner(bookingId, customerUserId, ErrorCode.ERR_SURCHARGE_FORBIDDEN);
    }

    // Đơn tồn tại (ERR_BOOKING_NOT_FOUND) và thuộc người gọi (lỗi truyền vào)
    private void requireOwner(Integer bookingId, Long customerUserId, ErrorCode forbidden) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (booking.customerUserId() == null || !booking.customerUserId().equals(customerUserId)) {
            throw new AppException(forbidden);
        }
    }

    // Khoản phải thuộc đơn trong đường dẫn và khách được thấy (nếu không coi như không tồn tại), đang WAITING_CUSTOMER
    private BookingSurcharge loadWaitingForUpdate(Integer bookingId, Integer id) {
        BookingSurcharge surcharge = surchargeSupport.loadForUpdate(id);
        if (!surcharge.getBooking().getId().equals(bookingId)
                || !SurchargeStatus.VISIBLE_TO_CUSTOMER.contains(surcharge.getStatus())) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_NOT_FOUND, id);
        }
        SurchargeSupport.requireStatus(surcharge, EnumSet.of(SurchargeStatus.WAITING_CUSTOMER));
        return surcharge;
    }
}
