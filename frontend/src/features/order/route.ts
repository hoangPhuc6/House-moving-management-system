import type { AddressDetail, Order } from '@/types';

/** Tên khu vực ngắn: "Phường Long Bình" → "Long Bình"; không có chi tiết thì lấy phần áp chót của địa chỉ. */
const shortPlace = (detail: AddressDetail | undefined | null, full: string): string => {
  if (detail?.ward) return detail.ward.replace(/^(Phường|Xã|Đặc khu)\s+/u, '');
  const parts = full.split(',').map((s) => s.trim()).filter(Boolean);
  return parts.length >= 2 ? parts[parts.length - 2]! : full;
};

const shortProvince = (p: string) => p.replace(/^(TP\.|Thành phố|Tỉnh)\s*/u, '');

/** Lộ trình rút gọn theo phường / xã, VD: "Thạnh Mỹ Tây → Long Bình". Khác tỉnh thì kèm tên tỉnh. */
export const routeLabelOf = (o: Order): string => {
  const d = o.bookingDetails;
  const from = shortPlace(d?.from, o.fromAddress);
  const to = shortPlace(d?.to, o.toAddress);
  if (d?.from?.province && d.to?.province && d.from.province !== d.to.province) {
    return `${from} (${shortProvince(d.from.province)}) → ${to} (${shortProvince(d.to.province)})`;
  }
  return `${from} → ${to}`;
};

/** Quãng đường tốt nhất đang có: theo khảo sát, nếu chưa có thì theo ước tính lúc đặt đơn. */
export const distanceKmOf = (o: Order): number | null => o.surveyReport?.proposal.distanceKm ?? o.referenceQuote?.distanceKm ?? null;

/** "Thạnh Mỹ Tây → Long Bình · 18 km" — đủ để Điều phối nhận ra đơn khi lướt danh sách. */
export const routeSummaryOf = (o: Order): string => {
  const km = distanceKmOf(o);
  return km !== null ? `${routeLabelOf(o)} · ${km} km` : routeLabelOf(o);
};

/** Link chỉ đường Google Maps giữa hai địa chỉ. */
export const mapsRouteUrl = (o: Order): string =>
  `https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(o.fromAddress)}&destination=${encodeURIComponent(o.toAddress)}`;
