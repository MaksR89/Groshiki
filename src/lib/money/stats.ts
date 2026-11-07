import { inclusiveDays, monthRange, yearRange } from "@/lib/money/dates";
import type { Account, Charge, Entry, Kind } from "@/lib/money/types";

export type PeriodMode = "day" | "month" | "year";

export function accountBalance(account: Account, entries: Entry[]): number {
  let n = account.opening;
  for (const e of entries) {
    if (e.kind === "income" && e.accountId === account.id) n += e.amount;
    else if (e.kind === "expense" && e.accountId === account.id) n -= e.amount;
    else if (e.kind === "transfer" && e.accountId === account.id) n -= e.amount;
    else if (e.kind === "transfer" && e.toAccountId === account.id) n += e.amount;
  }
  return n;
}

export function totalBalance(accounts: Account[], entries: Entry[]): number {
  let n = 0;
  for (const a of accounts) n += accountBalance(a, entries);
  return n;
}

export function periodBounds(mode: PeriodMode, anchor: string): { from: string; to: string } {
  const day = anchor.slice(0, 10);
  if (mode === "day") return { from: day, to: day };
  if (mode === "month") return monthRange(day);
  return yearRange(day);
}

export function averageDivisor(mode: PeriodMode, from: string, to: string, today: string): number {
  if (mode === "day") return 1;
  const end = to < today ? to : today;
  if (end < from) return 1;
  return Math.max(1, inclusiveDays(from, end));
}

export function sumKind(
  entries: Entry[],
  kind: Exclude<Kind, "transfer">,
  from: string,
  to: string,
  accountId: string,
): number {
  let n = 0;
  for (const e of entries) {
    if (e.kind !== kind) continue;
    const day = e.at.slice(0, 10);
    if (day < from || day > to) continue;
    if (accountId !== "all" && e.accountId !== accountId) continue;
    n += e.amount;
  }
  return n;
}

export function chargeSpend(charges: Charge[], from: string, to: string, accountId: string): number {
  if (accountId !== "all") return 0;
  let n = 0;
  for (const c of charges) {
    const day = c.at.slice(0, 10);
    if (day < from || day > to) continue;
    n += c.price;
  }
  return n;
}

export function byCategory(
  entries: Entry[],
  kind: Exclude<Kind, "transfer">,
  from: string,
  to: string,
  accountId: string,
): Map<string, number> {
  const map = new Map<string, number>();
  for (const e of entries) {
    if (e.kind !== kind || !e.categoryId) continue;
    const day = e.at.slice(0, 10);
    if (day < from || day > to) continue;
    if (accountId !== "all" && e.accountId !== accountId) continue;
    map.set(e.categoryId, (map.get(e.categoryId) ?? 0) + e.amount);
  }
  return map;
}

export function monthsOfYear(
  entries: Entry[],
  kind: Exclude<Kind, "transfer">,
  year: number,
  accountId: string,
): number[] {
  const buckets = Array.from({ length: 12 }, () => 0);
  const from = `${year}-01-01`;
  const to = `${year}-12-31`;
  for (const e of entries) {
    if (e.kind !== kind) continue;
    const day = e.at.slice(0, 10);
    if (day < from || day > to) continue;
    if (accountId !== "all" && e.accountId !== accountId) continue;
    buckets[Number(day.slice(5, 7)) - 1] += e.amount;
  }
  return buckets;
}

export function inAccount(entry: Entry, accountId: string): boolean {
  if (accountId === "all") return true;
  return entry.accountId === accountId || entry.toAccountId === accountId;
}
