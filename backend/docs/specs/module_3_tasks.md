### **BẢNG TỔNG QUAN PHÂN RÃ ATOMIC TASKS (MODULE 3\)**

| Task ID | Tên Atomic Task | Trọng tâm duy nhất | Phụ thuộc |
| :---- | :---- | :---- | :---- |
| **TASK-3.1** | Khởi tạo Database Migration, Entities & Repositories | Tạo 6 bảng DB, cấu hình Enums, ánh xạ JPA, thiết lập khóa ngoại và các hàm Repository đếm số lượng voucher. | Không |
| **TASK-3.2** | Xây dựng DTOs, Validation & Bổ sung ErrorCode | Đóng gói Request/Response, ràng buộc dữ liệu đầu vào đa tầng, bổ sung mã lỗi nghiệp vụ cho chốt đơn. | TASK-3.1 |
| **TASK-3.3** | Cài đặt Lõi Nghiệp vụ Chốt đơn (createBooking) | Transaction xử lý lưu đồng thời 6 bảng, gọi ngầm PricingService và gán snapshot đơn giá, kiểm tra limit voucher. | TASK-3.2, TASK-2.6 |
| **TASK-3.4** | Cài đặt Máy trạng thái & Truy vấn (BookingService) | Logic Hủy đơn (Ghi lịch sử, kiểm tra trạng thái cho phép), chống lỗi IDOR, tra cứu danh sách có phân trang. | TASK-3.3 |
| **TASK-3.5** | Xây dựng REST Controller cho Booking | Expose API /api/v1/bookings/\*\*, yêu cầu xác thực JWT, bảo vệ bằng @PreAuthorize. | TASK-3.4 |
| **TASK-3.6** | Viết Bộ Unit Test Kiểm thử Transaction & State | Kiểm thử rollback khi lỗi lưu bảng, chặn hủy đơn sai trạng thái, và kiểm thử vượt quá giới hạn voucher. | TASK-3.5 |

### 

### 

### 

### **ĐẶC TẢ CHI TIẾT TỪNG ATOMIC TASK**

### **TASK-3.1: Khởi tạo Cơ sở Dữ liệu & Data Access Layer (Migration, Entities & Repositories)**

* **Mục tiêu duy nhất:** Thiết lập cấu trúc lưu trữ nguyên khối cho 6 bảng, bảo toàn tính toàn vẹn dữ liệu qua Foreign Keys, ánh xạ Entity chính xác theo ERD, và cung cấp các hàm truy vấn Repository đặc thù (đặc biệt là logic đếm voucher).  
* **Phụ thuộc:** Không.

#### **1\. Artifacts cần tạo (Checklist Files)**

* **Database Migration Script:**  
  * src/main/resources/db/migration/V8\_\_create\_booking\_tables.sql: Tạo 6 bảng (bookings, booking\_routes, booking\_items, booking\_services, booking\_vouchers, booking\_status\_history).   
* **ORM / JPA Entities:**  
  * entity/enums/BookingStatus.java: PENDING\_SURVEY, CONFIRMED, IN\_PROGRESS, COMPLETED, CANCELLED.  
  * entity/enums/PropertyType.java: HOUSE, APARTMENT, ALLEY, OFFICE.  
  * entity/Booking.java: Gắn @OneToOne với BookingRoute, @OneToMany với items, services, vouchers, histories.  
  * entity/BookingRoute.java, BookingItem.java, BookingService.java, BookingVoucher.java, BookingStatusHistory.java.  
* **Data Repositories:**  
  * repository/BookingRepository.java: Kế thừa JpaRepository\<Booking, Integer\>.  
  * repository/BookingVoucherRepository.java.  
  * repository/BookingStatusHistoryRepository.java.

#### **2\. Hàm Repository bắt buộc & Ý nghĩa kỹ thuật**

* long countByVoucherId(Integer voucherId): Đếm tổng số lần một mã giảm giá đã được sử dụng trên toàn hệ thống (phục vụ đối chiếu total\_usage\_limit).  
* long countByVoucherIdAndBooking\_CustomerId(Integer voucherId, Integer customerId): Đếm số lần một khách hàng cụ thể đã sử dụng voucher này (phục vụ đối chiếu usage\_per\_user\_limit).  
* Page\<Booking\> findAllByCustomerId(Integer customerId, Pageable pageable): Lấy danh sách lịch sử đơn hàng của 1 user kèm phân trang.

#### **3\. Tiêu chí nghiệm thu (Definition of Done \- DoD)**

* Script V8 chạy thành công, tạo đủ 6 bảng với ràng buộc Foreign Key trỏ đúng về bảng users, services, vouchers.  
* Các trường Enum trong Entity được đánh dấu @Enumerated(EnumType.STRING).  
* Không sử dụng CascadeType.ALL một cách bừa bãi. Việc lưu các bảng con sẽ được thực thi rõ ràng (explicit save) trong tầng Service để dễ kiểm soát transaction.

### **TASK-3.2: Xây dựng DTOs, Validation & Bổ sung ErrorCode cho Booking**

* **Mục tiêu duy nhất:** Xây dựng hàng rào phòng ngự dữ liệu tại tầng Controller. Tái sử dụng các cấu trúc DTO đã có từ Module 2 (Pricing) nhưng điều chỉnh phù hợp cho việc chốt đơn.  
* **Phụ thuộc:** TASK-3.1.

#### **1\. Artifacts cần tạo & cập nhật**

* **Request DTOs:**  
  * dto/request/booking/CreateBookingRequest.java: Tương tự PricingCalculationRequest nhưng bổ sung contactName, contactPhone, note.  
  * dto/request/booking/CancelBookingRequest.java: Chứa trường reason (Lý do hủy đơn).  
* **Response DTOs:**  
  * dto/response/booking/BookingListResponse.java: Trả về thông tin tóm tắt cho màn hình danh sách (id, code, status, totalAmount, pickupAddress, dropoffAddress).  
  * dto/response/booking/BookingDetailResponse.java: Trả về đầy đủ thông tin chi tiết (gồm cả list đồ đạc, dịch vụ, lịch sử trạng thái).  
* **Cập nhật exception/ErrorCode.java:**  
  * ERR\_BOOKING\_NOT\_FOUND (404)  
  * ERR\_BOOKING\_INVALID\_STATUS (400): "Trạng thái đơn hàng hiện tại không cho phép thực hiện thao tác này."  
  * ERR\_VOUCHER\_LIMIT\_REACHED (400): "Voucher đã đạt giới hạn sử dụng tối đa."  
  * ERR\_FORBIDDEN (403): "Bạn không có quyền truy cập đơn hàng này."

#### **2\. Chi tiết Ràng buộc Validation**

* CreateBookingRequest: @Valid toàn bộ nested object (route, items). contactPhone phải thỏa mãn Regex số điện thoại VN (@Pattern(regexp \= "^(0|\\\\+84)\[3|5|7|8|9\]\[0-9\]{8}\$")).  
* CancelBookingRequest: reason @NotBlank(message \= "Vui lòng nhập lý do hủy đơn"), @Size(max \= 500\).

#### **3\. Tiêu chí nghiệm thu (DoD)**

* Vi phạm Regex số điện thoại hoặc thiếu reason khi hủy đơn sẽ bị GlobalExceptionHandler chặn lại với HTTP 400 và báo lỗi tiếng Việt chính xác.

### **TASK-3.3: Cài đặt Lõi Nghiệp vụ Chốt đơn (createBooking)**

* **Mục tiêu duy nhất:** Đảm bảo tính toàn vẹn ACID của giao dịch chốt đơn. Kết nối với Module 2 để không tin tưởng bất kỳ con số tiền tệ nào từ Client gửi lên.  
* **Phụ thuộc:** TASK-3.2, TASK-2.6 (PricingService).

#### **1\. Artifacts cần tạo**

* util/BookingCodeGenerator.java: Hàm sinh mã đơn ngẫu nhiên (Ví dụ: BKG \+ ngày tháng năm \+ 4 chuỗi random).  
* service/BookingService.java & service/impl/BookingServiceImpl.java.

#### **2\. Quy trình Thực thi chuẩn (Step-by-step Execution)**

1. **Kiểm tra Voucher (Fail-fast):** Nếu có voucherCode, tra cứu voucher. Dùng 2 hàm đếm COUNT ở BookingVoucherRepository để xác minh chưa vượt quá total\_usage\_limit và usage\_per\_user\_limit.  
2. **Gọi ngầm Tính Giá (Pricing Engine):** Truyền payload vào pricingService.calculate(). Lấy con số finalTotal, subtotal, discountAmount và danh sách breakdownItems từ kết quả trả về.  
3. **Khởi tạo Đơn gốc:** Sinh bookingCode. Tạo entity Booking với status \= PENDING\_SURVEY, gán customer\_id từ JWT token. save(booking).  
4. **Lưu Tuyến đường & Đồ đạc:** Map dữ liệu từ Request sang BookingRoute và list BookingItem. Gán bookingId vừa tạo. saveAll().  
5. **Snapshot Đơn giá Dịch vụ:** Duyệt list breakdownItems từ PricingService. Chuyển thành BookingService. Gán price\_applied \= item.getUnitPrice() (Snapshot). saveAll().  
6. **Lịch sử & Voucher:**  
   * Lưu 1 record vào booking\_vouchers (nếu có dùng).  
   * Lưu 1 record vào booking\_status\_history (new\_status \= PENDING\_SURVEY, reason \= "Khách hàng tạo đơn").

#### **3\. Tiêu chí nghiệm thu (DoD)**

* Phải có @Transactional(rollbackFor \= Exception.class) bọc toàn bộ hàm createBooking().  
* Bắt buộc phải gọi pricingService.calculate(), KHÔNG lấy trường totalAmount từ Request DTO.  
* Bảng booking\_services lưu chính xác price\_applied thay vì chỉ lưu service\_id.

### **TASK-3.4: Cài đặt Máy trạng thái & Truy vấn (BookingService)**

* **Mục tiêu duy nhất:** Hoàn thiện các luồng tra cứu và chuyển đổi trạng thái (cụ thể là Hủy đơn), tích hợp cơ chế bảo vệ quyền sở hữu (IDOR protection).  
* **Phụ thuộc:** TASK-3.3.

#### **1\. Hàm cần cài đặt trong BookingServiceImpl.java**

* getBookingById(Integer id, Integer currentUserId):  
  * Truy vấn đơn hàng. Kiểm tra if (\!booking.getCustomerId().equals(currentUserId)) throw ERR\_FORBIDDEN.  
* getCustomerBookings(Integer currentUserId, Pageable pageable):  
  * Trả về danh sách đơn của user hiện tại, được ánh xạ qua BookingListResponse.  
* cancelBooking(Integer id, CancelBookingRequest req, Integer currentUserId):  
  * Kiểm tra tồn tại và quyền sở hữu (IDOR).  
  * **Máy trạng thái:** if (booking.getStatus() \!= BookingStatus.PENDING\_SURVEY) throw ERR\_BOOKING\_INVALID\_STATUS.  
  * Đổi booking.setStatus(CANCELLED).  
  * Ghi bản ghi mới vào booking\_status\_history (previous \= PENDING\_SURVEY, new \= CANCELLED, reason \= req.getReason()).

#### **2\. Tiêu chí nghiệm thu (DoD)**

* Khách hàng cố tình truyền id của đơn hàng không thuộc về mình sẽ bị chặn đứng bởi ERR\_FORBIDDEN (HTTP 403).  
* Không thể hủy đơn nếu admin đã chuyển trạng thái sang CONFIRMED hoặc IN\_PROGRESS.

### **TASK-3.5: Xây dựng REST Controller cho Booking [HOÀN THÀNH]**

* **Mục tiêu duy nhất:** Mở API Endpoints định tuyến vào các hàm của Service, trích xuất danh tính khách hàng từ Spring Security (JWT) để truyền xuống dưới.  
* **Phụ thuộc:** TASK-3.4.

#### **1\. Artifacts đã hoàn thành**

* `controller/BookingController.java` (Customer Endpoints)
* `controller/ManagementBookingController.java` (Admin/Dispatcher Management Endpoints)
* `dto/request/booking/BookingManagementFilterRequest.java`
* `repository/specification/BookingSpecification.java`
* `service/BookingManagementService.java` & `service/impl/BookingManagementServiceImpl.java`
* `src/main/resources/db/migration/V9__seed_booking_sample_data.sql` (Seed 5 đơn hàng mẫu bao quát 5 trạng thái)

#### **2\. Ma trận Đặc tả Endpoints**

| HTTP Method | Endpoint URI | Quyền (JWT) | Request Body | Phản hồi | HTTP Status |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **POST** | /api/v1/bookings | @PreAuthorize("hasRole('CUSTOMER')") | @Valid CreateBookingRequest | BookingDetailResponse | **201 Created** |
| **GET** | /api/v1/bookings | @PreAuthorize("hasRole('CUSTOMER')") | Query: ?status=...&page=0\&size=10 | PageResponse\<BookingSummaryResponse\> | **200 OK** |
| **GET** | /api/v1/bookings/{id} | @PreAuthorize("hasRole('CUSTOMER')") | *None* | BookingDetailResponse | **200 OK** |
| **PATCH** | /api/v1/bookings/{id}/cancel | @PreAuthorize("hasRole('CUSTOMER')") | @Valid CancelBookingRequest | BookingDetailResponse | **200 OK** |
| **GET** | /api/v1/management/bookings | @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')") | Query Specification Filter | PageResponse\<BookingSummaryResponse\> | **200 OK** |
| **GET** | /api/v1/management/bookings/{id} | @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')") | *None* | BookingDetailResponse | **200 OK** |
| **PATCH** | /api/v1/management/bookings/{id}/cancel | @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')") | @Valid CancelBookingRequest | BookingDetailResponse | **200 OK** |

#### **3\. Tiêu chí nghiệm thu (DoD)**

* Mọi API đều trích xuất currentUserId thông qua @AuthenticationPrincipal và truyền xuống Service. Tuyệt đối không nhận customerId từ Payload JSON. [ĐẠT]
* Swagger/OpenAPI render đúng chuẩn, endpoint Hủy đơn sử dụng PATCH. [ĐẠT]
* Bộ test `BookingControllerTest` (13/13 test cases) & `ManagementBookingControllerTest` (10/10 test cases) vượt qua 100%. [ĐẠT]

### **TASK-3.6: Viết Bộ Unit Test Kiểm thử Transaction & State Machine [HOÀN THÀNH]**

* **Mục tiêu duy nhất:** Kiểm chứng khả năng rollback của Database và các rào chắn nghiệp vụ biên.  
* **Artifacts đã hoàn thành:** 
  - `src/test/java/.../BookingServiceTest.java` (15 test cases)
  - `src/test/java/.../BookingManagementServiceTest.java` (6 test cases)
  - `src/test/java/.../BookingRepositoryTest.java` (5 integration test cases với DB thực tế & Flyway Migration V9)

#### **1\. Danh mục Test Cases bắt buộc**

* testCreateBooking\_WhenSavingHistoryFails\_ThenRollbackAll: Mock hàm historyRepository.save() ném exception. Kiểm chứng rollback kích hoạt thành công. [ĐẠT]
* testCreateBooking\_WhenVoucherLimitReached\_ThrowsException: Giả lập hàm countByVoucherId đạt giới hạn toàn hệ thống hoặc per-user, ném ERR\_VOUCHER\_LIMIT\_REACHED. [ĐẠT]
* testCancelBooking\_WhenStatusIsConfirmed\_ThrowsInvalidStatus: Giả lập đơn hàng đang ở CONFIRMED, khách hàng cancel sẽ bị ném ERR_BOOKING_INVALID_STATUS. [ĐẠT]
* testCancelBooking\_WhenIdorAttack\_ThrowsForbidden: Giả lập user B truyền bookingId của user A, hệ thống chặn đứng với ERR\_BOOKING_FORBIDDEN (HTTP 403). [ĐẠT]

#### **2\. Tiêu chí nghiệm thu (DoD)**

* Độ bao phủ (Coverage) kiểm thử toàn bộ các nhánh if bảo mật (IDOR, Status machine, Voucher Limit, Transaction rollback, Voucher usedCount revert). [ĐẠT]
* Toàn bộ 52/52 test cases trong test suite vượt qua với kết quả BUILD SUCCESS. [ĐẠT]


