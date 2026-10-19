-- =============================================================================
-- Migration V7: Seed Initial Sample Data for House Moving Management System
-- Passwords for all seeded users: Password@123
-- BCrypt Hash: $2a$10$jJf.2.0DsZfRwblfX8qkCOlFY26GF3ofKWP7xGAfUMtD9tZrU7WPS
-- =============================================================================

-- 1. Roles (Đảm bảo 4 vai trò hệ thống)
INSERT INTO "roles" ("code", "name", "description", "is_active", "created_at", "updated_at")
VALUES
  ('ADMIN', 'Quản trị viên', 'Toàn quyền quản trị hệ thống', true, NOW(), NOW()),
  ('DISPATCHER', 'Nhân viên điều phối', 'Điều phối đơn hàng, xe tải và nhân lực vận chuyển', true, NOW(), NOW()),
  ('MOVER', 'Nhân viên vận chuyển', 'Trực tiếp bốc vác, tháo dỡ và vận chuyển đồ đạc', true, NOW(), NOW()),
  ('CUSTOMER', 'Khách hàng', 'Khách hàng sử dụng dịch vụ chuyển nhà', true, NOW(), NOW())
ON CONFLICT ("code") DO NOTHING;

-- 2. Users (Tài khoản mẫu, mật khẩu chung: Password@123)
INSERT INTO "users" ("email", "password", "is_active", "created_at", "updated_at")
VALUES
  ('admin@housemoving.vn', '$2a$10$jJf.2.0DsZfRwblfX8qkCOlFY26GF3ofKWP7xGAfUMtD9tZrU7WPS', true, NOW(), NOW()),
  ('dispatcher@housemoving.vn', '$2a$10$jJf.2.0DsZfRwblfX8qkCOlFY26GF3ofKWP7xGAfUMtD9tZrU7WPS', true, NOW(), NOW()),
  ('mover1@housemoving.vn', '$2a$10$jJf.2.0DsZfRwblfX8qkCOlFY26GF3ofKWP7xGAfUMtD9tZrU7WPS', true, NOW(), NOW()),
  ('mover2@housemoving.vn', '$2a$10$jJf.2.0DsZfRwblfX8qkCOlFY26GF3ofKWP7xGAfUMtD9tZrU7WPS', true, NOW(), NOW()),
  ('customer1@gmail.com', '$2a$10$jJf.2.0DsZfRwblfX8qkCOlFY26GF3ofKWP7xGAfUMtD9tZrU7WPS', true, NOW(), NOW()),
  ('customer2@gmail.com', '$2a$10$jJf.2.0DsZfRwblfX8qkCOlFY26GF3ofKWP7xGAfUMtD9tZrU7WPS', true, NOW(), NOW())
ON CONFLICT ("email") DO NOTHING;

-- 3. User Roles (Phân quyền tài khoản)
INSERT INTO "user_roles" ("user_id", "role_id")
SELECT u.id, r.id FROM "users" u CROSS JOIN "roles" r WHERE u.email = 'admin@housemoving.vn' AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO "user_roles" ("user_id", "role_id")
SELECT u.id, r.id FROM "users" u CROSS JOIN "roles" r WHERE u.email = 'dispatcher@housemoving.vn' AND r.code = 'DISPATCHER'
ON CONFLICT DO NOTHING;

INSERT INTO "user_roles" ("user_id", "role_id")
SELECT u.id, r.id FROM "users" u CROSS JOIN "roles" r WHERE u.email = 'mover1@housemoving.vn' AND r.code = 'MOVER'
ON CONFLICT DO NOTHING;

INSERT INTO "user_roles" ("user_id", "role_id")
SELECT u.id, r.id FROM "users" u CROSS JOIN "roles" r WHERE u.email = 'mover2@housemoving.vn' AND r.code = 'MOVER'
ON CONFLICT DO NOTHING;

INSERT INTO "user_roles" ("user_id", "role_id")
SELECT u.id, r.id FROM "users" u CROSS JOIN "roles" r WHERE u.email = 'customer1@gmail.com' AND r.code = 'CUSTOMER'
ON CONFLICT DO NOTHING;

INSERT INTO "user_roles" ("user_id", "role_id")
SELECT u.id, r.id FROM "users" u CROSS JOIN "roles" r WHERE u.email = 'customer2@gmail.com' AND r.code = 'CUSTOMER'
ON CONFLICT DO NOTHING;

-- 4. Staff Profile (Thông tin nhân viên nội bộ)
INSERT INTO "staff" ("user_id", "staff_code", "full_name", "hire_date", "employment_status", "note", "created_at")
SELECT u.id, 'STF-001', 'Nguyễn Văn Quản Trị', '2024-01-01', 'ACTIVE', 'Quản trị viên trưởng', NOW()
FROM "users" u WHERE u.email = 'admin@housemoving.vn'
ON CONFLICT ("user_id") DO NOTHING;

INSERT INTO "staff" ("user_id", "staff_code", "full_name", "hire_date", "employment_status", "note", "created_at")
SELECT u.id, 'STF-002', 'Trần Thị Điều Phối', '2024-02-15', 'ACTIVE', 'Trưởng nhóm điều phối xe và nhân công', NOW()
FROM "users" u WHERE u.email = 'dispatcher@housemoving.vn'
ON CONFLICT ("user_id") DO NOTHING;

INSERT INTO "staff" ("user_id", "staff_code", "full_name", "hire_date", "employment_status", "note", "created_at")
SELECT u.id, 'STF-003', 'Lê Hoàng Nhân Công', '2024-03-01', 'ACTIVE', 'Nhân viên bốc xếp - tháo lắp máy lạnh', NOW()
FROM "users" u WHERE u.email = 'mover1@housemoving.vn'
ON CONFLICT ("user_id") DO NOTHING;

INSERT INTO "staff" ("user_id", "staff_code", "full_name", "hire_date", "employment_status", "note", "created_at")
SELECT u.id, 'STF-004', 'Phạm Minh Tài Xế', '2024-03-10', 'ACTIVE', 'Tài xế xe tải hạng C', NOW()
FROM "users" u WHERE u.email = 'mover2@housemoving.vn'
ON CONFLICT ("user_id") DO NOTHING;

-- 5. Customer Profile (Thông tin khách hàng)
INSERT INTO "customers" ("user_id", "full_name", "phone_number", "birthday", "created_at")
SELECT u.id, 'Võ Minh Khách', '0901234567', '1992-05-15', NOW()
FROM "users" u WHERE u.email = 'customer1@gmail.com'
ON CONFLICT ("user_id") DO NOTHING;

INSERT INTO "customers" ("user_id", "full_name", "phone_number", "birthday", "created_at")
SELECT u.id, 'Đặng Thu Hằng', '0987654321', '1995-10-20', NOW()
FROM "users" u WHERE u.email = 'customer2@gmail.com'
ON CONFLICT ("user_id") DO NOTHING;

-- 6. Service Types (5 danh mục phân loại dịch vụ)
INSERT INTO "service_types" ("id", "name", "description", "is_active")
OVERRIDING SYSTEM VALUE
VALUES
  (1, 'Xe tải vận chuyển', 'Dịch vụ phương tiện xe tải các loại trọng tải phục vụ chuyển nhà, văn phòng', true),
  (2, 'Bốc xếp & Khuân vác', 'Nhân công bốc vác, khuân vác, đóng gói đồ đạc chuyên nghiệp', true),
  (3, 'Dịch vụ kỹ thuật', 'Kỹ thuật viên tháo lắp điều hòa nhiệt độ, bình nóng lạnh, đồ gỗ, giường tủ', true),
  (4, 'Vật tư đóng gói', 'Cung cấp vật tư chuyên dụng bảo vệ tài sản như thùng carton, màng PE bọc đồ, xốp nổ', true),
  (5, 'Phụ phí địa hình', 'Phụ phí khuân vác thang bộ nhà cao tầng, đường hẻm nhỏ xe không vào tận nơi', true)
ON CONFLICT ("id") DO NOTHING;

-- Reset sequence cho service_types
SELECT setval(pg_get_serial_sequence('service_types', 'id'), COALESCE((SELECT MAX(id) FROM service_types), 1));

-- 7. Services (Các dịch vụ chi tiết có đơn giá tham chiếu)
INSERT INTO "services" ("service_type_id", "name", "description", "base_price", "status", "created_at", "updated_at")
VALUES
  -- 1. Xe tải vận chuyển
  (1, 'Xe tải 500kg (Nội thành)', 'Phù hợp chuyển phòng trọ sinh viên, đồ đạc ít cồng kềnh', 300000.00, 'ACTIVE', NOW(), NOW()),
  (1, 'Xe tải 1.5 Tấn (Nội thành)', 'Phù hợp chuyển căn hộ 1-2 phòng ngủ, gia đình nhỏ', 450000.00, 'ACTIVE', NOW(), NOW()),
  (1, 'Xe tải 2.0 Tấn (Nội thành)', 'Phù hợp chuyển nhà phố, căn hộ lớn 3 phòng ngủ hoặc văn phòng nhỏ', 600000.00, 'ACTIVE', NOW(), NOW()),

  -- 2. Bốc xếp & Khuân vác
  (2, 'Nhân công bốc xếp cơ bản', 'Bốc xếp, bê vác đồ đạc lên xuống xe và sắp xếp theo yêu cầu (tính theo người/chuyến)', 250000.00, 'ACTIVE', NOW(), NOW()),
  (2, 'Nhân công bốc xếp đồ nặng/cồng kềnh', 'Chuyên bê vác két sắt, đàn piano, tủ gỗ đặc khổ lớn', 400000.00, 'ACTIVE', NOW(), NOW()),

  -- 3. Dịch vụ kỹ thuật
  (3, 'Tháo lắp máy lạnh (1.0 - 1.5 HP)', 'Bao gồm tháo dỡ tại điểm đi và lắp đặt, hút chân không tại điểm đến', 300000.00, 'ACTIVE', NOW(), NOW()),
  (3, 'Tháo lắp giường tủ gỗ 3 buồng', 'Kỹ thuật viên tháo lắp giường phản, tủ quần áo gỗ ép/gỗ tự nhiên', 200000.00, 'ACTIVE', NOW(), NOW()),

  -- 4. Vật tư đóng gói
  (4, 'Thùng carton tiêu chuẩn (60x40x40)', 'Thùng 5 lớp sóng BC siêu cứng, chịu lực tốt khi xếp chồng', 25000.00, 'ACTIVE', NOW(), NOW()),
  (4, 'Cuộn màng PE bọc đồ (50cm x 2.4kg)', 'Bọc chống trầy xước đồ da, sofa, bàn ghế gỗ, đồ điện tử', 120000.00, 'ACTIVE', NOW(), NOW()),
  (4, 'Cuộn băng keo dán thùng (100 yard)', 'Băng dính bản rộng 5cm độ bám dính cao', 15000.00, 'ACTIVE', NOW(), NOW()),

  -- 5. Phụ phí địa hình
  (5, 'Phụ phí khuân vác thang bộ (mỗi lầu)', 'Áp dụng cho căn hộ/nhà từ lầu 2 trở lên không có thang máy (tính theo tầng)', 50000.00, 'ACTIVE', NOW(), NOW()),
  (5, 'Phụ phí hẻm sâu (> 50 mét)', 'Áp dụng khi xe tải cách nhà trên 50m phải đẩy xe kéo tay trung chuyển', 100000.00, 'ACTIVE', NOW(), NOW());

-- 8. Vouchers (Các mã ưu đãi mẫu)
INSERT INTO "vouchers" ("code", "title", "description", "discount_type", "discount_value", "max_discount_amount", "min_order_value", "total_usage_limit", "used_count", "start_date", "end_date", "is_active", "created_at", "updated_at")
VALUES
  ('WELCOME10', 'Ưu đãi Khách hàng mới 10%', 'Giảm 10% tối đa 50.000 VND cho đơn từ 200.000 VND', 'PERCENTAGE', 10.00, 50000.00, 200000.00, 1000, 0, NOW() - INTERVAL '1 day', NOW() + INTERVAL '1 year', true, NOW(), NOW()),
  ('GIAM50K', 'Giảm trực tiếp 50.000 VND', 'Áp dụng cho mọi đơn dọn nhà có giá trị từ 500.000 VND', 'FIXED_AMOUNT', 50000.00, NULL, 500000.00, 500, 0, NOW() - INTERVAL '1 day', NOW() + INTERVAL '6 months', true, NOW(), NOW()),
  ('CHUYENVIEN100K', 'Ưu đãi Đơn trọn gói 100K', 'Giảm 100.000 VND cho đơn từ 1.500.000 VND', 'FIXED_AMOUNT', 100000.00, NULL, 1500000.00, 200, 0, NOW() - INTERVAL '1 day', NOW() + INTERVAL '1 year', true, NOW(), NOW()),
  ('VIP15', 'Khách hàng thân thiết 15%', 'Giảm 15% tối đa 150.000 VND cho đơn từ 1.000.000 VND', 'PERCENTAGE', 15.00, 150000.00, 1000000.00, 100, 0, NOW() - INTERVAL '1 day', NOW() + INTERVAL '1 year', true, NOW(), NOW()),
  ('HETHAN2025', 'Voucher hết hạn thử nghiệm', 'Voucher mẫu đã hết hạn dùng để test validation lỗi', 'FIXED_AMOUNT', 30000.00, NULL, 100000.00, 50, 0, NOW() - INTERVAL '30 days', NOW() - INTERVAL '1 day', true, NOW(), NOW())
ON CONFLICT ("code") DO NOTHING;

