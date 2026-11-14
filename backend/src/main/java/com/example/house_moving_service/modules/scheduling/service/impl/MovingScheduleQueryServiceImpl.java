package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.scheduling.dto.request.AvailabilityQuery;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingScheduleFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingStaffAvailabilityResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.mapper.VehicleMapper;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.scheduling.repository.specification.MovingScheduleSpecification;
import com.example.house_moving_service.modules.scheduling.service.MovingScheduleQueryService;
import com.example.house_moving_service.modules.survey.service.SurveyStaffScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovingScheduleQueryServiceImpl implements MovingScheduleQueryService {

    // Xe được phân công: IN_USE vẫn nhận vì có thể rảnh vào khung giờ khác (dùng chung với ManagementMovingScheduleServiceImpl)
    static final Set<VehicleStatus> ASSIGNABLE_VEHICLE_STATUSES = EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.IN_USE);
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("scheduledStartAt"), Sort.Order.asc("id"));
    // ID chuyến luôn >= 1 nên 0 nghĩa là không loại trừ chuyến nào
    private static final int NO_EXCLUDED_SCHEDULE = 0;

    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;
    private final ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final StaffLookupService staffLookupService;
    private final SurveyStaffScheduleService surveyStaffScheduleService;
    private final MovingScheduleMapper movingScheduleMapper;
    private final VehicleMapper vehicleMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MovingScheduleSummaryResponse> search(MovingScheduleFilterRequest filter, Pageable pageable) {
        // Không truyền sort thì sắp theo giờ hẹn bắt đầu (lịch gần nhất trước)
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        Page<MovingSchedule> page = movingScheduleRepository.findAll(MovingScheduleSpecification.filter(filter), sorted);

        // Nạp phân công của cả trang bằng 2 truy vấn rồi nhóm theo chuyến (tránh N+1)
        List<Integer> ids = page.getContent().stream().map(MovingSchedule::getId).toList();
        Map<Integer, List<ScheduleStaffAssignment>> staffBySchedule = ids.isEmpty() ? Map.of()
                : staffAssignmentRepository.findAllWithStaffByScheduleIds(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getMovingSchedule().getId()));
        Map<Integer, List<ScheduleVehicleAssignment>> vehiclesBySchedule = ids.isEmpty() ? Map.of()
                : vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getMovingSchedule().getId()));

        return PageResponse.from(page.map(s -> movingScheduleMapper.toSummary(s,
                staffBySchedule.getOrDefault(s.getId(), List.of()),
                vehiclesBySchedule.getOrDefault(s.getId(), List.of()))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> findAvailableVehicles(AvailabilityQuery query) {
        validate(query);
        return vehicleRepository.findAvailable(query.getStart(), query.getEnd(), excludedScheduleId(query),
                        ASSIGNABLE_VEHICLE_STATUSES,
                        EnumSet.of(AssignmentStatus.CANCELLED, AssignmentStatus.COMPLETED),
                        MovingScheduleStatus.UNFINISHED)
                .stream()
                .filter(v -> query.getVehicleTypeId() == null
                        || Objects.equals(v.getVehicleType().getId(), query.getVehicleTypeId()))
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovingStaffAvailabilityResponse> findStaffAvailability(AvailabilityQuery query) {
        validate(query);
        List<StaffSummary> movers = staffLookupService.findActiveByRole(RoleCode.MOVER);
        if (movers.isEmpty()) {
            return List.of();
        }
        Set<Long> busyMoving = new HashSet<>(staffAssignmentRepository.findConflictingStaffIds(
                movers.stream().map(StaffSummary::userId).toList(), query.getStart(), query.getEnd(), excludedScheduleId(query)));

        return movers.stream()
                .map(m -> {
                    String busyReason = null;
                    if (busyMoving.contains(m.userId())) {
                        busyReason = MovingStaffAvailabilityResponse.BUSY_MOVING;
                    } else if (surveyStaffScheduleService.hasSurveyBetween(m.userId(), query.getStart(), query.getEnd())) {
                        busyReason = MovingStaffAvailabilityResponse.BUSY_SURVEY;
                    }
                    return MovingStaffAvailabilityResponse.builder()
                            .userId(m.userId())
                            .staffCode(m.staffCode())
                            .fullName(m.fullName())
                            .available(busyReason == null)
                            .busyReason(busyReason)
                            .build();
                })
                .toList();
    }

    private static void validate(AvailabilityQuery query) {
        if (query == null || query.getStart() == null || query.getEnd() == null) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, "phải truyền start và end (vd 2026-11-20T08:00:00)");
        }
        if (!query.getEnd().isAfter(query.getStart())) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, "end phải sau start");
        }
    }

    private static int excludedScheduleId(AvailabilityQuery query) {
        return query.getExcludeScheduleId() != null ? query.getExcludeScheduleId() : NO_EXCLUDED_SCHEDULE;
    }
}
