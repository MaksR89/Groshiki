import { useEffect, useState } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Lock,
  Menu,
  PieChart,
  PlugZap,
  Plus,
  Shapes,
  Wallet,
} from "lucide-react";
import { cn } from "@/components/ledger/bits";
import { ChargesView } from "@/components/ledger/charges";
import { Composer } from "@/components/ledger/composer";
import { Feed } from "@/components/ledger/feed";
import { AccountsView, CategoriesView, MoreView } from "@/components/ledger/places";
import { OtherLock, OtherView } from "@/components/ledger/other";
import { Report } from "@/components/ledger/report";
import { Money } from "@/components/ledger/byn";
import { useShowBalance } from "@/lib/money/prefs";
import { totalBalance } from "@/lib/money/stats";
import { bootLedger, useLedger } from "@/lib/money/store";
import type { Entry, Kind } from "@/lib/money/types";

type Tab = "expenses" | "income" | "charges" | "report" | "accounts" | "categories" | "more" | "other";

const NAV: { id: Tab; label: string; icon: typeof Wallet }[] = [
  { id: "expenses", label: "Расходы", icon: ArrowDownLeft },
  { id: "income", label: "Доходы", icon: ArrowUpRight },
  { id: "charges", label: "Зарядки", icon: PlugZap },
  { id: "report", label: "Отчёт", icon: PieChart },
  { id: "accounts", label: "Счета", icon: Wallet },
  { id: "categories", label: "Статьи", icon: Shapes },
  { id: "more", label: "Книга", icon: Menu },
];

export function LedgerApp() {
  const hydrated = useLedger((s) => s.hydrated);
  const [splash, setSplash] = useState(true);
  const accounts = useLedger((s) => s.accounts);
  const entries = useLedger((s) => s.entries);
  const [tab, setTab] = useState<Tab>("expenses");
  const [composer, setComposer] = useState<{ kind: Kind; entry: Entry | null } | null>(null);
  const [chargeDraft, setChargeDraft] = useState(0);
  const [otherDraft, setOtherDraft] = useState(0);
  const [otherOpen, setOtherOpen] = useState(false);
  const showCharges = useLedger((s) => s.showCharges);
  const showReport = useLedger((s) => s.showReport);
  const showReserve = useLedger((s) => s.showReserve);
  const [showBalance] = useShowBalance();

  useEffect(() => {
    if (tab !== "other") setOtherOpen(false);
    if ((tab === "charges" && !showCharges) || (tab === "report" && !showReport) || (tab === "other" && !showReserve)) setTab("expenses");
  }, [tab, showCharges, showReport, showReserve]);

  useEffect(() => {
    void bootLedger();
  }, []);

  const phoneNav = NAV.filter((item) => {
    if (item.id === "accounts" || item.id === "categories") return false;
    if (item.id === "charges") return showCharges;
    if (item.id === "report") return showReport;
    return true;
  });
  if (showReserve) phoneNav.splice(phoneNav.length - 1, 0, { id: "other", label: "Другое", icon: Lock });
  const sideNav = NAV.filter((item) => {
    if (item.id === "charges") return showCharges;
    if (item.id === "report") return showReport;
    return true;
  });
  if (showReserve) {
    const bookAt = sideNav.findIndex((item) => item.id === "more");
    sideNav.splice(bookAt, 0, { id: "other", label: "Другое", icon: Lock });
  }

  useEffect(() => {
    const timer = window.setTimeout(() => setSplash(false), 2000);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    const onFocus = (event: FocusEvent) => {
      const el = event.target;
      if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement)) return;
      if (el.type === "date" || el.type === "time" || el.type === "checkbox" || el.type === "radio") return;
      window.setTimeout(() => el.scrollIntoView({ block: "center", inline: "nearest" }), 120);
    };
    document.addEventListener("focusin", onFocus);
    return () => document.removeEventListener("focusin", onFocus);
  }, []);

  if (!hydrated || splash) {
    return (
      <main className="grid min-h-dvh place-items-center bg-desk px-6 text-center text-ink">
        <div>
          <h1 className="font-display text-5xl">Грошыки</h1>
          <p className="mt-2 text-muted">Книга учета доходов и расходов</p>
        </div>
      </main>
    );
  }

  const balance = totalBalance(accounts, entries);
  const addKind: Kind = tab === "income" ? "income" : "expense";

  function openNew() {
    if (!accounts.length) {
      setTab("accounts");
      return;
    }
    setComposer({ kind: addKind, entry: null });
  }

  return (
    <div className="min-h-dvh bg-desk text-ink">
      <div className="mx-auto flex min-h-dvh w-full max-w-6xl lg:border-x lg:border-line">
        <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r border-line bg-bg px-4 py-6 lg:flex">
          <div className="px-2">
            <div className="font-display text-4xl leading-none">Грошыки</div>
            {showBalance ? <div className="mt-4 font-display text-3xl tabular-nums"><Money cents={balance} /></div> : null}
            <div className="mt-1 text-sm text-muted">{accounts.length === 1 ? accounts[0].name : `${accounts.length} счетов`}</div>
          </div>
          <button type="button" onClick={tab === "charges" ? () => setChargeDraft((n) => n + 1) : tab === "other" && otherOpen ? () => setOtherDraft((n) => n + 1) : openNew} className={cn("mt-5 flex h-11 items-center justify-center gap-2 rounded-full font-medium text-surface", tab === "charges" || tab === "other" ? "bg-ink" : "bg-expense", tab === "other" && !otherOpen && "hidden")}>
            <Plus className="size-4" /> {tab === "charges" ? "Новая зарядка" : tab === "other" ? "Добавить" : "Новая запись"}
          </button>
          <nav className="mt-6 grid gap-1">
            {sideNav.map((item) => {
              const Icon = item.icon;
              const on = tab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setTab(item.id)}
                  className={cn("flex h-11 items-center gap-3 rounded-xl px-3 text-left", on ? "bg-chip font-medium" : "text-muted")}
                >
                  <Icon className="size-5" />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </aside>

        <div className="min-w-0 flex-1">
          <header className="sticky top-0 z-20 border-b border-line bg-desk px-4 py-3 lg:hidden">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="font-display text-2xl leading-none">Грошыки</div>
                <div className="mt-1 text-sm text-muted">{accounts[0]?.name ?? "Нет счетов"}</div>
              </div>
              {showBalance ? <div className="font-display text-xl tabular-nums"><Money cents={balance} /></div> : null}
            </div>
            <button
              type="button"
              onClick={tab === "charges" ? () => setChargeDraft((n) => n + 1) : tab === "other" && otherOpen ? () => setOtherDraft((n) => n + 1) : openNew}
              className={cn(
                "mt-2 flex h-10 w-full items-center justify-center gap-2 rounded-xl text-base font-medium text-surface",
                tab === "charges" || tab === "other" ? "bg-ink" : addKind === "income" ? "bg-income" : "bg-expense",
                tab === "other" && !otherOpen && "hidden",
              )}
            >
              <Plus className="size-4" />
              {tab === "charges" ? "Добавить зарядку" : tab === "other" && otherOpen ? "Добавить" : "Добавить запись"}
            </button>
          </header>

          <main className="pb-nav pt-4 lg:px-8 lg:py-6 lg:pb-10">
            {tab === "expenses" ? <Feed kind="expense" onOpen={(entry) => setComposer({ kind: entry.kind, entry })} /> : null}
            {tab === "income" ? <Feed kind="income" onOpen={(entry) => setComposer({ kind: entry.kind, entry })} /> : null}
            {tab === "charges" ? <ChargesView draftKey={chargeDraft} /> : null}
            {tab === "report" ? <Report onOpen={(entry) => setComposer({ kind: entry.kind, entry })} /> : null}
            {tab === "accounts" ? <AccountsView onBack={() => setTab("more")} /> : null}
            {tab === "categories" ? <CategoriesView onBack={() => setTab("more")} /> : null}
            {tab === "more" ? <MoreView onAccounts={() => setTab("accounts")} onCategories={() => setTab("categories")} /> : null}
            {tab === "other" && showReserve ? (otherOpen ? <OtherView draftKey={otherDraft} onBack={() => setTab("expenses")} /> : <OtherLock onUnlock={() => setOtherOpen(true)} />) : null}
          </main>
        </div>
      </div>

      <nav className="nav-safe fixed inset-x-0 bottom-0 z-20 border-t border-line bg-bg lg:hidden">
        <div className="mx-auto grid max-w-lg" style={{ gridTemplateColumns: `repeat(${phoneNav.length}, minmax(0, 1fr))` }}>
          {phoneNav.map((item) => {
            const Icon = item.icon;
            const on = item.id === "more" ? tab === "more" || tab === "accounts" || tab === "categories" : tab === item.id;
            return (
              <button key={item.id} type="button" onClick={() => setTab(item.id)} className={cn("flex h-16 flex-col items-center justify-center gap-1 text-[11px]", on ? "font-medium text-ink" : "text-muted")}>
                <Icon className="size-5" />
                {item.label}
              </button>
            );
          })}
        </div>
      </nav>

      {composer ? (
        <Composer
          initialKind={composer.kind}
          editing={composer.entry}
          onClose={() => setComposer(null)}
        />
      ) : null}
    </div>
  );
}
