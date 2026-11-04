export type TermUnit = "day" | "month" | "year";
export type Cap = "none" | "month" | "quarter" | "year";
export type LoanKind = "annuity" | "diff";

export type DepositCalc = { total: number; interest: number; paid: number };
export type LoanCalc = {
  months: number;
  payment: number;
  first: number;
  last: number;
  overpay: number;
  total: number;
};

function yearsOf(count: number, unit: TermUnit): number {
  if (unit === "day") return count / 365;
  if (unit === "month") return count / 12;
  return count;
}

function termMonths(count: number, unit: TermUnit): number {
  if (unit === "year") return count * 12;
  if (unit === "day") return Math.round(count * 12 / 365);
  return count;
}

function money(value: number): number {
  if (!Number.isFinite(value)) return Number.NaN;
  return Math.round(value * 100);
}

export function depositCalc(
  amountCents: number,
  ratePct: number,
  count: number,
  unit: TermUnit,
  cap: Cap,
  extraCents = 0,
  extraMonths = 0,
): DepositCalc | null {
  if (amountCents <= 0 || count <= 0 || ratePct < 0) return null;
  const principal = amountCents / 100;
  const rate = ratePct / 100;
  const extra = extraCents > 0 && extraMonths > 0 ? extraCents / 100 : 0;
  const months = extra > 0 ? termMonths(count, unit) : 0;
  if (extra > 0) {
    if (months <= 0 || months > 600) return null;
    const payMonths = Math.min(extraMonths, months);
    const every = cap === "month" ? 1 : cap === "quarter" ? 3 : cap === "year" ? 12 : 0;
    let balance = principal;
    let accrued = 0;
    for (let month = 1; month <= months; month += 1) {
      accrued += balance * (rate / 12);
      if (every > 0 && month % every === 0) {
        balance += accrued;
        accrued = 0;
      }
      if (month <= payMonths) balance += extra;
    }
    const cents = money(balance + accrued);
    const paid = Math.round(extra * payMonths * 100);
    if (!Number.isFinite(cents) || cents > 1_000_000_000_000_00) return null;
    return { total: cents, interest: cents - amountCents - paid, paid };
  }
  const years = yearsOf(count, unit);
  const total = cap === "none"
    ? principal * (1 + rate * years)
    : principal * Math.pow(1 + rate / (cap === "month" ? 12 : cap === "quarter" ? 4 : 1), (cap === "month" ? 12 : cap === "quarter" ? 4 : 1) * years);
  const cents = money(total);
  if (!Number.isFinite(cents) || cents > 1_000_000_000_000_00) return null;
  return { total: cents, interest: cents - amountCents, paid: 0 };
}

export function loanCalc(amountCents: number, ratePct: number, count: number, unit: Exclude<TermUnit, "day">, kind: LoanKind): LoanCalc | null {
  if (amountCents <= 0 || count <= 0 || ratePct < 0) return null;
  const months = unit === "year" ? count * 12 : count;
  if (months <= 0 || months > 600) return null;
  const principal = amountCents / 100;
  const monthly = ratePct / 100 / 12;
  const part = principal / months;
  if (kind === "annuity") {
    const payment = monthly === 0
      ? part
      : principal * monthly * Math.pow(1 + monthly, months) / (Math.pow(1 + monthly, months) - 1);
    const pay = money(payment);
    const total = pay * months;
    if (!Number.isFinite(total) || total > 1_000_000_000_000_00) return null;
    return { months, payment: pay, first: pay, last: pay, overpay: total - amountCents, total };
  }
  let rest = principal;
  let total = 0;
  let first = 0;
  let last = 0;
  for (let i = 0; i < months; i += 1) {
    const pay = part + rest * monthly;
    if (i === 0) first = pay;
    last = pay;
    total += pay;
    rest -= part;
  }
  const firstCents = money(first);
  const lastCents = money(last);
  const totalCents = money(total);
  if (!Number.isFinite(totalCents) || totalCents > 1_000_000_000_000_00) return null;
  return { months, payment: firstCents, first: firstCents, last: lastCents, overpay: totalCents - amountCents, total: totalCents };
}
