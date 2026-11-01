export type Kind = "expense" | "income" | "transfer";
export type CatKind = "expense" | "income" | "both";
export type Source = "empty" | "moneyok" | "cleared" | "edited";

export type Account = {
  id: string;
  name: string;
  opening: number;
  color: string;
};

export type Category = {
  id: string;
  name: string;
  kind: CatKind;
  group: string;
  icon: string;
  color: string;
};

export type Entry = {
  id: string;
  kind: Kind;
  amount: number;
  categoryId: string | null;
  accountId: string;
  toAccountId: string | null;
  note: string;
  at: string;
};

export type Station = {
  id: string;
  name: string;
  icon: string;
  color: string;
  logo: string;
};

export type Charge = {
  id: string;
  stationId: string;
  kwh: number;
  rate: number;
  price: number;
  at: string;
};

export type Currency = "EUR" | "USD" | "RUB" | "BYN";

export type Aid = {
  id: string;
  amount: number;
  currency: Currency;
  who: string;
  note: string;
  at: string;
};

export type Hold = {
  id: string;
  amount: number;
  currency: Currency;
  note: string;
  at: string;
};

export type Deposit = {
  id: string;
  bank: string;
  amount: number;
  currency: Currency;
  rate: number;
  note: string;
  at: string;
  until: string;
};

export type ReserveBook = {
  aids: Aid[];
  holds: Hold[];
  deposits: Deposit[];
};

export type Snapshot = {
  accounts: Account[];
  categories: Category[];
  entries: Entry[];
};

export type LedgerRaw = {
  accountName: string;
  openingCents: number;
  balanceCents: number;
  categories: {
    name: string;
    kind: CatKind;
    group: string;
    icon: string;
    color: string;
  }[];
  entries: (string | number)[][];
};
