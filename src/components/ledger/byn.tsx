import { formatMoney } from "@/lib/money/format";
import { useLedger } from "@/lib/money/store";
import type { Currency } from "@/lib/money/types";

const MARK =
  "M0.26 58.54L13.02 58.28L13.02 0.26L62.54 0.26L62.54 10.06L22.82 10.06L22.69 37.13L45.00 37.13L50.80 37.78L58.54 40.62L63.57 44.10L66.40 46.93L68.85 50.29L70.92 54.41L72.21 58.80L72.59 64.34L72.08 68.60L70.40 73.62L67.44 78.65L63.95 82.39L56.60 87.16L52.22 88.71L47.32 89.61L13.02 89.61L13.02 68.47L0.26 68.21L0.26 58.54ZM22.82 46.93L22.82 58.54L45.26 58.54L45.26 68.21L22.82 68.21L22.82 79.94L46.16 79.94L49.38 79.43L52.09 78.52L56.48 75.95L60.73 71.17L62.79 65.37L62.41 59.57L59.83 54.28L55.32 50.03L51.19 47.97L46.68 46.93L22.82 46.93Z";

const FOREIGN: Record<Exclude<Currency, "BYN">, string> = { EUR: "€", USD: "$", RUB: "₽" };

export function BynMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 73 90" className={className} aria-hidden="true">
      <path fill="currentColor" fillRule="evenodd" d={MARK} />
    </svg>
  );
}

export function Money({ cents, sign }: { cents: number; sign?: boolean }) {
  const currency = useLedger((s) => s.currency);
  return (
    <span className="inline-flex items-baseline whitespace-nowrap">
      <span>{formatMoney(cents, { sign })}</span>
      {currency === "BYN" ? <BynMark className="ml-[0.12em] h-[0.62em] w-auto" /> : <span className="ml-[0.15em]">{FOREIGN[currency]}</span>}
    </span>
  );
}

export function BookMark({ className }: { className?: string }) {
  const currency = useLedger((s) => s.currency);
  if (currency === "BYN") return <BynMark className={className} />;
  return <span className="ml-[0.12em]">{FOREIGN[currency]}</span>;
}

export function Sum({ cents, currency }: { cents: number; currency: Currency }) {
  return (
    <span className="inline-flex items-baseline whitespace-nowrap">
      <span>{formatMoney(cents)}</span>
      {currency === "BYN" ? <BynMark className="ml-[0.12em] h-[0.62em] w-auto" /> : <span className="ml-[0.15em]">{FOREIGN[currency]}</span>}
    </span>
  );
}