export const parseShift = (shift: string | null): [number, number] | null => {
  if (!shift) return null;
  const m = shift.match(/(\d{2}):\d{2}\s*-\s*(\d{2}):\d{2}/);
  return m ? [Number(m[1]), Number(m[2])] : null;
};
