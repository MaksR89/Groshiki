const MONTHS_OF = [
  "января", "февраля", "марта", "апреля", "мая", "июня",
  "июля", "августа", "сентября", "октября", "ноября", "декабря",
];
const MONTHS_CAP = [
  "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
  "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
];
export const MONTHS_SHORT = ["янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"];

export function toKey(y: number, m: number, d: number): string {
  return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

export function parts(key: string): { y: number; m: number; d: number } {
  const [y, m, d] = key.slice(0, 10).split("-").map(Number);
  return { y, m, d };
}

export function todayKey(now = new Date()): string {
  return toKey(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

export function nowStamp(now = new Date()): string {
  const day = todayKey(now);
  const hh = String(now.getHours()).padStart(2, "0");
  const mm = String(now.getMinutes()).padStart(2, "0");
  const ss = String(now.getSeconds()).padStart(2, "0");
  return `${day}T${hh}:${mm}:${ss}`;
}

export function addDays(key: string, delta: number): string {
  const { y, m, d } = parts(key);
  const dt = new Date(y, m - 1, d + delta);
  return toKey(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
}

export function addMonths(key: string, delta: number): string {
  const { y, m } = parts(key);
  const dt = new Date(y, m - 1 + delta, 1);
  return toKey(dt.getFullYear(), dt.getMonth() + 1, 1);
}

export function monthRange(key: string): { from: string; to: string } {
  const { y, m } = parts(key);
  const last = new Date(y, m, 0).getDate();
  return { from: toKey(y, m, 1), to: toKey(y, m, last) };
}

export function yearRange(key: string): { from: string; to: string } {
  const { y } = parts(key);
  return { from: toKey(y, 1, 1), to: toKey(y, 12, 31) };
}

export function inclusiveDays(from: string, to: string): number {
  const a = parts(from);
  const b = parts(to);
  const ua = Date.UTC(a.y, a.m - 1, a.d);
  const ub = Date.UTC(b.y, b.m - 1, b.d);
  return Math.round((ub - ua) / 86400000) + 1;
}

export function dayLabel(key: string, today = todayKey()): string {
  const day = key.slice(0, 10);
  if (day === today) return "Сегодня";
  if (day === addDays(today, -1)) return "Вчера";
  const { y, m, d } = parts(day);
  const base = `${d} ${MONTHS_OF[m - 1]}`;
  return y === parts(today).y ? base : `${base} ${y}`;
}

export function monthTitle(key: string): string {
  const { y, m } = parts(key);
  return `${MONTHS_CAP[m - 1]} ${y}`;
}

export function yearTitle(key: string): string {
  return String(parts(key).y);
}
