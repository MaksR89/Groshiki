import { useEffect, useMemo, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "@/components/ledger/bits";
import { guessColor, ICON_KEYS, SWATCHES } from "@/lib/money/catalog";
import { nowStamp } from "@/lib/money/dates";
import { centsToInput, formatRawInput, inputToCents } from "@/lib/money/format";
import { CatIcon } from "@/lib/money/icons";
import { applyKey, Keypad } from "@/components/ledger/keypad";
import { useLedger } from "@/lib/money/store";
import type { Entry, Kind } from "@/lib/money/types";

export function Composer({
  initialKind,
  editing,
  onClose,
}: {
  initialKind: Kind;
  editing: Entry | null;
  onClose: () => void;
}) {
  const accounts = useLedger((s) => s.accounts);
  const categories = useLedger((s) => s.categories);
  const entries = useLedger((s) => s.entries);
  const lastCategory = useLedger((s) => s.lastCategory);
  const addEntry = useLedger((s) => s.addEntry);
  const updateEntry = useLedger((s) => s.updateEntry);
  const removeEntry = useLedger((s) => s.removeEntry);
  const addCategory = useLedger((s) => s.addCategory);

  const [kind, setKind] = useState<Kind>(editing?.kind ?? initialKind);
  const [raw, setRaw] = useState(editing ? centsToInput(editing.amount) : "");
  const [categoryId, setCategoryId] = useState<string | null>(() => {
    if (editing) return editing.categoryId;
    if (initialKind === "transfer") return null;
    return lastCategory[initialKind];
  });
  const [accountId, setAccountId] = useState(editing?.accountId ?? accounts[0]?.id ?? "");
  const [toAccountId, setToAccountId] = useState(editing?.toAccountId ?? accounts[1]?.id ?? "");
  const [note, setNote] = useState(editing?.note ?? "");
  const [date, setDate] = useState((editing?.at ?? nowStamp()).slice(0, 10));
  const [time, setTime] = useState((editing?.at ?? nowStamp()).slice(11, 16));
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [newName, setNewName] = useState("");
  const [newIcon, setNewIcon] = useState("ellipsis");
  const [newColor, setNewColor] = useState(SWATCHES[0]);
  const [adding, setAdding] = useState(false);

  const ranked = useMemo(() => {
    const counts = new Map<string, number>();
    for (const e of entries) {
      if (e.categoryId && e.kind === kind) counts.set(e.categoryId, (counts.get(e.categoryId) ?? 0) + 1);
    }
    return categories
      .filter((c) => c.kind === kind || c.kind === "both")
      .slice()
      .sort((a, b) => (counts.get(b.id) ?? 0) - (counts.get(a.id) ?? 0) || a.name.localeCompare(b.name, "ru"));
  }, [categories, entries, kind]);

  const cents = inputToCents(raw);
  const canSave =
    cents > 0 &&
    (kind === "transfer"
      ? Boolean(accountId && toAccountId && accountId !== toAccountId)
      : Boolean(categoryId && accountId));

  function save() {
    if (!canSave) return;
    const at = `${date}T${time || "12:00"}:00`;
    const entry = {
      kind,
      amount: cents,
      categoryId: kind === "transfer" ? null : categoryId,
      accountId,
      toAccountId: kind === "transfer" ? toAccountId : null,
      note: note.trim(),
      at,
    };
    if (editing) updateEntry(editing.id, entry);
    else addEntry(entry);
    onClose();
  }

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") {
        if (e.key === "Enter") save();
        return;
      }
      if (e.key >= "0" && e.key <= "9") setRaw((v) => applyKey(v, e.key));
      else if (e.key === "." || e.key === ",") setRaw((v) => applyKey(v, ","));
      else if (e.key === "Backspace") setRaw((v) => applyKey(v, "⌫"));
      else if (e.key === "Enter") save();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  function chooseKind(next: Kind) {
    setKind(next);
    if (next === "transfer") return;
    const cat = categories.find((c) => c.id === categoryId);
    if (!cat || (cat.kind !== "both" && cat.kind !== next)) {
      const last = lastCategory[next];
      const lastOk = last && categories.some((c) => c.id === last && (c.kind === next || c.kind === "both"));
      setCategoryId(lastOk ? last : null);
    }
  }

  function createCategory() {
    const name = newName.trim();
    if (!name || kind === "transfer") return;
    const id = addCategory({
      name,
      kind,
      group: "",
      icon: newIcon,
      color: newColor,
    });
    setCategoryId(id);
    setNewName("");
    setNewIcon("ellipsis");
    setNewColor(guessColor(name));
    setAdding(false);
  }

  const title = editing ? "Редактирование" : kind === "income" ? "Новый доход" : kind === "transfer" ? "Перевод" : "Новый расход";

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-ink/40" />
        <Dialog.Content
          className="sheet"
          aria-describedby={undefined}
          onOpenAutoFocus={(e) => e.preventDefault()}
        >
          <div className="flex items-center justify-between px-4 py-3">
            <Dialog.Close className="grid size-11 place-items-center rounded-full bg-chip" aria-label="Закрыть">
              <X className="size-5" />
            </Dialog.Close>
            <Dialog.Title className="font-display text-xl">{title}</Dialog.Title>
            <button type="button" disabled={!canSave} onClick={save} className="h-11 rounded-full bg-expense px-4 font-medium text-surface disabled:opacity-40">
              Готово
            </button>
          </div>

          <div className="px-4">
            <div className="grid grid-cols-3 rounded-full bg-chip p-1">
              {(["expense", "income", "transfer"] as const).map((id) => (
                <button
                  key={id}
                  type="button"
                  disabled={id === "transfer" && accounts.length < 2}
                  onClick={() => chooseKind(id)}
                  className={cn("h-9 rounded-full text-sm disabled:opacity-30", kind === id ? "bg-surface font-medium" : "text-muted")}
                >
                  {id === "expense" ? "Расход" : id === "income" ? "Доход" : "Перевод"}
                </button>
              ))}
            </div>
            <div className="mt-3 text-center font-display text-5xl tabular-nums" aria-live="polite">
              {formatRawInput(raw)}
            </div>
          </div>

          <div className="mt-3 grid grid-cols-2 gap-2 px-4">
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="h-11 rounded-xl border border-line bg-surface px-3 outline-none" />
            <input type="time" value={time} onChange={(e) => setTime(e.target.value)} className="h-11 rounded-xl border border-line bg-surface px-3 outline-none" />
          </div>
          <div className="mt-2 px-4">
            <input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Заметка"
              maxLength={200}
              className="h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none placeholder:text-muted"
            />
          </div>

          {accounts.length > 1 ? (
            <div className="mt-2 grid grid-cols-2 gap-2 px-4">
              <label className="block text-sm text-muted">
                {kind === "transfer" ? "Откуда" : "Счёт"}
                <select value={accountId} onChange={(e) => setAccountId(e.target.value)} className="mt-1 h-11 w-full rounded-xl border border-line bg-surface px-2">
                  {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                </select>
              </label>
              {kind === "transfer" ? (
                <label className="block text-sm text-muted">
                  Куда
                  <select value={toAccountId} onChange={(e) => setToAccountId(e.target.value)} className="mt-1 h-11 w-full rounded-xl border border-line bg-surface px-2">
                    {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                  </select>
                </label>
              ) : <span />}
            </div>
          ) : null}

          {kind !== "transfer" ? (
            <div className="mt-3 min-h-0 flex-1 overflow-y-auto px-4">
              <div className="grid grid-cols-4 gap-2 pb-3">
                {ranked.map((cat) => {
                  const on = cat.id === categoryId;
                  return (
                    <button
                      key={cat.id}
                      type="button"
                      onClick={() => setCategoryId(cat.id)}
                      className={cn("flex min-h-20 flex-col items-center justify-center gap-1 rounded-2xl px-1 py-2", on ? "bg-ink text-surface" : "bg-chip")}
                      style={on ? undefined : { color: cat.color }}
                    >
                      <CatIcon name={cat.icon} className={cn("size-6", on && "text-surface")} />
                      <span className={cn("line-clamp-2 text-center text-xs leading-tight", on ? "text-surface" : "text-ink")}>{cat.name}</span>
                    </button>
                  );
                })}
              </div>
              <button type="button" onClick={() => setAdding(true)} className="mb-3 h-11 w-full rounded-2xl bg-chip text-sm font-medium">
                Добавить статью
              </button>
            </div>
          ) : <div className="flex-1" />}

          <div className="px-4 pb-4 pt-2">
            <Keypad onKey={(key) => setRaw((v) => applyKey(v, key))} />
          </div>
          {editing ? (
            <div className="px-4 pb-4">
              <button
                type="button"
                onClick={() => {
                  if (!confirmDelete) { setConfirmDelete(true); return; }
                  removeEntry(editing.id);
                  onClose();
                }}
                className="h-11 w-full rounded-xl text-sm text-expense"
              >
                {confirmDelete ? "Точно удалить" : "Удалить операцию"}
              </button>
            </div>
          ) : null}
          {adding ? (
            <Dialog.Root open onOpenChange={(open) => { if (!open) setAdding(false); }}>
              <Dialog.Portal>
                <Dialog.Overlay className="fixed inset-0 z-50 bg-ink/40" />
                <Dialog.Content className="sheet z-50" aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()}>
                  <div className="flex items-center justify-between px-4 py-3">
                    <button type="button" onClick={() => setAdding(false)} className="grid size-11 place-items-center rounded-full bg-chip" aria-label="Закрыть">
                      <X className="size-5" />
                    </button>
                    <Dialog.Title className="font-display text-xl">Новая статья</Dialog.Title>
                    <button type="button" onClick={createCategory} disabled={!newName.trim()} className="h-11 rounded-full bg-ink px-4 font-medium text-surface disabled:opacity-40">
                      Готово
                    </button>
                  </div>
                  <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-6">
                    <input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Название" className="h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none placeholder:text-muted" />
                    <p className="mt-4 text-sm text-muted">{kind === "income" ? "Значок дохода" : "Значок расхода"}</p>
                    <div className="mt-2 grid grid-cols-6 gap-2">
                      {ICON_KEYS.map((key) => (
                        <button key={key} type="button" onClick={() => setNewIcon(key)} className={cn("grid h-11 place-items-center rounded-xl", newIcon === key ? "bg-ink text-surface" : "bg-chip")} style={newIcon === key ? undefined : { color: newColor }}>
                          <CatIcon name={key} className="size-5" />
                        </button>
                      ))}
                    </div>
                    <div className="mt-4 flex flex-wrap gap-2">
                      {SWATCHES.map((swatch) => (
                        <button key={swatch} type="button" aria-label={swatch} onClick={() => setNewColor(swatch)} className={cn("size-9 rounded-full", newColor === swatch && "ring-2 ring-ink ring-offset-2")} style={{ background: swatch }} />
                      ))}
                    </div>
                  </div>
                </Dialog.Content>
              </Dialog.Portal>
            </Dialog.Root>
          ) : null}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
