import { guessColor, guessIcon } from "@/lib/money/catalog";
import { parseLooseCents } from "@/lib/money/format";
import type { Account, Aid, CatKind, Category, Charge, Currency, Deposit, Entry, Hold, Kind, ReserveBook, Snapshot, Station } from "@/lib/money/types";

function uid(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

export type IoResult =
  | { ok: true; snapshot: Snapshot; stations?: Station[]; charges?: Charge[]; reserve?: ReserveBook }
  | { ok: false; error: string };

function parseRows(text: string, delim: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cur = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          cur += '"';
          i += 1;
        } else quoted = false;
      } else cur += ch;
      continue;
    }
    if (ch === '"') quoted = true;
    else if (ch === delim) {
      row.push(cur);
      cur = "";
    } else if (ch === "\n") {
      row.push(cur);
      rows.push(row);
      row = [];
      cur = "";
    } else if (ch !== "\r") cur += ch;
  }
  if (cur.length || row.length) {
    row.push(cur);
    rows.push(row);
  }
  return rows;
}

function parseWhen(raw: string): string | null {
  const t = raw.trim();
  const m = t.match(/^(\d{4})[.\-/](\d{2})[.\-/](\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?$/);
  if (!m) return null;
  const time = m[4] ? `T${m[4]}:${m[5]}:${m[6] ?? "00"}` : "T12:00:00";
  return `${m[1]}-${m[2]}-${m[3]}${time}`;
}

export function fromMoneyOkCsv(text: string): IoResult {
  const clean = text.replace(/^\uFEFF/, "");
  const first = clean.split(/\r?\n/, 1)[0] ?? "";
  const delim = first.includes("\t") ? "\t" : first.includes(";") ? ";" : ",";
  const table = parseRows(clean, delim);
  const accounts = new Map<string, { name: string; net: number; balance: number | null }>();
  const cats = new Map<string, { name: string; group: string; income: number; expense: number }>();
  const drafts: {
    at: string;
    kind: "expense" | "income";
    amount: number;
    account: string;
    category: string;
    note: string;
  }[] = [];

  for (const cols of table) {
    if (!cols.some((c) => c.trim())) continue;
    const dateRaw = (cols[0] ?? "").trim();
    if (!dateRaw || dateRaw === "Дата") continue;
    const amount = parseLooseCents(cols[1] ?? "");
    const accountName = (cols[3] ?? "").trim() || "Мои деньги";
    const categoryName = (cols[4] ?? "").trim();
    const group = (cols[5] ?? "").trim();
    const note = (cols[6] ?? "").trim();
    if (categoryName === "Остаток на счете") {
      const acc = accounts.get(accountName) ?? { name: accountName, net: 0, balance: null };
      acc.balance = amount;
      accounts.set(accountName, acc);
      continue;
    }
    if (amount === null || amount === 0 || !categoryName) continue;
    const at = parseWhen(dateRaw);
    if (!at) continue;
    const kind = amount < 0 ? "expense" : "income";
    const abs = Math.abs(amount);
    const acc = accounts.get(accountName) ?? { name: accountName, net: 0, balance: null };
    acc.net += amount;
    accounts.set(accountName, acc);
    const cat = cats.get(categoryName) ?? { name: categoryName, group: "", income: 0, expense: 0 };
    if (group) cat.group = group;
    if (kind === "income") cat.income += 1;
    else cat.expense += 1;
    cats.set(categoryName, cat);
    drafts.push({ at, kind, amount: abs, account: accountName, category: categoryName, note });
  }

  if (!drafts.length) return { ok: false, error: "В файле не нашлось операций." };

  const accountList: Account[] = [];
  const accountId = new Map<string, string>();
  for (const acc of accounts.values()) {
    const id = uid("acc");
    accountId.set(acc.name, id);
    const opening = acc.balance === null ? 0 : acc.balance - acc.net;
    accountList.push({ id, name: acc.name, opening, color: guessColor(acc.name) });
  }

  const categoryList: Category[] = [];
  const categoryId = new Map<string, string>();
  for (const cat of cats.values()) {
    const id = uid("cat");
    categoryId.set(cat.name, id);
    const kind: CatKind = cat.income && cat.expense ? "both" : cat.income ? "income" : "expense";
    categoryList.push({
      id,
      name: cat.name,
      kind,
      group: cat.group,
      icon: guessIcon(cat.name),
      color: guessColor(cat.name),
    });
  }

  const entries: Entry[] = drafts.map((d) => ({
    id: uid("e"),
    kind: d.kind,
    amount: d.amount,
    categoryId: categoryId.get(d.category) ?? null,
    accountId: accountId.get(d.account) ?? accountList[0].id,
    toAccountId: null,
    note: d.note,
    at: d.at,
  }));
  entries.sort(compareEntries);
  return { ok: true, snapshot: { accounts: accountList, categories: categoryList, entries } };
}

export function compareEntries(a: Entry, b: Entry): number {
  if (a.at < b.at) return -1;
  if (a.at > b.at) return 1;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

type Backup = {
  version: 1;
  accounts: Account[];
  categories: Category[];
  entries: Entry[];
  reserve?: ReserveBook;
};

export function toBackup(snapshot: Snapshot, reserve?: ReserveBook): string {
  const body: Backup = { version: 1, ...snapshot, ...(reserve ? { reserve } : {}) };
  return JSON.stringify(body);
}

export function fromBackup(text: string): IoResult {
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    return { ok: false, error: "Файл не похож на JSON." };
  }
  if (!data || typeof data !== "object") return { ok: false, error: "Пустой файл." };
  const bag = data as Partial<Backup>;
  if (!Array.isArray(bag.accounts) || !Array.isArray(bag.categories) || !Array.isArray(bag.entries)) {
    return { ok: false, error: "В файле нет счетов, статей или операций." };
  }
  const accounts = bag.accounts.filter(isAccount);
  const categories = bag.categories.filter(isCategory);
  const entries = bag.entries.filter(isEntry).sort(compareEntries);
  if (!accounts.length || !entries.length) return { ok: false, error: "В копии нет операций." };
  const reserve = readReserve(bag.reserve);
  return { ok: true, snapshot: { accounts, categories, entries }, ...(reserve ? { reserve } : {}) };
}

function readReserve(raw: unknown): ReserveBook | undefined {
  if (!raw || typeof raw !== "object") return undefined;
  const bag = raw as Partial<ReserveBook>;
  return {
    aids: (Array.isArray(bag.aids) ? bag.aids : []).filter(isAid),
    holds: (Array.isArray(bag.holds) ? bag.holds : []).filter(isHold),
    deposits: (Array.isArray(bag.deposits) ? bag.deposits : []).filter(isDeposit),
  };
}

function isMoneyRow(v: unknown): v is { id: string; amount: number; currency: Currency; note: string; at: string } {
  if (!v || typeof v !== "object") return false;
  const row = v as Aid;
  return typeof row.id === "string" && typeof row.amount === "number" && (row.currency === "EUR" || row.currency === "USD" || row.currency === "RUB" || row.currency === "BYN") && typeof row.note === "string" && typeof row.at === "string";
}

function isAid(v: unknown): v is Aid {
  return isMoneyRow(v) && typeof (v as Aid).who === "string";
}

function isHold(v: unknown): v is Hold {
  return isMoneyRow(v);
}

function isDeposit(v: unknown): v is Deposit {
  if (!isMoneyRow(v)) return false;
  const row = v as Deposit;
  return typeof row.bank === "string" && typeof row.rate === "number" && typeof row.until === "string";
}

function isAccount(v: unknown): v is Account {
  if (!v || typeof v !== "object") return false;
  const a = v as Account;
  return typeof a.id === "string" && typeof a.name === "string" && typeof a.opening === "number";
}

function isCategory(v: unknown): v is Category {
  if (!v || typeof v !== "object") return false;
  const c = v as Category;
  return typeof c.id === "string" && typeof c.name === "string" && (c.kind === "expense" || c.kind === "income" || c.kind === "both");
}

function isEntry(v: unknown): v is Entry {
  if (!v || typeof v !== "object") return false;
  const e = v as Entry;
  const kindOk = e.kind === "expense" || e.kind === "income" || e.kind === "transfer";
  return kindOk && typeof e.id === "string" && typeof e.amount === "number" && typeof e.at === "string" && typeof e.accountId === "string";
}

export function toCsv(snapshot: Snapshot): string {
  const accounts = new Map(snapshot.accounts.map((a) => [a.id, a]));
  const categories = new Map(snapshot.categories.map((c) => [c.id, c]));
  const lines = ["Дата\tВид\tСчёт\tГруппа\tСтатья\tСумма\tКомментарий"];
  const ordered = snapshot.entries.slice().sort(compareEntries).reverse();
  for (const e of ordered) {
    const cat = e.categoryId ? categories.get(e.categoryId) : undefined;
    const acc = accounts.get(e.accountId);
    const signed = e.kind === "expense" ? -e.amount : e.amount;
    const sum = (signed / 100).toFixed(2).replace(".", ",");
    const kind: Record<Kind, string> = { expense: "Расход", income: "Доход", transfer: "Перевод" };
    lines.push(
      [e.at.replace("T", " "), kind[e.kind], acc?.name ?? "", cat?.group ?? "", cat?.name ?? "", sum, e.note]
        .map(csvCell)
        .join("\t"),
    );
  }
  return `\uFEFF${lines.join("\n")}`;
}

function csvCell(value: string): string {
  if (/["\t\n]/.test(value)) return `"${value.replaceAll('"', '""')}"`;
  return value;
}

export function downloadText(filename: string, text: string, type: string) {
  const bridge = (window as unknown as { SmetaFile?: { save: (name: string, mime: string, text: string) => void } }).SmetaFile;
  if (bridge) {
    bridge.save(filename, type, text);
    return;
  }
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
