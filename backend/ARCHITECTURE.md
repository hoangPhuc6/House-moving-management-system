# Kiến trúc backend

Tài liệu mô tả cách tổ chức mã nguồn của `house-moving-service`: code đặt ở đâu, module nào được phụ thuộc vào module nào, và phân quyền hoạt động thế nào.

**Cập nhật file này khi:** thêm module mới, thêm vùng phân quyền hoặc annotation mới, hay thay đổi quy tắc phụ thuộc.

---

## 1. Cấu trúc tổng thể

Code được chia **theo nghiệp vụ (module)**, không chia theo tầng.

```
com.example.house_moving_service/
├── HouseMovingServiceApplication.java
├── common/                 # Dùng chung, không chứa nghiệp vụ
│   ├── config/             OpenAPIConfig
│   ├── dto/                ApiResponse, PageResponse
│   ├── enums/              PropertyType
│   └── exception/          AppException, ErrorCode, GlobalExceptionHandler
├── security/               # Hạ tầng xác thực & phân quyền
│   ├── SecurityConfig
│   ├── access/             AccessZones, @AdminOnly, @StaffOnly, @CustomerOnly, @AuthenticatedUser
│   ├── handler/            CustomAccessDeniedHandler
│   ├── jwt/                JwtTokenProvider, JwtAuthenticationFilter, JwtAuthenticationEntryPoint
│   └── userdetails/        CustomUserDetails, CustomUserDetailsService
└── modules/
    ├── identity/           # Tài khoản, đăng nhập, role, refresh token        (23 file)
    ├── catalog/            # Danh mục dịch vụ và nhóm dịch vụ, giá niêm yết   (21 file)
    ├── voucher/            # Mã giảm giá                                      (5 file)
    ├── pricing/            # Bộ tính giá (5 cấu phần chi phí, khoảng cách)    (16 file)
    └── booking/            # Đơn hàng: khách đặt/hủy, nhân viên quản lý       (33 file)
```

Test nằm trong `src/test/java` và **dùng cùng package với class được test**, ví dụ `modules/booking/service/BookingServiceTest`.

---

## 2. Cấu trúc bên trong một module

Mỗi module chỉ tạo những sub-package mà nó thật sự cần:

| Sub-package | Chứa | Ví dụ |
|---|---|---|
| `controller/` | REST controller. Chia nhỏ theo vùng phân quyền khi module phục vụ nhiều nhóm người dùng: `admin/`, `client/`, `management/` | `catalog/controller/admin/AdminServiceController` |
| `service/` | Interface service: **API công khai của module**, module khác chỉ được gọi vào đây | `booking/service/BookingService` |
| `service/impl/` | Cài đặt service | `booking/service/impl/BookingServiceImpl` |
| `entity/` | JPA entity | `catalog/entity/Service` |
| `enums/` | Enum của module | `booking/enums/BookingStatus` |
| `repository/` | Spring Data repository (+ `specification/` nếu có) | `booking/repository/specification/BookingSpecification` |
| `dto/request/`, `dto/response/` | DTO vào/ra của API | `pricing/dto/request/PricingCalculationRequest` |
| `mapper/` | Chuyển entity ↔ DTO | `booking/mapper/BookingMapper` |
| Khác | `config/`, `helper/`, `util/`, `seeder/` khi cần | `pricing/helper/RouteDistanceHelper` |

Hiện trạng từng module:

| Module | Sub-package |
|---|---|
| identity | controller, dto, entity, enums, mapper, repository, seeder, service |
| catalog | controller/{admin,client}, dto, entity, enums, repository, service(+impl) |
| voucher | entity, enums, repository, service(+impl) |
| pricing | config, controller, dto, helper, service(+impl) |
| booking | controller/{client,management}, dto, entity, enums, mapper, repository(+specification), service(+impl), util |

---

## 3. Phân quyền

Có **hai lớp kiểm tra**. Cả hai phải cho qua thì request mới vào được controller.

### Lớp 1: theo vùng URL (`SecurityConfig` + `AccessZones`)

| Vùng | Đường dẫn | Ai được vào | Dùng cho |
|---|---|---|---|
| `AUTH` | `/api/auth/**` | Tất cả | Đăng ký, đăng nhập, refresh token |
| `DOCS` | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Tất cả | Tài liệu API |
| `ADMIN` | `/api/v1/admin/**` | ADMIN | **Cấu hình hệ thống**: dịch vụ, nhóm dịch vụ, (sau này) voucher, tài khoản |
| `MANAGEMENT` | `/api/v1/management/**` | ADMIN, DISPATCHER | **Vận hành**: đơn hàng, (sau này) báo giá, phân công |
| Còn lại | mọi đường dẫn khác | Đã đăng nhập | API phía người dùng; role cụ thể đặt ở lớp 2 |

Lớp này là lưới an toàn: controller mới đặt dưới `/api/v1/admin/**` mà quên gắn annotation thì vẫn chỉ ADMIN gọi được.

### Lớp 2: annotation trên controller hoặc method (`security/access/`)

| Annotation | Tương đương | Dùng cho |
|---|---|---|
| `@AdminOnly` | `hasRole('ADMIN')` | Controller trong vùng `ADMIN` |
| `@StaffOnly` | `hasAnyRole('ADMIN', 'DISPATCHER')` | Controller trong vùng `MANAGEMENT` |
| `@CustomerOnly` | `hasRole('CUSTOMER')` | API khách thao tác trên dữ liệu của chính mình |
| `@AuthenticatedUser` | `isAuthenticated()` | API mọi tài khoản đã đăng nhập đều dùng được |

**Quy tắc:** không viết chuỗi `@PreAuthorize("...")` trực tiếp trong controller. Cần nhóm quyền mới thì tạo annotation mới trong `security/access/`.

Các controller hiện có:

| Controller | Đường dẫn | Phân quyền |
|---|---|---|
| `identity/AuthController` | `/api/auth` | Public (vùng `AUTH`) |
| `identity/UserController` | `/api/users` | Đã đăng nhập |
| `catalog/admin/AdminServiceController` | `/api/v1/admin/services` | `@AdminOnly` |
| `catalog/admin/AdminServiceTypeController` | `/api/v1/admin/service-types` | `@AdminOnly` |
| `catalog/client/ServiceCatalogController` | `/api/v1/services` | `@AuthenticatedUser` |
| `pricing/PricingController` | `/api/v1/pricing` | `@AuthenticatedUser` (trên method) |
| `booking/client/BookingController` | `/api/v1/bookings` | `@CustomerOnly` |
| `booking/management/ManagementBookingController` | `/api/v1/management/bookings` | `@StaffOnly` |

### Quyền sở hữu dữ liệu (chống IDOR)

Annotation chỉ trả lời câu hỏi *"role này có được gọi API không"*. Câu hỏi *"user này có được xem bản ghi này không"* phải kiểm tra **ở tầng service**. Ví dụ `BookingServiceImpl#getBookingById` so sánh chủ đơn với `currentUserId` và ném `ERR_BOOKING_FORBIDDEN` nếu không khớp.

---

## 4. Quy tắc phụ thuộc

### Sơ đồ phụ thuộc hiện tại

```
booking ──► pricing ──► catalog
   │           │
   │           └──────► voucher
   ├──────────────────► catalog, voucher
   └──────────────────► identity

mọi module ──► common, security
security ◄──► identity        (ngoại lệ được chấp nhận)
```

### Quy tắc

1. **Không phụ thuộc vòng** giữa các module.
2. **`common` không phụ thuộc vào module nào.**
3. **Module khác chỉ được gọi vào `service/` của một module**, không gọi thẳng `repository/` của module đó. Được phép dùng entity của module khác để tạo quan hệ JPA, ví dụ `Booking` → `Customer`.
4. **Ngoại lệ `security ↔ identity`:** `CustomUserDetailsService` cần `UserRepository` để nạp tài khoản, còn `AuthService` cần `JwtTokenProvider` để cấp token. Hai phần này gắn chặt với nhau theo bản chất nên được chấp nhận.

### Interface tra cứu dùng chung

Module khác thao tác dữ liệu của `catalog` và `voucher` qua các interface hẹp sau:

| Module cung cấp | Interface | Dùng bởi | Method |
|---|---|---|---|
| catalog | `ServiceLookupService` | pricing, booking | `findAllByIds`, `getReference` |
| voucher | `VoucherService` | pricing, booking | `getValidVoucher`, `lockValidVoucher`, `getReference`, `increaseUsage`, `releaseUsage` |

`ServiceLookupService` tách riêng khỏi `ServiceCatalogService` (API cho controller), nên module khác chỉ phụ thuộc đúng phần cần dùng. Interface này do một bean riêng (`ServiceLookupServiceImpl`) cài đặt, không dùng chung bean với `ServiceCatalogServiceImpl`: mock hay thay đổi một bên không ảnh hưởng bên kia.

### Ngoại lệ tạm thời của quy tắc 3

| Module | Class | Đang gọi thẳng | Ghi chú |
|---|---|---|---|
| booking | `BookingServiceImpl` | `identity.UserRepository`, `identity.CustomerRepository` | Thuộc phạm vi module identity (người phụ trách khác); chờ identity cung cấp interface tra cứu |
| booking | `BookingManagementServiceImpl` | `identity.UserRepository` | Như trên |

Code mới **không được** thêm vào danh sách này.

---

## 5. Quy ước đặt tên và lưu ý

- **Controller:** `Admin*Controller` cho vùng `ADMIN`, `Management*Controller` cho vùng `MANAGEMENT`, tên thường cho phía người dùng.
- **Service:** interface `XxxService` đặt trong `service/`, cài đặt `XxxServiceImpl` đặt trong `service/impl/`.
- **Trùng tên `BookingService`:** module booking có cả **entity** `entity.BookingService` (dịch vụ trong một đơn, bảng `booking_services`) và **interface** `service.BookingService`. File nào dùng cả hai thì import interface, còn entity viết tên đầy đủ: `com.example.house_moving_service.modules.booking.entity.BookingService`.
- **Lỗi nghiệp vụ:** ném `AppException(ErrorCode.XXX, args...)`, không ném exception thô. `GlobalExceptionHandler` chuyển lỗi thành `ApiResponse` theo format chung.
- **Response:** mọi API trả về `ApiResponse<T>`, có phân trang thì dùng `PageResponse<T>`.

---

## 6. Database và migration

- Schema do **Flyway** quản lý (`src/main/resources/db/migration`). Hibernate chạy với `ddl-auto: validate`, nên entity lệch với schema thì **app không khởi động**.
- Migration hiện có: `V1` … `V9`. **Migration tiếp theo là `V10__<mô_tả>.sql`.**
- **Không sửa migration đã chạy**, vì Flyway sẽ báo lỗi checksum trên mọi máy đã chạy migration đó. Muốn thay đổi thì tạo migration mới.
- Nhiều nhánh cùng tạo migration thì thống nhất số version trước khi merge để tránh trùng.

---

## 7. Cấu hình

- File cấu hình: `src/main/resources/application.yml`. Giá trị riêng của từng máy đặt trong `backend/.env` (đã gitignore; mẫu ở `.env.example`).
- Các nhóm cấu hình của ứng dụng:

| Prefix | Class đọc | Nội dung |
|---|---|---|
| `app.jwt.*` | `JwtTokenProvider`, `RefreshTokenService` | Secret, thời hạn access token (mặc định 15 phút), refresh token (mặc định 1 ngày) |
| `app.pricing.*` | `pricing/config/PricingProperties` | Lead time tối thiểu, số km miễn phí, hệ số đường vòng Haversine |
| `app.map.*` | `pricing/config/MapApiProperties` | Bật/tắt, nhà cung cấp (Goong), API key, timeout |

- **Biến bắt buộc:** `MAP_API_KEY` (không có giá trị mặc định). Các biến khác (`DB_*`, `SERVER_PORT`, `JWT_*`, …) đều có mặc định trong `application.yml`.

---

## 8. Checklist thêm module mới

1. Tạo `modules/<tên>/` với các sub-package cần thiết (mục 2).
2. Entity và migration `V<n>__...sql` (mục 6).
3. Interface service trong `service/`, cài đặt trong `service/impl/`. Module khác chỉ gọi qua interface.
4. Controller đặt đúng vùng URL và gắn annotation phân quyền (mục 3). Cần kiểm tra quyền sở hữu thì làm ở service.
5. Lỗi mới thì thêm vào `common/exception/ErrorCode`.
6. Test ở package tương ứng trong `src/test/java`.
7. Kiểm tra phụ thuộc theo mục 4 (không vòng, không gọi thẳng repository của module khác).
8. Cập nhật file này: cây ở mục 1, bảng ở mục 2 và 3, sơ đồ ở mục 4.

---

## 9. Lịch sử tái cấu trúc

| PR | Nội dung |
|---|---|
| #10 | Phân quyền theo vùng URL (`AccessZones`) và 4 annotation phân quyền |
| #11 | Tách `common/` và chia `security/` thành `jwt/`, `userdetails/`, `handler/` |
| #12 | Tách module `identity` |
| #13 | Tách module `catalog`; đổi tên `PublicServiceController` → `ServiceCatalogController` |
| #14 | Tách module `voucher` |
| #15 | Tách module `pricing` |
| #16 | Tách module `booking`, hoàn tất chia module |
