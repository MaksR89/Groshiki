import { useMemo, useState, type ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip, BarChart, Bar, XAxis } from "recharts";
import { DayGroups, cn } from "@/components/ledger/bits";
import { MONTHS_SHORT, addDays, addMonths, monthTitle, parts, todayKey, yearTitle } from "@/lib/money/dates";
import { Money } from "@/components/ledger/byn";
import { formatKwh } from "@/lib/money/format";
import { averageDivisor, byCategory, monthsOfYear, periodBounds, sumKind, type PeriodMode } from "@/lib/money/stats";
import { useLedger } from "@/lib/money/store";
import type { Entry } from "@/lib/money/types";

const TOP = 8;

export function Report({ onOpen }: { onOpen: (entry: Entry) => void }) {
  const entries = useLedger((s) => s.entries);
  const categories = useLedger((s) => s.categories);
  const accounts = useLedger((s) => s.accounts);
  const charges = useLedger((s) => s.charges);
  const stations = useLedger((s) => s.stations);
  const today = useMemo(() => todayKey(), []);
  const [mode, setMode] = useState<PeriodMode>("month");
  const [anchor, setAnchor] = useState(today);
  const [accountId, setAccountId] = useState("all");
  const [kindChoice, setKindChoice] = useState<"expense" | "income" | "charge" | null>(null);
  const [drill, setDrill] = useState<string | null>(null);

  const { from, to } = periodBounds(mode, anchor);
  const spent = sumKind(entries, "expense", from, to, accountId);
  const earned = sumKind(entries, "income", from, to, accountId);
  const kind = kindChoice ?? (earned > spent ? "income" : "expense");
  const periodCharges = useMemo(
    () => charges.filter((c) => { const day = c.at.slice(0, 10); return day >= from && day <= to; }),
    [charges, from, to],
  );
  const chargeKwh = periodCharges.reduce((sum, c) => sum + c.kwh, 0);
  const chargeCost = periodCharges.reduce((sum, c) => sum + c.price, 0);
  const stationById = useMemo(() => new Map(stations.map((s) => [s.id, s])), [stations]);
  const total = kind === "charge" ? chargeCost : kind === "income" ? earned : spent;
  const days = averageDivisor(mode, from, to, today);
  const average = Math.round(total / days);
  const catById = useMemo(() => new Map(categories.map((c) => [c.id, c])), [categories]);

  const slices = useMemo(() => {
    if (kind === "charge") {
      const map = new Map<string, { value: number; kwh: number }>();
      for (const c of periodCharges) {
        const row = map.get(c.stationId) ?? { value: 0, kwh: 0 };
        row.value += c.price;
        row.kwh += c.kwh;
        map.set(c.stationId, row);
      }
      return [...map.entries()]
        .map(([id, row]) => {
          const station = stationById.get(id);
          return { id, name: station?.name ?? "Станция", value: row.value, kwh: row.kwh, color: station?.color ?? "var(--color-muted)", logo: station?.logo ?? "" };
        })
        .sort((a, b) => b.value - a.value);
    }
    const map = byCategory(entries, kind, from, to, accountId);
    const rows = [...map.entries()]
      .map(([id, value]) => {
        const cat = catById.get(id);
        return { id, name: cat?.name ?? "Без статьи", value, kwh: 0, color: cat?.color ?? "var(--color-muted)", logo: "" };
      })
      .sort((a, b) => b.value - a.value);
    if (rows.length <= TOP) return rows;
    const head = rows.slice(0, TOP);
    const rest = rows.slice(TOP).reduce((s, r) => s + r.value, 0);
    return [...head, { id: "__rest", name: "Остальные", value: rest, kwh: 0, color: "var(--color-muted)", logo: "" }];
  }, [entries, kind, from, to, accountId, catById, periodCharges, stationById]);

  const year = parts(anchor).y;
  const bars = useMemo(() => {
    if (mode !== "year") return [];
    if (kind === "charge") {
      const buckets = Array.from({ length: 12 }, () => 0);
      for (const c of charges) {
        if (!c.at.startsWith(String(year))) continue;
        buckets[Number(c.at.slice(5, 7)) - 1] += c.price;
      }
      return buckets.map((value, i) => ({ name: MONTHS_SHORT[i], value }));
    }
    return monthsOfYear(entries, kind, year, accountId).map((value, i) => ({
      name: MONTHS_SHORT[i],
      value,
    }));
  }, [mode, entries, kind, year, accountId, charges]);

  const atEnd =
    mode === "day" ? anchor.slice(0, 10) >= today :
    mode === "month" ? anchor.slice(0, 7) >= today.slice(0, 7) :
    anchor.slice(0, 4) >= today.slice(0, 4);

  const title = mode === "day" ? anchor.slice(0, 10).split("-").reverse().join(".") : mode === "month" ? monthTitle(anchor) : yearTitle(anchor);

  function shift(delta: number) {
    setDrill(null);
    if (mode === "day") setAnchor(addDays(anchor, delta));
    else if (mode === "month") setAnchor(addMonths(anchor, delta));
    else setAnchor(addMonths(anchor, delta * 12));
  }

  const drilled = useMemo(() => {
    if (!drill || kind === "charge") return [];
    const restIds = new Set(
      [...byCategory(entries, kind, from, to, accountId).entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(TOP)
        .map(([id]) => id),
    );
    return entries
      .filter((e) => {
        if (e.kind !== kind || !e.categoryId) return false;
        const day = e.at.slice(0, 10);
        if (day < from || day > to) return false;
        if (accountId !== "all" && e.accountId !== accountId) return false;
        if (drill === "__rest") return restIds.has(e.categoryId);
        return e.categoryId === drill;
      })
      .slice()
      .reverse();
  }, [drill, entries, kind, from, to, accountId]);

  const drilledCharges = useMemo(() => {
    if (!drill || kind !== "charge") return [];
    return periodCharges.filter((c) => c.stationId === drill).slice().sort((a, b) => (a.at < b.at ? 1 : -1));
  }, [drill, kind, periodCharges]);

  return (
    <div className="px-4 lg:px-0">
      <div className="grid grid-cols-3 rounded-full bg-chip p-1">
        {([["day", "День"], ["month", "Месяц"], ["year", "Год"]] as const).map(([id, label]) => (
          <button
            key={id}
            type="button"
            aria-pressed={mode === id}
            onClick={() => { setMode(id); setAnchor(today); setDrill(null); }}
            className={cn("h-10 rounded-full text-sm", mode === id ? "bg-surface font-medium" : "text-muted")}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="mt-4 flex items-center justify-between gap-3">
        <IconButton label="Назад" onClick={() => shift(-1)}><ChevronLeft className="size-5" /></IconButton>
        <h2 className="font-display text-2xl">{title}</h2>
        <IconButton label="Вперёд" disabled={atEnd} onClick={() => shift(1)}><ChevronRight className="size-5" /></IconButton>
      </div>

      {accounts.length > 1 ? (
        <div className="mt-3 flex gap-2 overflow-x-auto">
          <Mini active={accountId === "all"} onClick={() => { setAccountId("all"); setDrill(null); }}>Все</Mini>
          {accounts.map((a) => (
            <Mini key={a.id} active={accountId === a.id} onClick={() => { setAccountId(a.id); setDrill(null); }}>{a.name}</Mini>
          ))}
        </div>
      ) : null}

      <div className="mt-4 grid grid-cols-2 gap-3">
        <button type="button" onClick={() => { setKindChoice("expense"); setDrill(null); }} className={cn("rounded-2xl px-4 py-3 text-left", kind === "expense" ? "bg-ink text-surface" : "bg-surface")}>
          <div className={cn("text-sm", kind === "expense" ? "text-chip" : "text-muted")}>Потрачено</div>
          <div className="mt-1 font-display text-2xl tabular-nums"><Money cents={spent} /></div>
        </button>
        <button type="button" onClick={() => { setKindChoice("income"); setDrill(null); }} className={cn("rounded-2xl px-4 py-3 text-left", kind === "income" ? "bg-ink text-surface" : "bg-surface")}>
          <div className={cn("text-sm", kind === "income" ? "text-chip" : "text-muted")}>Доходы</div>
          <div className="mt-1 font-display text-2xl tabular-nums"><Money cents={earned} /></div>
        </button>
      </div>
      <button
        type="button"
        onClick={() => { setKindChoice("charge"); setDrill(null); }}
        className={cn("mt-3 w-full rounded-2xl px-4 py-3 text-left", kind === "charge" ? "bg-ink text-surface" : "bg-surface")}
      >
        <div className="flex items-baseline justify-between gap-3">
          <span className={cn("text-sm", kind === "charge" ? "text-chip" : "text-muted")}>Зарядки</span>
          <span className="font-display text-2xl tabular-nums">{formatKwh(chargeKwh)} кВт·ч</span>
        </div>
        <div className={cn("mt-1 text-sm tabular-nums", kind === "charge" ? "text-chip" : "text-muted")}><Money cents={chargeCost} /></div>
      </button>
      <p className="mt-3 text-sm text-muted">
        {kind === "charge"
          ? chargeKwh > 0
            ? <>
                <Money cents={Math.round((chargeCost * 100) / chargeKwh)} /> за кВт·ч
                {mode !== "day" ? ` · ${formatKwh(Math.round(chargeKwh / days))} кВт·ч в день` : ""}
              </>
            : ""
          : <>
              Разница <Money cents={earned - spent} sign />
              {mode !== "day" ? <> · среднее <Money cents={average} /> в день</> : null}
            </>}
      </p>

      {drill ? (
        <div className="mt-4">
          <button type="button" onClick={() => setDrill(null)} className="text-sm text-muted">← К диаграмме</button>
          <h3 className="mt-2 font-display text-2xl">{slices.find((s) => s.id === drill)?.name ?? (kind === "charge" ? "Станция" : "Статья")}</h3>
          {kind === "charge" ? (
            <ul className="mt-3 grid gap-2">
              {drilledCharges.map((row) => (
                <li key={row.id} className="flex items-center justify-between rounded-2xl bg-surface px-3 py-3">
                  <span>
                    <span className="block text-sm text-muted">{row.at.slice(0, 10).split("-").reverse().join(".")} · {row.at.slice(11, 16)}</span>
                    <span className="tabular-nums">{formatKwh(row.kwh)} кВт·ч</span>
                  </span>
                  <span className="tabular-nums"><Money cents={row.price} /></span>
                </li>
              ))}
            </ul>
          ) : (
            <DayGroups entries={drilled} categories={categories} accounts={accounts} onOpen={onOpen} />
          )}
        </div>
      ) : total === 0 ? (
        <p className="mt-10 text-center text-muted">{kind === "charge" ? "Зарядок в этом периоде нет." : "В этом периоде пусто."}</p>
      ) : (
        <>
          <div className="relative mt-2 h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={slices}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={68}
                  outerRadius={96}
                  paddingAngle={slices.length > 1 ? 1.4 : 0}
                  stroke="none"
                  isAnimationActive={false}
                  onClick={(row: { id?: string }) => { if (row?.id) setDrill(row.id); }}
                >
                  {slices.map((s) => <Cell key={s.id} fill={s.color} />)}
                </Pie>
                <Tooltip content={<Tip />} />
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 grid place-items-center">
              <div className="text-center">
                <div className="font-display text-xl tabular-nums">{kind === "charge" ? formatKwh(chargeKwh) : <Money cents={total} />}</div>
                <div className="text-xs text-muted">{kind === "charge" ? <Money cents={total} /> : slices.length === 1 ? "1 статья" : `${slices.length} статей`}</div>
              </div>
            </div>
          </div>

          {mode === "year" ? (
            <div className="mt-2 h-36">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={bars} barCategoryGap="28%">
                  <XAxis dataKey="name" tick={{ fill: "var(--color-muted)", fontSize: 12 }} axisLine={false} tickLine={false} />
                  <Tooltip content={<Tip />} cursor={{ fill: "var(--color-chip)" }} />
                  <Bar dataKey="value" name={kind === "charge" ? "Зарядки" : kind === "income" ? "Доходы" : "Расходы"} fill={kind === "charge" ? "#1a6fd4" : kind === "income" ? "var(--color-income)" : "var(--color-expense)"} radius={[6, 6, 0, 0]} isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : null}

          <ul className="mt-2">
            {slices.map((s) => {
              const pct = total ? Math.round((s.value / total) * 100) : 0;
              return (
                <li key={s.id}>
                  <button type="button" onClick={() => setDrill(s.id)} className="flex w-full items-center gap-3 py-2.5 text-left">
                    {s.logo ? <img src={s.logo} alt="" className="size-8 shrink-0 rounded-lg object-cover" /> : <span className="size-3 shrink-0 rounded-full" style={{ background: s.color }} />}
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline justify-between gap-3">
                        <span className="truncate">{s.name}</span>
                        <span className="shrink-0 tabular-nums"><Money cents={s.value} /></span>
                      </span>
                      {kind === "charge" ? <span className="text-sm text-muted">{formatKwh(s.kwh)} кВт·ч</span> : null}
                      <span className="mt-1 block h-1.5 overflow-hidden rounded-full bg-chip">
                        <span className="block h-full rounded-full" style={{ width: `${pct}%`, background: s.color }} />
                      </span>
                    </span>
                    <span className="w-10 shrink-0 text-right text-sm tabular-nums text-muted">{pct}%</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}

function Tip({ active, payload }: { active?: boolean; payload?: Array<{ name?: string; value?: number }> }) {
  if (!active || !payload?.length) return null;
  const item = payload[0];
  return (
    <div className="rounded-xl bg-ink px-3 py-2 text-sm text-surface">
      <div>{item.name}</div>
      <div className="mt-0.5 tabular-nums"><Money cents={Number(item.value) || 0} /></div>
    </div>
  );
}

function IconButton({ label, disabled, onClick, children }: { label: string; disabled?: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" aria-label={label} disabled={disabled} onClick={onClick} className="grid size-11 place-items-center rounded-full bg-surface disabled:opacity-30">
      {children}
    </button>
  );
}

function Mini({ active, onClick, children }: { active: boolean; onClick: () => void; children: string }) {
  return (
    <button type="button" onClick={onClick} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", active ? "bg-ink text-surface" : "bg-surface")}>
      {children}
    </button>
  );
}
