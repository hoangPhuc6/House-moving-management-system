package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SendSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeReviewNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.BookingSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.helper.SurchargePricingResolver;
import com.example.house_moving_service.modules.scheduling.mapper.SurchargeMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.specification.SurchargeSpecification;
import com.example.house_moving_service.modules.scheduling.service.ManagementSurchargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@Slf4j
@RequiredArgsConstructor
public class ManagementSurchargeServiceImpl implements ManagementSurchargeService {

    // Khoản cần xử lý: cũ nhất trước
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id"));

    private final BookingSurchargeRepository surchargeRepository;
    private final MovingScheduleRepository movingScheduleRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final UserLookupService userLookupService;
    private final SurchargePricingResolver pricingResolver;
    private final SurchargeSupport surchargeSupport;
    private final SurchargeMapper surchargeMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SurchargeResponse> search(SurchargeFilterRequest filter, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return PageResponse.from(surchargeRepository
                .findAll(SurchargeSpecification.filterSurcharges(filter), sorted)
                .map(surchargeMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingSurchargeSummaryResponse getByBooking(Integer bookingId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        return surchargeMapper.toBookingSummary(booking,
                surchargeRepository.findAllByBookingIdOrderByRequestedAtAscIdAsc(bookingId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurchargeResponse create(Integer bookingId, CreateSurchargeRequest request, Long userId) {
        SurchargeSupport.requireBookingOpen(bookingWorkflowService.getSnapshot(bookingId));
        MovingSchedule schedule = null;
        if (request.getScheduleId() != null) {
            schedule = movingScheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, request.getScheduleId()));
            if (!schedule.getBooking().getId().equals(bookingId)) {
                throw new AppException(ErrorCode.ERR_SURCHARGE_SCHEDULE_INVALID, "chuyến không thuộc đơn hàng");
            }
            SurchargeSupport.requireScheduleOpen(schedule);
        }

        // Điều phối viên tự tạo thì coi như đã duyệt và gửi khách ngay
        LocalDateTime now = LocalDateTime.now(clock);
        BookingSurcharge surcharge = surchargeSupport.build(bookingId, schedule, request,
                SurchargeStatus.WAITING_CUSTOMER, userId, now);
        surcharge.setReviewedBy(userLookupService.getUserReference(userId));
        surcharge.setReviewedAt(now);
        surcharge.setSentToCustomerAt(now);
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} ({} x {} = {}) created and sent to customer for booking id: {} by user id: {}",
                saved.getId(), saved.getQuantity(), saved.getUnitPrice(), saved.getAmount(), bookingId, userId);
        return surchargeMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurchargeResponse send(Integer id, SendSurchargeRequest request, Long userId) {
        BookingSurcharge surcharge = surchargeSupport.loadForUpdate(id, EnumSet.of(SurchargeStatus.PROPOSED));
        SurchargeSupport.requireBookingOpen(bookingWorkflowService.getSnapshot(surcharge.getBooking().getId()));

        int quantity = surcharge.getQuantity();
        BigDecimal unitPrice = surcharge.getUnitPrice();
        String reviewNote = null;
        if (request != null) {
            if (request.getQuantity() != null) {
                pricingResolver.validateQuantity(request.getQuantity());
                quantity = request.getQuantity();
            }
            unitPrice = pricingResolver.resolveOnSend(surcharge.getSurchargeType(), unitPrice, request.getUnitPrice());
            if (request.getReviewNote() != null && !request.getReviewNote().isBlank()) {
                reviewNote = request.getReviewNote().trim();
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        surcharge.setQuantity(quantity);
        surcharge.setUnitPrice(unitPrice);
        surcharge.setAmount(SurchargePricingResolver.amountOf(quantity, unitPrice));
        surcharge.setStatus(SurchargeStatus.WAITING_CUSTOMER);
        surcharge.setReviewedBy(userLookupService.getUserReference(userId));
        surcharge.setReviewedAt(now);
        surcharge.setReviewNote(reviewNote);
        surcharge.setSentToCustomerAt(now);
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} sent to customer by user id: {} ({} x {} = {})",
                id, userId, quantity, unitPrice, saved.getAmount());
        return surchargeMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurchargeResponse reject(Integer id, SurchargeReviewNoteRequest request, Long userId) {
        BookingSurcharge surcharge = surchargeSupport.loadForUpdate(id, EnumSet.of(SurchargeStatus.PROPOSED));
        review(surcharge, SurchargeStatus.REJECTED, request.getReviewNote(), userId);
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} rejected by user id: {}", id, userId);
        return surchargeMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurchargeResponse withdraw(Integer id, SurchargeReviewNoteRequest request, Long userId) {
        BookingSurcharge surcharge = surchargeSupport.loadForUpdate(id, SurchargeStatus.WITHDRAWABLE);
        SurchargeStatus from = surcharge.getStatus();
        review(surcharge, SurchargeStatus.CANCELLED, request.getReviewNote(), userId);
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} withdrawn ({} -> CANCELLED) by user id: {}", id, from, userId);
        return surchargeMapper.toResponse(saved);
    }

    private void review(BookingSurcharge surcharge, SurchargeStatus to, String reviewNote, Long userId) {
        surcharge.setStatus(to);
        surcharge.setReviewedBy(userLookupService.getUserReference(userId));
        surcharge.setReviewedAt(LocalDateTime.now(clock));
        surcharge.setReviewNote(reviewNote.trim());
    }
}
