package com.example.house_moving_service.modules.booking.repository;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingService;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.entity.BookingVoucher;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.catalog.repository.ServiceTypeRepository;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.voucher.enums.DiscountType;
import com.example.house_moving_service.common.enums.PropertyType;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.identity.repository.CustomerRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.voucher.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BookingRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingRouteRepository bookingRouteRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private BookingServiceRepository bookingServiceRepository;

    @Autowired
    private BookingVoucherRepository bookingVoucherRepository;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    private Customer customer1;
    private Customer customer2;
    private Service truckService;
    private Voucher discountVoucher;

    @BeforeEach
    void setUp() {
        // Tạo User & Customer 1
        User user1 = userRepository.save(User.builder()
                .email("test_cust1_" + System.currentTimeMillis() + "@gmail.com")
                .password("$2a$10$dummyHashPassword123")
                .isActive(true)
                .build());

        customer1 = customerRepository.save(Customer.builder()
                .user(user1)
                .fullName("Nguyễn Khách Hàng 1")
                .phoneNumber("0901111111")
                .build());

        // Tạo User & Customer 2
        User user2 = userRepository.save(User.builder()
                .email("test_cust2_" + System.currentTimeMillis() + "@gmail.com")
                .password("$2a$10$dummyHashPassword123")
                .isActive(true)
                .build());

        customer2 = customerRepository.save(Customer.builder()
                .user(user2)
                .fullName("Trần Khách Hàng 2")
                .phoneNumber("0902222222")
                .build());

        // Tạo ServiceType & Service
        ServiceType serviceType = serviceTypeRepository.save(ServiceType.builder()
                .name("Xe tải chuyển nhà")
                .isActive(true)
                .build());

        truckService = serviceRepository.save(Service.builder()
                .serviceType(serviceType)
                .name("Xe tải 2 Tấn")
                .basePrice(new BigDecimal("600000.00"))
                .status(ServiceStatus.ACTIVE)
                .build());

        // Tạo Voucher
        discountVoucher = voucherRepository.save(Voucher.builder()
                .code("VOUCHER_TEST_" + System.currentTimeMillis())
                .title("Giảm giá test 50K")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50000.00"))
                .minOrderValue(new BigDecimal("200000.00"))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("Lưu đồng thời và truy vấn 6 bảng Booking nguyên khối thành công")
    void testSaveAndFindBooking_FullFlow_Success() {
        // 1. Lưu Booking gốc
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingCode("BKG-TEST-001")
                .customer(customer1)
                .totalAmount(new BigDecimal("1500000.00"))
                .discountAmount(new BigDecimal("50000.00"))
                .status(BookingStatus.PENDING)
                .contactName("Anh Hùng")
                .contactPhone("0901234567")
                .note("Dọn nhà vào buổi sáng")
                .build());

        assertThat(booking.getId()).isNotNull();

        // 2. Lưu BookingRoute (1-1)
        BookingRoute route = bookingRouteRepository.save(BookingRoute.builder()
                .booking(booking)
                .pickupAddress("123 Lê Lợi, Q1")
                .pickupPropertyType(PropertyType.HOUSE)
                .dropoffAddress("456 CMT8, Q10")
                .dropoffPropertyType(PropertyType.APARTMENT)
                .estimatedDistanceKm(new BigDecimal("8.50"))
                .build());

        assertThat(route.getId()).isNotNull();

        // 3. Lưu BookingItem
        BookingItem item = bookingItemRepository.save(BookingItem.builder()
                .booking(booking)
                .itemName("Tủ lạnh Side-by-side")
                .quantity(1)
                .isFragile(true)
                .packingRequired(true)
                .build());

        assertThat(item.getId()).isNotNull();

        // 4. Lưu BookingService kèm Snapshot price_applied
        BookingService bService = bookingServiceRepository.save(BookingService.builder()
                .booking(booking)
                .service(truckService)
                .priceApplied(new BigDecimal("600000.00"))
                .quantity(1)
                .build());

        assertThat(bService.getId()).isNotNull();
        assertThat(bService.getPriceApplied()).isEqualByComparingTo("600000.00");

        // 5. Lưu BookingVoucher
        BookingVoucher bVoucher = bookingVoucherRepository.save(BookingVoucher.builder()
                .booking(booking)
                .voucher(discountVoucher)
                .discountAmount(new BigDecimal("50000.00"))
                .build());

        assertThat(bVoucher.getId()).isNotNull();

        // 6. Lưu BookingStatusHistory
        BookingStatusHistory history = bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
                .booking(booking)
                .newStatus(BookingStatus.PENDING)
                .changedBy(customer1.getUser())
                .note("Khách hàng tạo đơn mới")
                .build());

        assertThat(history.getId()).isNotNull();

        // Kiểm tra truy vấn theo mã đơn
        Optional<Booking> foundOpt = bookingRepository.findByBookingCode("BKG-TEST-001");
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getContactName()).isEqualTo("Anh Hùng");
    }

    @Test
    @DisplayName("findAllByCustomerId: Phân trang và đóng gói chuyển đổi thành công sang PageResponse")
    void testFindAllByCustomerId_WithPaginationAndPageResponse() {
        // Tạo 2 bookings cho customer 1
        bookingRepository.save(Booking.builder()
                .bookingCode("BKG-PAGE-001")
                .customer(customer1)
                .totalAmount(new BigDecimal("1000000.00"))
                .status(BookingStatus.PENDING)
                .build());

        bookingRepository.save(Booking.builder()
                .bookingCode("BKG-PAGE-002")
                .customer(customer1)
                .totalAmount(new BigDecimal("2000000.00"))
                .status(BookingStatus.CONFIRMED)
                .build());

        // Tạo 1 booking cho customer 2 (để kiểm tra phân cách khách hàng)
        bookingRepository.save(Booking.builder()
                .bookingCode("BKG-PAGE-003")
                .customer(customer2)
                .totalAmount(new BigDecimal("500000.00"))
                .status(BookingStatus.PENDING)
                .build());

        // Phân trang đơn của customer 1: page 0, size 10
        Page<Booking> page = bookingRepository.findAllByCustomerId(customer1.getId(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);

        // Đóng gói qua PageResponse
        PageResponse<Booking> pageResponse = PageResponse.from(page);

        // Kiểm tra đúng chuẩn hợp đồng PageResponse
        assertThat(pageResponse.getPageNo()).isEqualTo(1); // 1-indexed (0 + 1)
        assertThat(pageResponse.getPageSize()).isEqualTo(10);
        assertThat(pageResponse.getTotalElements()).isEqualTo(2);
        assertThat(pageResponse.getTotalPages()).isEqualTo(1);
        assertThat(pageResponse.isLast()).isTrue();
        assertThat(pageResponse.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("findByIdAndCustomerId: Bảo vệ quyền sở hữu, chống lỗ hổng IDOR")
    void testFindByIdAndCustomerId_IdorProtection() {
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingCode("BKG-IDOR-001")
                .customer(customer1)
                .totalAmount(new BigDecimal("1000000.00"))
                .status(BookingStatus.PENDING)
                .build());

        // Đúng chủ sở hữu (customer 1) -> tìm thấy
        Optional<Booking> rightOwner = bookingRepository.findByIdAndCustomerId(booking.getId(), customer1.getId());
        assertThat(rightOwner).isPresent();

        // Sai chủ sở hữu (customer 2) -> không tìm thấy (IDOR blocked)
        Optional<Booking> wrongOwner = bookingRepository.findByIdAndCustomerId(booking.getId(), customer2.getId());
        assertThat(wrongOwner).isEmpty();
    }

    @Test
    @DisplayName("BookingVoucherRepository: Đếm chính xác tổng lượt dùng và lượt dùng của từng khách hàng")
    void testVoucherUsageCountQueries() {
        // Đơn 1 của Customer 1 dùng voucher
        Booking booking1 = bookingRepository.save(Booking.builder()
                .bookingCode("BKG-VOUCHER-001")
                .customer(customer1)
                .totalAmount(new BigDecimal("1000000.00"))
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingVoucherRepository.save(BookingVoucher.builder()
                .booking(booking1)
                .voucher(discountVoucher)
                .discountAmount(new BigDecimal("50000.00"))
                .build());

        // Đơn 2 của Customer 2 dùng cùng voucher đó
        Booking booking2 = bookingRepository.save(Booking.builder()
                .bookingCode("BKG-VOUCHER-002")
                .customer(customer2)
                .totalAmount(new BigDecimal("800000.00"))
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingVoucherRepository.save(BookingVoucher.builder()
                .booking(booking2)
                .voucher(discountVoucher)
                .discountAmount(new BigDecimal("50000.00"))
                .build());

        // Tổng lượt dùng toàn hệ thống = 2
        long totalUsage = bookingVoucherRepository.countByVoucherId(discountVoucher.getId());
        assertThat(totalUsage).isEqualTo(2);

        // Lượt dùng của Customer 1 = 1
        long user1Usage = bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(
                discountVoucher.getId(), customer1.getId());
        assertThat(user1Usage).isEqualTo(1);

        // Lượt dùng của Customer 2 = 1
        long user2Usage = bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(
                discountVoucher.getId(), customer2.getId());
        assertThat(user2Usage).isEqualTo(1);
    }

    @Test
    @DisplayName("findAllByCustomerIdAndStatus: Lọc chính xác theo trạng thái đơn có phân trang")
    void testFindAllByCustomerIdAndStatus_FiltersProperly() {
        bookingRepository.save(Booking.builder()
                .bookingCode("BKG-STATUS-001")
                .customer(customer1)
                .totalAmount(new BigDecimal("1000000.00"))
                .status(BookingStatus.PENDING)
                .build());

        bookingRepository.save(Booking.builder()
                .bookingCode("BKG-STATUS-002")
                .customer(customer1)
                .totalAmount(new BigDecimal("2000000.00"))
                .status(BookingStatus.COMPLETED)
                .build());

        Page<Booking> pendingPage = bookingRepository.findAllByCustomerIdAndStatus(
                customer1.getId(), BookingStatus.PENDING, PageRequest.of(0, 10));

        assertThat(pendingPage.getTotalElements()).isEqualTo(1);
        assertThat(pendingPage.getContent().get(0).getBookingCode()).isEqualTo("BKG-STATUS-001");
    }

    @Test
    @DisplayName("findByIdForUpdate: Khóa dòng (SELECT ... FOR UPDATE) chạy được trên PostgreSQL và trả đúng đơn")
    void testFindByIdForUpdate_ReturnsBooking() {
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingCode("BKG-LOCK-001")
                .customer(customer1)
                .totalAmount(new BigDecimal("500000.00"))
                .discountAmount(BigDecimal.ZERO)
                .status(BookingStatus.PENDING_SURVEY)
                .build());

        Optional<Booking> locked = bookingRepository.findByIdForUpdate(booking.getId());

        assertThat(locked).isPresent();
        assertThat(locked.get().getBookingCode()).isEqualTo("BKG-LOCK-001");
        assertThat(bookingRepository.findByIdForUpdate(-1)).isEmpty();
    }
}

