export const SWATCHES = [
  "#b6402c",
  "#1f6b45",
  "#c4892a",
  "#2f5573",
  "#8a4d68",
  "#3f6f62",
  "#a15a32",
  "#4c5d8a",
  "#6d5a3a",
  "#2a6f86",
  "#9a3f4a",
  "#5c5348",
];

const BY_NAME: Record<string, string> = {
  продукты: "basket",
  бензин: "fuel",
  другое: "ellipsis",
  сервис: "wrench",
  мобильный: "phone",
  проезд: "bus",
  коммуналка: "house",
  рассрочка: "landmark",
  отдых: "ticket",
  налоги: "receipt",
  подарки: "gift",
  стройка: "hammer",
  расходка: "package",
  гараж: "warehouse",
  "аренда квартиры": "key",
  алиэкспрес: "bag",
  "радиозапчасти и сопут": "cpu",
  одежда: "shirt",
  здоровье: "heart",
  дом: "sofa",
  охота: "target",
  гигиена: "droplets",
  интернет: "wifi",
  wb: "bag",
  "техника и комплектующие": "monitor",
  долг: "coins",
  "холд крипты": "bitcoin",
  штраф: "alert",
  "приборы инструменты": "ruler",
  детям: "baby",
  монитор: "monitor",
  печать: "printer",
  "адмистрирование пк": "server",
  бизнес: "briefcase",
  подработка: "wrench",
  чекер: "badge",
  автостекло: "car",
  телевизор: "tv",
  попутчики: "users",
  дивиденды: "trend",
  узелок: "gem",
  куфар: "store",
  кофемастер: "coffee",
  клевер: "clover",
  подписки: "repeat",
  принтер: "printer",
  барахолка: "tags",
  saifi: "briefcase",
};

export const ICON_KEYS = [
  "basket", "fuel", "ellipsis", "wrench", "phone", "bus", "house", "landmark",
  "ticket", "receipt", "gift", "hammer", "package", "warehouse", "key", "bag",
  "cpu", "shirt", "heart", "sofa", "target", "droplets", "wifi", "monitor",
  "coins", "bitcoin", "alert", "ruler", "baby", "printer", "server", "briefcase",
  "badge", "car", "tv", "users", "trend", "gem", "store", "coffee", "clover",
  "repeat", "tags", "plug",
] as const;

export function guessIcon(name: string): string {
  const known = BY_NAME[name.trim().toLowerCase()];
  if (known) return known;
  let h = 0;
  for (const ch of name) h = (h * 33 + ch.charCodeAt(0)) >>> 0;
  return ICON_KEYS[h % ICON_KEYS.length] ?? "ellipsis";
}

export function guessColor(name: string): string {
  let h = 0;
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return SWATCHES[h % SWATCHES.length] ?? SWATCHES[0];
}
