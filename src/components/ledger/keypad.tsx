import { Delete } from "lucide-react";
import { cn } from "@/components/ledger/bits";
import { backspaceRaw, formatRawInput, pushDigit, pushDot } from "@/lib/money/format";

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "⌫"] as const;

export function applyKey(raw: string, key: string): string {
  if (key === "⌫") return backspaceRaw(raw);
  if (key === ",") return pushDot(raw);
  if (key === "±") return raw.startsWith("-") ? raw.slice(1) : raw ? `-${raw}` : "-";
  return pushDigit(raw, key);
}

export function Keypad({ onKey, signed }: { onKey: (key: string) => void; signed?: boolean }) {
  return (
    <div className="grid grid-cols-3 gap-2">
      {signed ? (
        <button type="button" onClick={() => onKey("±")} className="col-span-3 flex h-11 items-center justify-center rounded-2xl bg-chip text-lg">
          ±
        </button>
      ) : null}
      {KEYS.map((key) => (
        <button
          key={key}
          type="button"
          onClick={() => onKey(key)}
          className="flex h-12 items-center justify-center rounded-2xl bg-surface text-xl"
        >
          {key === "⌫" ? <Delete className="size-5" aria-label="Стереть" /> : key}
        </button>
      ))}
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
        "mt-1 flex h-12 w-full items-center rounded-xl border bg-surface px-3 text-left",
        active ? "border-2 border-ink ring-2 ring-ink/25" : "border-line",
      )}
    >
      {active && !shown ? <span className="smeta-caret mr-1 inline-block h-7 w-0.5 shrink-0 bg-ink" /> : null}
      {shown ? (
        <span className="font-display text-2xl">{shown}</span>
      ) : (
        <span className="font-sans text-xl font-normal text-ink/25">{placeholder}</span>
      )}
      {active && shown ? <span className="smeta-caret ml-0.5 inline-block h-7 w-0.5 shrink-0 bg-ink" /> : null}
    </button>
  );
}
