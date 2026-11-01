package by.smeta.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthsOf = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val monthsCap = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
val monthsShort = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

fun todayKey(): String = LocalDate.now().toString()
fun nowStamp(): String = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

fun addDays(key: String, delta: Long): String = LocalDate.parse(key.take(10)).plusDays(delta).toString()
fun addMonths(key: String, delta: Long): String = LocalDate.parse(key.take(10)).withDayOfMonth(1).plusMonths(delta).toString()

fun dayLabel(key: String, today: String = todayKey()): String {
    val day = key.take(10)
    if (day == today) return "Сегодня"
    if (day == addDays(today, -1)) return "Вчера"
    val date = LocalDate.parse(day)
    val base = "${date.dayOfMonth} ${monthsOf[date.monthValue - 1]}"
    return if (date.year == LocalDate.parse(today).year) base else "$base ${date.year}"
}

fun monthTitle(key: String): String {
    val date = LocalDate.parse(key.take(10))
    return "${monthsCap[date.monthValue - 1]} ${date.year}"
}

fun monthRange(key: String): Pair<String, String> {
    val date = LocalDate.parse(key.take(10)).withDayOfMonth(1)
    return date.toString() to date.plusMonths(1).minusDays(1).toString()
}

fun formatMoney(cents: Long, sign: Boolean = false): String {
    val rounded = cents
    val neg = rounded < 0
    val abs = kotlin.math.abs(rounded)
    val whole = abs / 100
    val frac = abs % 100
    val grouped = group(whole)
    val body = if (frac == 0L) grouped else "$grouped,${frac.toString().padStart(2, '0')}"
    val mark = if (neg) "−" else if (sign && rounded > 0) "+" else ""
    return mark + body
}

fun formatKwh(hundredths: Long): String {
    val abs = kotlin.math.abs(hundredths)
    val whole = abs / 100
    val frac = abs % 100
    return "${group(whole)},${frac.toString().padStart(2, '0')}"
}

fun formatCount(n: Int): String = group(n.toLong())

private fun group(n: Long): String = n.toString().reversed().chunked(3).joinToString("\u00a0").reversed()

fun inputToCents(raw: String): Long {
    if (raw.isBlank() || raw == "." || raw == "-") return 0
    val neg = raw.trim().startsWith("-")
    val clean = raw.replace("-", "")
    val bits = clean.split(".")
    val whole = bits[0].toLongOrNull() ?: 0
    val fracSrc = bits.getOrElse(1) { "" }
    val frac = (fracSrc + "00").take(2).toLongOrNull() ?: 0
    var cents = whole * 100 + frac
    if (fracSrc.length > 2 && fracSrc[2] >= '5') cents += 1
    return if (neg) -cents else cents
}

fun centsToInput(cents: Long): String {
    val abs = kotlin.math.abs(cents)
    val body = if (abs % 100 == 0L) (abs / 100).toString() else "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    return if (cents < 0) "-$body" else body
}

fun formatRawInput(raw: String): String {
    if (raw.isBlank() || raw == "-" || raw == "." || raw == "-.") return "0"
    val neg = raw.startsWith("-")
    val clean = raw.removePrefix("-")
    val bits = clean.split(".")
    val grouped = (bits[0].ifBlank { "0" }).reversed().chunked(3).joinToString(" ").reversed()
    val body = if (bits.size == 1) grouped else "$grouped,${bits[1]}"
    return if (neg) "−$body" else body
}

fun parseLooseCents(input: String): Long? {
    val t = input.trim().replace("\u00a0", "").replace(" ", "").replace("−", "-").replace(",", ".")
    if (!Regex("^-?\\d+(\\.\\d+)?$").matches(t)) return null
    return inputToCents(t)
}

fun pushDigit(raw: String, digit: String): String {
    val next = if (raw == "0") digit else raw + digit
    val frac = next.substringAfter(".", "")
    if (next.contains(".") && frac.length > 2) return raw
    if (next.replace(".", "").replace("-", "").length > 10) return raw
    return next
}

fun pushDot(raw: String): String = when {
    raw.isEmpty() -> "0."
    raw.contains(".") -> raw
    else -> "$raw."
}

fun backspaceRaw(raw: String): String {
    val next = raw.dropLast(1)
    return if (next == "-") "" else next
}

fun applyKey(raw: String, key: String): String = when (key) {
    "⌫" -> backspaceRaw(raw)
    "," -> pushDot(raw)
    "±" -> when {
        raw.startsWith("-") -> raw.drop(1)
        raw.isNotEmpty() -> "-$raw"
        else -> "-"
    }
    else -> pushDigit(raw, key)
}

fun inAccount(entry: Entry, accountId: String) = accountId == "all" || entry.accountId == accountId || entry.toAccountId == accountId

fun sumKind(entries: List<Entry>, kind: String, from: String, to: String, accountId: String): Long {
    var n = 0L
    for (e in entries) {
        if (e.kind != kind) continue
        val day = e.at.take(10)
        if (day < from || day > to) continue
        if (accountId != "all" && e.accountId != accountId) continue
        n += e.amount
    }
    return n
}
