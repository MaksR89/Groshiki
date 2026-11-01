import { useLedger } from "@/lib/money/store";

export function useShowBalance(): [boolean, () => void] {
  const on = useLedger((s) => s.showBalance);
  const setShowBalance = useLedger((s) => s.setShowBalance);
  return [on, () => setShowBalance(!on)];
}
