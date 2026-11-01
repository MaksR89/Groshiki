import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { DayGroups, cn } from "@/components/ledger/bits";
import { addMonths, dayLabel, monthTitle, parts, todayKey, toKey } from "@/lib/money/dates";
import { Money } from "@/components/ledger/byn";
import { inAccount, sumKind } from "@/lib/money/stats";
import { useLedger } from "@/lib/money/store";
import type { Entry, Kind } from "@/lib/money/types";

const PAGE = 40;

export function Feed({
  kind,
  onOpen,
}: {
  kind: Exclude<Kind, "transfer">;
  onOpen: (entry: Entry) => void;
}) {
  const entries = useLedger((s) => s.entries);
  const categories = useLedger((s) => s.categories);
  const accounts = useLedger((s) => s.accounts);
  const [mode, setMode] = useState<"recent" | "month">("recent");
  const [month, setMonth] = useState(() => todayKey());
  const [query, setQuery] = useState("");
  const [accountId, setAccountId] = useState("all");
  const [limit, setLimit] = useState(PAGE);
  const today = todayKey();
  const yesterday = useMemo(() => {
    const [y, m, d] = today.split("-").map(Number);
    const dt = new Date(y, m - 1, d - 1);
    return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, "0")}-${String(dt.getDate()).padStart(2, "0")}`;
  }, [today]);

  const catById = useMemo(() => new Map(categories.map((c) => [c.id, c])), [categories]);

  const pool = useMemo(() => {
    const q = query.trim().toLowerCase();
    const monthKey = month.slice(0, 7);
    const list = entries.filter((e) => {
      if (e.kind !== kind && e.kind !== "transfer") return false;
      if (!inAccount(e, accountId)) return false;
      if (!q && mode === "month" && !e.at.startsWith(monthKey)) return false;
      if (!q) return true;
      const cat = e.categoryId ? catById.get(e.categoryId) : undefined;
      const hay = `${cat?.name ?? ""} ${cat?.group ?? ""} ${e.note}`.toLowerCase();
      return hay.includes(q);
    });
    return list.slice().reverse();
  }, [entries, kind, accountId, query, mode, month, catById]);

  const visible = pool.slice(0, limit);
  const todaySum = sumKind(entries, kind, today, today, accountId);
  const ySum = sumKind(entries, kind, yesterday, yesterday, accountId);
  const shownMonth = mode === "month" && !query ? month : today;
  const monthKey = shownMonth.slice(0, 7);
  const { y, m } = parts(shownMonth);
  const monthFrom = toKey(y, m, 1);
  const monthTo = toKey(y, m, new Date(y, m, 0).getDate());
  const monthSum = sumKind(entries, kind, monthFrom, monthTo, accountId);
  const monthLabel = monthKey === today.slice(0, 7) ? "В этом месяце" : monthTitle(shownMonth);
  const noun = kind === "expense" ? "расход" : "доход";
  const latest = entries.filter((e) => e.kind === kind && inAccount(e, accountId)).at(-1);
  const latestCat = latest?.categoryId ? catById.get(latest.categoryId) : undefined;
  const futureMonth = month.slice(0, 7) >= today.slice(0, 7);

  return (
    <div className="px-4 lg:px-0">
      <div className="grid grid-cols-2 gap-3">
        <Summary label="Сегодня" value={todaySum} />
        <Summary label="Вчера" value={ySum} />
      </div>
      <div className="mt-3 flex items-baseline justify-between gap-3 rounded-2xl bg-surface px-4 py-3">
        <span className="text-sm text-muted">{monthLabel}</span>
        <span className="text-2xl font-semibold"><Money cents={monthSum} /></span>
      </div>
      {todaySum === 0 && ySum === 0 && latest ? (
        <p className="mt-3 text-sm text-muted">
          Последний {noun}: {dayLabel(latest.at, today).toLowerCase()}
          {latestCat ? ` — ${latestCat.name}` : ""}, <Money cents={latest.amount} />
        </p>
      ) : null}

      <div className="mt-4 grid grid-cols-2 rounded-full bg-chip p-1">
        <button
          type="button"
          aria-pressed={mode === "recent"}
          onClick={() => { setMode("recent"); setLimit(PAGE); }}
          className={cn("h-10 rounded-full text-sm", mode === "recent" ? "bg-surface font-medium" : "text-muted")}
        >
          Последние
        </button>
        <button
          type="button"
          aria-pressed={mode === "month"}
          onClick={() => { setMode("month"); setLimit(PAGE); }}
          className={cn("h-10 rounded-full text-sm", mode === "month" ? "bg-surface font-medium" : "text-muted")}
        >
          Месяц
        </button>
      </div>

      {mode === "month" && !query ? (
        <div className="mt-3 flex items-center justify-between">
          <button type="button" className="grid size-11 place-items-center rounded-full bg-surface" aria-label="Предыдущий месяц" onClick={() => { setMonth(addMonths(month, -1)); setLimit(PAGE); }}>
            ‹
          </button>
          <div className="font-display text-xl">{monthTitle(month)}</div>
          <button type="button" disabled={futureMonth} className="grid size-11 place-items-center rounded-full bg-surface disabled:opacity-30" aria-label="Следующий месяц" onClick={() => { setMonth(addMonths(month, 1)); setLimit(PAGE); }}>
            ›
          </button>
        </div>
      ) : null}

      {accounts.length > 1 ? (
        <div className="mt-3 flex gap-2 overflow-x-auto">
          <Chip active={accountId === "all"} onClick={() => setAccountId("all")}>Все счета</Chip>
          {accounts.map((a) => (
            <Chip key={a.id} active={accountId === a.id} onClick={() => setAccountId(a.id)}>{a.name}</Chip>
          ))}
        </div>
      ) : null}

      <label className="mt-3 flex h-11 items-center gap-2 rounded-2xl bg-surface px-3">
        <Search className="size-4 shrink-0 text-muted" aria-hidden="true" />
        <input
          value={query}
          onChange={(e) => { setQuery(e.target.value); setLimit(PAGE); }}
          placeholder="Статья или заметка"
          className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-muted"
        />
      </label>

      {visible.length === 0 ? (
        <p className="mt-10 text-center text-muted">
          {query ? "Ничего не нашлось." : kind === "expense" ? "Расходов здесь нет." : "Доходов здесь нет."}
        </p>
      ) : (
        <DayGroups entries={visible} categories={categories} accounts={accounts} onOpen={onOpen} />
      )}

      {pool.length > visible.length ? (
        <button
          type="button"
          onClick={() => setLimit((n) => n + PAGE)}
          className="mt-4 h-11 w-full rounded-2xl bg-surface font-medium"
        >
          Ещё {Math.min(PAGE, pool.length - visible.length)}
        </button>
      ) : null}
    </div>
  );
}

function Summary({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl bg-surface px-4 py-3">
      <div className="text-sm text-muted">{label}</div>
      <div className="mt-1 text-2xl font-semibold"><Money cents={value} /></div>
    </div>
  );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn("h-9 shrink-0 rounded-full px-3 text-sm", active ? "bg-ink text-surface" : "bg-surface")}
    >
      {children}
    </button>
  );
}
