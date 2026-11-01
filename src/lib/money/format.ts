export function formatMoney(cents: number, opts?: { sign?: boolean }): string {
  const rounded = Math.round(cents);
  const neg = rounded < 0;
  const abs = Math.abs(rounded);
  const whole = Math.trunc(abs / 100);
  const frac = abs % 100;
  const grouped = new Intl.NumberFormat("ru-RU").format(whole);
  const body = frac === 0 ? grouped : `${grouped},${String(frac).padStart(2, "0")}`;
  const sign = neg ? "−" : opts?.sign && rounded > 0 ? "+" : "";
  return `${sign}${body}`;
}

export function formatKwh(hundredths: number): string {
  const abs = Math.abs(Math.round(hundredths));
  const whole = Math.trunc(abs / 100);
  const frac = abs % 100;
  const grouped = new Intl.NumberFormat("ru-RU").format(whole);
  return `${grouped},${String(frac).padStart(2, "0")}`;
}

export function formatCount(n: number): string {
  return new Intl.NumberFormat("ru-RU").format(n);
}

export function inputToCents(raw: string): number {
  if (!raw || raw === ".") return 0;
  const clean = raw.replace("-", "");
  const [w, f = ""] = clean.split(".");
  const whole = Number(w || "0");
  if (!Number.isFinite(whole)) return 0;
  const frac = (f + "00").slice(0, 2);
  let cents = whole * 100 + Number(frac);
  if (f.length > 2 && f[2] >= "5") cents += 1;
  return raw.trim().startsWith("-") ? -cents : cents;
}

export function centsToInput(cents: number): string {
  const abs = Math.abs(Math.round(cents));
  const whole = Math.trunc(abs / 100);
  const frac = abs % 100;
  const body = frac === 0 ? String(whole) : `${whole}.${String(frac).padStart(2, "0")}`;
  return cents < 0 ? `-${body}` : body;
}

export function formatRawInput(raw: string): string {
  if (!raw || raw === "-" || raw === "." || raw === "-.") return "0";
  const neg = raw.startsWith("-");
  const clean = raw.replace("-", "");
  const [w, f] = clean.split(".");
  const grouped = (w || "0").replace(/\B(?=(\d{3})+(?!\d))/g, " ");
  const body = f === undefined ? grouped : `${grouped},${f}`;
  return neg ? `−${body}` : body;
}

export function parseLooseCents(input: string): number | null {
  const t = input
    .trim()
    .replace(/[\s\u00a0\u202f]/g, "")
    .replace("−", "-")
    .replace(",", ".");
  if (!/^-?\d+(\.\d+)?$/.test(t)) return null;
  return inputToCents(t);
}

export function pushDigit(raw: string, digit: string): string {
  const next = raw === "0" ? digit : `${raw}${digit}`;
  const frac = next.split(".")[1];
  if (frac && frac.length > 2) return raw;
  const digits = next.replace(".", "").replace("-", "");
  if (digits.length > 10) return raw;
  return next;
}

export function pushDot(raw: string): string {
  if (!raw) return "0.";
  if (raw.includes(".")) return raw;
  return `${raw}.`;
}

export function backspaceRaw(raw: string): string {
  const next = raw.slice(0, -1);
  return next === "-" ? "" : next;
}
