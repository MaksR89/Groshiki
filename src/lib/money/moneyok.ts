import { unzipSync } from "fflate";
import { guessColor, guessIcon } from "@/lib/money/catalog";
import { fromMoneyOkCsv, type IoResult } from "@/lib/money/io";
import type { Account, CatKind, Category, Entry } from "@/lib/money/types";

function uid(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

function decodeXml(value: string): string {
  return value
    .replace(/</g, "<")
    .replace(/>/g, ">")
    .replace(/"/g, '"')
    .replace(/'/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&/g, "&");
}

function textOf(xml: string): string {
  return [...xml.matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((m) => decodeXml(m[1])).join("");
}

function sharedStrings(xml: string): string[] {
  return [...xml.matchAll(/<si\b[^>]*>([\s\S]*?)<\/si>/g)].map((m) => textOf(m[1]));
}

function column(ref: string): string {
  return ref.replace(/\d+/g, "");
}

function sheetRows(xml: string, shared: string[]): Map<string, string>[] {
  const rows: Map<string, string>[] = [];
  for (const row of xml.matchAll(/<row\b[^>]*>([\s\S]*?)<\/row>/g)) {
    const cells = new Map<string, string>();
    for (const cell of row[1].matchAll(/<c\b([^>]*)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const attrs = cell[1];
      const ref = /r="([^"]+)"/.exec(attrs)?.[1] ?? "";
      const type = /t="([^"]+)"/.exec(attrs)?.[1] ?? "";
      const body = cell[2] ?? "";
      let value = "";
      if (type === "inlineStr") value = textOf(body);
      else {
        value = decodeXml(/<v>([\s\S]*?)<\/v>/.exec(body)?.[1] ?? "");
        if (type === "s" && value) value = shared[Number(value)] ?? "";
      }
      const col = column(ref);
      if (col) cells.set(col, value);
    }
    if (cells.size) rows.push(cells);
  }
  return rows;
}

function headers(row: Map<string, string> | undefined): Map<string, string> {
  const map = new Map<string, string>();
  if (!row) return map;
  for (const [col, name] of row) map.set(name.trim(), col);
  return map;
}

function cell(row: Map<string, string>, head: Map<string, string>, name: string): string {
  const col = head.get(name);
  return col ? row.get(col) ?? "" : "";
}

function excelStamp(serialRaw: string): string | null {
  const serial = Number(serialRaw);
  if (!Number.isFinite(serial)) return null;
  const ms = Date.UTC(1899, 11, 30) + Math.round(serial * 86400) * 1000;
  const date = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  const frac = Math.abs(serial - Math.trunc(serial));
  const time = frac > 1e-6 ? `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}` : "12:00:00";
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${time}`;
}

function cents(raw: string): number | null {
  const n = Number(raw.replace(",", "."));
  if (!Number.isFinite(n)) return null;
  return Math.round(n * 100);
}

function sheetIndex(workbook: string, rels: string): Map<string, string> {
  const byId = new Map<string, string>();
  for (const rel of rels.matchAll(/Id="([^"]+)"[^>]*Target="([^"]+)"/g)) {
    const target = rel[2].replace(/^\//, "");
    byId.set(rel[1], target.startsWith("xl/") ? target : `xl/${target}`);
  }
  const sheets = new Map<string, string>();
  for (const tag of workbook.matchAll(/<sheet\b([^>]*)\/>/g)) {
    const name = /name="([^"]+)"/.exec(tag[1])?.[1];
    const id = /(?:r:id|[A-Za-z0-9]+:id)="([^"]+)"/.exec(tag[1])?.[1];
    if (!name || !id) continue;
    const path = byId.get(id);
    if (path) sheets.set(decodeXml(name), path);
  }
  return sheets;
}

export function fromMoneyOkXlsx(buf: ArrayBuffer): IoResult {
  let files: Record<string, Uint8Array>;
  try {
    files = unzipSync(new Uint8Array(buf));
  } catch {
    return { ok: false, error: "Файл Excel не открылся." };
  }
  const read = (path: string) => {
    const bytes = files[path];
    return bytes ? new TextDecoder().decode(bytes) : "";
  };
  const sheets = sheetIndex(read("xl/workbook.xml"), read("xl/_rels/workbook.xml.rels"));
  const opsPath = sheets.get("Операции");
  if (!opsPath) return { ok: false, error: "В файле нет листа «Операции». Это не выгрузка Деньги OK." };
  const shared = sharedStrings(read("xl/sharedStrings.xml"));
  const ops = sheetRows(read(opsPath), shared);
  const opHead = headers(ops[0]);
  const accountsSheet = sheetRows(read(sheets.get("Счета") ?? ""), shared);
  const accHead = headers(accountsSheet[0]);
  const transfers = sheetRows(read(sheets.get("Переводы") ?? ""), shared);
  const trHead = headers(transfers[0]);

  const balances = new Map<string, number>();
  for (const row of accountsSheet.slice(1)) {
    const name = cell(row, accHead, "Счет").trim();
    const balance = cents(cell(row, accHead, "Остаток").trim());
    if (name && balance !== null) balances.set(name, balance);
  }

  const net = new Map<string, number>();
  const cats = new Map<string, { name: string; group: string; income: number; expense: number }>();
  const drafts: Entry[] = [];
  const touch = (name: string, delta: number) => net.set(name, (net.get(name) ?? 0) + delta);

  for (const row of ops.slice(1)) {
    const at = excelStamp(cell(row, opHead, "Дата"));
    const kindRaw = cell(row, opHead, "Вид").trim().toLowerCase();
    const account = cell(row, opHead, "Счет").trim() || "Мои деньги";
    const group = cell(row, opHead, "Группа").trim();
    const category = cell(row, opHead, "Статья").trim();
    const amount = cents(cell(row, opHead, "Сумма").trim());
    const note = cell(row, opHead, "Комментарий");
    if (!at || amount === null || amount === 0 || !category) continue;
    const kind = kindRaw.startsWith("дох") ? "income" : "expense";
    touch(account, kind === "income" ? amount : -amount);
    const cat = cats.get(category) ?? { name: category, group: "", income: 0, expense: 0 };
    if (group) cat.group = group;
    if (kind === "income") cat.income += 1;
    else cat.expense += 1;
    cats.set(category, cat);
    drafts.push({
      id: uid("e"),
      kind,
      amount,
      categoryId: category,
      accountId: account,
      toAccountId: null,
      note,
      at,
    });
  }

  for (const row of transfers.slice(1)) {
    const at = excelStamp(cell(row, trHead, "Дата"));
    const from = cell(row, trHead, "Со счета").trim();
    const to = cell(row, trHead, "На счет").trim();
    const amount = cents(cell(row, trHead, "Списано").trim());
    const note = cell(row, trHead, "Комментарий");
    if (!at || !from || !to || amount === null || amount === 0) continue;
    touch(from, -amount);
    touch(to, amount);
    drafts.push({
      id: uid("e"),
      kind: "transfer",
      amount,
      categoryId: null,
      accountId: from,
      toAccountId: to,
      note,
      at,
    });
  }

  if (!drafts.length) return { ok: false, error: "В файле не нашлось операций." };

  const names: string[] = [];
  for (const row of accountsSheet.slice(1)) {
    const name = cell(row, accHead, "Счет").trim();
    if (name && !names.includes(name)) names.push(name);
  }
  for (const name of net.keys()) if (!names.includes(name)) names.push(name);

  const accountId = new Map<string, string>();
  const accountList: Account[] = names.map((name) => {
    const id = uid("acc");
    accountId.set(name, id);
    const flow = net.get(name) ?? 0;
    const balance = balances.get(name);
    return { id, name, opening: balance === undefined ? 0 : balance - flow, color: guessColor(name) };
  });

  const categoryId = new Map<string, string>();
  const categoryList: Category[] = [...cats.values()].map((cat) => {
    const id = uid("cat");
    categoryId.set(cat.name, id);
    const kind: CatKind = cat.income && cat.expense ? "both" : cat.income ? "income" : "expense";
    return { id, name: cat.name, kind, group: cat.group, icon: guessIcon(cat.name), color: guessColor(cat.name) };
  });

  const entries = drafts
    .map((entry) => ({
      ...entry,
      categoryId: entry.categoryId ? categoryId.get(entry.categoryId) ?? null : null,
      accountId: accountId.get(entry.accountId) ?? accountList[0].id,
      toAccountId: entry.toAccountId ? accountId.get(entry.toAccountId) ?? null : null,
    }))
    .sort((a, b) => (a.at < b.at ? -1 : a.at > b.at ? 1 : a.id < b.id ? -1 : 1));

  return { ok: true, snapshot: { accounts: accountList, categories: categoryList, entries } };
}

export async function fromMoneyOkFile(file: File): Promise<IoResult> {
  const name = file.name.toLowerCase();
  if (name.endsWith(".xlsx")) return fromMoneyOkXlsx(await file.arrayBuffer());
  return fromMoneyOkCsv(await file.text());
}
