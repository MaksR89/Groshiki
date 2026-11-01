const DB = "smeta";
const STORE = "book";
const KEY = "ledger";

export type MirroredBook = {
  source?: string;
  accounts?: unknown[];
  categories?: unknown[];
  entries?: unknown[];
  stations?: unknown[];
  charges?: unknown[];
  lastCategory?: { expense: string | null; income: string | null };
  showBalance?: boolean;
  showCharges?: boolean;
  showReport?: boolean;
  showReserve?: boolean;
  reserveHash?: string;
  aids?: unknown[];
  holds?: unknown[];
  deposits?: unknown[];
};

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function mirrorBook(book: MirroredBook): Promise<void> {
  try {
    const db = await openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).put(book, KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  } catch {
    /* private mode */
  }
}

export async function readMirror(): Promise<MirroredBook | null> {
  try {
    const db = await openDb();
    const book = await new Promise<MirroredBook | null>((resolve, reject) => {
      const tx = db.transaction(STORE, "readonly");
      const request = tx.objectStore(STORE).get(KEY);
      request.onsuccess = () => resolve((request.result as MirroredBook | undefined) ?? null);
      request.onerror = () => reject(request.error);
    });
    db.close();
    return book;
  } catch {
    return null;
  }
}
