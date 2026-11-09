import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CUSTOMER } from '@/features/customer/constants';
import { TIME_SLOTS } from '@/constants/resources';
import { buildReferenceQuote, estimateDistanceKm } from '@/services/referenceQuote';
import { useCatalog } from '@/services/store/CatalogStore';
import type { AddressDetail, BookingDetails, BookingItem, HouseType, NewOrderInput, ServiceMode, SurveyPreference } from '@/types';
import {
  DRAFT_STORAGE_KEY,
  EMAIL_REGEX,
  INVENTORY_PRESETS,
  LABOR_SERVICE_KEYWORD,
  MAX_PHOTOS,
  PHONE_REGEX,
  SURVEY_CONTACT_SLOTS,
  emptyAddress,
  formatAddress,
  houseTypeOf,
  isAddressComplete,
} from './constants';

export interface WizardDraft {
  from: AddressDetail;
  to: AddressDetail;
  scheduledDate: string;
  timeSlot: string;
  flexibleTime: boolean;
  scheduleNote: string;
  serviceMode: ServiceMode;
  /** Tên dịch vụ xe tải khách chọn; rỗng = để hệ thống đề xuất theo khối lượng. */
  vehicleService: string;
  addOns: string[];
  homeScale: string;
  quantities: Record<string, number>;
  customItems: BookingItem[];
  surveyPreference: SurveyPreference;
  surveyContactSlot: string;
  customerName: string;
  phone: string;
  email: string;
  customerNote: string;
  agreed: boolean;
}

export interface WizardPhoto {
  name: string;
  url: string;
}

export type StepErrors = Partial<Record<string, string>>;

const toISODate = (d: Date): string => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

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
    timeSlot: TIME_SLOTS[0]!.label,
    flexibleTime: false,
    scheduleNote: '',
    serviceMode: 'FULL_PACKAGE',
    vehicleService: '',
    addOns: [],
    homeScale: '',
    quantities: {},
    customItems: [],
    surveyPreference: 'VIDEO_CALL',
    surveyContactSlot: SURVEY_CONTACT_SLOTS[1]!,
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
    return { ...defaultDraft(), ...(JSON.parse(raw) as Partial<WizardDraft>), agreed: false };
  } catch {
    return null;
  }
};

/** Phụ phí tính theo đầu khó khăn hơn trong hai đầu bốc / dỡ. */
const HOUSE_SEVERITY: HouseType[] = ['GROUND_FLOOR', 'APARTMENT', 'DEEP_ALLEY'];

export const validateStep = (draft: WizardDraft, step: number, itemCount: number): StepErrors => {
  const e: StepErrors = {};
  if (step === 0) {
    (['from', 'to'] as const).forEach((side) => {
      const a = draft[side];
      if (!a.district) e[`${side}.district`] = 'Chọn quận / huyện';
      if (!a.ward) e[`${side}.ward`] = 'Chọn phường / xã';
      if (!a.street.trim()) e[`${side}.street`] = 'Nhập số nhà & tên đường';
    });
    if (isAddressComplete(draft.from) && isAddressComplete(draft.to) && formatAddress(draft.from) === formatAddress(draft.to)) {
      e['to.street'] = 'Điểm đến phải khác điểm xuất phát';
    }
    if (!draft.scheduledDate) e.scheduledDate = 'Chọn ngày chuyển nhà';
    else if (draft.scheduledDate < minMoveDate()) e.scheduledDate = 'Ngày chuyển phải từ ngày mai trở đi';
  }
  if (step === 2 && itemCount === 0) e.items = 'Thêm ít nhất một món đồ hoặc chọn quy mô nhà để gợi ý danh mục';
  if (step === 3) {
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
  const [photos, setPhotos] = useState<WizardPhoto[]>([]);

  const photosRef = useRef(photos);
  photosRef.current = photos;
  // Chỉ thu hồi object URL khi rời trang; ảnh bị xóa lẻ được thu hồi trong removePhoto.
  useEffect(() => () => photosRef.current.forEach((p) => URL.revokeObjectURL(p.url)), []);

  const addPhotos = (files: File[]) =>
    setPhotos((arr) => [...arr, ...files.slice(0, Math.max(0, MAX_PHOTOS - arr.length)).map((f) => ({ name: f.name, url: URL.createObjectURL(f) }))]);
  const removePhoto = (url: string) => {
    URL.revokeObjectURL(url);
    setPhotos((arr) => arr.filter((p) => p.url !== url));
  };

  const update = useCallback(<K extends keyof WizardDraft>(key: K, value: WizardDraft[K]) => setDraft((d) => ({ ...d, [key]: value })), []);
  const updateAddress = useCallback(
    (side: 'from' | 'to', patch: Partial<AddressDetail>) => setDraft((d) => ({ ...d, [side]: { ...d[side], ...patch } })),
    [],
  );

  const activeServices = useMemo(() => catalog.services.filter((s) => s.active), [catalog.services]);

  const items = useMemo<BookingItem[]>(
    () => [
      ...INVENTORY_PRESETS.filter((p) => (draft.quantities[p.name] ?? 0) > 0).map((p) => ({
        id: `preset-${p.name}`,
        name: p.name,
        quantity: draft.quantities[p.name]!,
        volumeM3: p.volumeM3,
        fragile: p.fragile,
      })),
      ...draft.customItems,
    ],
    [draft.quantities, draft.customItems],
  );

  const houseType = useMemo<HouseType>(() => {
    const a = HOUSE_SEVERITY.indexOf(houseTypeOf(draft.from.propertyType));
    const b = HOUSE_SEVERITY.indexOf(houseTypeOf(draft.to.propertyType));
    return HOUSE_SEVERITY[Math.max(a, b)]!;
  }, [draft.from.propertyType, draft.to.propertyType]);

  const requestedServices = useMemo(() => {
    const names: string[] = [];
    if (draft.vehicleService) names.push(draft.vehicleService);
    if (draft.serviceMode === 'FULL_PACKAGE') {
      const labor = activeServices.find((s) => s.group === 'LABOR' && s.name.toLowerCase().includes(LABOR_SERVICE_KEYWORD));
      if (labor) names.push(labor.name);
    }
    draft.addOns.filter((n) => activeServices.some((s) => s.name === n)).forEach((n) => names.push(n));
    return names;
  }, [draft.vehicleService, draft.serviceMode, draft.addOns, activeServices]);

  const fromAddress = formatAddress(draft.from);
  const toAddress = formatAddress(draft.to);
  const routeReady = isAddressComplete(draft.from) && isAddressComplete(draft.to);
  const distanceKm = routeReady ? estimateDistanceKm(fromAddress, toAddress) : null;

  const referenceQuote = useMemo(
    () => buildReferenceQuote({ fromAddress, toAddress, houseType, items, requestedServices }, catalog.services),
    [fromAddress, toAddress, houseType, items, requestedServices, catalog.services],
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
    photosRef.current.forEach((p) => URL.revokeObjectURL(p.url));
    setPhotos([]);
  };

  const buildOrderInput = (): NewOrderInput => {
    const details: BookingDetails = {
      from: draft.from,
      to: draft.to,
      serviceMode: draft.serviceMode,
      homeScale: draft.homeScale,
      surveyPreference: draft.surveyPreference,
      surveyContactSlot: draft.surveyPreference === 'PHOTOS_ONLY' ? '' : draft.surveyContactSlot,
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
      photoNames: photos.map((p) => p.name),
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
    photos,
    addPhotos,
    removePhoto,
    restored,
    activeServices,
    items,
    houseType,
    requestedServices,
    fromAddress,
    toAddress,
    routeReady,
    distanceKm,
    referenceQuote,
    saveDraft,
    clearDraft,
    resetDraft,
    buildOrderInput,
  };
};

export type BookingWizard = ReturnType<typeof useBookingWizard>;
