import type { Station } from "@/lib/money/types";

/** Public charging networks in Belarus, not every private socket owner. */
export const OPERATORS: Station[] = [
  { id: "st-malanka", name: "Malanka", icon: "plug", color: "#2f9e45", logo: "/stations/malanka.png" },
  { id: "st-batteryfly", name: "BatteryFly", icon: "plug", color: "#10281f", logo: "/stations/batteryfly.png" },
  { id: "st-forevo", name: "forEVo", icon: "plug", color: "#242424", logo: "/stations/forevo.png" },
  { id: "st-evika", name: "Evika", icon: "plug", color: "#6a2d91", logo: "/stations/evika.png" },
  { id: "st-zaryadka", name: "Zaryadka", icon: "plug", color: "#1a6fd4", logo: "/stations/zaryadka.png" },
  { id: "st-united", name: "United Company", icon: "plug", color: "#14161c", logo: "/stations/united.png" },
  { id: "st-chargego", name: "Charge&GO", icon: "plug", color: "#0c6b4c", logo: "/stations/chargego.png" },
  { id: "st-csms", name: "ЦСМС", icon: "plug", color: "#d64545", logo: "/stations/csms.png" },
  { id: "st-evon", name: "EVON", icon: "plug", color: "#1497b8", logo: "/stations/evon.png" },
  { id: "st-orange", name: "Orange", icon: "plug", color: "#f08a12", logo: "/stations/orange.png" },
  { id: "st-skat", name: "SKAT", icon: "plug", color: "#1aa8c4", logo: "/stations/skat.png" },
  { id: "st-prizma", name: "Prizma", icon: "plug", color: "#5c4d8a", logo: "/stations/prizma.png" },
  { id: "st-belgto", name: "Белтехосмотр", icon: "plug", color: "#1e7a45", logo: "/stations/belgto.png" },
  { id: "st-iskra", name: "ISKRA", icon: "plug", color: "#e2b000", logo: "/stations/iskra.png" },
  { id: "st-home", name: "Дом", icon: "house", color: "#8c4a32", logo: "/stations/home.png" },
];
