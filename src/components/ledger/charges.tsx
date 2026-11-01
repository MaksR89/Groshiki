import { useEffect, useMemo, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { ChevronDown, X } from "lucide-react";
import { cn } from "@/components/ledger/bits";
import { ICON_KEYS, SWATCHES } from "@/lib/money/catalog";
import { dayLabel, nowStamp, todayKey } from "@/lib/money/dates";
import { BookMark, Money } from "@/components/ledger/byn";
import { centsToInput, formatKwh, parseLooseCents } from "@/lib/money/format";
import { CatIcon } from "@/lib/money/icons";
import { AmountButton, applyKey, Keypad } from "@/components/ledger/keypad";
import { OPERATORS } from "@/lib/money/operators";
import { useLedger } from "@/lib/money/store";
import type { Charge, Station } from "@/lib/money/types";

export function ChargesView({ draftKey }: { draftKey: number }) {
  const stations = useLedger((s) => s.stations);
  const charges = useLedger((s) => s.charges);
  const restoreOperators = useLedger((s) => s.restoreOperators);
  const [stationEdit, setStationEdit] = useState<Station | null>(null);
  const [chargeEdit, setChargeEdit] = useState<Charge | "new" | null>(null);
  const [stationsOpen, setStationsOpen] = useState(false);

  useEffect(() => {
    if (draftKey > 0) setChargeEdit("new");
  }, [draftKey]);

  const byId = useMemo(() => new Map(stations.map((s) => [s.id, s])), [stations]);
  const month = todayKey().slice(0, 7);
  const monthRows = charges.filter((c) => c.at.startsWith(month));
  const kwh = monthRows.reduce((sum, c) => sum + c.kwh, 0);
  const spent = monthRows.reduce((sum, c) => sum + c.price, 0);
  const missing = OPERATORS.some((o) => !stations.some((s) => s.id === o.id));

  const groups = useMemo(() => {
    const sorted = charges.slice().sort((a, b) => (a.at < b.at ? 1 : a.at > b.at ? -1 : 0));
    const out: { day: string; items: Charge[] }[] = [];
    for (const row of sorted) {
      const day = row.at.slice(0, 10);
      const last = out[out.length - 1];
      if (last?.day === day) last.items.push(row);
      else out.push({ day, items: [row] });
    }
    return out;
  }, [charges]);

  return (
    <div className="px-4 lg:px-0">
      <h2 className="font-display text-3xl">Зарядки</h2>
      <button type="button" onClick={() => setStationsOpen((open) => !open)} className="mt-3 flex h-11 w-full items-center justify-between text-left">
        <span className="text-sm text-muted">Станции</span>
        <ChevronDown className={cn("size-5 text-muted", stationsOpen && "rotate-180")} />
      </button>
      {stationsOpen ? (
        <>
          <div className="mt-2 grid grid-cols-3 gap-2">
            {stations.map((station) => (
              <button
                key={station.id}
                type="button"
                onClick={() => setStationEdit(station)}
                className="flex min-h-16 flex-col items-center justify-center gap-1 rounded-2xl bg-surface px-1 py-2"
              >
                <StationMark station={station} className="size-8 rounded-lg object-cover" />
                <span className="line-clamp-2 text-center text-xs leading-tight text-ink">{station.name}</span>
              </button>
            ))}
            <button
              type="button"
              onClick={() => setStationEdit({ id: "", name: "", icon: "plug", color: SWATCHES[0], logo: "" })}
              className="flex min-h-16 flex-col items-center justify-center rounded-2xl bg-chip text-sm font-medium"
            >
              Новая
            </button>
          </div>
          {missing ? (
            <button type="button" onClick={restoreOperators} className="mt-3 text-sm font-medium text-ink">
              Вернуть сети
            </button>
          ) : null}
        </>
      ) : null}

      <div className="mt-4 grid grid-cols-2 gap-3">
        <div className="rounded-2xl bg-surface px-4 py-3">
          <div className="text-sm text-muted">Этот месяц</div>
          <div className="mt-1 font-display text-2xl tabular-nums">{formatKwh(kwh)} кВт·ч</div>
        </div>
        <div className="rounded-2xl bg-surface px-4 py-3">
          <div className="text-sm text-muted">Цена</div>
          <div className="mt-1 font-display text-2xl tabular-nums"><Money cents={spent} /></div>
        </div>
      </div>

      {groups.length === 0 ? (
        <p className="mt-8 text-center text-muted">Зарядок пока нет. Нажмите «Добавить зарядку».</p>
      ) : (
        <div className="mt-5 grid gap-4">
          {groups.map((group) => (
            <section key={group.day}>
              <h3 className="text-sm text-muted">{dayLabel(group.day)}</h3>
              <div className="mt-2 grid gap-2">
                {group.items.map((row) => {
                  const station = byId.get(row.stationId);
                  return (
                    <button
                      key={row.id}
                      type="button"
                      onClick={() => setChargeEdit(row)}
                      className="flex items-center gap-3 rounded-2xl bg-surface px-3 py-3 text-left"
                    >
                      <span className="grid size-6 shrink-0 place-items-center overflow-hidden rounded-md bg-chip">
                        {station ? <StationMark station={station} className="size-6 object-cover" /> : <CatIcon name="plug" className="size-3.5" />}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium">{station?.name ?? "Станция"}</span>
                        <span className="block text-sm text-muted">{row.at.slice(11, 16)} · {formatKwh(row.kwh)} кВт·ч</span>
                      </span>
                      <span className="shrink-0 tabular-nums"><Money cents={row.price} /></span>
                    </button>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      )}

      {stationEdit ? <StationDialog station={stationEdit} onClose={() => setStationEdit(null)} /> : null}
      {chargeEdit ? <ChargeDialog charge={chargeEdit === "new" ? null : chargeEdit} onClose={() => setChargeEdit(null)} /> : null}
    </div>
  );
}

function StationMark({ station, className }: { station: Station; className?: string }) {
  if (station.logo) {
    return <img src={station.logo} alt="" className={className ?? "size-16 rounded-xl object-cover"} />;
  }
  return <CatIcon name={station.icon} className="size-6" />;
}

function StationDialog({ station, onClose }: { station: Station; onClose: () => void }) {
  const addStation = useLedger((s) => s.addStation);
  const updateStation = useLedger((s) => s.updateStation);
  const removeStation = useLedger((s) => s.removeStation);
  const [name, setName] = useState(station.name);
  const [icon, setIcon] = useState(station.icon);
  const [color, setColor] = useState(station.color);
  const [error, setError] = useState<string | null>(null);
  const isNew = !station.id;

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-ink/40" />
        <Dialog.Content className="sheet" aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()}>
          <div className="flex items-center justify-between px-4 py-3">
            <Dialog.Close className="grid size-11 place-items-center rounded-full bg-chip" aria-label="Закрыть"><X className="size-5" /></Dialog.Close>
            <Dialog.Title className="font-display text-xl">{isNew ? "Новая станция" : "Станция"}</Dialog.Title>
            <button
              type="button"
              onClick={() => {
                const trimmed = name.trim();
                if (!trimmed) { setError("Нужно название."); return; }
                const patch = { name: trimmed, icon, color, logo: station.logo };
                if (isNew) addStation(patch);
                else updateStation(station.id, patch);
                onClose();
              }}
              className="h-11 rounded-full bg-ink px-4 font-medium text-surface"
            >
              Готово
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-6">
            {station.logo ? (
              <div className="mb-3 grid h-24 place-items-center">
                <img src={station.logo} alt="" className="size-20 rounded-2xl object-cover" />
              </div>
            ) : null}
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Название" className="h-11 w-full rounded-xl border border-line bg-surface px-3 outline-none" />
            {!station.logo ? (
              <>
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
              </>
            ) : null}
            {error ? <p className="mt-3 text-sm text-expense">{error}</p> : null}
            {!isNew ? (
              <button
                type="button"
                onClick={() => {
                  const message = removeStation(station.id);
                  if (message) setError(message);
                  else onClose();
                }}
                className="mt-6 text-sm text-expense"
              >
                Удалить станцию
              </button>
            ) : null}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function ChargeDialog({ charge, onClose }: { charge: Charge | null; onClose: () => void }) {
  const stations = useLedger((s) => s.stations);
  const addCharge = useLedger((s) => s.addCharge);
  const updateCharge = useLedger((s) => s.updateCharge);
  const removeCharge = useLedger((s) => s.removeCharge);
  const [stationId, setStationId] = useState(charge?.stationId ?? stations[0]?.id ?? "");
  const [kwhRaw, setKwhRaw] = useState(charge ? centsToInput(charge.kwh) : "");
  const [rateRaw, setRateRaw] = useState(charge?.rate ? centsToInput(charge.rate) : "");
  const [date, setDate] = useState((charge?.at ?? nowStamp()).slice(0, 10));
  const [time, setTime] = useState((charge?.at ?? nowStamp()).slice(11, 16));
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [field, setField] = useState<"kwh" | "rate">("kwh");
  const [listOpen, setListOpen] = useState(false);
  const station = stations.find((item) => item.id === stationId) ?? null;

  const kwhValue = parseLooseCents(kwhRaw.replace(".", ",")) ?? 0;
  const rateValue = parseLooseCents(rateRaw.replace(".", ",")) ?? 0;
  const total = kwhValue > 0 && rateValue > 0 ? Math.round((kwhValue * rateValue) / 100) : 0;

  function save() {
    const kwh = parseLooseCents(kwhRaw.replace(".", ","));
    const rate = parseLooseCents(rateRaw.replace(".", ",")) ?? 0;
    if (!stationId) { setError("Выберите станцию."); return; }
    if (kwh === null || kwh <= 0) { setError("Укажите, сколько кВт·ч."); return; }
    if (rate < 0) { setError("Цена за кВт·ч не может быть меньше нуля."); return; }
    const price = Math.round((kwh * rate) / 100);
    const next = { stationId, kwh, rate, price, at: `${date}T${time || "12:00"}:00` };
    if (charge) updateCharge(charge.id, next);
    else addCharge(next);
    onClose();
  }

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-ink/40" />
        <Dialog.Content className="sheet" aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()}>
          <div className="flex items-center justify-between px-4 py-3">
            <Dialog.Close className="grid size-11 place-items-center rounded-full bg-chip" aria-label="Закрыть"><X className="size-5" /></Dialog.Close>
            <Dialog.Title className="font-display text-xl">{charge ? "Редактирование" : "Новая зарядка"}</Dialog.Title>
            <button type="button" onClick={save} className="h-11 rounded-full bg-ink px-4 font-medium text-surface">Готово</button>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
            <label className="block text-sm text-muted">
              Станция
              <button type="button" onClick={() => setListOpen((open) => !open)} className="mt-1 flex h-14 w-full items-center gap-3 rounded-xl border border-line bg-surface px-3 text-left">
                {station ? <StationMark station={station} className="size-9 rounded-lg object-cover" /> : <span className="size-9 rounded-lg bg-chip" />}
                <span className="min-w-0 flex-1 truncate text-base text-ink">{station?.name ?? "Выберите"}</span>
                <ChevronDown className={cn("size-5 shrink-0 text-muted", listOpen && "rotate-180")} />
              </button>
            </label>
            {listOpen ? (
              <div className="mt-2 max-h-64 overflow-y-auto rounded-xl bg-surface">
                {stations.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => { setStationId(item.id); setListOpen(false); }}
                    className={cn("flex h-12 w-full items-center gap-3 px-3 text-left", item.id === stationId && "bg-chip")}
                  >
                    <StationMark station={item} className="size-8 rounded-lg object-cover" />
                    <span className="min-w-0 flex-1 truncate">{item.name}</span>
                  </button>
                ))}
              </div>
            ) : null}
            <label className="mt-4 block text-sm text-muted">
              Сколько, кВт·ч
              <AmountButton raw={kwhRaw} active={field === "kwh"} placeholder="0" onPress={() => setField("kwh")} />
            </label>
            <label className="mt-3 block text-sm text-muted">
              Цена за кВт·ч, <BookMark className="inline-block h-[0.9em] w-auto translate-y-[0.12em]" />
              <AmountButton raw={rateRaw} active={field === "rate"} placeholder="0,73" onPress={() => setField("rate")} />
            </label>
            <div className="mt-3 grid grid-cols-2 gap-2">
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="h-11 rounded-xl border border-line bg-surface px-3 outline-none" />
              <input type="time" value={time} onChange={(e) => setTime(e.target.value)} className="h-11 rounded-xl border border-line bg-surface px-3 outline-none" />
            </div>
            <div className="mt-4 rounded-2xl bg-surface px-4 py-4">
              <div className="text-sm text-muted">Цена</div>
              <div className="font-display text-4xl tabular-nums"><Money cents={total} /></div>
              <div className="mt-1 text-sm text-muted">{formatKwh(kwhValue)} кВт·ч × <Money cents={rateValue} /></div>
            </div>
            {error ? <p className="mt-3 text-sm text-expense">{error}</p> : null}
            {charge ? (
              <button
                type="button"
                onClick={() => {
                  if (!confirmDelete) { setConfirmDelete(true); return; }
                  removeCharge(charge.id);
                  onClose();
                }}
                className="mt-6 text-sm text-expense"
              >
                {confirmDelete ? "Точно удалить" : "Удалить зарядку"}
              </button>
            ) : null}
          </div>
          <div className="px-4 pb-4 pt-2">
            <Keypad
              onKey={(key) => {
                if (field === "kwh") setKwhRaw((value) => applyKey(value, key));
                else setRateRaw((value) => applyKey(value, key));
              }}
            />
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
