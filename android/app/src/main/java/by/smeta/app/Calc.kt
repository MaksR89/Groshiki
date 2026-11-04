package by.smeta.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.round

private data class DepositOut(val total: Long, val interest: Long, val paid: Long)
private data class LoanOut(val months: Int, val first: Long, val last: Long, val overpay: Long, val total: Long)

private fun yearsOf(count: Int, unit: String): Double = when (unit) {
    "day" -> count / 365.0
    "month" -> count / 12.0
    else -> count.toDouble()
}

private fun termMonths(count: Int, unit: String): Int = when (unit) {
    "year" -> count * 12
    "day" -> round(count * 12.0 / 365.0).toInt()
    else -> count
}

private fun money(value: Double): Long? {
    if (!value.isFinite() || value > 1_000_000_000_000.0) return null
    return round(value * 100).toLong()
}

private fun depositOut(amount: Long, ratePct: Double, count: Int, unit: String, cap: String, extraCents: Long, extraMonths: Int): DepositOut? {
    if (amount <= 0 || count <= 0 || ratePct < 0) return null
    val principal = amount / 100.0
    val rate = ratePct / 100.0
    val extra = if (extraCents > 0 && extraMonths > 0) extraCents / 100.0 else 0.0
    if (extra > 0) {
        val months = termMonths(count, unit)
        if (months <= 0 || months > 600) return null
        val payMonths = minOf(extraMonths, months)
        val every = when (cap) { "month" -> 1; "quarter" -> 3; "year" -> 12; else -> 0 }
        var balance = principal
        var accrued = 0.0
        for (month in 1..months) {
            accrued += balance * (rate / 12.0)
            if (every > 0 && month % every == 0) {
                balance += accrued
                accrued = 0.0
            }
            if (month <= payMonths) balance += extra
        }
        val cents = money(balance + accrued) ?: return null
        val paid = round(extra * payMonths * 100).toLong()
        return DepositOut(cents, cents - amount - paid, paid)
    }
    val years = yearsOf(count, unit)
    val n = when (cap) { "month" -> 12.0; "quarter" -> 4.0; "year" -> 1.0; else -> 0.0 }
    val total = if (n == 0.0) principal * (1 + rate * years) else principal * (1 + rate / n).pow(n * years)
    val cents = money(total) ?: return null
    return DepositOut(cents, cents - amount, 0)
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
    var extraRaw by remember { mutableStateOf("") }
    var extraMonthsRaw by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("month") }
    var cap by remember { mutableStateOf("month") }
    var loanKind by remember { mutableStateOf("annuity") }
    var field by remember { mutableStateOf("amount") }
    var depositAnswer by remember { mutableStateOf<DepositOut?>(null) }
    var loanAnswer by remember { mutableStateOf<LoanOut?>(null) }
    var miss by remember { mutableStateOf(false) }
    fun wipe() {
        depositAnswer = null
        loanAnswer = null
        miss = false
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 260.dp)) {
            Text("Расчёт", color = Ink, fontFamily = Display, fontSize = 30.sp)
            Text("Считает по кнопке и не пишется в книгу. Год — 365 дней.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
                listOf("deposit" to "Депозит", "loan" to "Кредит").forEach { (id, label) ->
                    Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (mode == id) Surface else Color.Transparent).clickable {
                        if (id == mode) return@clickable
                        mode = id
                        wipe()
                        if (id == "loan") {
                            amountRaw = ""
                            rateRaw = ""
                            termRaw = ""
                            extraRaw = ""
                            extraMonthsRaw = ""
                            unit = "month"
                            field = "amount"
                        } else if (unit == "day") unit = "month"
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
                units.forEach { (id, label) -> Choice(label, unit == id) { unit = id; wipe() } }
            }
            if (mode == "deposit") {
                Text("Капитализация", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("none" to "Без", "month" to "Месяц", "quarter" to "Квартал", "year" to "Год").forEach { (id, label) ->
                        Choice(label, cap == id) { cap = id; wipe() }
                    }
                }
                Text("Ежемесячный взнос", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                AmountButton(extraRaw, field == "extra", "Не обязательно") { field = "extra" }
                Text("Сколько месяцев пополнять", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                AmountButton(extraMonthsRaw, field == "extraMonths", "Не обязательно") { field = "extraMonths" }
                Text("Взнос в конце месяца и не дольше срока.", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("Платежи", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Равными частями", loanKind == "annuity") { loanKind = "annuity"; wipe() }
                    Choice("Остаток уменьшается", loanKind == "diff") { loanKind = "diff"; wipe() }
                }
            }
            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Ink).clickable {
                    val amount = inputToCents(amountRaw)
                    val rate = (parseLooseCents(rateRaw.replace(".", ",")) ?: 0) / 100.0
                    val count = termRaw.filter { it.isDigit() }.toIntOrNull() ?: 0
                    depositAnswer = null
                    loanAnswer = null
                    miss = false
                    if (mode == "deposit") {
                        val extra = inputToCents(extraRaw)
                        val extraMonths = extraMonthsRaw.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val out = depositOut(amount, rate, count, unit, cap, extra, extraMonths)
                        if (out == null) miss = true else depositAnswer = out
                    } else {
                        val out = loanOut(amount, rate, count, if (unit == "day") "month" else unit, loanKind)
                        if (out == null) miss = true else loanAnswer = out
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text("Посчитать", color = Surface, fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            if (miss) Text("Проверьте сумму, ставку и срок.", color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            depositAnswer?.let { deposit ->
                Column(Modifier.padding(top = 16.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp)) {
                    Text(if (cap == "none") "Процент от исходной суммы" else "Процент причисляется к сумме", color = Muted, fontFamily = Body, fontSize = 14.sp)
                    if (deposit.paid > 0) {
                        Text("Пополнено", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                        MoneyText(deposit.paid, size = 28.sp, font = Display)
                    }
                    Text("Доход", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    MoneyText(deposit.interest, size = 28.sp, font = Display)
                    Text("Сумма в конце", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    MoneyText(deposit.total, size = 28.sp, font = Display)
                }
            }
            loanAnswer?.let { loan ->
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
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Desk)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Keypad { key ->
                    wipe()
                    when (field) {
                        "rate" -> rateRaw = applyKey(rateRaw, key)
                        "term" -> if (key != "," && key != "±") termRaw = applyKey(termRaw, key).filter { it.isDigit() }.take(4)
                        "extra" -> extraRaw = applyKey(extraRaw, key)
                        "extraMonths" -> if (key != "," && key != "±") extraMonthsRaw = applyKey(extraMonthsRaw, key).filter { it.isDigit() }.take(4)
                        else -> amountRaw = applyKey(amountRaw, key)
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, click: () -> Unit) {
    Text(label, color = if (on) Surface else Ink, fontFamily = Body, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clip(CircleShape).background(if (on) Ink else Surface).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 8.dp))
}
