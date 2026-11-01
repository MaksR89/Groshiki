import { useRef, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { ChevronLeft, X } from "lucide-react";
import { cn } from "@/components/ledger/bits";
import { ICON_KEYS, SWATCHES, guessColor } from "@/lib/money/catalog";
import { Money } from "@/components/ledger/byn";
import { formatCount, parseLooseCents, centsToInput } from "@/lib/money/format";
import { AmountButton, applyKey, Keypad } from "@/components/ledger/keypad";
import { CatIcon } from "@/lib/money/icons";
import { downloadText, fromBackup, fromMoneyOkCsv, toBackup, toCsv } from "@/lib/money/io";
import { fromMoneyOkFile } from "@/lib/money/moneyok";
import { accountBalance, totalBalance } from "@/lib/money/stats";
import { useLedger } from "@/lib/money/store";
import { useShowBalance } from "@/lib/money/prefs";
import { PassSheet } from "@/components/ledger/other";
import { APP_VERSION } from "@/lib/money/version";
import type { Category, CatKind } from "@/lib/money/types";

function OpeningField({ cents, onCommit }: { cents: number; onCommit: (cents: number) => void }) {
  const [raw, setRaw] = useState(centsToInput(cents));
  const [open, setOpen] = useState(false);
  return (
    <div>
      <AmountButton raw={raw} active={open} placeholder="0" onPress={() => setOpen((value) => !value)} />
      {open ? (
        <div className="mt-2">
          <Keypad
            signed
            onKey={(key) => {
              const next = applyKey(raw, key);
              setRaw(next);
              const parsed = parseLooseCents(next);
              if (parsed !== null) onCommit(parsed);
            }}
          />
        </div>
      ) : null}
    </div>
  );
}

export function AccountsView({ onBack }: { onBack?: () => void }) {
  const accounts = useLedger((s) => s.accounts);
  const entries = useLedger((s) => s.entries);
  const addAccount = useLedger((s) => s.addAccount);
  const updateAccount = useLedger((s) => s.updateAccount);
  const removeAccount = useLedger((s) => s.removeAccount);
  const [showBalance] = useShowBalance();
  const [name, setName] = useState("");
  const [opening, setOpening] = useState("");
  const [openingPad, setOpeningPad] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="px-4 lg:px-0">
      {onBack ? <Back onClick={onBack} /> : null}
      <h2 className="font-display text-3xl">Счета</h2>
      <div className="mt-4 space-y-3">
        {accounts.map((account) => {
          const balance = accountBalance(account, entries);
          return (
            <article key={account.id} className="rounded-3xl bg-surface p-5">
              <div className="text-sm text-muted">{account.name}</div>
              {showBalance ? <div className="mt-1 font-display text-4xl tabular-nums"><Money cents={balance} /></div> : null}
              <label className="mt-4 block text-sm text-muted">
                Название
                <input
                  defaultValue={account.name}
                  onBlur={(e) => {
                    const next = e.target.value.trim();
                    if (next && next !== account.name) updateAccount(account.id, { name: next });
                  }}
                  className="mt-1 h-11 w-full rounded-xl border border-line bg-bg px-3 text-ink outline-none"
                />
              </label>
              <div className="mt-3 text-sm text-muted">
                Начальный остаток
                <OpeningField cents={account.opening} onCommit={(opening) => updateAccount(account.id, { opening })} />
              </div>
              {account.opening < 0 ? (
                <p className="mt-3 text-sm text-muted">
                  Начальный остаток отрицательный, чтобы сумма операций сошлась с остатком MoneyOK. В статьи эта разница не входит.
                </p>
              ) : null}
              {accounts.length > 1 ? (
                <button
                  type="button"
                  onClick={() => setError(removeAccount(account.id))}
                  className="mt-3 text-sm text-expense"
                >
                  Удалить счёт
                </button>
              ) : null}
            </article>
          );
        })}
      </div>

      <form
        className="mt-4 rounded-3xl bg-surface p-5"
        onSubmit={(e) => {
          e.preventDefault();
          const trimmed = name.trim();
          if (!trimmed) return;
          const cents = opening.trim() ? parseLooseCents(opening) : 0;
          if (cents === null) { setError("Не понял начальный остаток."); return; }
          addAccount({ name: trimmed, opening: cents, color: guessColor(trimmed) });
          setName("");
          setOpening("");
          setError(null);
        }}
      >
        <h3 className="font-medium">Новый счёт</h3>
        <input value={name} onChange={(e) => setName(e.target.value)} onFocus={() => setOpeningPad(false)} placeholder="Название" className="mt-3 h-11 w-full rounded-xl border border-line bg-bg px-3 outline-none placeholder:text-muted" />
        <div className="mt-2 text-sm text-muted">
          Начальный остаток
          <AmountButton raw={opening} active={openingPad} placeholder="0" onPress={() => setOpeningPad(true)} />
        </div>
        {openingPad ? (
          <div className="mt-2">
            <Keypad signed onKey={(key) => setOpening((value) => applyKey(value, key))} />
          </div>
        ) : null}
        <button type="submit" className="mt-3 h-11 rounded-full bg-ink px-4 font-medium text-surface">Добавить</button>
      </form>
      {error ? <p className="mt-3 text-sm text-expense">{error}</p> : null}
    </div>
  );
}

export function CategoriesView({ onBack }: { onBack?: () => void }) {
  const categories = useLedger((s) => s.categories);
  const [side, setSide] = useState<Exclude<CatKind, "both">>("expense");
  const [editing, setEditing] = useState<Category | null>(null);
  const shown = categories.filter((c) => c.kind === side || c.kind === "both");

  return (
    <div className="px-4 lg:px-0">
      {onBack ? <Back onClick={onBack} /> : null}
      <h2 className="font-display text-3xl">Статьи</h2>
      <div className="mt-4 grid grid-cols-2 rounded-full bg-chip p-1">
        <button type="button" aria-pressed={side === "expense"} onClick={() => setSide("expense")} className={cn("h-10 rounded-full text-sm", side === "expense" ? "bg-surface font-medium" : "text-muted")}>Расходы</button>
        <button type="button" aria-pressed={side === "income"} onClick={() => setSide("income")} className={cn("h-10 rounded-full text-sm", side === "income" ? "bg-surface font-medium" : "text-muted")}>Доходы</button>
      </div>
      <div className="mt-4 grid grid-cols-4 gap-2">
        {shown.map((cat) => (
          <button key={cat.id} type="button" onClick={() => setEditing(cat)} className="flex min-h-20 flex-col items-center justify-center gap-1 rounded-2xl bg-surface px-1 py-2" style={{ color: cat.color }}>
            <CatIcon name={cat.icon} className="size-6" />
            <span className="line-clamp-2 text-center text-xs leading-tight text-ink">{cat.name}</span>
          </button>
        ))}
        <button
          type="button"
          onClick={() => setEditing({ id: "", name: "", kind: side, group: "", icon: "ellipsis", color: SWATCHES[0] })}
          className="flex min-h-20 flex-col items-center justify-center rounded-2xl bg-chip text-sm font-medium"
        >
          Новая
        </button>
      </div>
      {editing ? <CategoryDialog category={editing} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

function CategoryDialog({ category, onClose }: { category: Category; onClose: () => void }) {
  const addCategory = useLedger((s) => s.addCategory);
  const updateCategory = useLedger((s) => s.updateCategory);
  const removeCategory = useLedger((s) => s.removeCategory);
  const [name, setName] = useState(category.name);
  const [group, setGroup] = useState(category.group);
  const [kind, setKind] = useState<CatKind>(category.kind);
  const [icon, setIcon] = useState(category.icon);
  const [color, setColor] = useState(category.color);
  const [error, setError] = useState<string | null>(null);
  const isNew = !category.id;

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-ink/40" />
        <Dialog.Content className="sheet" aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()}>
          <div className="flex items-center justify-between px-4 py-3">
            <Dialog.Close className="grid size-11 place-items-center rounded-full bg-chip" aria-label="Закрыть"><X className="size-5" /></Dialog.Close>
            <Dialog.Title className="font-display text-xl">{isNew ? "Новая статья" : "Статья"}</Dialog.Title>
            <button
              type="button"
              onClick={() => {
                const trimmed = name.trim();
                if (!trimmed) { setError("Нужно название."); return; }
                const patch = { name: trimmed, group: group.trim(), kind, icon, color };
                if (isNew) addCategory(patch);
                else updateCategory(category.id, patch);
                onClose();
              }}
              className="h-11 rounded-full bg-ink px-4 font-medium text-surface"
            >
              Готово
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-6">
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Название" className="h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none" />
            <input value={group} onChange={(e) => setGroup(e.target.value)} placeholder="Группа, если нужна" className="mt-2 h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none placeholder:text-muted" />
            <div className="mt-3 grid grid-cols-3 rounded-full bg-chip p-1">
              {(["expense", "income", "both"] as const).map((id) => (
                <button key={id} type="button" onClick={() => setKind(id)} className={cn("h-9 rounded-full text-sm", kind === id ? "bg-surface font-medium" : "text-muted")}>
                  {id === "expense" ? "Расход" : id === "income" ? "Доход" : "Обе"}
                </button>
              ))}
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              {SWATCHES.map((swatch) => (
                <button key={swatch} type="button" aria-label={swatch} onClick={() => setColor(swatch)} className={cn("size-9 rounded-full", color === swatch && "ring-2 ring-ink ring-offset-2 ring-offset-bg")} style={{ background: swatch }} />
              ))}
            </div>
            <div className="mt-4 grid grid-cols-6 gap-2">
              {ICON_KEYS.map((key) => (
                <button key={key} type="button" onClick={() => setIcon(key)} className={cn("grid h-11 place-items-center rounded-xl", icon === key ? "bg-ink text-surface" : "bg-chip")} style={icon === key ? undefined : { color }}>
                  <CatIcon name={key} className="size-5" />
                </button>
              ))}
            </div>
            {error ? <p className="mt-3 text-sm text-expense">{error}</p> : null}
            {!isNew ? (
              <button
                type="button"
                onClick={() => {
                  const message = removeCategory(category.id);
                  if (message) setError(message);
                  else onClose();
                }}
                className="mt-6 text-sm text-expense"
              >
                Удалить статью
              </button>
            ) : null}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

export function MoreView({
  onAccounts,
  onCategories,
}: {
  onAccounts: () => void;
  onCategories: () => void;
}) {
  const accounts = useLedger((s) => s.accounts);
  const categories = useLedger((s) => s.categories);
  const entries = useLedger((s) => s.entries);
  const aids = useLedger((s) => s.aids);
  const holds = useLedger((s) => s.holds);
  const deposits = useLedger((s) => s.deposits);
  const setReserve = useLedger((s) => s.setReserve);
  const clearAll = useLedger((s) => s.clearAll);
  const replaceAll = useLedger((s) => s.replaceAll);
  const fileRef = useRef<HTMLInputElement>(null);
  const [mode, setMode] = useState<"csv" | "json" | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [showBalance, toggleBalance] = useShowBalance();
  const showReserve = useLedger((s) => s.showReserve);
  const showCharges = useLedger((s) => s.showCharges);
  const showReport = useLedger((s) => s.showReport);
  const reserveHash = useLedger((s) => s.reserveHash);
  const setShowReserve = useLedger((s) => s.setShowReserve);
  const setShowCharges = useLedger((s) => s.setShowCharges);
  const setShowReport = useLedger((s) => s.setShowReport);
  const currency = useLedger((s) => s.currency);
  const setCurrency = useLedger((s) => s.setCurrency);
  const [passMode, setPassMode] = useState<"create" | "change" | null>(null);
  const balance = totalBalance(accounts, entries);
  const first = entries[0]?.at.slice(0, 4);
  const last = entries[entries.length - 1]?.at.slice(0, 4);

  async function onFile(file: File) {
    try {
      const lower = file.name.toLowerCase();
      const result = mode === "json" || lower.endsWith(".json")
        ? fromBackup(await file.text())
        : lower.endsWith(".xlsx") || mode === "csv"
          ? await fromMoneyOkFile(file)
          : fromMoneyOkCsv(await file.text());
      if (!result.ok) { setMessage(result.error); return; }
      replaceAll(result.snapshot);
      if (result.reserve) setReserve(result.reserve);
      const balance = totalBalance(result.snapshot.accounts, result.snapshot.entries);
      setMessage(
        mode === "json" || lower.endsWith(".json")
          ? "Копия загружена."
          : `Загружено ${formatCount(result.snapshot.entries.length)} операций. Данные как в файле.`,
      );
      setConfirm(false);
    } catch {
      setMessage("Файл не прочитался.");
    }
  }

  return (
    <div className="px-4 lg:px-0">
      <h2 className="font-display text-3xl">Книга</h2>
      <p className="mt-3 text-sm leading-relaxed text-muted">
        {entries.length
          ? <>{formatCount(entries.length)} операций{first && last ? `, ${first}–${last}` : ""}{showBalance ? <>. На счетах сейчас <Money cents={balance} /></> : null}.</>
          : "Пока пусто. Загрузите выгрузку Деньги OK или добавьте операцию."}
      </p>

      <label className="mt-5 block text-sm text-muted">
        Валюта учёта
        <select
          value={currency}
          onChange={(e) => setCurrency(e.target.value as typeof currency)}
          className="mt-1 h-11 w-full rounded-xl border border-line bg-surface px-3 text-base text-ink"
        >
          <option value="BYN">Бел. рубль</option>
          <option value="USD">Доллар</option>
          <option value="EUR">Евро</option>
          <option value="RUB">Росс. рубль</option>
        </select>
      </label>
      <p className="mt-2 px-1 text-sm leading-relaxed text-muted">Суммы не пересчитываются. Меняется только знак. «Другое» ведёт свою валюту.</p>

      <div className="mt-5 grid gap-2">
        <button
          type="button"
          role="switch"
          aria-checked={showBalance}
          onClick={toggleBalance}
          className="flex h-12 items-center justify-between rounded-2xl bg-surface px-4 text-left"
        >
          <span>Показывать остаток</span>
          <span className={cn("flex h-7 w-12 rounded-full p-1", showBalance ? "bg-ink" : "bg-chip")}>
            <span className={cn("size-5 rounded-full bg-surface", showBalance && "translate-x-5")} />
          </span>
        </button>
        <p className="px-1 text-sm leading-relaxed text-muted">Это текущий остаток счёта, не оборот за всё время.</p>
        <p className="px-1 pt-2 text-sm leading-relaxed text-muted">Расходы и доходы всегда в меню. Остальные разделы можно выключить.</p>
        <SwitchRow on={showCharges} label="Зарядки" onClick={() => setShowCharges(!showCharges)} />
        <SwitchRow on={showReport} label="Отчёт" onClick={() => setShowReport(!showReport)} />
        <SwitchRow
          on={showReserve}
          label="Другое"
          onClick={() => {
            if (showReserve) { setShowReserve(false); return; }
            if (!reserveHash) { setPassMode("create"); return; }
            setShowReserve(true);
          }}
        />
        <p className="px-1 text-sm leading-relaxed text-muted">«Другое» стоит в меню и открывается паролем.</p>
        {reserveHash ? <Row onClick={() => setPassMode("change")}>Сменить пароль «Другое»</Row> : null}
        <Row onClick={onAccounts}>Счета</Row>
        <Row onClick={onCategories}>Статьи</Row>
        <Row onClick={() => downloadText(`groshyki-${stamp()}.json`, toBackup({ accounts, categories, entries }, { aids, holds, deposits }), "application/json")}>Скачать копию JSON</Row>
        <Row onClick={() => downloadText(`groshyki-${stamp()}.csv`, toCsv({ accounts, categories, entries }), "text/csv;charset=utf-8")}>Скачать CSV</Row>
        <p className="px-1 pt-2 text-sm leading-relaxed text-muted">Лучше Excel из Деньги OK: в нём есть время операции. CSV тоже подойдёт, но без времени суток.</p>
        <Row onClick={() => { setMode("csv"); fileRef.current?.click(); }}>Загрузить выгрузку MoneyOK</Row>
        <Row onClick={() => { setMode("json"); fileRef.current?.click(); }}>Загрузить копию JSON</Row>
      </div>
      <input
        ref={fileRef}
        type="file"
        accept=".csv,.xlsx,.json,text/csv,application/json,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        className="absolute h-px w-px opacity-0"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (file) void onFile(file);
        }}
      />

      <div className="mt-6 grid gap-2">
        <button type="button" onClick={() => setConfirm(true)} className="h-11 rounded-2xl text-expense">Очистить книгу</button>
      </div>
      {confirm ? (
        <div className="mt-3 rounded-2xl bg-surface p-4">
          <p className="text-sm">Удалятся все операции, счета и статьи на этом устройстве.</p>
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              onClick={() => {
                clearAll();
                setConfirm(false);
                setMessage("Книга очищена.");
              }}
              className="h-11 rounded-full bg-ink px-4 font-medium text-surface"
            >
              Да
            </button>
            <button type="button" onClick={() => setConfirm(false)} className="h-11 rounded-full bg-chip px-4">Нет</button>
          </div>
        </div>
      ) : null}
      {message ? <p className="mt-3 text-sm text-muted">{message}</p> : null}
      {passMode ? (
        <Dialog.Root open onOpenChange={(open) => { if (!open) setPassMode(null); }}>
          <Dialog.Portal>
            <Dialog.Overlay className="fixed inset-0 z-40 bg-ink/40" />
            <Dialog.Content className="sheet" aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()}>
              <PassSheet
                title={passMode === "create" ? "Пароль для «Другое»" : "Новый пароль"}
                mode={passMode}
                onDone={() => {
                  if (passMode === "create") setShowReserve(true);
                  setPassMode(null);
                }}
                onCancel={() => setPassMode(null)}
              />
            </Dialog.Content>
          </Dialog.Portal>
        </Dialog.Root>
      ) : null}
      <p className="mt-8 text-center text-sm text-muted">Версия {APP_VERSION}. Обновление книгу не стирает.</p>
    </div>
  );
}

function SwitchRow({ on, label, onClick }: { on: boolean; label: string; onClick: () => void }) {
  return (
    <button type="button" role="switch" aria-checked={on} onClick={onClick} className="flex h-12 items-center justify-between rounded-2xl bg-surface px-4 text-left">
      <span>{label}</span>
      <span className={cn("flex h-7 w-12 rounded-full p-1", on ? "bg-ink" : "bg-chip")}>
        <span className={cn("size-5 rounded-full bg-surface", on && "translate-x-5")} />
      </span>
    </button>
  );
}

function Back({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="mb-3 inline-flex items-center gap-1 text-sm text-muted lg:hidden">
      <ChevronLeft className="size-4" /> Назад
    </button>
  );
}

function Row({ onClick, children }: { onClick: () => void; children: string }) {
  return (
    <button type="button" onClick={onClick} className="flex h-12 items-center justify-between rounded-2xl bg-surface px-4 text-left">
      <span>{children}</span>
      <span className="text-muted">›</span>
    </button>
  );
}

function stamp(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
