# **ĐẶC TẢ KIẾN TRÚC & NGỮ CẢNH \- MODULE 3: BOOKING MANAGEMENT**

**Version:** 2.2 | **Status:** Approved for Vibe Coding (Strictly Mapped to ERD)

## **1\. MỤC TIÊU NGHIỆP VỤ & LUỒNG HOẠT ĐỘNG**

> * **Sự khác biệt với Module 2:** Khách hàng chốt đơn. Module này tiếp nhận payload, gọi ngầm lại cỗ máy PricingService (Module 2\) để tính giá cuối cùng, sau đó khởi tạo giao dịch lưu toàn vẹn dữ liệu vào 6 bảng liên quan.  
> * **Tính năng cốt lõi:**  
  1. Tạo Booking (Transaction lưu đồng thời vào bookings, routes, items, services, vouchers và lịch sử).  
  2. Gán price\_applied (Snapshot giá tại thời điểm đặt) vào chi tiết dịch vụ để làm bằng chứng tài chính, không phụ thuộc giá gốc.  
  3. Kiểm tra và áp dụng Voucher (Kiểm tra giới hạn qua bảng booking\_vouchers và vouchers gốc).  
  4. Quản lý danh sách đơn hàng của khách hàng (Lọc theo trạng thái, xem chi tiết, hủy đơn).

## **2\. KIẾN TRÚC DATABASE BẮT BUỘC (Khớp 100% ERD SE100.jpg)**

### **2.1. Bảng bookings**

> * id (int, PK)  
> * booking\_code (varchar(50)) \- Mã sinh tự động  
> * customer\_id (int, FK)  
> * created\_by (int)  
> * total\_amount (decimal(12,2))  
> * discount\_amount (decimal(12,2))  
> * note (text)  
> * status (ENUM: booking\_status)  
> * desired\_start\_at, desired\_end\_at (datetime)  
> * contact\_name (varchar(255)), contact\_phone (varchar(20))  
> * created\_at, updated\_at (datetime)

### **2.2. Bảng booking\_routes**

> * id (int, PK)  
> * booking\_id (int, FK)  
> * pickup\_address, dropoff\_address (varchar(500))  
> * pickup\_lat, pickup\_lng, dropoff\_lat, dropoff\_lng (decimal(10,7))  
> * pickup\_property\_type, dropoff\_property\_type (ENUM: property\_type)  
> * pickup\_note, dropoff\_note (text)  
> * estimated\_distance\_km (decimal(10,2))

### **2.3. Bảng booking\_items**

> * id (int, PK)  
> * booking\_id (int, FK)  
> * item\_name (varchar(255))  
> * quantity (int)  
> * estimated\_volume\_m3 (decimal(10,2))  
> * is\_fragile (boolean)  
> * packing\_required (boolean)  
> * disassembly\_required (boolean)  
> * note (text)

### **2.4. Bảng booking\_services**

> * id (int, PK)  
> * booking\_id (int, FK)  
> * service\_id (int, FK)  
> * price\_applied (decimal(12,2)) \- Cực kỳ quan trọng: Giá chốt (Snapshot)  
> * quantity (int)  
> * note (text)

### **2.5. Bảng booking\_vouchers**

> * id (int, PK)  
> * voucher\_id (int, FK)  
> * booking\_id (int, FK)  
> * discount\_amount (decimal(12,2))  
> * applied\_date (datetime)

### **2.6. Bảng booking\_status\_history**

> * id (int, PK)  
> * booking\_id (int, FK)  
> * old\_status, new\_status (ENUM: booking\_status)  
> * changed\_by (int, FK)  
> * changed\_at (datetime)  
> * note (text)

## **3\. QUY CHUẨN KỸ THUẬT BẮT BUỘC (VIBE CODING RULES)**

> 1. **Security Trust (Không tin tưởng Client):** Tuyệt đối không lấy field total\_amount từ Client gửi lên. Khi nhận request, bắt buộc truyền thông số qua PricingService.calculate() để lấy giá trị chuẩn, sau đó lấy kết quả này lưu xuống DB.  
> 2. **Database Transaction (Atomicity):** API tạo Booking bắt buộc phải được bọc trong @Transactional(rollbackFor \= Exception.class). Đảm bảo lưu thành công đồng thời vào 6 bảng: bookings, booking\_routes, booking\_items, booking\_services, booking\_vouchers, và booking\_status\_history. Lỗi 1 bảng phải rollback toàn bộ, tuyệt đối không tạo ra đơn hàng rác.  
> 3. **Data Types & Enum Validation:** Các trường status và property\_type (HOUSE, APARTMENT, ALLEY...) phải được map sang Java Enum với Postgres @Enumerated(EnumType.STRING). Không dùng magic strings.  
> 4. **Clean Architecture & DTOs:** Tách biệt rạch ròi Controller \- Service \- Repository. Luôn trả về ApiResponse bọc các DTO. Tuyệt đối không expose Entity ra ngoài Controller.  
> 5. **Sinh mã đơn (Booking Code):** Mã đơn booking\_code phải được sinh tự động và duy nhất tại Service layer (ví dụ: BKG \+ yyMMdd \+ 4 ký tự random).

## **4\. API CONTRACTS (DỰ KIẾN)**

| Phương thức | Đường dẫn API | Xác thực | Mô tả chi tiết |
| :---- | :---- | :---- | :---- |
| POST | /api/v1/bookings | JWT (CUSTOMER) | Khởi tạo đơn Booking mới. Body: Thông tin lộ trình, đồ đạc, dịch vụ, voucher. |
| GET | /api/v1/bookings | JWT (CUSTOMER) | Xem danh sách đơn của user hiện tại (hỗ trợ phân trang, lọc theo trạng thái). |
| GET | /api/v1/bookings/{id} | JWT (CUSTOMER) | Xem chi tiết 1 đơn (Bắt buộc kiểm tra quyền sở hữu dựa trên customer\_id của JWT). |
| PUT | /api/v1/bookings/{id} | JWT (CUSTOMER) | Cập nhật đơn (Chỉ cho phép khi status đang ở PENDING hoặc trạng thái hợp lệ). |
| POST | /api/v1/bookings/{id}/cancel | JWT (CUSTOMER) | Khách hàng chủ động hủy đơn. Yêu cầu gửi kèm lý do (reason) để ghi vào bảng lịch sử. |

