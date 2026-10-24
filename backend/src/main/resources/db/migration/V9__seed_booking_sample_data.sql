-- =============================================================================
-- Migration V9: Seed Booking Sample Data (Module 3 - Testing Data)
-- Tables: bookings, booking_routes, booking_items, booking_services,
--         booking_vouchers, booking_status_history
-- =============================================================================

-- 1. Chèn 5 Đơn hàng mẫu (bookings) đại diện cho 5 trạng thái
INSERT INTO "bookings" (
  "id", "booking_code", "customer_id", "created_by", "total_amount", "discount_amount",
  "note", "status", "desired_start_at", "desired_end_at", "contact_name", "contact_phone",
  "created_at", "updated_at"
)
OVERRIDING SYSTEM VALUE
VALUES
  -- Đơn 1: PENDING_SURVEY (Chờ khảo sát - customer1) - Áp dụng voucher GIAM50K
  (
    1, 'BKG20261019001',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    1570000.00, 50000.00,
    'Chuyển căn hộ chung cư 2 phòng ngủ, có thang máy vận chuyển hàng',
    'PENDING_SURVEY',
    NOW() + INTERVAL '2 days',
    NOW() + INTERVAL '2 days 4 hours',
    'Võ Minh Khách', '0901234567',
    NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'
  ),

  -- Đơn 2: CONFIRMED (Đã xác nhận chốt đơn - customer1) - Áp dụng voucher CHUYENVIEN100K
  (
    2, 'BKG20261018002',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    2850000.00, 100000.00,
    'Chuyển nhà phố 2 tầng sang căn hộ, có tháo lắp máy lạnh và giường tủ',
    'CONFIRMED',
    NOW() + INTERVAL '3 days',
    NOW() + INTERVAL '3 days 6 hours',
    'Võ Minh Khách', '0901234567',
    NOW() - INTERVAL '1 day', NOW() - INTERVAL '12 hours'
  ),

  -- Đơn 3: IN_PROGRESS (Đang thi công chuyển nhà - customer2) - Áp dụng voucher VIP15
  (
    3, 'BKG20261017003',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer2@gmail.com'),
    (SELECT u.id FROM "users" u WHERE u.email = 'customer2@gmail.com'),
    3400000.00, 150000.00,
    'Chuyển văn phòng nhỏ công ty, có két sắt 150kg và nhiều dàn máy tính',
    'IN_PROGRESS',
    NOW() + INTERVAL '3 hours',
    NOW() + INTERVAL '8 hours',
    'Đặng Thu Hằng', '0987654321',
    NOW() - INTERVAL '2 days', NOW() - INTERVAL '3 hours'
  ),

  -- Đơn 4: COMPLETED (Đã hoàn thành - customer2) - Không dùng voucher
  (
    4, 'BKG20261015004',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer2@gmail.com'),
    (SELECT u.id FROM "users" u WHERE u.email = 'customer2@gmail.com'),
    1150000.00, 0.00,
    'Chuyển phòng trọ sinh viên, đồ đạc gọn nhẹ đã đóng thùng sẵn',
    'COMPLETED',
    NOW() - INTERVAL '4 days',
    NOW() - INTERVAL '4 days' + INTERVAL '3 hours',
    'Đặng Thu Hằng', '0987654321',
    NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days'
  ),

  -- Đơn 5: CANCELLED (Đã bị hủy - customer1) - Đơn hàng đã hủy vì khách đổi kế hoạch
  (
    5, 'BKG20261014005',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    1800000.00, 0.00,
    'Khách hàng thay đổi kế hoạch dọn nhà sang tháng sau',
    'CANCELLED',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '3 days' + INTERVAL '4 hours',
    'Võ Minh Khách', '0901234567',
    NOW() - INTERVAL '6 days', NOW() - INTERVAL '4 days'
  )
ON CONFLICT ("id") DO NOTHING;

-- Reset sequence cho bảng bookings
SELECT setval(pg_get_serial_sequence('bookings', 'id'), COALESCE((SELECT MAX(id) FROM bookings), 1));


-- 2. Chèn Lộ trình di chuyển (booking_routes) cho 5 đơn hàng
INSERT INTO "booking_routes" (
  "id", "booking_id", "pickup_address", "pickup_lat", "pickup_lng", "pickup_property_type",
  "pickup_note", "dropoff_address", "dropoff_lat", "dropoff_lng", "dropoff_property_type",
  "dropoff_note", "estimated_distance_km"
)
OVERRIDING SYSTEM VALUE
VALUES
  -- Route Đơn 1: Nhà phố Q1 -> Chung cư Q7 (12.5 km)
  (
    1, 1,
    '123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', 10.7768890, 106.7008060, 'HOUSE',
    'Tầng: 0, Thang máy: Không, Cự ly hẻm: 0m (Xe tải đỗ tận cửa nhà)',
    'Chung cư Sunrise City, Nguyễn Hữu Thọ, Phường Tân Hưng, Quận 7, TP. Hồ Chí Minh', 10.7554210, 106.6998520, 'APARTMENT',
    'Tầng: 12, Thang máy: Có, Cự ly hẻm: 0m (Có hầm đỗ xe tải và thang máy tải hàng)',
    12.50
  ),

  -- Route Đơn 2: Chung cư cũ lầu 3 thang bộ Q3 -> Nhà phố hẻm sâu Q10 (8.2 km)
  (
    2, 2,
    '456 Nguyễn Đình Chiểu, Phường 4, Quận 3, TP. Hồ Chí Minh', 10.7712340, 106.6854320, 'APARTMENT',
    'Tầng: 3, Thang máy: Không, Cự ly hẻm: 15m (Phải vác thang bộ từ lầu 3 xuống)',
    '789 Sư Vạn Hạnh, Phường 12, Quận 10, TP. Hồ Chí Minh', 10.7756780, 106.6698760, 'ALLEY',
    'Tầng: 1, Thang máy: Không, Cự ly hẻm: 60m (Hẻm nhỏ xe tải không vào được, cần xe kéo đẩy bộ 60m)',
    8.20
  ),

  -- Route Đơn 3: Tòa nhà văn phòng Q.Bình Thạnh -> KCN Tân Bình (15.8 km)
  (
    3, 3,
    '18 Điện Biên Phủ, Phường 15, Quận Bình Thạnh, TP. Hồ Chí Minh', 10.7987650, 106.7098760, 'OFFICE',
    'Tầng: 4, Thang máy: Có, Cự ly hẻm: 0m (Đỗ xe tại sảnh nhận hàng)',
    'Số 5 Đường CN13, KCN Tân Bình, Phường Tây Thạnh, Quận Tân Phú, TP. Hồ Chí Minh', 10.8123450, 106.6234560, 'OFFICE',
    'Tầng: 1, Thang máy: Không, Cự ly hẻm: 0m (Mặt tiền kho bãi rộng)',
    15.80
  ),

  -- Route Đơn 4: Nhà trọ Gò Vấp -> Căn hộ mini Bình Tân (14.0 km)
  (
    4, 4,
    '50 Quang Trung, Phường 10, Quận Gò Vấp, TP. Hồ Chí Minh', 10.8345670, 106.6678900, 'ALLEY',
    'Tầng: 1, Thang máy: Không, Cự ly hẻm: 20m',
    '120 Tên Lửa, Phường An Lạc A, Quận Bình Tân, TP. Hồ Chí Minh', 10.7512340, 106.6123450, 'APARTMENT',
    'Tầng: 2, Thang máy: Có, Cự ly hẻm: 0m',
    14.00
  ),

  -- Route Đơn 5: Đơn hủy
  (
    5, 5,
    '99 Bến Vân Đồn, Phường 9, Quận 4, TP. Hồ Chí Minh', 10.7623450, 106.7012340, 'APARTMENT',
    'Tầng: 8, Thang máy: Có, Cự ly hẻm: 0m',
    '33 Thảo Điền, Phường Thảo Điền, TP. Thủ Đức, TP. Hồ Chí Minh', 10.8034560, 106.7345670, 'HOUSE',
    'Tầng: 0, Thang máy: Không, Cự ly hẻm: 0m',
    11.20
  )
ON CONFLICT ("booking_id") DO NOTHING;

SELECT setval(pg_get_serial_sequence('booking_routes', 'id'), COALESCE((SELECT MAX(id) FROM booking_routes), 1));


-- 3. Chèn Chi tiết đồ đạc (booking_items)
INSERT INTO "booking_items" (
  "id", "booking_id", "item_name", "quantity", "estimated_volume_m3", "is_fragile",
  "packing_required", "disassembly_required", "note"
)
OVERRIDING SYSTEM VALUE
VALUES
  -- Đồ đạc Đơn 1
  (1, 1, 'Tủ lạnh Panasonic Inverter 300L', 1, 1.20, false, true, false, 'Cần quấn màng PE chống trầy xước cánh tủ'),
  (2, 1, 'Máy giặt cửa ngang LG 9kg', 1, 0.80, false, true, false, 'Cần chốt khóa lồng giặt trước khi di chuyển'),
  (3, 1, 'Giường ngủ gỗ MDF 1m8x2m', 1, 1.50, false, false, true, 'Yêu cầu kỹ thuật viên tháo dỡ và lắp ráp lại'),
  (4, 1, 'Bàn ăn mặt kính + 4 ghế gỗ', 1, 1.10, true, true, false, 'Mặt kính cường lực dễ vỡ, bọc xốp bóng khí 2 lớp'),
  (5, 1, 'Thùng carton đựng quần áo và sách vở', 10, 1.00, false, false, false, 'Đã dán băng keo niêm phong'),

  -- Đồ đạc Đơn 2
  (6, 2, 'Bộ ghế Sofa chữ L đệm da thật', 1, 2.50, false, true, false, 'Đồ da xịn, yêu cầu bao bọc PE kỹ càng'),
  (7, 2, 'Tivi Sony Oled 65 inch', 1, 0.40, true, true, true, 'Rất dễ vỡ, cần tháo giá treo tường và đóng thùng xốp riêng'),
  (8, 2, 'Tủ quần áo gỗ xoan đào 3 cánh lớn', 1, 2.00, false, false, true, 'Gỗ tự nhiên nặng, cần 2 kỹ thuật viên tháo lắp'),
  (9, 2, 'Máy lạnh Daikin 1.5 HP (Cục nóng + Cục lạnh)', 1, 0.60, true, true, true, 'Yêu cầu thu hồi gas và hút chân không khi lắp lại'),

  -- Đồ đạc Đơn 3
  (10, 3, 'Két sắt gia đình Hòa Phát 150kg', 1, 0.50, false, false, false, 'Hàng siêu nặng cồng kềnh, cần 3 nhân công lực lưỡng khuân vác'),
  (11, 3, 'Bàn làm việc văn phòng cụm 4 chỗ', 2, 2.20, false, false, true, 'Tháo dời vách ngăn và chân sắt'),
  (12, 3, 'Dàn máy tính PC để bàn', 8, 1.60, true, true, false, 'Màn hình và case máy tính đóng thùng carton riêng'),

  -- Đồ đạc Đơn 4
  (13, 4, 'Tủ lạnh mini 90L', 1, 0.40, false, true, false, 'Đồ trọ sinh viên'),
  (14, 4, 'Bàn học gấp gọn + Ghế xoay', 1, 0.30, false, false, false, 'Gọn nhẹ'),
  (15, 4, 'Vali quần áo + Đồ dùng cá nhân', 4, 0.60, false, false, false, 'Đã đóng gói sẵn')
ON CONFLICT ("id") DO NOTHING;

SELECT setval(pg_get_serial_sequence('booking_items', 'id'), COALESCE((SELECT MAX(id) FROM booking_items), 1));


-- 4. Chèn Dịch vụ đính kèm Snapshot giá (booking_services) - Dùng subquery linh hoạt tìm theo tên dịch vụ
INSERT INTO "booking_services" (
  "id", "booking_id", "service_id", "price_applied", "quantity", "note"
)
OVERRIDING SYSTEM VALUE
VALUES
  -- Dịch vụ Đơn 1: Xe tải 1.5T + 2 Nhân công + Màng PE + Thùng carton + Tháo lắp giường
  (
    1, 1,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Xe tải 1.5 Tấn%' LIMIT 1),
    450000.00, 1, 'Xe tải 1.5 Tấn nội thành'
  ),
  (
    2, 1,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Nhân công bốc xếp cơ bản%' LIMIT 1),
    250000.00, 2, '2 Nhân công bốc xếp cơ bản theo chuyến'
  ),
  (
    3, 1,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Tháo lắp giường tủ%' LIMIT 1),
    200000.00, 1, 'Tháo lắp giường tủ gỗ 1m8'
  ),
  (
    4, 1,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Thùng carton tiêu chuẩn%' LIMIT 1),
    25000.00, 10, '10 Thùng carton tiêu chuẩn'
  ),
  (
    5, 1,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Cuộn màng PE%' LIMIT 1),
    120000.00, 1, '1 Cuộn màng PE bọc đồ'
  ),

  -- Dịch vụ Đơn 2: Xe tải 2T + Nhân công đồ nặng + Tháo máy lạnh + Phụ phí thang bộ + Phụ phí hẻm sâu
  (
    6, 2,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Xe tải 2.0 Tấn%' LIMIT 1),
    600000.00, 1, 'Xe tải 2.0 Tấn nội thành'
  ),
  (
    7, 2,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Nhân công bốc xếp đồ nặng%' LIMIT 1),
    400000.00, 2, '2 Nhân công chuyên bê đồ nặng, két sắt, sofa khổ lớn'
  ),
  (
    8, 2,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Tháo lắp máy lạnh%' LIMIT 1),
    300000.00, 1, 'Tháo lắp máy lạnh trọn gói'
  ),
  (
    9, 2,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Tháo lắp giường tủ%' LIMIT 1),
    200000.00, 1, 'Tháo lắp tủ quần áo 3 cánh'
  ),
  (
    10, 2,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Phụ phí khuân vác thang bộ%' LIMIT 1),
    50000.00, 2, 'Phụ phí khuân vác thang bộ lầu 3 (2 tầng phát sinh)'
  ),
  (
    11, 2,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Phụ phí hẻm sâu%' LIMIT 1),
    100000.00, 1, 'Phụ phí hẻm sâu 60m trung chuyển xe kéo tay'
  ),

  -- Dịch vụ Đơn 3: Xe tải 2T + 3 Nhân công đồ nặng + Thùng carton
  (
    12, 3,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Xe tải 2.0 Tấn%' LIMIT 1),
    600000.00, 1, 'Xe tải 2.0 Tấn'
  ),
  (
    13, 3,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Nhân công bốc xếp đồ nặng%' LIMIT 1),
    400000.00, 3, '3 Nhân công khiêng két sắt 150kg'
  ),
  (
    14, 3,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Thùng carton tiêu chuẩn%' LIMIT 1),
    25000.00, 20, '20 Thùng carton đựng hồ sơ'
  ),

  -- Dịch vụ Đơn 4: Xe tải 500kg + 1 Nhân công
  (
    15, 4,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Xe tải 500kg%' LIMIT 1),
    300000.00, 1, 'Xe tải nhỏ 500kg phòng trọ'
  ),
  (
    16, 4,
    (SELECT s.id FROM "services" s WHERE s.name LIKE 'Nhân công bốc xếp cơ bản%' LIMIT 1),
    250000.00, 1, '1 Nhân công phụ khuân đồ lên xe'
  )
ON CONFLICT ("id") DO NOTHING;

SELECT setval(pg_get_serial_sequence('booking_services', 'id'), COALESCE((SELECT MAX(id) FROM booking_services), 1));


-- 5. Chèn Voucher áp dụng (booking_vouchers) - Dùng subquery tìm voucher_id theo code
INSERT INTO "booking_vouchers" (
  "id", "voucher_id", "booking_id", "discount_amount", "applied_date"
)
OVERRIDING SYSTEM VALUE
VALUES
  -- Đơn 1 dùng voucher GIAM50K
  (
    1,
    (SELECT v.id FROM "vouchers" v WHERE v.code = 'GIAM50K'),
    1, 50000.00, NOW() - INTERVAL '2 hours'
  ),
  -- Đơn 2 dùng voucher CHUYENVIEN100K
  (
    2,
    (SELECT v.id FROM "vouchers" v WHERE v.code = 'CHUYENVIEN100K'),
    2, 100000.00, NOW() - INTERVAL '1 day'
  ),
  -- Đơn 3 dùng voucher VIP15
  (
    3,
    (SELECT v.id FROM "vouchers" v WHERE v.code = 'VIP15'),
    3, 150000.00, NOW() - INTERVAL '2 days'
  )
ON CONFLICT ("voucher_id", "booking_id") DO NOTHING;

SELECT setval(pg_get_serial_sequence('booking_vouchers', 'id'), COALESCE((SELECT MAX(id) FROM booking_vouchers), 1));

-- Cập nhật lượt dùng used_count cho các voucher đã được áp dụng
UPDATE "vouchers" SET "used_count" = "used_count" + 1 WHERE "code" IN ('GIAM50K', 'CHUYENVIEN100K', 'VIP15');


-- 6. Chèn Lịch sử chuyển đổi trạng thái (booking_status_history)
INSERT INTO "booking_status_history" (
  "id", "booking_id", "old_status", "new_status", "changed_by", "changed_at", "note"
)
OVERRIDING SYSTEM VALUE
VALUES
  -- Lịch sử Đơn 1 (PENDING_SURVEY)
  (
    1, 1, NULL, 'PENDING_SURVEY',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    NOW() - INTERVAL '2 hours',
    'Khách hàng tạo đơn đặt chuyển nhà trực tuyến'
  ),

  -- Lịch sử Đơn 2 (PENDING_SURVEY -> CONFIRMED)
  (
    2, 2, NULL, 'PENDING_SURVEY',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    NOW() - INTERVAL '1 day',
    'Khách hàng tạo đơn đặt chuyển nhà'
  ),
  (
    3, 2, 'PENDING_SURVEY', 'CONFIRMED',
    (SELECT u.id FROM "users" u WHERE u.email = 'dispatcher@housemoving.vn'),
    NOW() - INTERVAL '12 hours',
    'Điều phối viên đã liên hệ khảo sát trực tuyến và xác nhận lịch hẹn'
  ),

  -- Lịch sử Đơn 3 (PENDING_SURVEY -> CONFIRMED -> IN_PROGRESS)
  (
    4, 3, NULL, 'PENDING_SURVEY',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer2@gmail.com'),
    NOW() - INTERVAL '2 days',
    'Khách hàng tạo đơn dọn văn phòng'
  ),
  (
    5, 3, 'PENDING_SURVEY', 'CONFIRMED',
    (SELECT u.id FROM "users" u WHERE u.email = 'dispatcher@housemoving.vn'),
    NOW() - INTERVAL '1 day',
    'Đã điều phối xe tải 2.0 Tấn và 3 nhân công chuyên nghiệp'
  ),
  (
    6, 3, 'CONFIRMED', 'IN_PROGRESS',
    (SELECT u.id FROM "users" u WHERE u.email = 'mover1@housemoving.vn'),
    NOW() - INTERVAL '3 hours',
    'Đội ngũ nhân viên đã có mặt tại điểm đón và đang tiến hành bốc xếp tài sản'
  ),

  -- Lịch sử Đơn 4 (PENDING_SURVEY -> CONFIRMED -> IN_PROGRESS -> COMPLETED)
  (
    7, 4, NULL, 'PENDING_SURVEY',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer2@gmail.com'),
    NOW() - INTERVAL '5 days',
    'Khách hàng đặt chuyển phòng trọ'
  ),
  (
    8, 4, 'PENDING_SURVEY', 'CONFIRMED',
    (SELECT u.id FROM "users" u WHERE u.email = 'dispatcher@housemoving.vn'),
    NOW() - INTERVAL '4 days 6 hours',
    'Xác nhận lịch vận chuyển'
  ),
  (
    9, 4, 'CONFIRMED', 'IN_PROGRESS',
    (SELECT u.id FROM "users" u WHERE u.email = 'mover2@housemoving.vn'),
    NOW() - INTERVAL '4 days 3 hours',
    'Bắt đầu chuyển đồ'
  ),
  (
    10, 4, 'IN_PROGRESS', 'COMPLETED',
    (SELECT u.id FROM "users" u WHERE u.email = 'mover2@housemoving.vn'),
    NOW() - INTERVAL '4 days',
    'Chuyển nhà hoàn tất, khách hàng đã nghiệm thu tài sản nguyên vẹn và thanh toán đầy đủ'
  ),

  -- Lịch sử Đơn 5 (PENDING_SURVEY -> CANCELLED)
  (
    11, 5, NULL, 'PENDING_SURVEY',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    NOW() - INTERVAL '6 days',
    'Khách hàng tạo đơn'
  ),
  (
    12, 5, 'PENDING_SURVEY', 'CANCELLED',
    (SELECT u.id FROM "users" u WHERE u.email = 'customer1@gmail.com'),
    NOW() - INTERVAL '4 days',
    'Khách hàng chủ động hủy đơn: Thay đổi ngày nhận nhà mới'
  )
ON CONFLICT ("id") DO NOTHING;

SELECT setval(pg_get_serial_sequence('booking_status_history', 'id'), COALESCE((SELECT MAX(id) FROM booking_status_history), 1));

