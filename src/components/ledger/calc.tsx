import { useState } from "react";
import { Money } from "@/components/ledger/byn";
import { Keypad, applyKey, AmountButton } from "@/components/ledger/keypad";
import { cn } from "@/components/ledger/bits";
import { depositCalc, loanCalc, type Cap, type DepositCalc, type LoanCalc, type LoanKind, type TermUnit } from "@/lib/money/calc";
import { inputToCents, parseLooseCents } from "@/lib/money/format";

type Field = "amount" | "rate" | "term" | "extra" | "extraMonths";

function digits(raw: string): number {
  const n = Number(raw.replace(/\D/g, ""));
  return Number.isFinite(n) ? n : 0;
}

export function CalcView() {
  const [mode, setMode] = useState<"deposit" | "loan">("deposit");
  const [amountRaw, setAmountRaw] = useState("");
  const [rateRaw, setRateRaw] = useState("");
  const [termRaw, setTermRaw] = useState("");
  const [extraRaw, setExtraRaw] = useState("");
  const [extraMonthsRaw, setExtraMonthsRaw] = useState("");
  const [unit, setUnit] = useState<TermUnit>("month");
  const [cap, setCap] = useState<Cap>("month");
  const [loanKind, setLoanKind] = useState<LoanKind>("annuity");
  const [field, setField] = useState<Field>("amount");
  const [deposit, setDeposit] = useState<DepositCalc | null>(null);
  const [loan, setLoan] = useState<LoanCalc | null>(null);
  const [miss, setMiss] = useState(false);
  const units = mode === "deposit"
    ? [["day", "Дни"], ["month", "Месяцы"], ["year", "Годы"]] as const
    : [["month", "Месяцы"], ["year", "Годы"]] as const;

  function wipe() {
    setDeposit(null);
    setLoan(null);
    setMiss(false);
  }

  function onKey(key: string) {
    wipe();
    if (field === "rate") setRateRaw((v) => applyKey(v, key));
    else if (field === "term") {
      if (key === "," || key === "±") return;
      setTermRaw((v) => applyKey(v, key).replace(/\D/g, "").slice(0, 4));
    } else if (field === "extra") setExtraRaw((v) => applyKey(v, key));
    else if (field === "extraMonths") {
      if (key === "," || key === "±") return;
      setExtraMonthsRaw((v) => applyKey(v, key).replace(/\D/g, "").slice(0, 4));
    } else setAmountRaw((v) => applyKey(v, key));
  }

  function countNow() {
    const amount = inputToCents(amountRaw);
    const rate = (parseLooseCents(rateRaw.replace(".", ",")) ?? 0) / 100;
    const count = digits(termRaw);
    setDeposit(null);
    setLoan(null);
    setMiss(false);
    if (mode === "deposit") {
      const out = depositCalc(amount, rate, count, unit, cap, inputToCents(extraRaw), digits(extraMonthsRaw));
      if (out) setDeposit(out);
      else setMiss(true);
    } else {
      const out = loanCalc(amount, rate, count, unit === "day" ? "month" : unit, loanKind);
      if (out) setLoan(out);
      else setMiss(true);
    }
  }

  return (
    <>
    <div className="px-4 pb-[17.5rem] lg:px-0 lg:pb-4">
        <h2 className="font-display text-3xl">Расчёт</h2>
        <p className="mt-2 text-sm leading-relaxed text-muted">Считает по кнопке и не пишется в книгу. Год — 365 дней.</p>
        <div className="mt-4 flex rounded-full bg-chip p-1">
          {([["deposit", "Депозит"], ["loan", "Кредит"]] as const).map(([id, label]) => (
            <button key={id} type="button" onClick={() => { setMode(id); if (id === "loan" && unit === "day") setUnit("month"); wipe(); }} className={cn("h-9 flex-1 rounded-full text-sm", mode === id ? "bg-surface font-medium" : "text-muted")}>
              {label}
            </button>
          ))}
        </div>
        <p className="mt-3 text-sm text-muted">Сумма</p>
        <AmountButton raw={amountRaw} active={field === "amount"} placeholder="0" onPress={() => setField("amount")} />
        <p className="mt-3 text-sm text-muted">Ставка, % годовых</p>
        <AmountButton raw={rateRaw} active={field === "rate"} placeholder="0" onPress={() => setField("rate")} />
        <p className="mt-3 text-sm text-muted">Срок</p>
        <AmountButton raw={termRaw} active={field === "term"} placeholder="0" onPress={() => setField("term")} />
        <div className="mt-2 flex gap-2 overflow-x-auto">
          {units.map(([id, label]) => (
            <button key={id} type="button" onClick={() => { setUnit(id); wipe(); }} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", unit === id ? "bg-ink text-surface" : "bg-surface")}>{label}</button>
          ))}
        </div>
        {mode === "deposit" ? (
          <>
            <p className="mt-4 text-sm text-muted">Капитализация</p>
            <div className="mt-2 flex gap-2 overflow-x-auto">
              {([["none", "Без"], ["month", "Месяц"], ["quarter", "Квартал"], ["year", "Год"]] as const).map(([id, label]) => (
                <button key={id} type="button" onClick={() => { setCap(id); wipe(); }} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", cap === id ? "bg-ink text-surface" : "bg-surface")}>{label}</button>
              ))}
            </div>
            <p className="mt-4 text-sm text-muted">Ежемесячный взнос</p>
            <AmountButton raw={extraRaw} active={field === "extra"} placeholder="Не обязательно" onPress={() => setField("extra")} />
            <p className="mt-3 text-sm text-muted">Сколько месяцев пополнять</p>
            <AmountButton raw={extraMonthsRaw} active={field === "extraMonths"} placeholder="Не обязательно" onPress={() => setField("extraMonths")} />
            <p className="mt-2 text-sm text-muted">Взнос в конце месяца и не дольше срока.</p>
          </>
        ) : (
          <>
            <p className="mt-4 text-sm text-muted">Платежи</p>
            <div className="mt-2 flex gap-2 overflow-x-auto">
              <button type="button" onClick={() => { setLoanKind("annuity"); wipe(); }} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", loanKind === "annuity" ? "bg-ink text-surface" : "bg-surface")}>Равными частями</button>
              <button type="button" onClick={() => { setLoanKind("diff"); wipe(); }} className={cn("h-9 shrink-0 rounded-full px-3 text-sm", loanKind === "diff" ? "bg-ink text-surface" : "bg-surface")}>Остаток уменьшается</button>
            </div>
          </>
        )}
        <button type="button" onClick={countNow} className="mt-4 flex h-10 w-full items-center justify-center rounded-xl bg-ink text-base font-medium text-surface">Посчитать</button>
        {miss ? <p className="mt-3 text-sm text-expense">Проверьте сумму, ставку и срок.</p> : null}
        {deposit ? (
          <div className="mt-4 rounded-2xl bg-surface p-4">
            <p className="text-sm text-muted">{cap === "none" ? "Процент от исходной суммы" : "Процент причисляется к сумме"}</p>
            {deposit.paid > 0 ? (
              <>
                <p className="mt-3 text-sm text-muted">Пополнено</p>
                <div className="font-display text-3xl"><Money cents={deposit.paid} /></div>
              </>
            ) : null}
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
      <div className="fixed inset-x-0 z-30 border-t border-line bg-desk px-4 py-3 lg:static lg:z-auto lg:border-0 lg:px-0" style={{ bottom: "calc(4rem + env(safe-area-inset-bottom))" }}>
        <div className="mx-auto max-w-lg lg:max-w-none">
          <Keypad onKey={onKey} />
        </div>
      </div>
    </>
  );
}

