import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { OPERATORS } from "@/lib/money/operators";
import { compareEntries } from "@/lib/money/io";
import { mirrorBook, readMirror, type MirroredBook } from "@/lib/money/mirror";
import { DATA_VERSION } from "@/lib/money/version";
import type { Account, Aid, Category, Charge, Deposit, Entry, Hold, Kind, ReserveBook, Snapshot, Source, Station, Currency } from "@/lib/money/types";

type LastCategory = { expense: string | null; income: string | null };

type LedgerState = Snapshot & {
  hydrated: boolean;
  source: Source;
  stations: Station[];
  charges: Charge[];
  aids: Aid[];
  holds: Hold[];
  deposits: Deposit[];
  lastCategory: LastCategory;
  showBalance: boolean;
  showCharges: boolean;
  showReport: boolean;
  showReserve: boolean;
  reserveHash: string;
  currency: Currency;
  setShowBalance: (on: boolean) => void;
  setShowCharges: (on: boolean) => void;
  setShowReport: (on: boolean) => void;
  setShowReserve: (on: boolean) => void;
  setReserveHash: (hash: string) => void;
  setCurrency: (currency: Currency) => void;
  clearAll: () => void;
  replaceAll: (snapshot: Snapshot) => void;
  addEntry: (entry: Omit<Entry, "id">) => void;
  updateEntry: (id: string, entry: Omit<Entry, "id">) => void;
  removeEntry: (id: string) => void;
  addAccount: (account: Omit<Account, "id">) => string;
  updateAccount: (id: string, patch: Partial<Omit<Account, "id">>) => void;
  removeAccount: (id: string) => string | null;
  addCategory: (category: Omit<Category, "id">) => string;
  updateCategory: (id: string, patch: Partial<Omit<Category, "id">>) => void;
  removeCategory: (id: string) => string | null;
  addStation: (station: Omit<Station, "id">) => string;
  updateStation: (id: string, patch: Partial<Omit<Station, "id">>) => void;
  removeStation: (id: string) => string | null;
  addCharge: (charge: Omit<Charge, "id">) => void;
  updateCharge: (id: string, charge: Omit<Charge, "id">) => void;
  removeCharge: (id: string) => void;
  setChargeBook: (stations: Station[], charges: Charge[]) => void;
  restoreOperators: () => void;
  addAid: (row: Omit<Aid, "id">) => void;
  updateAid: (id: string, row: Omit<Aid, "id">) => void;
  removeAid: (id: string) => void;
  addHold: (row: Omit<Hold, "id">) => void;
  updateHold: (id: string, row: Omit<Hold, "id">) => void;
  removeHold: (id: string) => void;
  addDeposit: (row: Omit<Deposit, "id">) => void;
  updateDeposit: (id: string, row: Omit<Deposit, "id">) => void;
  removeDeposit: (id: string) => void;
  setReserve: (book: ReserveBook) => void;
};

function uid(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

const memoryStorage = {
  getItem: () => null,
  setItem: () => {},
  removeItem: () => {},
};

export const useLedger = create<LedgerState>()(
  persist(
    (set, get) => ({
      hydrated: false,
      source: "empty",
      accounts: [],
      categories: [],
      entries: [],
      stations: [],
      charges: [],
      aids: [],
      holds: [],
      deposits: [],
      lastCategory: { expense: null, income: null },
      showBalance: false,
      showCharges: true,
      showReport: true,
      showReserve: false,
      reserveHash: "",
      currency: "BYN",
      setShowBalance: (on) => set({ showBalance: on }),
      setShowCharges: (on) => set({ showCharges: on }),
      setShowReport: (on) => set({ showReport: on }),
      setShowReserve: (on) => set({ showReserve: on }),
      setReserveHash: (hash) => set({ reserveHash: hash }),
      setCurrency: (currency) => set({ currency }),
      clearAll: () =>
        set({
          accounts: [],
          categories: [],
          entries: [],
          source: "cleared",
          lastCategory: { expense: null, income: null },
        }),
      replaceAll: (snapshot) => set({ ...snapshot, source: "edited" }),
      addEntry: (entry) => {
        const next: Entry = { ...entry, id: uid("e") };
        const last = { ...get().lastCategory };
        if ((entry.kind === "expense" || entry.kind === "income") && entry.categoryId) {
          last[entry.kind] = entry.categoryId;
        }
        set({
          entries: [...get().entries, next].sort(compareEntries),
          lastCategory: last,
          source: get().source === "cleared" || get().source === "empty" ? "edited" : get().source === "moneyok" ? "edited" : get().source,
        });
      },
      updateEntry: (id, entry) =>
        set({
          entries: get().entries.map((e) => (e.id === id ? { ...entry, id } : e)).sort(compareEntries),
          source: "edited",
        }),
      removeEntry: (id) => set({ entries: get().entries.filter((e) => e.id !== id), source: "edited" }),
      addAccount: (account) => {
        const id = uid("acc");
        set({ accounts: [...get().accounts, { ...account, id }], source: "edited" });
        return id;
      },
      updateAccount: (id, patch) =>
        set({
          accounts: get().accounts.map((a) => (a.id === id ? { ...a, ...patch } : a)),
          source: "edited",
        }),
      removeAccount: (id) => {
        if (get().accounts.length < 2) return "Нужен хотя бы один счёт.";
        const used = get().entries.some((e) => e.accountId === id || e.toAccountId === id);
        if (used) return "На счёте есть операции. Сначала перенесите их.";
        set({ accounts: get().accounts.filter((a) => a.id !== id), source: "edited" });
        return null;
      },
      addCategory: (category) => {
        const id = uid("cat");
        set({ categories: [...get().categories, { ...category, id }], source: "edited" });
        return id;
      },
      updateCategory: (id, patch) =>
        set({
          categories: get().categories.map((c) => (c.id === id ? { ...c, ...patch } : c)),
          source: "edited",
        }),
      removeCategory: (id) => {
        const used = get().entries.filter((e) => e.categoryId === id).length;
        if (used) return `Статья используется в ${used} операциях.`;
        set({ categories: get().categories.filter((c) => c.id !== id), source: "edited" });
        return null;
      },
      addStation: (station) => {
        const id = uid("st");
        set({ stations: [...get().stations, { ...station, id }] });
        return id;
      },
      updateStation: (id, patch) =>
        set({ stations: get().stations.map((s) => (s.id === id ? { ...s, ...patch } : s)) }),
      removeStation: (id) => {
        const used = get().charges.filter((c) => c.stationId === id).length;
        if (used) return `Станция используется в ${used} зарядках.`;
        set({ stations: get().stations.filter((s) => s.id !== id) });
        return null;
      },
      addCharge: (charge) => {
        const next: Charge = { ...charge, id: uid("ch") };
        set({ charges: [...get().charges, next].sort(compareCharges) });
      },
      updateCharge: (id, charge) =>
        set({
          charges: get().charges.map((c) => (c.id === id ? { ...charge, id } : c)).sort(compareCharges),
        }),
      removeCharge: (id) => set({ charges: get().charges.filter((c) => c.id !== id) }),
      setChargeBook: (stations, charges) => set({ stations, charges: charges.slice().sort(compareCharges) }),
      restoreOperators: () => {
        const have = new Set(get().stations.map((s) => s.id));
        const missing = OPERATORS.filter((o) => !have.has(o.id));
        if (!missing.length) return;
        set({ stations: [...get().stations, ...missing] });
      },
      addAid: (row) => set({ aids: [...get().aids, { ...row, id: uid("aid") }].sort(byDate) }),
      updateAid: (id, row) => set({ aids: get().aids.map((a) => (a.id === id ? { ...row, id } : a)).sort(byDate) }),
      removeAid: (id) => set({ aids: get().aids.filter((a) => a.id !== id) }),
      addHold: (row) => set({ holds: [...get().holds, { ...row, id: uid("hold") }].sort(byDate) }),
      updateHold: (id, row) => set({ holds: get().holds.map((a) => (a.id === id ? { ...row, id } : a)).sort(byDate) }),
      removeHold: (id) => set({ holds: get().holds.filter((a) => a.id !== id) }),
      addDeposit: (row) => set({ deposits: [...get().deposits, { ...row, id: uid("dep") }].sort(byDate) }),
      updateDeposit: (id, row) => set({ deposits: get().deposits.map((a) => (a.id === id ? { ...row, id } : a)).sort(byDate) }),
      removeDeposit: (id) => set({ deposits: get().deposits.filter((a) => a.id !== id) }),
      setReserve: (book) => set({
        aids: book.aids.slice().sort(byDate),
        holds: book.holds.slice().sort(byDate),
        deposits: book.deposits.slice().sort(byDate),
      }),
    }),
    {
      name: "smeta-ledger-v1",
      version: DATA_VERSION,
      skipHydration: true,
      storage: createJSONStorage(() => (typeof window === "undefined" ? memoryStorage : localStorage)),
      migrate: (persisted) => keepBook(persisted as MirroredBook | undefined),
      partialize: (s) => ({
        source: s.source,
        accounts: s.accounts,
        categories: s.categories,
        entries: s.entries,
        stations: s.stations,
        charges: s.charges,
        aids: s.aids,
        holds: s.holds,
        deposits: s.deposits,
        lastCategory: s.lastCategory,
        showBalance: s.showBalance,
        showCharges: s.showCharges,
        showReport: s.showReport,
        showReserve: s.showReserve,
        reserveHash: s.reserveHash,
        currency: s.currency,
      }),
    },
  ),
);

let booting: Promise<void> | null = null;

export function bootLedger(): Promise<void> {
  if (!booting) {
    booting = (async () => {
      try {
        await useLedger.persist.rehydrate();
      } catch {
        salvageLocal();
      }
      if (!hasBook(useLedger.getState())) {
        const saved = await readMirror();
        if (saved) applyBook(saved);
      }
      const s = useLedger.getState();
      const ops = new Map(OPERATORS.map((o) => [o.id, o]));
      const stations = (Array.isArray(s.stations) ? s.stations : []).map((st) => {
        const op = ops.get(st.id);
        return op ? { ...st, logo: op.logo, color: op.color } : { ...st, logo: st.logo ?? "" };
      });
      const charges = (Array.isArray(s.charges) ? s.charges : []).map((c) => ({
        ...c,
        rate: typeof c.rate === "number" ? c.rate : c.kwh > 0 ? Math.round((c.price * 100) / c.kwh) : 0,
      }));
      const known = new Set(stations.map((st) => st.id));
      const withHome = [...stations, ...OPERATORS.filter((op) => !known.has(op.id))];
      useLedger.setState({ stations: withHome, charges });
      if (!hasBook(useLedger.getState())) useLedger.setState({ source: "empty" });
      if (useLedger.getState().stations.length === 0) useLedger.setState({ stations: OPERATORS });
      useLedger.setState({ hydrated: true });
      void mirrorBook(pickBook(useLedger.getState()));
      try {
        void navigator.storage?.persist?.();
      } catch {
        /* not a browser */
      }
      useLedger.subscribe((state) => {
        if (!state.hydrated) return;
        void mirrorBook(pickBook(state));
      });
    })();
  }
  return booting;
}

function hasBook(state: { source: Source; accounts: unknown[]; entries: unknown[] }): boolean {
  return state.source === "cleared" || state.accounts.length > 0 || state.entries.length > 0;
}

function keepBook(raw: MirroredBook | undefined): {
  source: Source;
  accounts: Account[];
  categories: Category[];
  entries: Entry[];
  stations: Station[];
  charges: Charge[];
  aids: Aid[];
  holds: Hold[];
  deposits: Deposit[];
  lastCategory: { expense: string | null; income: string | null };
  showBalance: boolean;
  showCharges: boolean;
  showReport: boolean;
  showReserve: boolean;
  reserveHash: string;
  currency: Currency;
} {
  const book = raw ?? {};
  const source: Source = book.source === "empty" || book.source === "moneyok" || book.source === "cleared" || book.source === "edited" ? book.source : "edited";
  return {
    source,
    accounts: (Array.isArray(book.accounts) ? book.accounts : []) as Account[],
    categories: (Array.isArray(book.categories) ? book.categories : []) as Category[],
    entries: (Array.isArray(book.entries) ? book.entries : []) as Entry[],
    stations: (Array.isArray(book.stations) ? book.stations : []) as Station[],
    charges: (Array.isArray(book.charges) ? book.charges : []) as Charge[],
    aids: (Array.isArray(book.aids) ? book.aids : []) as Aid[],
    holds: (Array.isArray(book.holds) ? book.holds : []) as Hold[],
    deposits: (Array.isArray(book.deposits) ? book.deposits : []) as Deposit[],
    lastCategory: book.lastCategory ?? { expense: null, income: null },
    showBalance: book.showBalance === true,
    showCharges: book.showCharges !== false,
    showReport: book.showReport !== false,
    showReserve: book.showReserve === true,
    reserveHash: typeof book.reserveHash === "string" ? book.reserveHash : "",
    currency: book.currency === "USD" || book.currency === "EUR" || book.currency === "RUB" || book.currency === "BYN" ? book.currency : "BYN",
  };
}

function applyBook(raw: MirroredBook) {
  const book = keepBook(raw);
  if (!book.entries.length && !book.accounts.length && book.source !== "cleared") return;
  useLedger.setState(book);
}

function salvageLocal() {
  try {
    const text = localStorage.getItem("smeta-ledger-v1");
    if (!text) return;
    const parsed = JSON.parse(text) as { state?: MirroredBook };
    if (parsed.state) applyBook(parsed.state);
  } catch {
    /* оставляем запись в браузере как есть */
  }
}

function pickBook(state: LedgerState): MirroredBook {
  return {
    source: state.source,
    accounts: state.accounts,
    categories: state.categories,
    entries: state.entries,
    stations: state.stations,
    charges: state.charges,
    aids: state.aids,
    holds: state.holds,
    deposits: state.deposits,
    lastCategory: state.lastCategory,
    showBalance: state.showBalance,
    showCharges: state.showCharges,
    showReport: state.showReport,
    showReserve: state.showReserve,
    reserveHash: state.reserveHash,
    currency: state.currency,
  };
}

function byDate(a: { at: string; id: string }, b: { at: string; id: string }): number {
  if (a.at < b.at) return -1;
  if (a.at > b.at) return 1;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

function compareCharges(a: Charge, b: Charge): number {
  if (a.at < b.at) return -1;
  if (a.at > b.at) return 1;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

export function entryKindLabel(kind: Kind): string {
  if (kind === "income") return "Доход";
  if (kind === "transfer") return "Перевод";
  return "Расход";
}
