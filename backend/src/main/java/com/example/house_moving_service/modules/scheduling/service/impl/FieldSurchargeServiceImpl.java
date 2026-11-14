package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import com.example.house_moving_service.modules.payment.service.PaymentManagementService;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldScheduleSurchargesResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.mapper.SurchargeMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.service.FieldSurchargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FieldSurchargeServiceImpl implements FieldSurchargeService {

    private final BookingSurchargeRepository surchargeRepository;
    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final UserLookupService userLookupService;
    private final PaymentManagementService paymentManagementService;
    private final SurchargeSupport surchargeSupport;
    private final SurchargeMapper surchargeMapper;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurchargeResponse propose(Integer scheduleId, CreateSurchargeRequest request, Long userId) {
        MovingSchedule schedule = movingScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId));
        requireSupervisor(scheduleId, userId);
        SurchargeSupport.requireScheduleOpen(schedule);
        Integer bookingId = schedule.getBooking().getId();
        SurchargeSupport.requireBookingOpen(bookingWorkflowService.getSnapshot(bookingId));

        // Chuyến lấy theo đường dẫn, bỏ qua request.scheduleId
        BookingSurcharge surcharge = surchargeSupport.build(bookingId, schedule, request,
                SurchargeStatus.PROPOSED, userId, LocalDateTime.now(clock));
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} ({} x {} = {}) proposed on moving schedule id: {} by supervisor id: {}",
                saved.getId(), saved.getQuantity(), saved.getUnitPrice(), saved.getAmount(), scheduleId, userId);
        return surchargeMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FieldScheduleSurchargesResponse getBySchedule(Integer scheduleId, Long userId) {
        MovingSchedule schedule = movingScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId));
        requireAssigned(scheduleId, userId);
        Integer bookingId = schedule.getBooking().getId();

        List<BookingSurcharge> surcharges = surchargeRepository.findAllForSchedule(bookingId, scheduleId);
        // Cờ chặn tính trên toàn đơn: khoản của chuyến khác cùng đơn cũng chặn bắt đầu chuyến này
        List<Integer> blockingIds = surchargeRepository.findIdsByBookingIdAndStatusIn(bookingId, SurchargeStatus.BLOCKING_START);
        return FieldScheduleSurchargesResponse.builder()
                .scheduleId(scheduleId)
                .bookingId(bookingId)
                .startBlocked(!blockingIds.isEmpty())
                .blockingSurchargeIds(blockingIds)
                .acceptedTotal(SurchargeMapper.acceptedTotal(surcharges))
                .uncollectedTotal(SurchargeMapper.uncollectedTotal(surcharges))
                .surcharges(surcharges.stream().map(surchargeMapper::toResponse).toList())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurchargeResponse cancel(Integer id, Long userId) {
        BookingSurcharge surcharge = surchargeSupport.loadForUpdate(id);
        if (surcharge.getRequestedBy() == null || !surcharge.getRequestedBy().getId().equals(userId)) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_FORBIDDEN);
        }
        SurchargeSupport.requireStatus(surcharge, EnumSet.of(SurchargeStatus.PROPOSED));
        surcharge.setStatus(SurchargeStatus.CANCELLED);
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} cancelled by proposer id: {}", id, userId);
        return surchargeMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurchargeResponse collect(Integer id, Long userId) {
        BookingSurcharge surcharge = surchargeSupport.loadForUpdate(id);
        Integer bookingId = surcharge.getBooking().getId();
        if (surcharge.getSchedule() != null) {
            requireSupervisor(surcharge.getSchedule().getId(), userId);
        } else if (!staffAssignmentRepository.existsActiveRoleOnBooking(bookingId, userId, StaffAssignmentRole.SUPERVISOR,
                AssignmentStatus.CANCELLED, MovingScheduleStatus.CANCELLED)) {
            throw new AppException(ErrorCode.ERR_MOVING_NOT_SUPERVISOR);
        }
        SurchargeSupport.requireStatus(surcharge, EnumSet.of(SurchargeStatus.ACCEPTED));
        if (surcharge.isCollected()) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_STATUS);
        }

        // Phiếu thu tham gia cùng transaction: lỗi ở bước nào thì cả phiếu thu lẫn cờ đã thu đều rollback
        PaymentResponse payment = paymentManagementService.recordManualPayment(CreatePaymentRequest.builder()
                .bookingId(bookingId)
                .paymentMethod(PaymentMethod.CASH)
                .paymentType(PaymentType.PARTIAL)
                .amount(surcharge.getAmount())
                .note("Thu phụ phí #" + id + " tại hiện trường")
                .build(), userId);

        surcharge.setPaymentId(payment.getId());
        surcharge.setCollectedBy(userLookupService.getUserReference(userId));
        surcharge.setCollectedAt(LocalDateTime.now(clock));
        BookingSurcharge saved = surchargeRepository.save(surcharge);

        log.info("Surcharge id: {} ({}) collected by supervisor id: {}, payment id: {}",
                id, saved.getAmount(), userId, payment.getId());
        return surchargeMapper.toResponse(saved);
    }

    // Có dòng phân công chưa hủy trên chuyến (mọi role)
    private ScheduleStaffAssignment requireAssigned(Integer scheduleId, Long userId) {
        return staffAssignmentRepository.findByMovingScheduleIdAndStaffId(scheduleId, userId)
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_FORBIDDEN));
    }

    private void requireSupervisor(Integer scheduleId, Long userId) {
        if (requireAssigned(scheduleId, userId).getRole() != StaffAssignmentRole.SUPERVISOR) {
            throw new AppException(ErrorCode.ERR_MOVING_NOT_SUPERVISOR);
        }
    }
}
