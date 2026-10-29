package com.example.house_moving_service.modules.identity.service;

import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.repository.StaffRepository;
import com.example.house_moving_service.modules.identity.service.impl.StaffLookupServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffLookupServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private StaffLookupServiceImpl staffLookupService;

    private Staff mover(Long id, String name) {
        User user = User.builder().id(id).email("mover" + id + "@housemoving.vn").build();
        return Staff.builder()
                .id(id)
                .user(user)
                .staffCode("STF-00" + id)
                .fullName(name)
                .employmentStatus("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("findSummary: Should map all summary fields")
    void testFindSummary_Success() {
        when(staffRepository.findById(3L)).thenReturn(Optional.of(mover(3L, "Lê Hoàng Nhân Công")));

        Optional<StaffSummary> summary = staffLookupService.findSummary(3L);

        assertThat(summary).contains(StaffSummary.builder()
                .userId(3L)
                .staffCode("STF-003")
                .fullName("Lê Hoàng Nhân Công")
                .email("mover3@housemoving.vn")
                .employmentStatus("ACTIVE")
                .build());
    }

    @Test
    @DisplayName("findSummary: Should return empty when staff does not exist or id is null")
    void testFindSummary_NotFound() {
        when(staffRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(staffLookupService.findSummary(999L)).isEmpty();
        assertThat(staffLookupService.findSummary(null)).isEmpty();
    }

    @Test
    @DisplayName("findActiveByRole: Should map every staff returned by repository")
    void testFindActiveByRole_Success() {
        when(staffRepository.findAllActiveByRole(RoleCode.MOVER))
                .thenReturn(List.of(mover(3L, "Lê Hoàng Nhân Công"), mover(4L, "Phạm Minh Tài Xế")));

        List<StaffSummary> movers = staffLookupService.findActiveByRole(RoleCode.MOVER);

        assertThat(movers).extracting(StaffSummary::userId).containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("isActiveStaffWithRole: Should delegate to repository and return false for null input without querying")
    void testIsActiveStaffWithRole() {
        when(staffRepository.existsActiveByIdAndRole(3L, RoleCode.MOVER)).thenReturn(true);

        assertThat(staffLookupService.isActiveStaffWithRole(3L, RoleCode.MOVER)).isTrue();
        assertThat(staffLookupService.isActiveStaffWithRole(null, RoleCode.MOVER)).isFalse();
        assertThat(staffLookupService.isActiveStaffWithRole(3L, null)).isFalse();
        // Chỉ lần gọi hợp lệ chạm tới repository; 2 lần truyền null bị chặn trước
        verify(staffRepository, times(1)).existsActiveByIdAndRole(any(), any());
    }

    @Test
    @DisplayName("getReference: Should return proxy from repository")
    void testGetReference() {
        Staff proxy = mover(3L, "Lê Hoàng Nhân Công");
        when(staffRepository.getReferenceById(3L)).thenReturn(proxy);

        assertThat(staffLookupService.getReference(3L)).isSameAs(proxy);
    }
}
