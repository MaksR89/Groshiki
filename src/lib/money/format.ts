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
  const expr = splitExpr(raw);
  if (expr && expr.right && expr.right !== "-" && expr.right !== ".") {
    const result = computeCents(expr.left, expr.op, expr.right);
    if (result !== null) return result;
  }
  const plain = expr && (!expr.right || expr.right === "-" || expr.right === ".") ? expr.left : raw;
  return operandCents(plain);
}

function operandCents(raw: string): number {
  if (!raw || raw === "." || raw === "-") return 0;
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
  const expr = splitExpr(raw);
  if (!expr) return formatOperand(raw);
  const right = expr.right ? ` ${formatOperand(expr.right)}` : "";
  return `${formatOperand(expr.left)} ${expr.op}${right}`;
}

function formatOperand(raw: string): string {
  if (!raw || raw === "-" || raw === "." || raw === "-.") return "0";
  const neg = raw.startsWith("-");
  const clean = raw.replace("-", "");
  const [w, f] = clean.split(".");
  const grouped = (w || "0").replace(/\B(?=(\d{3})+(?!\d))/g, " ");
  const body = f === undefined ? grouped : `${grouped},${f}`;
  return neg ? `−${body}` : body;
}

const OPS = new Set(["+", "−", "×", "÷"]);

function splitExpr(raw: string): { left: string; op: string; right: string } | null {
  let i = raw.startsWith("-") ? 1 : 0;
  for (; i < raw.length; i++) {
    const ch = raw[i];
    if (OPS.has(ch)) return { left: raw.slice(0, i), op: ch, right: raw.slice(i + 1) };
  }
  return null;
}

function computeCents(left: string, op: string, right: string): number | null {
  const a = operandCents(left);
  const b = operandCents(right);
  let result: number;
  if (op === "+") result = a + b;
  else if (op === "−") result = a - b;
  else if (op === "×") result = Math.round((a * b) / 100);
  else if (op === "÷") {
    if (b === 0) return null;
    result = Math.round((a * 100) / b);
  } else return null;
  if (!Number.isSafeInteger(result) || Math.abs(result) > 9_999_999_999) return null;
  return result;
}

export function fromHundredths(cents: number): string {
  const abs = Math.abs(Math.round(cents));
  const whole = Math.trunc(abs / 100);
  const frac = abs % 100;
  const body = frac === 0 ? String(whole) : frac % 10 === 0 ? `${whole}.${frac / 10}` : `${whole}.${String(frac).padStart(2, "0")}`;
  return cents < 0 ? `-${body}` : body;
}

export function applyCalcKey(raw: string, key: string): string | null {
  if (!OPS.has(key) && key !== "=") return null;
  const expr = splitExpr(raw);
  if (!expr || !expr.right || expr.right === "-" || expr.right === ".") {
    const left = expr?.left || raw || "0";
    return key === "=" ? left : `${left}${key}`;
  }
  const result = computeCents(expr.left, expr.op, expr.right);
  if (result === null) return raw;
  const shown = fromHundredths(result);
  return key === "=" ? shown : `${shown}${key}`;
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
