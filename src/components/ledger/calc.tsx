import { useState } from "react";
import { Money } from "@/components/ledger/byn";
import { Keypad, applyKey } from "@/components/ledger/keypad";
import { cn } from "@/components/ledger/bits";
import { depositCalc, loanCalc, type Cap, type LoanKind, type TermUnit } from "@/lib/money/calc";
import { inputToCents, parseLooseCents } from "@/lib/money/format";

type Field = "amount" | "rate" | "term";

function digits(raw: string): number {
  const n = Number(raw.replace(/\D/g, ""));
  return Number.isFinite(n) ? n : 0;
}

export function CalcView() {
  const [mode, setMode] = useState<"deposit" | "loan">("deposit");
  const [amountRaw, setAmountRaw] = useState("");
  const [rateRaw, setRateRaw] = useState("");
  const [termRaw, setTermRaw] = useState("");
  const [unit, setUnit] = useState<TermUnit>("month");
  const [cap, setCap] = useState<Cap>("month");
  const [loanKind, setLoanKind] = useState<LoanKind>("annuity");
  const [field, setField] = useState<Field>("amount");
  const amount = inputToCents(amountRaw);
  const rate = (parseLooseCents(rateRaw.replace(".", ",")) ?? 0) / 100;
  const count = digits(termRaw);
  const deposit = mode === "deposit" ? depositCalc(amount, rate, count, unit, cap) : null;
  const loan = mode === "loan" ? loanCalc(amount, rate, count, unit === "day" ? "month" : unit, loanKind) : null;
  const units = mode === "deposit"
    ? [["day", "Дни"], ["month", "Месяцы"], ["year", "Годы"]] as const
    : [["month", "Месяцы"], ["year", "Годы"]] as const;

  function onKey(key: string) {
    if (field === "rate") setRateRaw((v) => applyKey(v, key));
    else if (field === "term") {
      if (key === "," || key === "±") return;
      setTermRaw((v) => applyKey(v, key).replace(/\D/g, "").slice(0, 4));
    } else setAmountRaw((v) => applyKey(v, key));
  }

  return (
    <div className="flex min-h-[calc(100dvh-11.5rem)] flex-col lg:min-h-0">
      <div className="px-4 lg:px-0">
        <h2 className="font-display text-3xl">Расчёт</h2>
        <p className="mt-2 text-sm leading-relaxed text-muted">Не записывается в книгу. Год в расчёте — 365 дней.</p>
        <div className="mt-4 flex rounded-full bg-chip p-1">
          {([["deposit", "Депозит"], ["loan", "Кредит"]] as const).map(([id, label]) => (
            <button key={id} type="button" onClick={() => { setMode(id); if (id === "loan" && unit === "day") setUnit("month"); }} className={cn("h-9 flex-1 rounded-full text-sm", mode === id ? "bg-surface font-medium" : "text-muted")}>
              {label}
            </button>
          ))}
        </div>
        <Field label="Сумма" raw={amountRaw} active={field === "amount"} onPress={() => setField("amount")} />
        <Field label="Ставка, % годовых" raw={rateRaw} active={field === "rate"} onPress={() => setField("rate")} />
        <Field label="Срок" raw={termRaw} active={field === "term"} onPress={() => setField("term")} />
        <div className="mt-2 flex gap-2 overflow-x-auto">
          {units.map(([id, label]) => (
            <button key={id} type="button" onClick={() => setUnit(id)} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", unit === id ? "bg-ink text-surface" : "bg-surface")}>{label}</button>
          ))}
        </div>
        {mode === "deposit" ? (
          <>
            <p className="mt-4 text-sm text-muted">Капитализация</p>
            <div className="mt-2 flex gap-2 overflow-x-auto">
              {([["none", "Без"], ["month", "Месяц"], ["quarter", "Квартал"], ["year", "Год"]] as const).map(([id, label]) => (
                <button key={id} type="button" onClick={() => setCap(id)} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", cap === id ? "bg-ink text-surface" : "bg-surface")}>{label}</button>
              ))}
            </div>
          </>
        ) : (
          <>
            <p className="mt-4 text-sm text-muted">Платежи</p>
            <div className="mt-2 flex gap-2 overflow-x-auto">
              <button type="button" onClick={() => setLoanKind("annuity")} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", loanKind === "annuity" ? "bg-ink text-surface" : "bg-surface")}>Равными частями</button>
              <button type="button" onClick={() => setLoanKind("diff")} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", loanKind === "diff" ? "bg-ink text-surface" : "bg-surface")}>Остаток уменьшается</button>
            </div>
          </>
        )}
        {deposit ? (
          <div className="mt-4 rounded-2xl bg-surface p-4">
            <p className="text-sm text-muted">{cap === "none" ? "Процент от исходной суммы" : "Процент причисляется к сумме"}</p>
            <p className="mt-3 text-sm text-muted">Доход</p>
            <div className="font-display text-3xl"><Money cents={deposit.interest} /></div>
            <p className="mt-3 text-sm text-muted">Сумма в конце</p>
            <div className="font-display text-3xl"><Money cents={deposit.total} /></div>
          </div>
        ) : null}
        {loan ? (
          <div className="mt-4 rounded-2xl bg-surface p-4">
            <p className="text-sm text-muted">{loanKind === "annuity" ? "Платёж каждый месяц" : "Платёж уменьшается"}</p>
            {loanKind === "annuity" ? (
              <>
                <p className="mt-3 text-sm text-muted">Платёж</p>
                <div className="font-display text-3xl"><Money cents={loan.first} /></div>
              </>
            ) : (
              <>
                <p className="mt-3 text-sm text-muted">Первый платёж</p>
                <div className="font-display text-3xl"><Money cents={loan.first} /></div>
                <p className="mt-3 text-sm text-muted">Последний платёж</p>
                <div className="font-display text-3xl"><Money cents={loan.last} /></div>
              </>
            )}
            <p className="mt-3 text-sm text-muted">Переплата</p>
            <div className="font-display text-3xl"><Money cents={loan.overpay} /></div>
            <p className="mt-3 text-sm text-muted">Всего к выплате</p>
            <div className="font-display text-3xl"><Money cents={loan.total} /></div>
          </div>
        ) : null}
      </div>
      <div className="px-4 pb-2 pt-4 lg:px-0">
        <Keypad onKey={onKey} />
      </div>
    </div>
  );
}

function Field({ label, raw, active, onPress }: { label: string; raw: string; active: boolean; onPress: () => void }) {
  const shown = raw && raw !== "-" ? raw.replace(".", ",") : "";
  return (
    <div className="mt-3">
      <p className="text-sm text-muted">{label}</p>
      <button type="button" onClick={onPress} className={cn("mt-1 flex h-12 w-full items-center rounded-xl bg-surface px-3 text-left text-xl", active && "ring-2 ring-ink")}>
        {shown ? <span className="font-display">{shown}</span> : <span className="text-ink/25">0</span>}
        {active ? <span className="smeta-caret ml-0.5 inline-block h-7 w-0.5 bg-ink" /> : null}
      </button>
    </div>
  );
}
