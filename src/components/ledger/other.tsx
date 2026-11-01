import { useEffect, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { ChevronLeft, X } from "lucide-react";
import { cn } from "@/components/ledger/bits";
import { Sum } from "@/components/ledger/byn";
import { AmountButton, applyKey, Keypad } from "@/components/ledger/keypad";
import { dayLabel, nowStamp } from "@/lib/money/dates";
import { centsToInput, formatMoney, parseLooseCents } from "@/lib/money/format";
import { hashPass } from "@/lib/money/pass";
import { useLedger } from "@/lib/money/store";
import type { Aid, Currency, Deposit, Hold } from "@/lib/money/types";

const CURRENCIES: { id: Currency; label: string }[] = [
  { id: "EUR", label: "Евро" },
  { id: "USD", label: "Доллар" },
  { id: "RUB", label: "Росс. рубль" },
  { id: "BYN", label: "Бел. рубль" },
];

type Side = "aid" | "hold" | "deposit";

export function OtherView({ draftKey, onBack }: { draftKey: number; onBack: () => void }) {
  const aids = useLedger((s) => s.aids);
  const holds = useLedger((s) => s.holds);
  const deposits = useLedger((s) => s.deposits);
  const [side, setSide] = useState<Side>("aid");
  const [editing, setEditing] = useState<Aid | Hold | Deposit | "new" | null>(null);

  useEffect(() => {
    if (draftKey > 0) setEditing("new");
  }, [draftKey]);

  const rows = side === "aid" ? aids : side === "hold" ? holds : deposits;
  const sorted = rows.slice().sort((a, b) => (a.at < b.at ? 1 : a.at > b.at ? -1 : 0));

  return (
    <div className="px-4 lg:px-0">
      <button type="button" onClick={onBack} className="mb-3 inline-flex items-center gap-1 text-sm text-muted lg:hidden">
        <ChevronLeft className="size-4" /> Назад
      </button>
      <h2 className="font-display text-3xl">Другое</h2>
      <p className="mt-2 text-sm text-muted">Не входит в доходы, расходы и остаток книги. Курс не считается.</p>
      <div className="mt-4 grid grid-cols-3 rounded-full bg-chip p-1">
        {([["aid", "Помощь"], ["hold", "Валюта"], ["deposit", "Депозит"]] as const).map(([id, label]) => (
          <button key={id} type="button" onClick={() => setSide(id)} className={cn("h-9 rounded-full text-sm", side === id ? "bg-surface font-medium" : "text-muted")}>
            {label}
          </button>
        ))}
      </div>
      <Totals rows={rows} />
      <div className="mt-4 grid gap-2">
        {sorted.map((row) => (
          <button key={row.id} type="button" onClick={() => setEditing(row)} className="rounded-2xl bg-surface px-4 py-3 text-left">
            <div className="flex items-baseline justify-between gap-3">
              <span className="font-medium">{titleOf(side, row)}</span>
              <span className="font-display text-2xl"><Sum cents={row.amount} currency={row.currency} /></span>
            </div>
            <div className="mt-1 text-sm text-muted">
              {dayLabel(row.at.slice(0, 10))}
              {side === "deposit" && "rate" in row && typeof row.rate === "number" && row.rate > 0 ? ` · ${formatMoney(row.rate)} %` : ""}
              {row.note ? ` · ${row.note}` : ""}
            </div>
          </button>
        ))}
        {!sorted.length ? <p className="px-1 text-sm text-muted">Пока пусто.</p> : null}
      </div>
      {editing ? <OtherDialog side={side} row={editing === "new" ? null : editing} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

function Totals({ rows }: { rows: { amount: number; currency: Currency }[] }) {
  const sums = new Map<Currency, number>();
  for (const row of rows) sums.set(row.currency, (sums.get(row.currency) ?? 0) + row.amount);
  const shown = CURRENCIES.filter((c) => sums.get(c.id));
  if (!shown.length) return null;
  return (
    <div className="mt-4 grid gap-2">
      {shown.map((c) => (
        <div key={c.id} className="flex items-baseline justify-between rounded-2xl bg-surface px-4 py-3">
          <span className="text-sm text-muted">{c.label}</span>
          <span className="font-display text-2xl"><Sum cents={sums.get(c.id) ?? 0} currency={c.id} /></span>
        </div>
      ))}
    </div>
  );
}

function titleOf(side: Side, row: Aid | Hold | Deposit): string {
  if (side === "aid" && "who" in row) return row.who || "Помощь";
  if (side === "deposit" && "bank" in row) return row.bank || "Депозит";
  return "note" in row && row.note ? row.note : "Отложено";
}

function OtherDialog({ side, row, onClose }: { side: Side; row: Aid | Hold | Deposit | null; onClose: () => void }) {
  const addAid = useLedger((s) => s.addAid);
  const updateAid = useLedger((s) => s.updateAid);
  const removeAid = useLedger((s) => s.removeAid);
  const addHold = useLedger((s) => s.addHold);
  const updateHold = useLedger((s) => s.updateHold);
  const removeHold = useLedger((s) => s.removeHold);
  const addDeposit = useLedger((s) => s.addDeposit);
  const updateDeposit = useLedger((s) => s.updateDeposit);
  const removeDeposit = useLedger((s) => s.removeDeposit);
  const [amountRaw, setAmountRaw] = useState(row ? centsToInput(row.amount) : "");
  const [rateRaw, setRateRaw] = useState(row && "rate" in row && row.rate ? centsToInput(row.rate) : "");
  const [currency, setCurrency] = useState<Currency>(row?.currency ?? (side === "hold" ? "USD" : "BYN"));
  const [who, setWho] = useState(row && "who" in row ? row.who : "");
  const [bank, setBank] = useState(row && "bank" in row ? row.bank : "");
  const [note, setNote] = useState(row?.note ?? "");
  const [date, setDate] = useState((row?.at ?? nowStamp()).slice(0, 10));
  const [until, setUntil] = useState(row && "until" in row ? row.until : "");
  const [field, setField] = useState<"amount" | "rate">("amount");
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  function save() {
    const amount = parseLooseCents(amountRaw);
    if (amount === null || amount <= 0) { setError("Укажите сумму."); return; }
    const at = `${date}T12:00:00`;
    if (side === "aid") {
      if (!who.trim()) { setError("Напишите, от кого помощь."); return; }
      const next = { amount, currency, who: who.trim(), note: note.trim(), at };
      if (row) updateAid(row.id, next);
      else addAid(next);
    } else if (side === "hold") {
      const next = { amount, currency, note: note.trim(), at };
      if (row) updateHold(row.id, next);
      else addHold(next);
    } else {
      if (!bank.trim()) { setError("Напишите банк."); return; }
      const rate = rateRaw.trim() ? parseLooseCents(rateRaw) : 0;
      if (rate === null || rate < 0) { setError("Не понял процент."); return; }
      const next = { bank: bank.trim(), amount, currency, rate, note: note.trim(), at, until };
      if (row) updateDeposit(row.id, next);
      else addDeposit(next);
    }
    onClose();
  }

  function remove() {
    if (!row) return;
    if (side === "aid") removeAid(row.id);
    else if (side === "hold") removeHold(row.id);
    else removeDeposit(row.id);
    onClose();
  }

  const title = row ? "Редактирование" : side === "aid" ? "Помощь" : side === "hold" ? "Валюта" : "Депозит";

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-ink/40" />
        <Dialog.Content className="sheet" aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()}>
          <div className="flex items-center justify-between px-4 py-3">
            <Dialog.Close className="grid size-11 place-items-center rounded-full bg-chip" aria-label="Закрыть"><X className="size-5" /></Dialog.Close>
            <Dialog.Title className="font-display text-xl">{title}</Dialog.Title>
            <button type="button" onClick={save} className="h-11 rounded-full bg-ink px-4 font-medium text-surface">Готово</button>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
            <div className="grid grid-cols-2 gap-2">
              {CURRENCIES.map((item) => (
                <button key={item.id} type="button" onClick={() => setCurrency(item.id)} className={cn("h-11 rounded-xl text-sm", currency === item.id ? "bg-ink font-medium text-surface" : "bg-chip")}>
                  {item.label}
                </button>
              ))}
            </div>
            {side === "aid" ? (
              <input value={who} onChange={(e) => setWho(e.target.value)} placeholder="От кого" className="mt-3 h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none" />
            ) : null}
            {side === "deposit" ? (
              <input value={bank} onChange={(e) => setBank(e.target.value)} placeholder="Банк" className="mt-3 h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none" />
            ) : null}
            <label className="mt-3 block text-sm text-muted">
              Сумма
              <AmountButton raw={amountRaw} active={field === "amount"} placeholder="0" onPress={() => setField("amount")} />
            </label>
            {side === "deposit" ? (
              <label className="mt-3 block text-sm text-muted">
                Процент годовых
                <AmountButton raw={rateRaw} active={field === "rate"} placeholder="0" onPress={() => setField("rate")} />
              </label>
            ) : null}
            <div className={cn("mt-3 grid gap-2", side === "deposit" ? "grid-cols-2" : "grid-cols-1")}>
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="h-11 rounded-xl border border-line bg-surface px-3 outline-none" />
              {side === "deposit" ? (
                <input type="date" value={until} onChange={(e) => setUntil(e.target.value)} className="h-11 rounded-xl border border-line bg-surface px-3 outline-none" />
              ) : null}
            </div>
            <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Заметка" className="mt-3 h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none placeholder:text-muted" />
            {error ? <p className="mt-3 text-sm text-expense">{error}</p> : null}
            {row ? (
              <button type="button" onClick={() => { if (!confirmDelete) { setConfirmDelete(true); return; } remove(); }} className="mt-6 text-sm text-expense">
                {confirmDelete ? "Точно удалить" : "Удалить запись"}
              </button>
            ) : null}
          </div>
          <div className="px-4 pb-4 pt-2">
            <Keypad onKey={(key) => {
              if (field === "rate") setRateRaw((value) => applyKey(value, key));
              else setAmountRaw((value) => applyKey(value, key));
            }} />
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

export function OtherLock({ onUnlock }: { onUnlock: () => void }) {
  const reserveHash = useLedger((s) => s.reserveHash);
  if (!reserveHash) return <PassSheet title="Другое" mode="create" onDone={onUnlock} />;
  return <PassSheet title="Другое" mode="unlock" onDone={onUnlock} />;
}

export function PassSheet({
  title,
  mode,
  onDone,
  onCancel,
}: {
  title: string;
  mode: "unlock" | "create" | "change";
  onDone: () => void;
  onCancel?: () => void;
}) {
  const reserveHash = useLedger((s) => s.reserveHash);
  const setReserveHash = useLedger((s) => s.setReserveHash);
  const [step, setStep] = useState<"now" | "next" | "again">(mode === "unlock" ? "now" : mode === "change" ? "now" : "next");
  const [pin, setPin] = useState("");
  const [first, setFirst] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const hint = step === "again" ? "Ещё раз" : step === "now" ? (mode === "unlock" ? "Пароль" : "Текущий пароль") : mode === "create" ? "Установите пароль" : "Новый пароль";

  async function submit(value: string) {
    if (value.length < 4) { setError("Минимум 4 цифры."); return; }
    setBusy(true);
    try {
      if (step === "now") {
        const hash = await hashPass(value);
        if (hash !== reserveHash) { setError("Не тот пароль."); setPin(""); return; }
        if (mode === "unlock") { onDone(); return; }
        setPin("");
        setStep("next");
        setError(null);
        return;
      }
      if (step === "next") {
        setFirst(value);
        setPin("");
        setStep("again");
        setError(null);
        return;
      }
      if (value !== first) { setError("Пароли не совпали."); setPin(""); setStep("next"); setFirst(""); return; }
      setReserveHash(await hashPass(value));
      onDone();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="px-4">
      <h2 className="font-display text-3xl">{title}</h2>
      <p className="mt-2 text-sm text-muted">{hint}</p>
      <div className="mt-4 flex h-12 items-center rounded-xl border-2 border-ink bg-surface px-3 font-display text-2xl tracking-[0.3em]">
        {pin ? <span>{"•".repeat(pin.length)}</span> : null}
        <span className="smeta-caret inline-block h-7 w-0.5 bg-ink" />
      </div>
      {error ? <p className="mt-3 text-sm text-expense">{error}</p> : null}
      <div className="mt-4">
        <Keypad onKey={(key) => {
          if (busy) return;
          if (key === ",") return;
          if (key === "⌫") { setPin((v) => v.slice(0, -1)); setError(null); return; }
          if (key === "±") return;
          setPin((v) => (v.length >= 12 ? v : `${v}${key}`));
          setError(null);
        }} />
      </div>
      <button type="button" disabled={busy} onClick={() => void submit(pin)} className="mt-3 h-12 w-full rounded-full bg-ink font-medium text-surface">Дальше</button>
      {onCancel && mode !== "unlock" ? (
        <button type="button" onClick={onCancel} className="mt-2 h-11 w-full text-sm text-muted">Отмена</button>
      ) : null}
    </div>
  );
}
