import { CatIcon } from "@/lib/money/icons";
import { Money } from "@/components/ledger/byn";
import { dayLabel } from "@/lib/money/dates";
import type { Account, Category, Entry } from "@/lib/money/types";

export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(" ");
}

export function entryTitle(entry: Entry, category: Category | undefined, accounts: Account[]): string {
  if (entry.kind === "transfer") {
    const to = accounts.find((a) => a.id === entry.toAccountId)?.name ?? "счёт";
    return `Перевод → ${to}`;
  }
  return category?.name ?? "Без статьи";
}

export function entryMeta(entry: Entry, category: Category | undefined): string {
  const bits: string[] = [];
  if (entry.kind !== "transfer" && category?.group) bits.push(category.group);
  if (entry.note) bits.push(entry.note);
  const time = entry.at.slice(11, 16);
  if (time && time !== "00:00") bits.push(time);
  return bits.join(" · ");
}

export function EntryRow({
  entry,
  category,
  accounts,
  onOpen,
}: {
  entry: Entry;
  category: Category | undefined;
  accounts: Account[];
  onOpen: (entry: Entry) => void;
}) {
  const title = entryTitle(entry, category, accounts);
  const meta = entryMeta(entry, category);
  const tone = entry.kind === "income" ? "text-income" : "text-ink";
  return (
    <button
      type="button"
      onClick={() => onOpen(entry)}
      className="flex w-full items-center gap-3 border-b border-line py-3 text-left last:border-b-0"
    >
      <span
        className="grid size-11 shrink-0 place-items-center rounded-full bg-chip"
        style={category ? { color: category.color } : undefined}
      >
        <CatIcon name={entry.kind === "transfer" ? "repeat" : (category?.icon ?? "ellipsis")} className="size-5" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-medium">{title}</span>
        {meta ? <span className="block truncate text-sm text-muted">{meta}</span> : null}
      </span>
      <span className={cn("shrink-0 font-medium", tone)}><Money cents={entry.amount} /></span>
    </button>
  );
}

export function DayGroups({
  entries,
  categories,
  accounts,
  onOpen,
}: {
  entries: Entry[];
  categories: Category[];
  accounts: Account[];
  onOpen: (entry: Entry) => void;
}) {
  const byId = new Map(categories.map((c) => [c.id, c]));
  const groups: { day: string; items: Entry[] }[] = [];
  for (const entry of entries) {
    const day = entry.at.slice(0, 10);
    const last = groups[groups.length - 1];
    if (!last || last.day !== day) groups.push({ day, items: [entry] });
    else last.items.push(entry);
  }
  return (
    <div>
      {groups.map((group) => (
        <section key={group.day} className="mt-5">
          <h3 className="text-sm font-medium text-muted">{dayLabel(group.day)}</h3>
          <div className="mt-1">
            {group.items.map((entry) => (
              <EntryRow
                key={entry.id}
                entry={entry}
                category={entry.categoryId ? byId.get(entry.categoryId) : undefined}
                accounts={accounts}
                onOpen={onOpen}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
