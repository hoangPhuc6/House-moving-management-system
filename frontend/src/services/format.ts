export const vndFormatter = new Intl.NumberFormat('vi-VN');

export const formatVND = (amount: number): string => `${vndFormatter.format(Math.round(amount))} đ`;

export const formatTime = (epoch: number): string =>
  new Date(epoch).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' });

export const formatCountdown = (ms: number): string => {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':');
};

let idCounter = 0;

export const uid = (prefix: string): string => {
  idCounter += 1;
  return `${prefix}-${Date.now().toString(36)}-${idCounter}`;
};

export const formatDateVN = (iso: string): string => iso.split('-').reverse().join('/');
