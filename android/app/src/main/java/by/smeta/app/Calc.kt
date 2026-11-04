package by.smeta.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.round

private data class DepositOut(val total: Long, val interest: Long)
private data class LoanOut(val months: Int, val first: Long, val last: Long, val overpay: Long, val total: Long)

private fun yearsOf(count: Int, unit: String): Double = when (unit) {
    "day" -> count / 365.0
    "month" -> count / 12.0
    else -> count.toDouble()
}

private fun money(value: Double): Long? {
    if (!value.isFinite() || value > 1_000_000_000_000.0) return null
    return round(value * 100).toLong()
}

private fun depositOut(amount: Long, ratePct: Double, count: Int, unit: String, cap: String): DepositOut? {
    if (amount <= 0 || count <= 0 || ratePct < 0) return null
    val years = yearsOf(count, unit)
    val principal = amount / 100.0
    val rate = ratePct / 100.0
    val n = when (cap) { "month" -> 12.0; "quarter" -> 4.0; "year" -> 1.0; else -> 0.0 }
    val total = if (n == 0.0) principal * (1 + rate * years) else principal * (1 + rate / n).pow(n * years)
    val cents = money(total) ?: return null
    return DepositOut(cents, cents - amount)
}

private fun loanOut(amount: Long, ratePct: Double, count: Int, unit: String, kind: String): LoanOut? {
    if (amount <= 0 || count <= 0 || ratePct < 0) return null
    val months = if (unit == "year") count * 12 else count
    if (months <= 0 || months > 600) return null
    val principal = amount / 100.0
    val monthly = ratePct / 100.0 / 12.0
    val part = principal / months
    if (kind == "annuity") {
        val payment = if (monthly == 0.0) part else principal * monthly * (1 + monthly).pow(months.toDouble()) / ((1 + monthly).pow(months.toDouble()) - 1)
        val pay = money(payment) ?: return null
        val total = pay * months
        return LoanOut(months, pay, pay, total - amount, total)
    }
    var rest = principal
    var total = 0.0
    var first = 0.0
    var last = 0.0
    for (i in 0 until months) {
        val pay = part + rest * monthly
        if (i == 0) first = pay
        last = pay
        total += pay
        rest -= part
    }
    val firstCents = money(first) ?: return null
    val lastCents = money(last) ?: return null
    val totalCents = money(total) ?: return null
    return LoanOut(months, firstCents, lastCents, totalCents - amount, totalCents)
}

@Composable
fun CalcScreen() {
    var mode by remember { mutableStateOf("deposit") }
    var amountRaw by remember { mutableStateOf("") }
    var rateRaw by remember { mutableStateOf("") }
    var termRaw by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("month") }
    var cap by remember { mutableStateOf("month") }
    var loanKind by remember { mutableStateOf("annuity") }
    var field by remember { mutableStateOf("amount") }
    val amount = inputToCents(amountRaw)
    val rate = (parseLooseCents(rateRaw.replace(".", ",")) ?: 0) / 100.0
    val count = termRaw.filter { it.isDigit() }.toIntOrNull() ?: 0
    val deposit = if (mode == "deposit") depositOut(amount, rate, count, unit, cap) else null
    val loan = if (mode == "loan") loanOut(amount, rate, count, if (unit == "day") "month" else unit, loanKind) else null
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Расчёт", color = Ink, fontFamily = Display, fontSize = 30.sp)
            Text("Не записывается в книгу. Год в расчёте — 365 дней.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
                listOf("deposit" to "Депозит", "loan" to "Кредит").forEach { (id, label) ->
                    Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (mode == id) Surface else Color.Transparent).clickable {
                        mode = id
                        if (id == "loan" && unit == "day") unit = "month"
                    }, contentAlignment = Alignment.Center) {
                        Text(label, color = if (mode == id) Ink else Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = if (mode == id) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            Text("Сумма", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            AmountButton(amountRaw, field == "amount", "0") { field = "amount" }
            Text("Ставка, % годовых", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            AmountButton(rateRaw, field == "rate", "0") { field = "rate" }
            Text("Срок", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            AmountButton(termRaw, field == "term", "0") { field = "term" }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val units = if (mode == "deposit") listOf("day" to "Дни", "month" to "Месяцы", "year" to "Годы") else listOf("month" to "Месяцы", "year" to "Годы")
                units.forEach { (id, label) -> Choice(label, unit == id) { unit = id } }
            }
            if (mode == "deposit") {
                Text("Капитализация", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("none" to "Без", "month" to "Месяц", "quarter" to "Квартал", "year" to "Год").forEach { (id, label) ->
                        Choice(label, cap == id) { cap = id }
                    }
                }
            } else {
                Text("Платежи", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Равными частями", loanKind == "annuity") { loanKind = "annuity" }
                    Choice("Остаток уменьшается", loanKind == "diff") { loanKind = "diff" }
                }
            }
            if (deposit != null) {
                Column(Modifier.padding(top = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp)) {
                    Text(if (cap == "none") "Процент от исходной суммы" else "Процент причисляется к сумме", color = Muted, fontFamily = Body, fontSize = 14.sp)
                    Text("Доход", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    MoneyText(deposit.interest, size = 28.sp, font = Display)
                    Text("Сумма в конце", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    MoneyText(deposit.total, size = 28.sp, font = Display)
                }
            }
            if (loan != null) {
                Column(Modifier.padding(top = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp)) {
                    Text(if (loanKind == "annuity") "Платёж каждый месяц" else "Платёж уменьшается", color = Muted, fontFamily = Body, fontSize = 14.sp)
                    if (loanKind == "annuity") {
                        Text("Платёж", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                        MoneyText(loan.first, size = 28.sp, font = Display)
                    } else {
                        Text("Первый платёж", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                        MoneyText(loan.first, size = 28.sp, font = Display)
                        Text("Последний платёж", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                        MoneyText(loan.last, size = 28.sp, font = Display)
                    }
                    Text("Переплата", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    MoneyText(loan.overpay, size = 28.sp, font = Display)
                    Text("Всего к выплате", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    MoneyText(loan.total, size = 28.sp, font = Display)
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            Keypad { key ->
                when (field) {
                    "rate" -> rateRaw = applyKey(rateRaw, key)
                    "term" -> if (key != "," && key != "±") termRaw = applyKey(termRaw, key).filter { it.isDigit() }.take(4)
                    else -> amountRaw = applyKey(amountRaw, key)
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, click: () -> Unit) {
    Text(label, color = if (on) Surface else Ink, fontFamily = Body, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clip(CircleShape).background(if (on) Ink else Surface).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 8.dp))
}
