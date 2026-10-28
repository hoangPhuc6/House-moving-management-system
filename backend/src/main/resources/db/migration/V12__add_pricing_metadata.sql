-- =============================================================================
-- V12: Dữ liệu phục vụ tính giá (Module 2)
-- 1. service_types.fee_category: nhóm phí của loại dịch vụ (thay cách dò chữ trong tên)
-- 2. services.code: mã cố định cho dịch vụ hệ thống tự cộng (phụ phí), không phụ thuộc id / tên
-- 3. Thêm dịch vụ "Phụ phí vượt km"
-- =============================================================================

-- 1. Nhóm phí. Có mặc định để API tạo loại dịch vụ, dữ liệu và test hiện có không bị ảnh hưởng
ALTER TABLE "service_types" ADD COLUMN IF NOT EXISTS "fee_category" varchar(20) NOT NULL DEFAULT 'TRANSPORT';
ALTER TABLE "service_types" ADD CONSTRAINT chk_service_types_fee_category
  CHECK (fee_category IN ('TRANSPORT', 'LABOR', 'TECHNICAL', 'MATERIAL', 'TERRAIN'));

-- id 1–5 cố định từ seed V7 (OVERRIDING SYSTEM VALUE)
UPDATE "service_types" SET "fee_category" = CASE "id"
  WHEN 2 THEN 'LABOR'
  WHEN 3 THEN 'TECHNICAL'
  WHEN 4 THEN 'MATERIAL'
  WHEN 5 THEN 'TERRAIN'
  ELSE 'TRANSPORT'
END;

-- 2. Mã dịch vụ hệ thống: chỉ dịch vụ hệ thống tự cộng mới có mã, còn lại null
ALTER TABLE "services" ADD COLUMN IF NOT EXISTS "code" varchar(50);
CREATE UNIQUE INDEX IF NOT EXISTS uq_services_code ON "services" ("code") WHERE "code" IS NOT NULL;

-- Gán theo tên trong seed V7 (id dịch vụ khác nhau giữa các máy). Không tìm thấy tên thì bỏ qua, gán tay sau
UPDATE "services" SET "code" = 'SURCHARGE_STAIRS'
WHERE "name" = 'Phụ phí khuân vác thang bộ (mỗi lầu)' AND "code" IS NULL;

UPDATE "services" SET "code" = 'SURCHARGE_DEEP_ALLEY'
WHERE "name" = 'Phụ phí hẻm sâu (> 50 mét)' AND "code" IS NULL;

-- 3. Dịch vụ phụ phí vượt km, thuộc loại "Xe tải vận chuyển" (id 1, nhóm TRANSPORT)
INSERT INTO "services" ("service_type_id", "name", "description", "base_price", "status", "code", "created_at", "updated_at")
SELECT 1, 'Phụ phí vượt km (mỗi km)',
       'Tính cho mỗi km vượt quá số km đã gồm trong giá xe (mặc định 10 km)',
       15000.00, 'ACTIVE', 'SURCHARGE_EXTRA_KM', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM "services" WHERE "code" = 'SURCHARGE_EXTRA_KM')
  AND EXISTS (SELECT 1 FROM "service_types" WHERE "id" = 1);
