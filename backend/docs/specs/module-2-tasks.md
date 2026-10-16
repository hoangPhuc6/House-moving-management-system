

| Task ID  | Tên Atomic Task  | Trọng tâm duy nhất  | Phụ thuộc  |
| ----- | ----- | ----- | ----- |
| TASK-2.1  | Khởi tạo Database Migration & Data Access Layer  | Tạo bảng, thiết lập ràng buộc toàn vẹn/chỉ mục (indexes), nạp dữ liệu mẫu khởi tạo (seed data), ánh xạ JPA Entities và cung cấp các hàm truy vấn gom cụm $O(1)$ chống lỗi $N+1$.   | Không |
| TASK-2.2 | Xây dựng DTOs & Validation cho Quản lý Dịch vụ  | Tạo Request/Response DTO và ràng buộc dữ liệu đầu vào. | TASK-2.1  |
| TASK-2.3 | Cài đặt Service Layer cho Danh mục Dịch vụ  | Nghiệp vụ CRUD Admin, soft-delete và tra cứu public | TASK-2.2  |
| TASK-2.4 | Xây dựng REST Controllers cho Danh mục Dịch vụ  | Expose API `/api/v1/admin/**` và `/api/v1/services`  | TASK-2.3 |
| TASK-2.5 | Thiết lập Hợp đồng Dữ liệu & Exceptions cho Pricing Engine  | Khởi tạo DTO `PricingRequest`, `PricingResponse` và Custom Exceptions  | TASK-2.1 |
| TASK-2.6 | Cài đặt việc tính giá (`PricingService`)  | Thuật toán phân rã 5 cấu phần chi phí và áp dụng voucher.  | TASK-2.5  |
| TASK-2.7 | Xây dựng REST Controller cho Pricing Estimate. | Expose API `POST /api/v1/pricing/estimate`. | TASK-2.6  |
| TASK-2.8 | Viết Bộ Unit Test Kiểm thử Toàn diện Pricing Engine  | Kiểm thử 5 cấu phần, voucher trần, số âm và dữ liệu không hợp lệ.  | TASK-2.6  |

### **ĐẶC TẢ CHI TIẾT TỪNG ATOMIC TASK**

### **TASK-2.1: Khởi tạo Cơ sở Dữ liệu & Data Access Layer (Migration, Entities & Repositories)**

* **Mục tiêu duy nhất:** Thiết lập cấu trúc bảng chuẩn trên Database thông qua migration script, nạp dữ liệu danh mục ban đầu (seed data), ánh xạ chính xác các Entity JPA/Hibernate theo ERD và cung cấp các hàm Repository truy vấn gom cụm $O(1)$ chống lỗi $N+1$.  
* **Phụ thuộc:** Không.

#### **1\. Artifacts cần tạo (Checklist Files)**

1. **Database Migration Script:**  
   * src/main/resources/db/migration/V5\_\_create\_service\_types\_and\_services.sql: Tạo cấu trúc 2 bảng service\_types và services, thiết lập khóa ngoại fk\_services\_service\_type, ràng buộc CHECK (base\_price \> 0\), CHECK (status IN ('ACTIVE', 'INACTIVE')) và các chỉ mục (indexes) trên status, service\_type\_id.  
2. **Database Seed Script:**  
   * src/main/resources/db/migration/V5.1\_\_seed\_services\_data.sql: Nạp sẵn danh mục nhóm dịch vụ (service\_types) và các dịch vụ cơ bản (services) gồm cước xe, nhân công bốc xếp, tháo lắp, vật tư đóng gói và phụ phí địa hình để phục vụ tính toán giá tham khảo.  
3. **ORM / JPA Entities:**  
   * entity/ServiceType.java: Ánh xạ bảng service\_types gồm id, name, description, isActive.  
   * entity/Service.java: Ánh xạ bảng services gồm id, name, description, quan hệ @ManyToOne với ServiceType (cột service\_type\_id), trường basePrice kiểu BigDecimal, status, createdAt, updatedAt.  
4. **Data Repositories:**  
   * repository/ServiceTypeRepository.java: Kế thừa JpaRepository\<ServiceType, Integer\>.  
   * repository/ServiceRepository.java: Kế thừa JpaRepository\<Service, Integer\>.

#### **2\. Hàm Repository bắt buộc & Ý nghĩa kỹ thuật**

* List\<Service\> findAllByIdInAndStatus(Collection\<Integer\> ids, String status):  
  * *Ý nghĩa:* Thực thi câu truy vấn gom cụm SELECT \* FROM services WHERE id IN (:ids) AND status \= :status. Cung cấp dữ liệu tức thì cho PricingService, triệt tiêu hoàn toàn lỗi $N+1$ query khi hệ thống nhận danh sách nhiều serviceId cùng lúc.  
* List\<Service\> findAllByStatus(String status):  
  * *Ý nghĩa:* Phục vụ API tra cứu công khai danh sách dịch vụ đang hoạt động (status \= 'ACTIVE') cho khách hàng trên Web/App.  
* List\<Service\> findAllByServiceTypeIdAndStatus(Integer serviceTypeId, String status):  
  * *Ý nghĩa:* Hỗ trợ lọc danh mục dịch vụ theo từng nhóm cụ thể (phương tiện, tháo lắp, vật tư).

#### **3\. Tiêu chí nghiệm thu (Definition of Done \- DoD)**

* File migration và seed script thực thi trơn tru trên database thật (PostgreSQL/MySQL), tạo đủ cấu trúc bảng và nạp dữ liệu mẫu ban đầu.  
* Entity ServiceType và Service ánh xạ chính xác 100% các cột và kiểu dữ liệu trong ERD. Trường tiền tệ base\_price bắt buộc dùng kiểu BigDecimal (không dùng Float hay Double để tránh sai số làm tròn số học).  
* Toàn bộ mã nguồn compile thành công, không gặp xung đột ánh xạ quan hệ (mapping mapping annotations/cascading).  
* Có integration test hoặc repository test xác nhận hàm findAllByIdInAndStatus truy vấn đúng các dịch vụ khả dụng trong 1 câu SQL duy nhất.

#### **TASK-2.2: Xây dựng DTOs & Validation cho Quản lý Dịch vụ**

* **Mục tiêu duy nhất:** Đóng gói toàn bộ cấu trúc dữ liệu truyền nhận cho phân hệ CRUD dịch vụ kèm validation.  
* **Artifacts cần tạo:**  
  * dto/request/CreateServiceTypeRequest.java, UpdateServiceTypeRequest.java  
  * dto/request/CreateServiceRequest.java, UpdateServiceRequest.java  
  * dto/request/UpdateServiceStatusRequest.java (chỉ nhận ACTIVE / INACTIVE)  
  * dto/response/ServiceTypeResponse.java, ServiceResponse.java  
* **Ràng buộc Validation:**  
  * @NotBlank, @Size(max \= 255\) cho name.  
  * @NotNull, @DecimalMin(value \= "0.0", inclusive \= false) cho base\_price.  
  * @NotNull cho serviceTypeId.  
* **Tiêu chí nghiệm thu (DoD):** Validation bắt lỗi chính xác khi payload thiếu trường hoặc truyền giá âm/bằng 0\.

### **TASK-2.3: Thiết lập Hạ tầng Phản hồi Chuẩn, Bắt lỗi Tập trung & Cài đặt Service Layer Danh mục Dịch vụ**

* **Mục tiêu duy nhất:**  
  1. Khởi tạo cấu trúc đóng gói phản hồi đồng nhất (ApiResponse\<T\>) cho toàn hệ thống (áp dụng cho cả trạng thái thành công lẫn thất bại).  
  2. Xây dựng cơ chế xử lý lỗi tập trung (ErrorCode enum \+ AppException \+ GlobalExceptionHandler) làm khung bắt lỗi dùng chung cho toàn bộ module.  
  3. Cài đặt toàn bộ logic nghiệp vụ quản lý danh mục dịch vụ cho Admin (CRUD, soft-delete) và tra cứu danh mục khả dụng cho Khách hàng.  
* **Phụ thuộc:** TASK-2.1, TASK-2.2.

#### **1\. Danh sách Artifacts cần tạo (Checklist Files)**

##### **A. Khối Hạ tầng Phản hồi & Xử lý Ngoại lệ (Core Exception & Response):**

* dto/response/ApiResponse.java: Lớp Generic wrapper chuẩn hóa mọi phản hồi JSON (status, code, message, data, timestamp). Cấu hình @JsonInclude(JsonInclude.Include.NON\_NULL) để tự động ẩn trường data khi xảy ra lỗi.  
* exception/ErrorCode.java: Enum trung tâm quản lý danh mục mã lỗi nghiệp vụ, thông điệp mẫu hỗ trợ truyền tham số động và HTTP Status Code tương ứng.  
* exception/AppException.java: RuntimeException nghiệp vụ duy nhất trong hệ thống, nhận ErrorCode và các tham số động để format message.  
* exception/GlobalExceptionHandler.java: @RestControllerAdvice bắt tập trung AppException, lỗi xác thực dữ liệu đầu vào (MethodArgumentNotValidException), và ngoại lệ không xác định (Exception), chuyển hóa thành ResponseEntity\<ApiResponse\<Void\>\>.

##### **B. Khối Nghiệp vụ Service Layer (Interfaces & Implementations):**

* service/ServiceTypeService.java & service/impl/ServiceTypeServiceImpl.java: Xử lý nghiệp vụ cho nhóm loại dịch vụ (service\_types).  
* service/ServiceCatalogService.java & service/impl/ServiceCatalogServiceImpl.java: Xử lý nghiệp vụ cho danh mục dịch vụ chi tiết (services).

#### **2\. Chi tiết Quy tắc Nghiệp vụ & Cơ chế Bắt lỗi**

##### **A. Danh mục Mã lỗi Khởi tạo trong ErrorCode:**

* ERR\_INVALID\_INPUT (HTTP 400): Dữ liệu đầu vào không hợp lệ hoặc vi phạm Bean Validation.  
* ERR\_SERVICE\_TYPE\_NOT\_FOUND (HTTP 404): Không tìm thấy loại dịch vụ theo ID chỉ định.  
* ERR\_SERVICE\_NOT\_FOUND (HTTP 404): Không tìm thấy dịch vụ theo ID chỉ định.  
* ERR\_SERVICE\_TYPE\_INACTIVE (HTTP 400): Không thể tạo hoặc bật dịch vụ thuộc nhóm loại dịch vụ đang bị vô hiệu hóa.  
* ERR\_SERVICE\_INACTIVE (HTTP 400): Dịch vụ đã ngừng hoạt động.  
* ERR\_DUPLICATE\_NAME (HTTP 409): Tên loại dịch vụ hoặc tên dịch vụ đã tồn tại trên hệ thống.  
* ERR\_INTERNAL\_SERVER (HTTP 500): Lỗi hệ thống ngoài dự tính.

##### **B. Logic Nghiệp vụ trong ServiceTypeServiceImpl:**

* create(CreateServiceTypeRequest req): Kiểm tra trùng tên loại dịch vụ $\rightarrow$ nếu trùng ném AppException(ErrorCode.ERR\_DUPLICATE\_NAME) $\rightarrow$ lưu bản ghi $\rightarrow$ trả về ServiceTypeResponse.  
* update(Integer id, UpdateServiceTypeRequest req): Tìm kiếm theo id $\rightarrow$ nếu không tồn tại ném AppException(ErrorCode.ERR\_SERVICE\_TYPE\_NOT\_FOUND, id) $\rightarrow$ cập nhật thông tin.  
* toggleActive(Integer id): Tìm kiếm theo id $\rightarrow$ đảo ngược trạng thái is\_active (true $\leftrightarrow$ false).

##### **C. Logic Nghiệp vụ trong ServiceCatalogServiceImpl:**

* create(CreateServiceRequest req):  
  * Kiểm tra service\_type\_id tồn tại: nếu không tìm thấy ném AppException(ErrorCode.ERR\_SERVICE\_TYPE\_NOT\_FOUND, req.getServiceTypeId()).  
  * Kiểm tra nhóm dịch vụ cha: nếu serviceType.getIsActive() \== false ném AppException(ErrorCode.ERR\_SERVICE\_TYPE\_INACTIVE).  
  * Khởi tạo entity Service với status \= "ACTIVE", basePrice, gán liên kết quan hệ và lưu Database.  
* update(Integer id, UpdateServiceRequest req):  
  * Kiểm tra tồn tại của dịch vụ theo id $\rightarrow$ nếu không có ném AppException(ErrorCode.ERR\_SERVICE\_NOT\_FOUND, id).  
  * Nếu request có thay đổi service\_type\_id, kiểm tra sự tồn tại và tính khả dụng của loại dịch vụ mới.  
  * Cập nhật thông tin và cập nhật mốc thời gian updatedAt.  
* softDelete(Integer id) (Quy tắc Bất biến Lịch sử):  
  * Kiểm tra tồn tại của dịch vụ theo id $\rightarrow$ ném ERR\_SERVICE\_NOT\_FOUND nếu không thấy.  
  * Tuyệt đối không gọi lệnh xóa vật lý deleteById().  
  * Thực hiện cập nhật trạng thái service.setStatus("INACTIVE"), cập nhật updatedAt và lưu lại Database.  
* getActiveCatalog() (Dành cho Client / Web tra cứu):  
  * Truy vấn danh sách các services thỏa mãn đồng thời: status \= 'ACTIVE' và serviceType.isActive \= true.  
  * Ánh xạ danh sách Entity sang danh sách ServiceResponse DTO.

#### **3\. Tiêu chí Nghiệm thu (Definition of Done \- DoD)**

* **Chuẩn hóa Phản hồi:** Mọi kết quả trả về khi gọi thành công hay khi xảy ra lỗi đều được bọc trong cấu trúc ApiResponse với đầy đủ status, code, message, timestamp. Trường data bị loại bỏ tự động khi có lỗi.  
* **Bắt lỗi Tập trung:** Khi Service ném AppException(ErrorCode.ERR\_SERVICE\_NOT\_FOUND, 999\), GlobalExceptionHandler chặn lại và trả về HTTP 404 cùng JSON chuẩn:  
* JSON  
  {  
    "status": 404,  
    "code": "ERR\_SERVICE\_NOT\_FOUND",  
    "message": "Không tìm thấy dịch vụ với ID: 999",  
    "timestamp": "2026-10-14T14:30:00"  
  }  
* **Validation Input đồng bộ:** Các lỗi kích hoạt bởi @Valid tại Controller trả về HTTP 400 với format của ApiResponse, tổng hợp đầy đủ các trường vi phạm trong message.  
* **Thực thi Soft-Delete chính xác:** Gọi hàm xóa dịch vụ chỉ cập nhật trường status thành INACTIVE, không xóa mất dữ liệu khỏi cơ sở dữ liệu.  
* **Độc lập Tầng Dữ liệu:** 100% các phương thức Service chỉ tiếp nhận Request DTO và trả về Response DTO, không để lộ Entity JPA ra bên ngoài tầng nghiệp vụ.  
* **Tính Kế thừa cao:** Cơ chế ApiResponse, ErrorCode, AppException đã sẵn sàng để các task sau (TASK-2.4, TASK-2.6, TASK-2.7) sử dụng trực tiếp mà không phải cấu hình lại hạ tầng.

### **TASK-2.4 (CẬP NHẬT): Xây dựng REST Controllers cho Danh mục Dịch vụ**

* **Mục tiêu duy nhất:** Định tuyến chính xác các Endpoint HTTP theo chuẩn RESTful, kích hoạt Bean Validation (@Valid), phân quyền truy cập và bọc dữ liệu trả về qua ApiResponse\<T\>.  
* **Phụ thuộc:** TASK-2.2, TASK-2.3.

#### **1\. Artifacts cần tạo**

* controller/AdminServiceTypeController.java  
* controller/AdminServiceController.java  
* controller/PublicServiceController.java

#### **2\. Ma trận Đặc tả Endpoints (API Contract Matrix)**

##### **A. AdminServiceTypeController (/api/v1/admin/service-types)**

*Ràng buộc bảo mật:* Toàn bộ Controller yêu cầu @PreAuthorize("hasRole('ADMIN')").

| HTTP Method | Endpoint URI | Request Body | Phản hồi (data) | HTTP Status | Mô tả nghiệp vụ |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **POST** | / | @Valid CreateServiceTypeRequest | ServiceTypeResponse | **201 Created** | Thêm mới một loại dịch vụ |
| **GET** | / | *None* | List\<ServiceTypeResponse\> | **200 OK** | Xem toàn bộ (cả bật lẫn tắt) |
| **GET** | /{id} | *None* | ServiceTypeResponse | **200 OK** | Xem chi tiết theo ID |
| **PUT** | /{id} | @Valid UpdateServiceTypeRequest | ServiceTypeResponse | **200 OK** | Cập nhật thông tin |
| **PATCH** | /{id}/toggle-active | *None* | ServiceTypeResponse | **200 OK** | Đổi trạng thái bật/tắt |

##### **B. AdminServiceController (/api/v1/admin/services)**

*Ràng buộc bảo mật:* Toàn bộ Controller yêu cầu @PreAuthorize("hasRole('ADMIN')").

| HTTP Method | Endpoint URI | Request Body | Phản hồi (data) | HTTP Status | Mô tả nghiệp vụ |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **POST** | / | @Valid CreateServiceRequest | ServiceResponse | **201 Created** | Tạo dịch vụ kèm base\_price |
| **GET** | / | *Query params:* ?typeId=\&status= | List\<ServiceResponse\> | **200 OK** | Quản lý danh sách dịch vụ |
| **GET** | /{id} | *None* | ServiceResponse | **200 OK** | Xem chi tiết 1 dịch vụ |
| **PUT** | /{id} | @Valid UpdateServiceRequest | ServiceResponse | **200 OK** | Sửa tên, mô tả, base\_price |
| **PATCH** | /{id}/status | @Valid UpdateServiceStatusRequest | ServiceResponse | **200 OK** | Đổi trạng thái ACTIVE / INACTIVE |
| **DELETE** | /{id} | *None* | Void (hoặc thông báo) | **200 OK** | Kích hoạt Soft-delete (chuyển sang INACTIVE) |

##### **C. PublicServiceController (/api/v1/services)**

*Ràng buộc bảo mật:* @PermitAll (Cho phép khách vãng lai và Web truy cập tự do).

| HTTP Method | Endpoint URI | Request Body | Phản hồi (data) | HTTP Status | Mô tả nghiệp vụ |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **GET** | / | *None* | List\<ServiceResponse\> | **200 OK** | Lấy toàn bộ dịch vụ đang ACTIVE để render giao diện chọn đồ |
| **GET** | /{id} | *None* | ServiceResponse | **200 OK** | Tra cứu giá niêm yết của 1 dịch vụ |

#### **3\. Quy chuẩn Kỹ thuật bắt buộc (Implementation Rules)**

* **100% Controller trả về ResponseEntity\<ApiResponse\<T\>\>:** Không được trả DTO trần trụi ra ngoài.  
* **Tất cả các Request Body đều phải có annotation @Valid:** Để Spring Validation tự động bắt lỗi các trường thiếu/sai và đẩy về GlobalExceptionHandler.  
* **Không hardcode chuỗi URL phân quyền:** Đặt URL gốc trên @RequestMapping ở cấp class để cấu hình Security (SecurityFilterChain) quản lý đồng bộ theo path pattern /api/v1/admin/\*\*.

#### **4\. Tiêu chí nghiệm thu (DoD)**

* Swagger/OpenAPI hiển thị đầy đủ danh sách endpoint đúng URL, method và mô tả.  
* Test gọi POST /api/v1/admin/services với body thiếu name $\rightarrow$ Trả về 400 Bad Request dạng ApiResponse có thông báo lỗi chi tiết.  
* Test tạo mới thành công $\rightarrow$ Trả về 201 Created kèm payload trong trường data.  
* Dùng tài khoản không có quyền Admin gọi vào /api/v1/admin/\*\* $\rightarrow$ Bị chặn 403 Forbidden.

### **TASK-2.5: Thiết lập Hợp đồng Dữ liệu (DTOs), Validation & Bổ sung Mã lỗi cho Pricing Engine**

* **Mục tiêu duy nhất:** Khởi tạo bộ DTO phức hợp (Request/Response) cho PricingService theo đúng Phase 1 Spec, thiết lập ràng buộc Validation chặt chẽ cho payload tính giá, và bổ sung các mã lỗi định giá/voucher vào enum ErrorCode có sẵn.  
* **Phụ thuộc:** TASK-2.1, TASK-2.3 (kế thừa hạ tầng ApiResponse và Exception).

#### **1\. Danh sách Artifacts cần tạo & cập nhật**

##### **A. Request DTOs (Tạo mới kèm Validation Rules):**

* dto/request/pricing/PricingCalculationRequest.java: DTO gốc tiếp nhận request.  
* dto/request/pricing/ScheduleDto.java: Chứa desiredStartAt, desiredEndAt.  
* dto/request/pricing/RouteDto.java: Chứa pickup, dropoff, manualDistanceKm.  
* dto/request/pricing/LocationDto.java: Chứa tọa độ, địa chỉ, loại nhà, số tầng, thang máy, cự ly hẻm.  
* dto/request/pricing/ServiceItemDto.java: Chứa serviceId, quantity.

##### **B. Response DTOs (Tạo mới theo chuẩn Breakdown Phase 1):**

* dto/response/pricing/PricingCalculationResponse.java: Response gốc gồm metadata (isEstimated, calculatedDistanceKm, currency, disclaimer) và 3 khối dữ liệu chính.  
* dto/response/pricing/FinancialSummaryDto.java: Khối tóm tắt (subtotal, discountAmount, finalTotal).  
* dto/response/pricing/CategoryFeesDto.java: Khối phân rã 5 nhóm phí (baseTransportFee, laborFee, technicalFee, materialFee, terrainSurchargeFee).  
* dto/response/pricing/BreakdownItemDto.java: Chi tiết từng dòng dịch vụ kèm unitPrice đã snapshot.  
* dto/response/pricing/AppliedVoucherDto.java: Thông tin chiết khấu voucher thực tế.

##### **C. Cập nhật Danh mục Lỗi (Không tạo file mới, chỉ sửa file hiện có):**

* exception/ErrorCode.java: Thêm các hằng số lỗi đặc thù cho định giá và voucher.

#### **2\. Chi tiết Đặc tả Ràng buộc Validation trên Request DTOs**

Để kích hoạt kiểm tra sâu vào các tầng dữ liệu lồng nhau, bắt buộc phải dùng @Valid trên các đối tượng con:

| DTO Class | Thuộc tính | Annotation Validation | Ý nghĩa kỹ thuật |
| :---- | :---- | :---- | :---- |
| **PricingCalculationRequest** | schedule | @NotNull, @Valid | Bắt buộc có thông tin lịch trình và validate sâu. |
|  | route | @NotNull, @Valid | Bắt buộc có thông tin lộ trình và validate sâu. |
|  | serviceItems | @NotEmpty(message \= "Danh sách dịch vụ không được để trống"), @Valid | Phải chọn ít nhất 1 dịch vụ (xe hoặc nhân công). |
|  | voucherCode | *Không bắt buộc* | Khách có thể không nhập mã voucher. |
| **ScheduleDto** | desiredStartAt | @NotNull(message \= "Thời gian bắt đầu không được để trống") | Chuẩn ISO-8601. |
|  | desiredEndAt | @NotNull(message \= "Thời gian kết thúc không được để trống") | Phải có mốc kết thúc dự kiến. |
| **RouteDto** | pickup | @NotNull, @Valid | Validate tọa độ điểm đi. |
|  | dropoff | @NotNull, @Valid | Validate tọa độ điểm đến. |
|  | manualDistanceKm | @DecimalMin(value \= "0.0") | Nếu có giá trị thì không được âm. |
| **LocationDto** | address | @NotBlank(message \= "Địa chỉ không được để trống") | Chuỗi ký tự địa chỉ. |
|  | lat, lng | @NotNull(message \= "Tọa độ không được để trống") | Tọa độ địa lý bắt buộc để tính km. |
|  | floorNumber | @Min(value \= 0, message \= "Số tầng không được âm") | Tầng trệt tương đương 0\. |
|  | hasElevator | @NotNull | Cờ xác định thang máy. |
|  | walkingDistanceMeters | @Min(value \= 0, message \= "Cự ly vác hẻm không được âm") | Khoảng cách vác bộ. |
| **ServiceItemDto** | serviceId | @NotNull(message \= "serviceId không được null") | Khóa ngoại dịch vụ. |
|  | quantity | @NotNull, @DecimalMin(value \= "0.1", message \= "Số lượng phải lớn hơn 0") | Số lượng hoặc cự ly phụ trội. |

#### **3\. Bổ sung Mã lỗi vào ErrorCode.java**

Mở file ErrorCode.java đã tạo ở TASK-2.3 và khai báo thêm các mã sau:

Java  
// Bổ sung vào enum ErrorCode:  
ERR\_PRICE\_INVALID\_INPUT("ERR\_PRICE\_INVALID\_INPUT", "Dữ liệu tính giá không hợp lệ: %s", HttpStatus.BAD\_REQUEST),  
ERR\_INVALID\_SCHEDULE("ERR\_INVALID\_SCHEDULE", "Thời gian bắt đầu vận chuyển phải cách hiện tại tối thiểu %d giờ", HttpStatus.BAD\_REQUEST),  
ERR\_VOUCHER\_NOT\_FOUND("ERR\_VOUCHER\_NOT\_FOUND", "Mã voucher '%s' không tồn tại trên hệ thống", HttpStatus.NOT\_FOUND),  
ERR\_VOUCHER\_EXPIRED("ERR\_VOUCHER\_EXPIRED", "Voucher '%s' đã hết hạn hoặc chưa tới thời gian áp dụng", HttpStatus.BAD\_REQUEST),  
ERR\_VOUCHER\_MIN\_VALUE("ERR\_VOUCHER\_MIN\_VALUE", "Đơn hàng chưa đạt giá trị tối thiểu (%,.0f đ) để áp dụng voucher", HttpStatus.BAD\_REQUEST),

#### **4\. Tiêu chí Nghiệm thu (Definition of Done \- DoD)**

* **Chuẩn hóa Hợp đồng Dữ liệu:** Tất cả các DTO Request và Response ánh xạ chính xác 100% các trường đã chốt ở Phase 1 Spec.  
* **Định dạng Số & Thời gian:**  
  * Toàn bộ các trường tiền tệ (BigDecimal) serialize ra chuỗi số thập phân có đúng 2 chữ số (ví dụ: 1400000.00).  
  * Các trường LocalDateTime tuân thủ chuẩn ISO-8601 (YYYY-MM-DDTHH:mm:ss).  
* **Bắt lỗi Validation Đa tầng:** Viết 1 test case controller giả lập gửi request với serviceItems \= \[\] hoặc pickup.lat \= null $\rightarrow$ Hệ thống tự động chặn lại, trả về HTTP 400 kèm ApiResponse báo đúng trường bị lỗi vi phạm mà không lọt vào Service layer.  
* **Tương thích hoàn toàn:** Cấu trúc DTO này sẵn sàng để PricingService (ở TASK-2.6) nhận làm tham số đầu vào và sinh đầu ra mà không cần chỉnh sửa schema.

# **TASK-2.6: Tích hợp Migration Voucher & Cài đặt Cỗ máy Tính giá Lõi (Pricing Engine Core)**

* #### **Mục tiêu duy nhất:**

  1. #### Khởi tạo cấu trúc bảng vouchers qua Flyway Migration, Entity JPA và Repository.

  2. #### Xây dựng bộ cấu hình tham số động (PricingProperties, MapApiProperties) loại bỏ hoàn toàn hardcode.

  3. #### Cài đặt RouteDistanceHelper phân giải cự ly di chuyển 3 tầng (Thủ công $\rightarrow$ Map API $\rightarrow$ In-memory Fallback).

  4. #### Hiện thực hóa thuật toán tính giá 5 cấu phần chi phí theo công thức BR-PRICE-01, cỗ máy khuyến mãi và snapshot đơn giá thuần túy In-Memory (Read-Only).

* #### **Phụ thuộc:** TASK-2.1, TASK-2.3, TASK-2.5.

## **1\. Danh sách Artifacts cần tạo mới & cấu hình**

### **A. Tầng Cơ sở dữ liệu & Entity (Voucher Domain):**

* #### src/main/resources/db/migration/V7\_\_create\_vouchers\_table.sql: Script DDL tạo bảng vouchers kèm indexes và constraints.

* #### entity/Voucher.java: Entity JPA ánh xạ bảng vouchers.

* #### repository/VoucherRepository.java: Interface Spring Data JPA tra cứu voucher khả dụng.

### **B. Tầng Cấu hình Động (Configuration Properties \- Tránh Hardcode):**

* #### config/PricingProperties.java: Mapping cấu hình từ app.pricing.\* trong application.yml (thời gian đặt tối thiểu, cự ly cơ sở, mã code dịch vụ hệ thống).

* #### config/MapApiProperties.java: Mapping cấu hình tích hợp API bản đồ từ app.map.\*.

### **C. Tầng Helper & Tích hợp Ngoại vi:**

* #### service/helper/RouteDistanceHelper.java: Component chịu trách nhiệm phân giải khoảng cách di chuyển (km).

### **D. Tầng Nghiệp vụ Tính toán Lõi (Pricing Service):**

* #### service/PricingService.java: Interface dùng chung (Contract phục vụ Client Public API và Module Quotation nội bộ).

* #### service/impl/PricingServiceImpl.java: Hiện thực hóa cỗ máy tính toán.

## **2\. Chi tiết Đặc tả Kỹ thuật**

### **2.1 Cấu trúc Migration V7\_\_create\_vouchers\_table.sql**

#### SQL

#### CREATE TABLE vouchers (

####     id SERIAL PRIMARY KEY,

####     code VARCHAR(50) NOT NULL UNIQUE,

####     title VARCHAR(255) NOT NULL,

####     description TEXT,

####     discount\_type VARCHAR(20) NOT NULL, \-- 'PERCENT' hoặc 'FIXED'

####     discount\_value DECIMAL(12, 2\) NOT NULL,

####     max\_discount\_amount DECIMAL(12, 2), \-- Giới hạn trần cho loại PERCENT

####     min\_order\_value DECIMAL(12, 2\) NOT NULL DEFAULT 0.00,

####     start\_date TIMESTAMP NOT NULL,

####     end\_date TIMESTAMP NOT NULL,

####     usage\_limit INT,                    \-- Số lượt tối đa toàn hệ thống (NULL \= không giới hạn)

####     used\_count INT NOT NULL DEFAULT 0,

####     is\_active BOOLEAN NOT NULL DEFAULT TRUE,

####     created\_at TIMESTAMP DEFAULT CURRENT\_TIMESTAMP,

####     updated\_at TIMESTAMP DEFAULT CURRENT\_TIMESTAMP,

#### 

####     CONSTRAINT chk\_vouchers\_discount\_type CHECK (discount\_type IN ('PERCENT', 'FIXED')),

####     CONSTRAINT chk\_vouchers\_discount\_value CHECK (discount\_value \> 0),

####     CONSTRAINT chk\_vouchers\_dates CHECK (end\_date \>= start\_date)

#### );

#### 

#### CREATE INDEX idx\_vouchers\_code\_active ON vouchers(code, is\_active);

#### CREATE INDEX idx\_vouchers\_dates ON vouchers(start\_date, end\_date);

#### 

### **2.2 Cấu hình Tham số Động trong application.yml**

#### YAML

#### app:

####   pricing:

####     min-lead-hours: 2                  \# Thời gian đặt trước tối thiểu (tiếng)

####     base-transport-included-km: 10.0   \# Cự ly định mức trong gói xe cơ bản (km)

####     haversine-winding-factor: 1.3      \# Hệ số uốn lượn đường bộ khi tính dự phòng

####     system-service-codes:

####       extra-distance: "EXTRA\_KM"       \# Mã code tra cứu dịch vụ phụ trội km

####       stair-carry: "STAIR\_CARRY"       \# Mã code tra cứu phụ phí vác thang bộ

####       alley-carry: "ALLEY\_CARRY"       \# Mã code tra cứu phụ phí vác hẻm sâu

####   map:

####     enabled: false                     \# Bật/tắt gọi API ngoài

####     provider: "GOONG"                  \# GOONG / GOOGLE / OSRM

####     api-key: "\${MAP\_API\_KEY:}"

####     connect-timeout-ms: 2000

####     read-timeout-ms: 3000

#### 

## **3\. Quy trình Xử lý Step-by-Step trong PricingServiceImpl**

#### Toàn bộ phương thức được đánh dấu @Transactional(readOnly \= true) để bảo đảm tính chất **Side-effect Free**.

#### \[PricingCalculationRequest\]

####          │

####          ▼

#### \[Bước 1: Validate Lịch trình\] ────────(Vi phạm)──► Ném AppException(ERR\_INVALID\_SCHEDULE)

####          │ (Hợp lệ)

####          ▼

#### \[Bước 2: Phân giải Cự ly qua RouteDistanceHelper (3 Tầng)\]

####   ├─ Tầng 1: manualDistanceKm \> 0 ──────► \[Nhận cự ly thủ công\]

####   ├─ Tầng 2: Gọi External Routing API ──► \[Nhận cự ly thực tế\]

####   └─ Tầng 3: (Timeout/Lỗi) Fallback Haversine \* 1.3 ──► \[Nhận cự ly ước tính\]

####          │

####          ▼

#### \[Bước 3: Xác định Dịch vụ Tự sinh & Batch Query Dữ liệu (1 câu SQL)\]

####   ├─ Phân tích cự ly vượt định mức \-\> Cần mã code EXTRA\_KM

####   ├─ Phân tích tầng lầu 2 đầu đi/đến \-\> Cần mã code STAIR\_CARRY

####   ├─ Phân tích cự ly vác hẻm \> 50m   \-\> Cần mã code ALLEY\_CARRY

####   └─ Query gom cụm: findAllByIdIn \+ findByCodeIn (Tránh N+1)

####      └─ Kiểm tra trạng thái: NOT FOUND \-\> ERR\_SERVICE\_NOT\_FOUND | INACTIVE \-\> ERR\_SERVICE\_INACTIVE

####          │

####          ▼

#### \[Bước 4: Tính toán 5 Cấu phần Chi phí (BigDecimal \- BR-PRICE-01)\]

####   ├─ 1\. BaseTransportFee : Gói cơ bản xe \+ Phụ trội (overKm \* giá km)

####   ├─ 2\. LaborFee         : Số nhân công \* Đơn giá bốc xếp

####   ├─ 3\. TechnicalFee     : Tháo lắp máy lạnh/giường/tủ

####   ├─ 4\. MaterialFee      : Thùng carton, màng PE, băng keo

####   └─ 5\. TerrainSurcharge : Thang bộ (tổng tầng 2 đầu) \+ Hẻm xa (\> 50m)

####          │

####          ▼

#### \[Bước 5: Tổng hợp Subtotal \= Tổng 5 cấu phần\]

####          │

####          ▼

#### \[Bước 6: Cỗ máy Tính Khuyến mãi (Voucher Engine)\]

####   ├─ Tìm voucher: findByCodeAndIsActiveTrue(code)

####   ├─ Kiểm tra thời hạn: now() trong \[start\_date, end\_date\] \-\> ERR\_VOUCHER\_EXPIRED

####   ├─ Kiểm tra điều kiện tối thiểu: subtotal \>= min\_order\_value \-\> ERR\_VOUCHER\_MIN\_VALUE

####   └─ Tính giảm giá:

####        \- PERCENT: min(subtotal \* value%, max\_discount\_amount)

####        \- FIXED  : min(value, subtotal)

####        \* Lưu ý: TUYỆT ĐỐI KHÔNG tăng used\_count ở bước này\!

####          │

####          ▼

#### \[Bước 7: Chốt FinalTotal \= max(0, Subtotal \- DiscountAmount)\]

####          │

####          ▼

#### \[Bước 8: Đóng gói Snapshot Breakdown Items & Trả về PricingCalculationResponse\]

#### 

## **4\. Xử lý các Trường hợp Biên (Edge Cases)**

1. #### **API Bản đồ bị Timeout hoặc Chết kết nối:**

   * #### RouteDistanceHelper bắt RestClientException, ghi log.warn(), tự động chuyển mượt sang tính toán tọa độ Haversine in-memory. Luồng tính giá của khách không bị đứt đoạn.

2. #### **Khách chọn nhà tầng cao nhưng có Thang máy:**

   * #### Nếu hasElevator \= true, dù floorNumber \= 20 thì số tầng tính phí thang bộ vẫn là 0\.

3. #### **Phụ phí Thang bộ tính độc lập cả 2 đầu:**

   * #### Điểm đi: Tầng 3 không thang máy \$\\implies 3\$ tầng.

   * #### Điểm đến: Tầng 2 không thang máy \$\\implies 2\$ tầng.

   * #### \$\\implies\$ Tổng số tầng vác bộ đưa vào tính phí \= $3+2=5$ tầng.

4. #### **Voucher có mức giảm vượt quá giá trị đơn hàng:**

   * #### Ví dụ: Đơn hàng $400.000\text{đ}$, voucher cố định $500.000\text{đ}$.

   * #### Kết quả: discountAmount \= 400000.00 và finalTotal \= 0.00 (chặn trần theo subtotal, bảo đảm không sinh số âm).

5. #### **Snapshot Đơn giá:**

   * #### Giá dịch vụ lưu vào từng phần tử của breakdownItems lấy trực tiếp từ service.getBasePrice() tại thời điểm tính toán. Dù Admin có sửa giá sau đó, kết quả trả về của lượt tính này là bất biến.

## **5\. Tiêu chí Nghiệm thu (Definition of Done \- DoD)**

* #### **Migration & Persistence:**

  * #### File V7\_\_create\_vouchers\_table.sql chạy migration thành công, không xung đột với các bảng trước đó.

  * #### Voucher.java mapping đầy đủ annotation JPA và validate schema.

* #### **Side-effect Free (Không can thiệp trạng thái DB):**

  * #### Phương thức calculate() chạy dưới @Transactional(readOnly \= true).

  * #### Không phát sinh bất kỳ câu lệnh INSERT, UPDATE, DELETE nào xuống database (kể cả bảng quotations, bookings hay vouchers.used\_count).

* #### **Hiệu năng & Tối ưu hóa Database:**

  * #### Toàn bộ thao tác tính toán chỉ kích hoạt tối đa **1 câu SELECT services** và **1 câu SELECT voucher**. Tuyệt đối không phát sinh lỗi $N+1$.

* #### **Độ chính xác Số học:**

  * #### \$100\\%\$ các trường tiền tệ tính bằng BigDecimal, làm tròn RoundingMode.HALF\_UP với 2 chữ số thập phân (scale \= 2).

* #### **Tính Động của Hệ thống:**

  * #### Khi đổi tham số trong application.yml (ví dụ: đổi min-lead-hours từ $2$ lên $4$), hệ thống tự động nhận diện ngưỡng mới mà không cần chỉnh sửa bất kỳ dòng code logic Java nào.

#### 

### **TASK-2.7: Xây dựng REST Controller cho Báo giá Dự kiến (Pricing Estimate Controller)**

* **Mục tiêu duy nhất:** Xây dựng endpoint bảo mật POST /api/v1/pricing/estimate, yêu cầu xác thực JWT, tự động trích xuất định danh người dùng (userId), kích hoạt Bean Validation đa tầng, điều phối xử lý qua PricingService và chuẩn hóa đầu ra qua ApiResponse\<T\>.  
* **Phụ thuộc:** TASK-2.3 (Hạ tầng ApiResponse, ErrorCode, GlobalExceptionHandler), TASK-2.5 (DTOs & Validation Rules), TASK-2.6 (PricingService).

#### **1\. Artifacts cần tạo & chỉnh sửa**

* **Tạo mới:** src/main/java/.../controller/PricingController.java  
* **Cấu hình Security:** Rà soát cấu hình bảo mật (ví dụ: SecurityConfig.java), đảm bảo path /api/v1/pricing/estimate nằm trong khu vực yêu cầu xác thực (authenticated()), không đưa vào danh sách permitAll().

#### **2\. Ma trận Đặc tả Endpoint (API Contract Matrix)**

| Thuộc tính | Chi tiết kỹ thuật |
| :---- | :---- |
| **Endpoint URI** | **POST /api/v1/pricing/estimate** |
| **HTTP Method** | POST |
| **Quyền hạn truy cập** | **Yêu cầu JWT** (@PreAuthorize("isAuthenticated()") hoặc @PreAuthorize("hasRole('CUSTOMER')")) |
| **Request Headers** | • Authorization: Bearer \<jwt\_token\> *(Bắt buộc)*  • Content-Type: application/json |
| **Request Body** | @Valid @RequestBody PricingCalculationRequest request |
| **User Principal** | @AuthenticationPrincipal CustomUserDetails userDetails (hoặc inject Authentication) |
| **Kiểu dữ liệu trả về** | ResponseEntity\<ApiResponse\<PricingCalculationResponse\>\> |
| **HTTP Status Code** | **200 OK** (Tính toán in-memory hoàn tất, không phát sinh tạo tài nguyên vật lý) |

#### **3\. Cấu trúc Khung Controller Mẫu (Controller Skeleton)**

Java

package com.moving.pricing.controller;

import com.moving.common.dto.ApiResponse;

import com.moving.pricing.dto.request.pricing.PricingCalculationRequest;

import com.moving.pricing.dto.response.pricing.PricingCalculationResponse;

import com.moving.pricing.service.PricingService;

import com.moving.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse;

import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.\*;

@RestController

@RequestMapping("/api/v1/pricing")

@RequiredArgsConstructor

@Tag(name \= "Pricing", description \= "APIs tính toán định giá dịch vụ và phân rã chi phí")

@SecurityRequirement(name \= "bearerAuth")

public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/estimate")

    @PreAuthorize("isAuthenticated()")

    @Operation(

        summary \= "Tính bảng giá tham khảo dự kiến",

        description \= "Tiếp nhận lộ trình, đồ đạc, lịch hẹn và voucher của khách hàng đã đăng nhập để tính toán 5 cấu phần chi phí."

    )

    @ApiResponses(value \= {

        @SwaggerResponse(responseCode \= "200", description \= "Tính toán thành công, trả về bảng phân rã chi tiết"),

        @SwaggerResponse(responseCode \= "400", description \= "Dữ liệu đầu vào sai, vi phạm lịch trình hoặc voucher không hợp lệ"),

        @SwaggerResponse(responseCode \= "401", description \= "Chưa đăng nhập hoặc JWT token không hợp lệ"),

        @SwaggerResponse(responseCode \= "404", description \= "Không tìm thấy dịch vụ hoặc mã voucher")

    })

    public ResponseEntity\<ApiResponse\<PricingCalculationResponse\>\> estimatePrice(

            @Valid @RequestBody PricingCalculationRequest request,

            @AuthenticationPrincipal CustomUserDetails userDetails) {

        PricingCalculationResponse response \= pricingService.calculate(request, userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.ok(response));

    }

}

#### **4\. Ma trận Ngoại lệ & Ánh xạ HTTP Status (Error Handling Matrix)**

Toàn bộ các trường hợp lỗi dưới đây được ủy quyền xử lý tự động cho GlobalExceptionHandler:

| Tình huống ngoại lệ | Lớp ngoại lệ / Cơ chế kích hoạt | HTTP Status | Mã lỗi phản hồi (code) |
| :---- | :---- | :---- | :---- |
| **Không gửi Bearer Token / Token hết hạn** | AuthenticationException (Spring Security Filter) | **401 Unauthorized** | ERR\_UNAUTHORIZED |
| **Tài khoản sai Role (nếu phân quyền hẹp)** | AccessDeniedException | **403 Forbidden** | ERR\_FORBIDDEN |
| **Payload thiếu trường bắt buộc (lat, lng,...)** | MethodArgumentNotValidException | **400 Bad Request** | ERR\_PRICE\_INVALID\_INPUT |
| **Lịch hẹn quá gấp (\< min-lead-hours)** | AppException | **400 Bad Request** | ERR\_INVALID\_SCHEDULE |
| **Dịch vụ khách chọn có trạng thái INACTIVE** | AppException | **400 Bad Request** | ERR\_SERVICE\_INACTIVE |
| **Voucher hết hạn / Không đạt giá trị tối thiểu** | AppException | **400 Bad Request** | ERR\_VOUCHER\_EXPIRED / ERR\_VOUCHER\_MIN\_VALUE |
| **Mã dịch vụ / Voucher không tồn tại trong DB** | AppException | **404 Not Found** | ERR\_SERVICE\_NOT\_FOUND / ERR\_VOUCHER\_NOT\_FOUND |

#### **5\. Tiêu chí Nghiệm thu (Definition of Done \- DoD)**

1. **Kiểm tra Xác thực & Phân quyền:**  
   * Gửi request không kèm Authorization: Bearer ... $\rightarrow$ Bị chặn ngay lập tức với mã 401 Unauthorized.  
   * Gửi kèm Bearer JWT hợp lệ $\rightarrow$ Controller trích xuất thành công userId để truyền xuống PricingService.  
2. **Kiểm tra Validation Đa tầng:**  
   * Gửi JSON thiếu schedule, route hoặc danh sách serviceItems rỗng $\rightarrow$ Trả về 400 Bad Request với danh sách trường lỗi cụ thể, không để lọt vào Service.  
3. **Tuân thủ Chuẩn RESTful & Kiến trúc:**  
   * Controller giữ vai trò mỏng (Thin Controller), không chứa logic toán học.  
   * Dữ liệu trả về luôn được bọc trong cấu trúc ApiResponse\<PricingCalculationResponse\> với cờ isEstimated \= true.  
4. **Tài liệu hóa Swagger/OpenAPI:**  
   * Hiển thị đầy đủ endpoint tại giao diện Swagger UI, có nút bấm nhập Bearer Token và hiển thị đầy đủ schema mẫu của request/response.

#### **TASK-2.8: Viết Bộ Unit Test Kiểm thử Toàn diện Pricing Engine**

* **Mục tiêu duy nhất:** Thiết lập các bài kiểm thử tự động chứng minh độ ổn định và tính nhất quán của thuật toán.  
* **Artifacts cần tạo:**  
  * src/test/java/.../PricingServiceTest.java  
* **Danh mục Test Cases bắt buộc:**  
  * testCalculate\_HappyPath\_ReturnsCorrectBreakdownAndTotal: Kiểm tra tính đúng số học của cả 5 khoản phí.  
  * testCalculate\_InactiveService\_ThrowsServiceInactiveException: Bị chặn khi chọn dịch vụ đã tắt.  
  * testCalculate\_InvalidTimeSchedule\_ThrowsInvalidPricingInputException: Chặn tạo đơn gấp dưới $[Z]$ giờ.  
  * testCalculate\_VoucherFixed\_DiscountCappedAtSubtotal: Giảm giá vượt tổng tiền thì finalTotal \= 0.00.  
  * testCalculate\_VoucherPercent\_WithMaxDiscountLimit: Kiểm tra chặn trần voucher.  
  * testCalculate\_Idempotency: Chạy 100 lần cùng một payload cho ra kết quả đồng nhất.  
* **Tiêu chí nghiệm thu (DoD):** Toàn bộ test suite pass 100%, độ bao phủ nhánh logic (branch coverage) của PricingServiceImpl đạt trên 90%.

