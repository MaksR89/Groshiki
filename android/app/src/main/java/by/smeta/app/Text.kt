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
    val expr = splitExpr(raw)
    if (expr != null && expr.third.isNotEmpty() && expr.third != "-" && expr.third != ".") {
        computeCents(expr.first, expr.second, expr.third)?.let { return it }
    }
    val plain = if (expr != null && (expr.third.isEmpty() || expr.third == "-" || expr.third == ".")) expr.first else raw
    return operandCents(plain)
}

private fun operandCents(raw: String): Long {
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
    val expr = splitExpr(raw) ?: return formatOperand(raw)
    val right = if (expr.third.isEmpty()) "" else " ${formatOperand(expr.third)}"
    return "${formatOperand(expr.first)} ${expr.second}$right"
}

private fun formatOperand(raw: String): String {
    if (raw.isBlank() || raw == "-" || raw == "." || raw == "-.") return "0"
    val neg = raw.startsWith("-")
    val clean = raw.removePrefix("-")
    val bits = clean.split(".")
    val grouped = (bits[0].ifBlank { "0" }).reversed().chunked(3).joinToString(" ").reversed()
    val body = if (bits.size == 1) grouped else "$grouped,${bits[1]}"
    return if (neg) "−$body" else body
}

private val OPS = setOf("+", "−", "×", "÷")

private fun splitExpr(raw: String): Triple<String, String, String>? {
    var i = if (raw.startsWith("-")) 1 else 0
    while (i < raw.length) {
        val ch = raw[i].toString()
        if (ch in OPS) return Triple(raw.substring(0, i), ch, raw.substring(i + 1))
        i++
    }
    return null
}

private fun computeCents(left: String, op: String, right: String): Long? {
    val a = operandCents(left)
    val b = operandCents(right)
    val result = when (op) {
        "+" -> a + b
        "−" -> a - b
        "×" -> roundDiv(a.toBigDecimal().multiply(b.toBigDecimal()), 100.toBigDecimal())
        "÷" -> if (b == 0L) null else roundDiv(a.toBigDecimal().multiply(100.toBigDecimal()), b.toBigDecimal())
        else -> null
    } ?: return null
    if (kotlin.math.abs(result) > 9_999_999_999L) return null
    return result
}

private fun roundDiv(num: java.math.BigDecimal, den: java.math.BigDecimal): Long =
    num.divide(den, 0, java.math.RoundingMode.HALF_UP).longValueExact()

private fun fromHundredths(cents: Long): String {
    val abs = kotlin.math.abs(cents)
    val whole = abs / 100
    val frac = abs % 100
    val body = when {
        frac == 0L -> whole.toString()
        frac % 10L == 0L -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
    return if (cents < 0) "-$body" else body
}

private fun onActive(raw: String, edit: (String) -> String): String {
    val expr = splitExpr(raw) ?: return edit(raw)
    return expr.first + expr.second + edit(expr.third)
}

private fun applyOp(raw: String, key: String): String {
    val expr = splitExpr(raw)
    if (expr == null || expr.third.isEmpty() || expr.third == "-" || expr.third == ".") {
        val left = expr?.first ?: raw.ifEmpty { "0" }
        return if (key == "=") left else "$left$key"
    }
    val result = computeCents(expr.first, expr.second, expr.third) ?: return raw
    val shown = fromHundredths(result)
    return if (key == "=") shown else "$shown$key"
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
    "⌫" -> onActive(raw) { backspaceRaw(it) }.let { next ->
        val expr = splitExpr(raw)
        if (expr != null && expr.third.isEmpty()) raw.dropLast(1) else next
    }
    "," -> onActive(raw, ::pushDot)
    "±" -> onActive(raw) { value ->
        when {
            value.startsWith("-") -> value.drop(1)
            value.isNotEmpty() -> "-$value"
            else -> "-"
        }
    }
    "+", "−", "×", "÷", "=" -> applyOp(raw, key)
    else -> onActive(raw) { pushDigit(it, key) }
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

fun chargeSpend(charges: List<Charge>, from: String, to: String, accountId: String): Long {
    if (accountId != "all") return 0
    var n = 0L
    for (c in charges) {
        val day = c.at.take(10)
        if (day < from || day > to) continue
        n += c.price
    }
    return n
}
