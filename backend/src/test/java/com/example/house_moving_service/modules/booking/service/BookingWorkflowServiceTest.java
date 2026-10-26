package com.example.house_moving_service.modules.booking.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;
import com.example.house_moving_service.modules.booking.dto.response.BookingItemSnapshot;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.mapper.BookingMapper;
import com.example.house_moving_service.modules.booking.repository.BookingItemRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.service.impl.BookingWorkflowServiceImpl;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.mapper.UserMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingWorkflowServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingRouteRepository bookingRouteRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;
    @Mock
    private EntityManager entityManager;

    private BookingWorkflowServiceImpl bookingWorkflowService;

    private Booking booking;

    @BeforeEach
    void setUp() {
        // BookingMapper thật để kiểm tra luôn phần tách chuỗi pickupAccess / dropoffAccess
        BookingMapper bookingMapper = new BookingMapper(mock(UserMapper.class));
        bookingWorkflowService = new BookingWorkflowServiceImpl(
                bookingRepository,
                bookingRouteRepository,
                bookingItemRepository,
                bookingStatusHistoryRepository,
                bookingMapper,
                entityManager);

        Customer customer = Customer.builder().id(7L).fullName("Võ Minh Khách").build();
        booking = Booking.builder()
                .id(100)
                .bookingCode("BKG001")
                .customer(customer)
                .status(BookingStatus.PENDING_SURVEY)
                .contactName("Anh Khách")
                .contactPhone("0901234567")
                .desiredStartAt(LocalDateTime.of(2026, 11, 5, 8, 0))
                .note("Gọi trước 30 phút")
                .build();
    }

    @Test
    @DisplayName("getSnapshot: Should map booking fields")
    void testGetSnapshot_Success() {
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

        BookingSnapshot snapshot = bookingWorkflowService.getSnapshot(100);

        assertThat(snapshot).isEqualTo(BookingSnapshot.builder()
                .id(100)
                .bookingCode("BKG001")
                .status(BookingStatus.PENDING_SURVEY)
                .customerUserId(7L)
                .customerName("Võ Minh Khách")
                .contactName("Anh Khách")
                .contactPhone("0901234567")
                .desiredStartAt(LocalDateTime.of(2026, 11, 5, 8, 0))
                .note("Gọi trước 30 phút")
                .build());
    }

    @Test
    @DisplayName("getSnapshot: Should throw ERR_BOOKING_NOT_FOUND when booking does not exist")
    void testGetSnapshot_NotFound() {
        when(bookingRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingWorkflowService.getSnapshot(999))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }

    @Test
    @DisplayName("getReference: Should throw ERR_BOOKING_NOT_FOUND when booking does not exist")
    void testGetReference_NotFound() {
        when(bookingRepository.existsById(999)).thenReturn(false);

        assertThatThrownBy(() -> bookingWorkflowService.getReference(999))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
        verify(bookingRepository, never()).getReferenceById(any());
    }

    @Test
    @DisplayName("getItems: Should map all flags of declared items")
    void testGetItems_Success() {
        BookingItem item = BookingItem.builder()
                .id(1).booking(booking).itemName("Tủ lạnh").quantity(1)
                .estimatedVolumeM3(BigDecimal.valueOf(1.2))
                .isFragile(true).packingRequired(false).disassemblyRequired(true)
                .note("Hai cánh")
                .build();
        when(bookingRepository.existsById(100)).thenReturn(true);
        when(bookingItemRepository.findAllByBookingId(100)).thenReturn(List.of(item));

        List<BookingItemSnapshot> items = bookingWorkflowService.getItems(100);

        assertThat(items).containsExactly(BookingItemSnapshot.builder()
                .id(1)
                .itemName("Tủ lạnh")
                .quantity(1)
                .estimatedVolumeM3(BigDecimal.valueOf(1.2))
                .isFragile(true)
                .packingRequired(false)
                .disassemblyRequired(true)
                .note("Hai cánh")
                .build());
    }

    @Test
    @DisplayName("getRoute: Should return route with access info parsed from note strings")
    void testGetRoute_ParsesAccessInfo() {
        BookingRoute route = BookingRoute.builder()
                .id(5).booking(booking)
                .pickupAddress("12 Lê Lợi")
                .pickupNote("Tầng: 3, Thang máy: Không, Cự ly hẻm: 15m (Phải vác thang bộ từ lầu 3 xuống)")
                .dropoffAddress("34 Nguyễn Huệ")
                .dropoffNote("Tầng: 12, Thang máy: Có, Cự ly hẻm: 0m")
                .build();
        when(bookingRouteRepository.findByBookingId(100)).thenReturn(Optional.of(route));

        BookingRouteResponse response = bookingWorkflowService.getRoute(100);

        assertThat(response.getPickupAccess())
                .isEqualTo(new RouteAccessInfo(3, false, 15, "Phải vác thang bộ từ lầu 3 xuống"));
        assertThat(response.getDropoffAccess()).isEqualTo(new RouteAccessInfo(12, true, 0, null));
        // Giữ chuỗi gốc cho FE cũ
        assertThat(response.getPickupNote()).startsWith("Tầng: 3");
    }

    @Test
    @DisplayName("updateRouteAccess: Should rewrite note strings and keep the side passed as null")
    void testUpdateRouteAccess_Success() {
        BookingRoute route = BookingRoute.builder()
                .id(5).booking(booking)
                .pickupNote("Tầng: 0, Thang máy: Không, Cự ly hẻm: 0m")
                .dropoffNote("Tầng: 2, Thang máy: Có, Cự ly hẻm: 0m")
                .build();
        when(bookingRouteRepository.findByBookingId(100)).thenReturn(Optional.of(route));

        bookingWorkflowService.updateRouteAccess(100, new RouteAccessInfo(4, false, 20, "Hẻm xe máy"), null);

        assertThat(route.getPickupNote()).isEqualTo("Tầng: 4, Thang máy: Không, Cự ly hẻm: 20m (Hẻm xe máy)");
        assertThat(route.getDropoffNote()).isEqualTo("Tầng: 2, Thang máy: Có, Cự ly hẻm: 0m");
        verify(bookingRouteRepository).save(route);
    }

    @Test
    @DisplayName("updateRouteAccess: Should throw ERR_BOOKING_NOT_FOUND when route does not exist")
    void testUpdateRouteAccess_NotFound() {
        when(bookingRouteRepository.findByBookingId(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingWorkflowService.updateRouteAccess(999, RouteAccessInfo.EMPTY, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }

    @Test
    @DisplayName("changeStatus: Should change status with row lock and record history")
    void testChangeStatus_Success() {
        User actor = User.builder().id(3L).build();
        when(bookingRepository.findByIdForUpdate(100)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entityManager.getReference(User.class, 3L)).thenReturn(actor);

        bookingWorkflowService.changeStatus(100, Set.of(BookingStatus.PENDING_SURVEY), BookingStatus.SURVEYING, 3L, "Bắt đầu khảo sát");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SURVEYING);
        ArgumentCaptor<BookingStatusHistory> historyCaptor = ArgumentCaptor.forClass(BookingStatusHistory.class);
        verify(bookingStatusHistoryRepository).save(historyCaptor.capture());
        BookingStatusHistory history = historyCaptor.getValue();
        assertThat(history.getOldStatus()).isEqualTo(BookingStatus.PENDING_SURVEY);
        assertThat(history.getNewStatus()).isEqualTo(BookingStatus.SURVEYING);
        assertThat(history.getChangedBy()).isSameAs(actor);
        assertThat(history.getNote()).isEqualTo("Bắt đầu khảo sát");
    }

    @Test
    @DisplayName("changeStatus: Should throw ERR_BOOKING_INVALID_STATUS when current status is not allowed")
    void testChangeStatus_InvalidFromStatus() {
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findByIdForUpdate(100)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingWorkflowService.changeStatus(
                100, Set.of(BookingStatus.PENDING_SURVEY), BookingStatus.SURVEYING, 3L, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_INVALID_STATUS);
        verify(bookingRepository, never()).save(any());
        verify(bookingStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: Should do nothing when booking is already in the target status")
    void testChangeStatus_AlreadyInTargetStatus() {
        booking.setStatus(BookingStatus.SURVEYING);
        when(bookingRepository.findByIdForUpdate(100)).thenReturn(Optional.of(booking));

        bookingWorkflowService.changeStatus(100, Set.of(BookingStatus.PENDING_SURVEY), BookingStatus.SURVEYING, 3L, null);

        verify(bookingRepository, never()).save(any());
        verify(bookingStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus: Should throw ERR_BOOKING_NOT_FOUND when booking does not exist")
    void testChangeStatus_NotFound() {
        when(bookingRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingWorkflowService.changeStatus(
                999, Set.of(BookingStatus.PENDING_SURVEY), BookingStatus.SURVEYING, 3L, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }
}
