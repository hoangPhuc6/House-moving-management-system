export const isoDate = (t: number): string => new Date(t).toISOString().slice(0, 10);

export const capLabel = (t: number): string => `${String(t).replace('.', ',')} tấn`;

export const matches = (q: string, ...fields: string[]) =>
  !q.trim() || fields.join(" ").toLowerCase().includes(q.trim().toLowerCase());
