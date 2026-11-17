import { useCallback, useMemo, useState } from 'react';
import { CUSTOMER } from '@/features/customer/constants';
import { maxLaborOf, VEHICLE_PRICING } from '@/constants/pricing';
import { DEFAULT_START_TIME, LAST_START_TIME, OPEN_TIME, isValidStartTime, toStartTime } from '@/constants/resources';
import { parseCapacityTons } from '@/services/dispatch';
import { buildReferenceQuote, estimateDistanceKm } from '@/services/referenceQuote';
import { useCatalog } from '@/services/store/CatalogStore';
import type { AddressDetail, BookingDetails, BookingItem, HouseType, NewOrderInput, ServiceMode, SurveyPreference, VehicleLine } from '@/types';
import {
  DRAFT_STORAGE_KEY,
  EMAIL_REGEX,
  LABOR_SERVICE_KEYWORD,
  PACKING_LABEL,
  PHONE_REGEX,
  SURVEY_TIME_SLOTS,
  emptyAddress,
  fitFloor,
  formatAddress,
  houseTypeOf,
  isAddressComplete,
  needsSurveyAppointment,
} from './constants';

export interface WizardDraft {
  from: AddressDetail;
  to: AddressDetail;
  scheduledDate: string;
  /** Giờ xe bắt đầu đi (HH:MM). */
  timeSlot: string;
  flexibleTime: boolean;
  scheduleNote: string;
  serviceMode: ServiceMode;
  /** Gói chỉ thuê xe: số xe theo tên dịch vụ xe tải (ghép được nhiều loại, ít nhất 1 xe). Gói trọn gói do chuyên viên đề xuất sau khảo sát. */
  vehicleQty: Record<string, number>;
  /** Gói chỉ thuê xe: số nhân viên bốc vác thuê thêm (0 = tự khiêng vác). */
  laborCount: number;
  /** Gói trọn gói: có cần MoveFlow đóng gói đồ đạc không (vật tư carton, băng keo, màng PE… chốt khi khảo sát). */
  packing: boolean;
  /** Tiện ích bổ sung khác (tháo lắp…) — không gồm vật tư đóng gói. */
  addOns: string[];
  surveyPreference: SurveyPreference;
  /** Ngày hẹn khảo sát (Video Call / tận nơi). */
  surveyDate: string;
  surveyContactSlot: string;
  customerName: string;
  phone: string;
  email: string;
  customerNote: string;
  agreed: boolean;
}

export type StepErrors = Partial<Record<string, string>>;

const toISODate = (d: Date): string => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

export const todayISO = (): string => toISODate(new Date());

export const minMoveDate = (): string => {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return toISODate(d);
};

const defaultDraft = (): WizardDraft => {
  const d = new Date();
  d.setDate(d.getDate() + 3);
  return {
    from: emptyAddress(),
    to: emptyAddress(),
    scheduledDate: toISODate(d),
    timeSlot: DEFAULT_START_TIME,
    flexibleTime: false,
    scheduleNote: '',
    serviceMode: 'FULL_PACKAGE',
    vehicleQty: {},
    laborCount: 0,
    packing: true,
    addOns: [],
    surveyPreference: 'VIDEO_CALL',
    surveyDate: minMoveDate(),
    surveyContactSlot: SURVEY_TIME_SLOTS[1]!,
    customerName: CUSTOMER.name,
    phone: CUSTOMER.phone,
    email: '',
    customerNote: '',
    agreed: false,
  };
};

const readDraft = (): WizardDraft | null => {
  try {
    const raw = window.localStorage.getItem(DRAFT_STORAGE_KEY);
    if (!raw) return null;
    const saved = JSON.parse(raw) as Partial<WizardDraft> & { vehicleService?: string };
    const draft: WizardDraft = { ...defaultDraft(), ...saved, agreed: false };
    if (!saved.vehicleQty && saved.vehicleService) draft.vehicleQty = { [saved.vehicleService]: 1 };
    const fit = (a: AddressDetail): AddressDetail => ({ ...a, floor: fitFloor(a.propertyType, a.floor) });
    return { ...draft, from: fit(draft.from), to: fit(draft.to), timeSlot: toStartTime(draft.timeSlot) };
  } catch {
    return null;
  }
};

/** Phụ phí tính theo đầu khó khăn hơn trong hai đầu bốc / dỡ. */
const HOUSE_SEVERITY: HouseType[] = ['GROUND_FLOOR', 'APARTMENT', 'DEEP_ALLEY'];

export const validateStep = (draft: WizardDraft, step: number): StepErrors => {
  const e: StepErrors = {};
  if (step === 0) {
    (['from', 'to'] as const).forEach((side) => {
      const a = draft[side];
      if (!a.ward) e[`${side}.ward`] = 'Chọn phường / xã';
      if (!a.street.trim()) e[`${side}.street`] = 'Nhập số nhà & tên đường';
    });
    if (isAddressComplete(draft.from) && isAddressComplete(draft.to) && formatAddress(draft.from) === formatAddress(draft.to)) {
      e['to.street'] = 'Điểm đến phải khác điểm xuất phát';
    }
    if (!draft.scheduledDate) e.scheduledDate = 'Chọn ngày chuyển nhà';
    else if (draft.scheduledDate < minMoveDate()) e.scheduledDate = 'Ngày chuyển phải từ ngày mai trở đi';
    if (!isValidStartTime(draft.timeSlot)) e.timeSlot = `Chọn giờ bắt đầu trong khoảng ${OPEN_TIME} – ${LAST_START_TIME}`;
  }
  if (step === 1 && draft.serviceMode === 'TRUCK_ONLY' && !Object.values(draft.vehicleQty).some((n) => n > 0)) e.vehicleService = 'Chọn ít nhất một xe cần thuê';
  if (step === 1 && draft.serviceMode === 'FULL_PACKAGE') {
    if (needsSurveyAppointment(draft.surveyPreference)) {
      if (!draft.surveyDate) e.surveyDate = 'Chọn ngày khảo sát';
      else if (draft.surveyDate < todayISO()) e.surveyDate = 'Ngày khảo sát không được ở quá khứ';
      else if (draft.scheduledDate && draft.surveyDate >= draft.scheduledDate) e.surveyDate = 'Ngày khảo sát phải trước ngày chuyển nhà';
      if (!SURVEY_TIME_SLOTS.includes(draft.surveyContactSlot)) e.surveyContactSlot = 'Chọn khung giờ khảo sát';
    }
  }
  if (step === 2) {
    if (!draft.customerName.trim()) e.customerName = 'Vui lòng nhập họ tên';
    if (!PHONE_REGEX.test(draft.phone.trim())) e.phone = 'Số điện thoại không hợp lệ';
    if (draft.email.trim() && !EMAIL_REGEX.test(draft.email.trim())) e.email = 'Email không hợp lệ';
    if (!draft.agreed) e.agreed = 'Bạn cần xác nhận thông tin & đồng ý điều khoản';
  }
  return e;
};

export const useBookingWizard = () => {
  const { catalog } = useCatalog();
  const [draft, setDraft] = useState<WizardDraft>(() => readDraft() ?? defaultDraft());
  const [restored] = useState(() => readDraft() !== null);

  const update = useCallback(<K extends keyof WizardDraft>(key: K, value: WizardDraft[K]) => setDraft((d) => ({ ...d, [key]: value })), []);
  const updateAddress = useCallback(
    (side: 'from' | 'to', patch: Partial<AddressDetail>) =>
      setDraft((d) => {
        const next = { ...d[side], ...patch };
        // Đổi loại bất động sản → tầng phải thuộc danh sách tầng của loại mới (VD: nhà mặt tiền chỉ có tầng trệt).
        return { ...d, [side]: { ...next, floor: fitFloor(next.propertyType, next.floor) } };
      }),
    [],
  );

  const activeServices = useMemo(() => catalog.services.filter((s) => s.active), [catalog.services]);

  /** Danh mục đồ không thu thập trong wizard — chuyên viên chốt khi khảo sát. */
  const items = useMemo<BookingItem[]>(() => [], []);

  const houseType = useMemo<HouseType>(() => {
    const a = HOUSE_SEVERITY.indexOf(houseTypeOf(draft.from.propertyType));
    const b = HOUSE_SEVERITY.indexOf(houseTypeOf(draft.to.propertyType));
    return HOUSE_SEVERITY[Math.max(a, b)]!;
  }, [draft.from.propertyType, draft.to.propertyType]);

  const laborService = useMemo(
    () => activeServices.find((s) => s.group === 'LABOR' && s.name.toLowerCase().includes(LABOR_SERVICE_KEYWORD)) ?? null,
    [activeServices],
  );

  /** Vật tư đóng gói trong danh mục — khách chỉ chọn "có / không", điều phối chốt số lượng từng loại khi khảo sát. */
  const packingServices = useMemo(() => activeServices.filter((s) => s.group === 'PACKING').map((s) => s.name), [activeServices]);
  /** Gói chỉ thuê xe dành cho khách đã tự đóng gói — không có dịch vụ đóng gói. */
  const packingRequested = draft.serviceMode === 'FULL_PACKAGE' && draft.packing && packingServices.length > 0;

  const requestedServices = useMemo(() => {
    const names: string[] = [];
    const truckOnly = draft.serviceMode === 'TRUCK_ONLY';
    if (truckOnly) Object.entries(draft.vehicleQty).forEach(([name, n]) => n > 0 && names.push(name));
    if (laborService && (!truckOnly || draft.laborCount > 0)) names.push(laborService.name);
    if (packingRequested) names.push(...packingServices);
    draft.addOns.filter((n) => !packingServices.includes(n) && activeServices.some((s) => s.name === n)).forEach((n) => names.push(n));
    return names;
  }, [draft.vehicleQty, draft.serviceMode, draft.laborCount, draft.addOns, activeServices, laborService, packingRequested, packingServices]);

  /** Dịch vụ hiển thị cho khách — các vật tư đóng gói gộp thành một mục "Đóng gói đồ đạc". */
  const displayServices = useMemo(() => {
    const names = requestedServices
      .filter((n) => !packingServices.includes(n))
      .map((n) => ((draft.vehicleQty[n] ?? 0) > 1 && draft.serviceMode === 'TRUCK_ONLY' ? `${n} ×${draft.vehicleQty[n]}` : n));
    if (packingRequested) names.push(PACKING_LABEL);
    return names;
  }, [requestedServices, packingServices, packingRequested, draft.vehicleQty, draft.serviceMode]);

  /** Bảng giá của xe khách chọn (gói chỉ thuê xe) — quyết định đơn giá & số bốc vác tối đa. */
  /** Các xe khách chọn (gói chỉ thuê xe) theo tải trọng. */
  const vehicleLines = useMemo<VehicleLine[]>(() => {
    if (draft.serviceMode !== 'TRUCK_ONLY') return [];
    return Object.entries(draft.vehicleQty)
      .map(([name, qty]) => ({ capacity: parseCapacityTons(name), qty }))
      .filter((l): l is VehicleLine => l.capacity !== null && l.qty > 0 && !!VEHICLE_PRICING[l.capacity]);
  }, [draft.serviceMode, draft.vehicleQty]);

  /** Số người tối đa các xe đã chọn chở được — giới hạn số bốc vác thuê thêm. */
  const seatLimit = maxLaborOf(vehicleLines);

  const setVehicleCount = (name: string, qty: number) =>
    setDraft((d) => {
      const vehicleQty = { ...d.vehicleQty, [name]: Math.max(0, qty) };
      const seats = maxLaborOf(
        Object.entries(vehicleQty)
          .map(([n, q]) => ({ capacity: parseCapacityTons(n) ?? 0, qty: q }))
          .filter((l) => l.qty > 0),
      );
      return { ...d, vehicleQty, laborCount: Math.min(d.laborCount, seats) };
    });

  const fromAddress = formatAddress(draft.from);
  const toAddress = formatAddress(draft.to);
  const routeReady = isAddressComplete(draft.from) && isAddressComplete(draft.to);
  const distanceKm = routeReady ? estimateDistanceKm(fromAddress, toAddress) : null;
  /**
   * Chỉ hiện giá tạm tính cho gói chỉ thuê xe (khách tự chọn xe, giá gần như giá chốt).
   * Gói trọn gói phụ thuộc khảo sát — không hiện số tạm tính để tránh khách bỏ đơn vì con số chưa chính xác.
   */
  const estimateReady = routeReady && draft.serviceMode === 'TRUCK_ONLY' && vehicleLines.length > 0;

  const referenceQuote = useMemo(
    () =>
      buildReferenceQuote(
        {
          fromAddress,
          toAddress,
          houseType,
          items,
          requestedServices,
          laborQuantity: draft.serviceMode === 'TRUCK_ONLY' ? draft.laborCount : undefined,
          vehicleLines: vehicleLines.length ? vehicleLines : undefined,
        },
        catalog.services,
      ),
    [fromAddress, toAddress, houseType, items, requestedServices, draft.serviceMode, draft.laborCount, vehicleLines, catalog.services],
  );

  const saveDraft = (): boolean => {
    try {
      window.localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify({ ...draft, agreed: false }));
      return true;
    } catch {
      return false;
    }
  };

  const clearDraft = () => {
    try {
      window.localStorage.removeItem(DRAFT_STORAGE_KEY);
    } catch {
      /* bộ nhớ trình duyệt không khả dụng — bỏ qua */
    }
  };

  const resetDraft = () => {
    clearDraft();
    setDraft(defaultDraft());
  };

  const buildOrderInput = (): NewOrderInput => {
    const fullPackage = draft.serviceMode === 'FULL_PACKAGE';
    const appointment = fullPackage && needsSurveyAppointment(draft.surveyPreference);
    const details: BookingDetails = {
      from: draft.from,
      to: draft.to,
      serviceMode: draft.serviceMode,
      surveyPreference: fullPackage ? draft.surveyPreference : null,
      laborCount: fullPackage ? 0 : draft.laborCount,
      vehicles: fullPackage ? undefined : vehicleLines,
      surveyDate: appointment ? draft.surveyDate : '',
      surveyContactSlot: appointment ? draft.surveyContactSlot : '',
      flexibleTime: draft.flexibleTime,
      scheduleNote: draft.scheduleNote.trim(),
      email: draft.email.trim(),
      customerNote: draft.customerNote.trim(),
    };
    return {
      customerName: draft.customerName.trim(),
      phone: draft.phone.trim(),
      fromAddress,
      toAddress,
      houseType,
      scheduledDate: draft.scheduledDate,
      timeSlot: draft.timeSlot,
      photoNames: [],
      items,
      requestedServices,
      referenceQuote,
      details,
    };
  };

  return {
    draft,
    update,
    updateAddress,
    setDraft,
    restored,
    activeServices,
    laborService,
    vehicleLines,
    seatLimit,
    setVehicleCount,
    items,
    houseType,
    requestedServices,
    displayServices,
    fromAddress,
    toAddress,
    routeReady,
    distanceKm,
    estimateReady,
    referenceQuote,
    saveDraft,
    clearDraft,
    resetDraft,
    buildOrderInput,
  };
};

export type BookingWizard = ReturnType<typeof useBookingWizard>;
