import { Delete } from "lucide-react";
import { cn } from "@/components/ledger/bits";
import { applyCalcKey, backspaceRaw, formatRawInput, pushDigit, pushDot } from "@/lib/money/format";

const ROWS = [
  ["1", "2", "3", "+"],
  ["4", "5", "6", "−"],
  ["7", "8", "9", "×"],
  [",", "0", "⌫", "÷"],
] as const;

export function applyKey(raw: string, key: string): string {
  const calc = applyCalcKey(raw, key);
  if (calc !== null) return calc;
  if (key === "⌫") {
    const exprAt = [...raw].findIndex((ch, i) => i > 0 && "+−×÷".includes(ch));
    if (exprAt > 0 && exprAt === raw.length - 1) return raw.slice(0, -1);
    return mapSide(raw, backspaceRaw);
  }
  if (key === ",") return mapSide(raw, pushDot);
  if (key === "±") return mapSide(raw, (value) => (value.startsWith("-") ? value.slice(1) : value ? `-${value}` : "-"));
  return mapSide(raw, (value) => pushDigit(value, key));
}

function mapSide(raw: string, edit: (value: string) => string): string {
  const at = [...raw].findIndex((ch, i) => i > 0 && "+−×÷".includes(ch));
  if (at < 0) return edit(raw);
  return raw.slice(0, at + 1) + edit(raw.slice(at + 1));
}

export function Keypad({ onKey, signed }: { onKey: (key: string) => void; signed?: boolean }) {
  return (
    <div className="grid grid-cols-4 gap-2 touch-none">
      {ROWS.flat().map((key) => (
        <button
          key={key}
          type="button"
          onClick={() => onKey(key)}
          className={cn("flex h-12 items-center justify-center rounded-2xl border border-line text-xl", "+−×÷".includes(key) ? "bg-chip" : "bg-surface")}
        >
          {key === "⌫" ? <Delete className="size-5" aria-label="Стереть" /> : key}
        </button>
      ))}
      {signed ? (
        <button type="button" onClick={() => onKey("±")} className="flex h-12 items-center justify-center rounded-2xl bg-chip text-xl">
          ±
        </button>
      ) : null}
      <button type="button" onClick={() => onKey("=")} className={cn("flex h-12 items-center justify-center rounded-2xl bg-ink text-xl text-surface", signed ? "col-span-3" : "col-span-4")}>
        =
      </button>
    </div>
  );
}

export function AmountButton({
  raw,
  active,
  placeholder,
  onPress,
}: {
  raw: string;
  active?: boolean;
  placeholder: string;
  onPress: () => void;
}) {
  const shown = raw && raw !== "-" ? formatRawInput(raw) : "";
  return (
    <button
      type="button"
      onClick={onPress}
      className={cn(
        "relative mt-1 flex h-12 w-full items-center rounded-xl border bg-surface px-3 text-left",
        active ? "border-2 border-ink ring-2 ring-ink/25" : "border-line",
      )}
    >
      {shown ? (
        <span className="font-display text-2xl">{shown}</span>
      ) : (
        <span className="pointer-events-none font-sans text-xl font-normal text-ink/25">{placeholder}</span>
      )}
      {active && !shown ? <span className="smeta-caret absolute left-3 top-1/2 h-7 w-0.5 -translate-y-1/2 bg-ink" /> : null}
      {active && shown ? <span className="smeta-caret ml-0.5 inline-block h-7 w-0.5 shrink-0 bg-ink" /> : null}
    </button>
  );
}
