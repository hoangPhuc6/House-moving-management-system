package com.example.house_moving_service.modules.identity.service.impl;

import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.repository.StaffRepository;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StaffLookupServiceImpl implements StaffLookupService {

    private final StaffRepository staffRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveStaffWithRole(Long userId, RoleCode role) {
        if (userId == null || role == null) {
            return false;
        }
        return staffRepository.existsActiveByIdAndRole(userId, role);
    }

    @Override
    public Staff getReference(Long userId) {
        return staffRepository.getReferenceById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffSummary> findSummary(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return staffRepository.findById(userId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffSummary> findActiveByRole(RoleCode role) {
        return staffRepository.findAllActiveByRole(role).stream()
                .map(this::toSummary)
                .toList();
    }

    private StaffSummary toSummary(Staff staff) {
        return StaffSummary.builder()
                .userId(staff.getId())
                .staffCode(staff.getStaffCode())
                .fullName(staff.getFullName())
                .email(staff.getUser() != null ? staff.getUser().getEmail() : null)
                .employmentStatus(staff.getEmploymentStatus())
                .build();
    }
}
