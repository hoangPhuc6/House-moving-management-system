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
│   ├── exception/          AppException, ErrorCode, GlobalExceptionHandler
│   └── storage/            FileStorageService, MinioFileStorageService, StorageProperties, StoredFile
├── security/               # Hạ tầng xác thực & phân quyền
│   ├── SecurityConfig
│   ├── access/             AccessZones, @AdminOnly, @StaffOnly, @CustomerOnly, @MoverOnly, @AuthenticatedUser
│   ├── handler/            CustomAccessDeniedHandler
│   ├── jwt/                JwtTokenProvider, JwtAuthenticationFilter, JwtAuthenticationEntryPoint
│   └── userdetails/        CustomUserDetails, CustomUserDetailsService
└── modules/
    ├── identity/           # Tài khoản, đăng nhập, role, refresh token        (29 file)
    ├── catalog/            # Danh mục dịch vụ và nhóm dịch vụ, giá niêm yết   (23 file)
    ├── voucher/            # Mã giảm giá                                      (5 file)
    ├── pricing/            # Bộ tính giá: dịch vụ, phụ phí tự động, voucher   (18 file)
    ├── booking/            # Đơn hàng: khách đặt/hủy, nhân viên quản lý       (40 file)
    ├── quotation/          # Báo giá: hiện mới có bản nháp mở từ khảo sát     (13 file)
    └── survey/             # Khảo sát: lịch, phân công, hiện trường, tệp      (60 file)
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
| Khác | `config/`, `engine/`, `event/`, `helper/`, `util/`, `seeder/` khi cần | `pricing/engine/PricingEngine` |

Hiện trạng từng module:

| Module | Sub-package |
|---|---|
| identity | controller, dto, entity, enums, mapper, repository, seeder, service |
| catalog | controller/{admin,client}, dto, entity, enums, repository, service(+impl) |
| voucher | entity, enums, repository, service(+impl) |
| pricing | config, controller, dto, engine, helper, service(+impl) |
| booking | controller/{client,management}, dto, entity, enums, event, mapper, repository(+specification), service(+impl), util |
| quotation | dto, entity, enums, mapper, repository, service(+impl) — chưa có controller |
| survey | config, controller/{admin,client,field,management}, dto, entity, enums, mapper, repository(+specification), service(+impl) |

---

## 3. Phân quyền

Có **hai lớp kiểm tra**. Cả hai phải cho qua thì request mới vào được controller.

### Lớp 1: theo vùng URL (`SecurityConfig` + `AccessZones`)

| Vùng | Đường dẫn | Ai được vào | Dùng cho |
|---|---|---|---|
| `AUTH` | `/api/auth/**` | Tất cả | Đăng ký, đăng nhập, refresh token |
| `DOCS` | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Tất cả | Tài liệu API |
| `ADMIN` | `/api/v1/admin/**` | ADMIN | **Cấu hình hệ thống**: dịch vụ, nhóm dịch vụ, (sau này) voucher, tài khoản |
| `MANAGEMENT` | `/api/v1/management/**` | ADMIN, DISPATCHER | **Vận hành**: đơn hàng, lịch khảo sát, (sau này) báo giá |
| `FIELD` | `/api/v1/field/**` | MOVER | **Hiện trường**: khảo sát (sau này vận chuyển) |
| Còn lại | mọi đường dẫn khác | Đã đăng nhập | API phía người dùng; role cụ thể đặt ở lớp 2 |

Lớp này là lưới an toàn: controller mới đặt dưới `/api/v1/admin/**` mà quên gắn annotation thì vẫn chỉ ADMIN gọi được.

### Lớp 2: annotation trên controller hoặc method (`security/access/`)

| Annotation | Tương đương | Dùng cho |
|---|---|---|
| `@AdminOnly` | `hasRole('ADMIN')` | Controller trong vùng `ADMIN` |
| `@StaffOnly` | `hasAnyRole('ADMIN', 'DISPATCHER')` | Controller trong vùng `MANAGEMENT` |
| `@CustomerOnly` | `hasRole('CUSTOMER')` | API khách thao tác trên dữ liệu của chính mình |
| `@MoverOnly` | `hasRole('MOVER')` | Controller trong vùng `FIELD` |
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
| `survey/admin/AdminSurveyTimeSlotController` | `/api/v1/admin/survey-time-slots` | `@AdminOnly` |
| `survey/management/ManagementSurveyTimeSlotController` | `/api/v1/management/survey-time-slots` | `@StaffOnly` |
| `survey/management/ManagementSurveyController` | `/api/v1/management/surveys`, `/api/v1/management/bookings/{id}/surveys` | `@StaffOnly` |
| `survey/field/FieldSurveyController` | `/api/v1/field/surveys` | `@MoverOnly` |
| `survey/client/CustomerSurveyController` | `/api/v1/bookings/{bookingId}/surveys` | `@CustomerOnly` |

### Quyền sở hữu dữ liệu (chống IDOR)

Annotation chỉ trả lời câu hỏi *"role này có được gọi API không"*. Câu hỏi *"user này có được xem bản ghi này không"* phải kiểm tra **ở tầng service**. Ví dụ `BookingServiceImpl#getBookingById` so sánh chủ đơn với `currentUserId` và ném `ERR_BOOKING_FORBIDDEN` nếu không khớp; `FieldSurveyServiceImpl` chỉ cho MOVER thao tác lịch được phân công cho chính mình (`ERR_SURVEY_FORBIDDEN`).

---

## 4. Quy tắc phụ thuộc

### Sơ đồ phụ thuộc hiện tại

```
survey ────► booking, quotation, identity, common.storage
quotation ─► booking, identity
booking ──► pricing ──► catalog
   │           │
   │           └──────► voucher
   ├──────────────────► catalog, voucher
   └──────────────────► identity

mọi module ──► common, security
security ◄──► identity        (ngoại lệ được chấp nhận)
```

- **Đảo phụ thuộc quotation / survey:** quotation cần biết "khảo sát mới nhất đã hoàn tất chưa" nhưng không được phụ thuộc survey (survey đã phụ thuộc quotation). Interface `SurveyCompletionChecker` đặt ở `quotation/service`, survey cài đặt (`SurveyCompletionCheckerImpl`).
- **Sự kiện thay cho phụ thuộc ngược:** booking không gọi survey. Khi đơn bị hủy, booking phát `BookingCancelledEvent`; `BookingCancelledSurveyListener` (survey) hủy lịch khảo sát và bản nháp báo giá **trong cùng transaction**.

### Quy tắc

1. **Không phụ thuộc vòng** giữa các module.
2. **`common` không phụ thuộc vào module nào.**
3. **Module khác chỉ được gọi vào `service/` của một module**, không gọi thẳng `repository/` của module đó. Được phép dùng entity của module khác để tạo quan hệ JPA, ví dụ `Booking` → `Customer`.
4. **Ngoại lệ `security ↔ identity`:** `CustomUserDetailsService` cần `UserRepository` để nạp tài khoản, còn `AuthService` cần `JwtTokenProvider` để cấp token. Hai phần này gắn chặt với nhau theo bản chất nên được chấp nhận.

### Interface tra cứu dùng chung

Module khác thao tác dữ liệu của nhau qua các interface hẹp sau:

| Module cung cấp | Interface | Dùng bởi | Method |
|---|---|---|---|
| catalog | `ServiceLookupService` | pricing, booking | `findAllByIds`, `findByCode`, `getReference` |
| voucher | `VoucherService` | pricing, booking | `getValidVoucher`, `lockValidVoucher`, `getReference`, `increaseUsage`, `releaseUsage` |
| pricing | `PricingService` | booking, (sau này) quotation | `calculatePrice` (giá tham khảo), `calculate(PricingInput)` (báo giá chính thức) |
| identity | `StaffLookupService` | survey | `findActiveByRole`, `findSummary`, `getReference`, `isActiveStaffWithRole` |
| identity | `UserLookupService` | booking, quotation, survey | `getUserReference` |
| booking | `BookingWorkflowService` | survey, quotation | `getSnapshot`, `getItems`, `getRoute`, `changeStatus`, `updateRouteAccess`, `getReference`, `getItemReference` |
| quotation | `QuotationDraftService` | survey | Mở / sửa / hết hạn bản nháp báo giá trong lúc khảo sát |
| quotation (survey cài đặt) | `SurveyCompletionChecker` | quotation | `isLatestSurveyCompleted` |
| survey | `SurveyStaffScheduleService` | (sau này) điều phối vận chuyển | `hasSurveyBetween`: MOVER có lịch khảo sát trong khoảng giờ không |
| survey (module khác cài đặt) | `StaffExternalScheduleChecker` | survey | `isBusy`: điểm mở rộng để lịch vận chuyển chặn trùng lịch khảo sát; hiện chưa có bean nào, survey bỏ qua |
| booking | sự kiện `BookingCancelledEvent` | survey | Phát khi đơn bị hủy (khách hoặc nhân viên) |

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
- Migration hiện có: `V1` … `V13` (`V10` bảng thanh toán, `V11` bảng báo giá, `V12` bảng khảo sát + 4 khung giờ mặc định, `V13` `service_types.fee_category` + `services.code` + dịch vụ phụ phí vượt km). **Migration tiếp theo là `V14__<mô_tả>.sql`.**
- **Không sửa migration đã chạy**, vì Flyway sẽ báo lỗi checksum trên mọi máy đã chạy migration đó. Muốn thay đổi thì tạo migration mới.
- Nhiều nhánh cùng tạo migration thì thống nhất số version trước khi merge để tránh trùng.

---

## 7. Cấu hình

- File cấu hình: `src/main/resources/application.yml`. Giá trị riêng của từng máy đặt trong `backend/.env` (đã gitignore; mẫu ở `.env.example`).
- Các nhóm cấu hình của ứng dụng:

| Prefix | Class đọc | Nội dung |
|---|---|---|
| `app.jwt.*` | `JwtTokenProvider`, `RefreshTokenService` | Secret, thời hạn access token (mặc định 15 phút), refresh token (mặc định 1 ngày) |
| `app.pricing.*` | `pricing/config/PricingProperties` | Lead time tối thiểu, số km đã gồm trong giá xe, hệ số đường vòng Haversine, mốc hẻm sâu (`deep-alley-meters`) |
| `app.map.*` | `pricing/config/MapApiProperties` | Bật/tắt, nhà cung cấp (Goong), API key, timeout |
| `app.survey.*` | `survey/config/SurveyProperties` | Số phút được bắt đầu sớm, số tệp đính kèm tối đa, số video tối đa |
| `app.storage.*` | `common/storage/StorageProperties` | Nhà cung cấp (`MINIO` hoặc để trống), thời hạn link tải, thông tin MinIO |

- **Biến bắt buộc:** `MAP_API_KEY` (không có giá trị mặc định). Các biến khác (`DB_*`, `SERVER_PORT`, `JWT_*`, …) đều có mặc định trong `application.yml`.
- **Lưu trữ tệp:** `STORAGE_PROVIDER=MINIO` cùng `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`. Để trống `STORAGE_PROVIDER` thì app vẫn chạy, chỉ API upload báo `ERR_FILE_STORAGE`. Chạy MinIO local: `docker compose up -d minio` (`backend/docker-compose.yml`).
- **Giờ nghiệp vụ:** module survey dùng bean `Clock` múi giờ `Asia/Ho_Chi_Minh` (`SurveyClockConfig`); test thay bằng đồng hồ cố định / chỉnh được.

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
| #17 | Thêm file tài liệu kiến trúc này |
| #18 | Tách `VoucherService` cho pricing / booking |
| #20 | Tách `ServiceLookupService` khỏi `ServiceCatalogService` |
| Nhánh `feat/survey-management` (chưa merge) | Module `survey` (lịch, phân công, hiện trường, tệp MinIO, khách xem), skeleton `quotation`, vùng `FIELD`; hoàn thiện tính giá: `fee_category`, `services.code`, `PricingEngine`, phụ phí tự động, `PricingService#calculate` |
