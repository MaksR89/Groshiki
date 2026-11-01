package by.smeta.app

object Operators {
    val all = listOf(
        Station("st-malanka", "Malanka", "plug", "#2f9e45", "/stations/malanka.png"),
        Station("st-batteryfly", "BatteryFly", "plug", "#10281f", "/stations/batteryfly.png"),
        Station("st-forevo", "forEVo", "plug", "#242424", "/stations/forevo.png"),
        Station("st-evika", "Evika", "plug", "#6a2d91", "/stations/evika.png"),
        Station("st-zaryadka", "Zaryadka", "plug", "#1a6fd4", "/stations/zaryadka.png"),
        Station("st-united", "United Company", "plug", "#14161c", "/stations/united.png"),
        Station("st-chargego", "Charge&GO", "plug", "#0c6b4c", "/stations/chargego.png"),
        Station("st-csms", "ЦСМС", "plug", "#d64545", "/stations/csms.png"),
        Station("st-evon", "EVON", "plug", "#1497b8", "/stations/evon.png"),
        Station("st-orange", "Orange", "plug", "#f08a12", "/stations/orange.png"),
        Station("st-skat", "SKAT", "plug", "#1aa8c4", "/stations/skat.png"),
        Station("st-prizma", "Prizma", "plug", "#5c4d8a", "/stations/prizma.png"),
        Station("st-belgto", "Белтехосмотр", "plug", "#1e7a45", "/stations/belgto.png"),
        Station("st-iskra", "ISKRA", "plug", "#e2b000", "/stations/iskra.png"),
        Station("st-home", "Дом", "house", "#8c4a32", "/stations/home.png"),
    )
}

private val swatches = listOf("#b6402c", "#1f6b45", "#c4892a", "#2f5573", "#8a4d68", "#3f6f62", "#a15a32", "#4c5d8a", "#6d5a3a", "#2a6f86", "#9a3f4a", "#5c5348")
private val icons = listOf("basket", "fuel", "ellipsis", "wrench", "phone", "bus", "house", "gift", "heart", "car", "coffee", "plug")

fun guessColor(name: String): String {
    var h = 0
    for (ch in name) h = (h * 31 + ch.code) ushr 0
    return swatches[Math.floorMod(h, swatches.size)]
}

fun guessIcon(name: String): String {
    val known = mapOf(
        "продукты" to "basket", "бензин" to "fuel", "проезд" to "bus", "кофе" to "coffee",
        "здоровье" to "heart", "одежда" to "shirt", "подарки" to "gift", "дом" to "house",
    )
    known[name.trim().lowercase()]?.let { return it }
    var h = 0
    for (ch in name) h = (h * 33 + ch.code) ushr 0
    return icons[Math.floorMod(h, icons.size)]
}

fun formatMoney(cents: Long, sign: Boolean = false): String {
    val neg = cents < 0
    val abs = kotlin.math.abs(cents)
    val whole = abs / 100
    val frac = abs % 100
    val grouped = "%,d".format(java.util.Locale("ru", "RU"), whole).replace('\u00a0', ' ')
    val body = if (frac == 0L) grouped else "$grouped,${frac.toString().padStart(2, '0')}"
    val mark = if (neg) "−" else if (sign && cents > 0) "+" else ""
    return mark + body
}

fun formatKwh(hundredths: Long): String {
    val abs = kotlin.math.abs(hundredths)
    val whole = abs / 100
    val frac = abs % 100
    val grouped = "%,d".format(java.util.Locale("ru", "RU"), whole).replace('\u00a0', ' ')
    return "$grouped,${frac.toString().padStart(2, '0')}"
}

fun parseLooseCents(input: String): Long? {
    val clean = input.trim().replace(" ", "").replace("\u00a0", "").replace("−", "-").replace(",", ".")
    if (clean.isEmpty() || clean == "-" || clean == ".") return null
    val n = clean.toDoubleOrNull() ?: return null
    return kotlin.math.round(n * 100).toLong()
}

fun centsToRaw(cents: Long): String {
    val abs = kotlin.math.abs(cents)
    val body = if (abs % 100 == 0L) (abs / 100).toString() else "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    return if (cents < 0) "-$body" else body
}

fun rawToCents(raw: String): Long {
    if (raw.isBlank() || raw == "-" || raw == "." || raw == "-.") return 0
    val neg = raw.startsWith("-")
    val clean = raw.removePrefix("-")
    val parts = clean.split(".")
    val whole = parts[0].toLongOrNull() ?: 0
    val frac = ((parts.getOrNull(1) ?: "") + "00").take(2).toLongOrNull() ?: 0
    val cents = whole * 100 + frac
    return if (neg) -cents else cents
}
