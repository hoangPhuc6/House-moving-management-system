import { AREAS, HOUSE_SURCHARGE } from '@/features/landing/constants';
import type { HouseType } from '@/types';

export const estimate = (fromId: string, toId: string, house: HouseType) => {
  const a = AREAS.find((x) => x.id === fromId);
  const b = AREAS.find((x) => x.id === toId);
  if (!a || !b) return null;
  const km = Math.max(3, Math.round(Math.hypot(a.x - b.x, a.y - b.y) * 1.35));
  const base =
    900_000 + km * 15_000 + 4 * 350_000 + 20 * 25_000 + HOUSE_SURCHARGE[house];
  return {
    km,
    min: Math.round((base * 0.92) / 10_000) * 10_000,
    max: Math.round((base * 1.12) / 10_000) * 10_000,
  };
};
