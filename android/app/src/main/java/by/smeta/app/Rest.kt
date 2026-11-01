package by.smeta.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun ChargesScreen(repo: Repo, draftKey: Int) {
    val book = repo.book
    var open by remember { mutableStateOf(false) }
    var editStation by remember { mutableStateOf<Station?>(null) }
    var editCharge by remember { mutableStateOf<Charge?>(null) }
    var creating by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(draftKey) { if (draftKey > 0) creating = true }
    val month = todayKey().take(7)
    val monthRows = book.charges.filter { it.at.startsWith(month) }
    val groups = book.charges.sortedByDescending { it.at }.groupBy { it.at.take(10) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Зарядки", color = Ink, fontFamily = Display, fontSize = 30.sp)
        Row(Modifier.fillMaxWidth().height(44.dp).clickable { open = !open }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Станции", color = Muted, fontFamily = Body, fontSize = 14.sp)
            LineIcon("chevron-down", tint = Muted, size = 20.dp, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) {
            val cells = book.stations + Station("", "Новая", "plug", "#5c5348", "")
            cells.chunked(3).forEach { row ->
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { station ->
                        Column(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(16.dp)).background(if (station.id.isEmpty()) Chip else Surface).clickable {
                            editStation = if (station.id.isEmpty()) Station("", "", "plug", SWATCHES[0], "") else station
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (station.id.isNotEmpty()) StationMark(station, 32.dp)
                            Text(if (station.id.isEmpty()) "Новая" else station.name, color = Ink, fontFamily = Body, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp, 12.dp)) {
                Text("Этот месяц", color = Muted, fontFamily = Body, fontSize = 14.sp)
                Text("${formatKwh(monthRows.sumOf { it.kwh })} кВт·ч", color = Ink, fontFamily = Display, fontSize = 24.sp)
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp, 12.dp)) {
                Text("Цена", color = Muted, fontFamily = Body, fontSize = 14.sp)
                MoneyText(monthRows.sumOf { it.price }, size = 24.sp, font = Display)
            }
        }
        if (groups.isEmpty()) Text("Зарядок пока нет. Нажмите «Добавить зарядку».", color = Muted, fontFamily = Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 32.dp).fillMaxWidth())
        groups.forEach { (day, rows) ->
            Text(dayLabel(day), color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
            rows.forEach { row ->
                val station = book.stations.find { it.id == row.stationId }
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable { editCharge = row }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (station != null) StationMark(station, 24.dp) else LineIcon("plug", size = 16.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(station?.name ?: "Станция", color = Ink, fontFamily = Body, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("${row.at.drop(11).take(5)} · ${formatKwh(row.kwh)} кВт·ч", color = Muted, fontFamily = Body, fontSize = 14.sp)
                    }
                    MoneyText(row.price)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    editStation?.let { StationSheet(repo, it) { editStation = null } }
    if (creating || editCharge != null) ChargeSheet(repo, if (creating) null else editCharge) { creating = false; editCharge = null }
}

@Composable
private fun StationSheet(repo: Repo, station: Station, onClose: () -> Unit) {
    var name by remember { mutableStateOf(station.name) }
    var icon by remember { mutableStateOf(station.icon.ifBlank { "plug" }) }
    var color by remember { mutableStateOf(station.color.ifBlank { SWATCHES[0] }) }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet(if (station.id.isEmpty()) "Новая станция" else "Станция", name.isNotBlank(), onClose, {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) error = "Нужно название."
        else if (station.id.isEmpty()) { repo.addStation(trimmed, icon, color, ""); onClose() }
        else { repo.updateStation(station.id, trimmed, icon, color); onClose() }
    }, Ink) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (station.logo.isNotBlank()) Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { StationMark(station, 80.dp) }
            Field(name, { name = it }, "Название")
            Swatches(color) { color = it }
            IconGrid(icon, parseHex(color)) { icon = it }
            error?.let { Text(it, color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
            if (station.id.isNotEmpty()) Text("Удалить станцию", color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp).clickable {
                val message = repo.removeStation(station.id)
                if (message != null) error = message else onClose()
            })
        }
    }
}

@Composable
private fun ChargeSheet(repo: Repo, charge: Charge?, onClose: () -> Unit) {
    val book = repo.book
    val context = LocalContext.current
    var stationId by remember { mutableStateOf(charge?.stationId ?: book.stations.firstOrNull()?.id ?: "") }
    var kwhRaw by remember { mutableStateOf(if (charge == null) "" else centsToInput(charge.kwh)) }
    var rateRaw by remember { mutableStateOf(if (charge?.rate != null && charge.rate != 0L) centsToInput(charge.rate) else "") }
    var date by remember { mutableStateOf((charge?.at ?: nowStamp()).take(10)) }
    var time by remember { mutableStateOf((charge?.at ?: nowStamp()).drop(11).take(5)) }
    var field by remember { mutableStateOf("kwh") }
    var listOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val station = book.stations.find { it.id == stationId }
    val kwh = parseLooseCents(kwhRaw.replace(".", ",")) ?: 0
    val rate = parseLooseCents(rateRaw.replace(".", ",")) ?: 0
    val total = if (kwh > 0 && rate > 0) (kwh * rate) / 100 else 0
    Sheet(if (charge == null) "Новая зарядка" else "Редактирование", true, onClose, {
        val k = parseLooseCents(kwhRaw.replace(".", ","))
        val r = parseLooseCents(rateRaw.replace(".", ",")) ?: 0
        when {
            stationId.isBlank() -> error = "Выберите станцию."
            k == null || k <= 0 -> error = "Укажите, сколько кВт·ч."
            r < 0 -> error = "Цена за кВт·ч не может быть меньше нуля."
            else -> {
                val next = Charge(charge?.id ?: newId("ch"), stationId, k, r, (k * r) / 100, "${date}T${time.ifBlank { "12:00" }}:00")
                if (charge == null) repo.addCharge(next) else repo.updateCharge(next)
                onClose()
            }
        }
    }, Ink) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Станция", color = Muted, fontFamily = Body, fontSize = 14.sp)
            Row(Modifier.padding(top = 4.dp).fillMaxWidth().height(56.dp).border(1.dp, Line, RoundedCornerShape(12.dp)).background(Surface, RoundedCornerShape(12.dp)).clickable { listOpen = !listOpen }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (station != null) StationMark(station, 36.dp) else Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Chip))
                Text(station?.name ?: "Выберите", color = Ink, fontFamily = Body, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1)
                LineIcon("chevron-down", tint = Muted, size = 20.dp, modifier = Modifier.rotate(if (listOpen) 180f else 0f))
            }
            if (listOpen) Column(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Surface)) {
                book.stations.forEach { item ->
                    Row(Modifier.fillMaxWidth().height(48.dp).background(if (item.id == stationId) Chip else Color.Transparent).clickable { stationId = item.id; listOpen = false }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        StationMark(item, 32.dp)
                        Text(item.name, color = Ink, fontFamily = Body, modifier = Modifier.padding(start = 12.dp), maxLines = 1)
                    }
                }
            }
            Text("Сколько, кВт·ч", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            AmountButton(kwhRaw, field == "kwh", "0") { field = "kwh" }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Цена за кВт·ч, ", color = Muted, fontFamily = Body, fontSize = 14.sp)
                BynMark(tint = Muted, height = 12.dp)
            }
            AmountButton(rateRaw, field == "rate", "0,73") { field = "rate" }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerBox(date, Modifier.weight(1f)) { pickDate(context, date) { date = it } }
                PickerBox(time, Modifier.weight(1f)) { pickTime(context, time) { time = it } }
            }
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp)) {
                Text("Цена", color = Muted, fontFamily = Body, fontSize = 14.sp)
                MoneyText(total, size = 36.sp, font = Display)
                Row {
                    Text("${formatKwh(kwh)} кВт·ч × ", color = Muted, fontFamily = Body, fontSize = 14.sp)
                    MoneyText(rate, color = Muted, size = 14.sp)
                }
            }
            error?.let { Text(it, color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
            if (charge != null) Text(if (confirm) "Точно удалить" else "Удалить зарядку", color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp).clickable {
                if (!confirm) confirm = true else { repo.removeCharge(charge.id); onClose() }
            })
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.padding(16.dp)) {
            Keypad { key -> if (field == "kwh") kwhRaw = applyKey(kwhRaw, key) else rateRaw = applyKey(rateRaw, key) }
        }
    }
}

@Composable
fun ReportScreen(repo: Repo, onOpen: (Entry) -> Unit) {
    val book = repo.book
    val today = remember { todayKey() }
    var mode by remember { mutableStateOf("month") }
    var anchor by remember { mutableStateOf(today) }
    var accountId by remember { mutableStateOf("all") }
    var kindChoice by remember { mutableStateOf<String?>(null) }
    var drill by remember { mutableStateOf<String?>(null) }
    val fromTo = when (mode) {
        "day" -> anchor.take(10) to anchor.take(10)
        "year" -> "${anchor.take(4)}-01-01" to "${anchor.take(4)}-12-31"
        else -> monthRange(anchor)
    }
    val from = fromTo.first
    val to = fromTo.second
    val spent = sumKind(book.entries, "expense", from, to, accountId)
    val earned = sumKind(book.entries, "income", from, to, accountId)
    val periodCharges = book.charges.filter { val d = it.at.take(10); d >= from && d <= to }
    val chargeKwh = periodCharges.sumOf { it.kwh }
    val chargeCost = periodCharges.sumOf { it.price }
    val kind = kindChoice ?: if (earned > spent) "income" else "expense"
    val total = if (kind == "charge") chargeCost else if (kind == "income") earned else spent
    val slices = if (kind == "charge") {
        periodCharges.groupBy { it.stationId }.map { (id, rows) ->
            val station = book.stations.find { it.id == id }
            Slice(id, station?.name ?: "Станция", rows.sumOf { it.price }, rows.sumOf { it.kwh }, station?.color ?: "#6f675e", station?.logo ?: "")
        }.sortedByDescending { it.value }
    } else {
        val rows = book.entries.filter { it.kind == kind && it.categoryId != null && it.at.take(10) in from..to && (accountId == "all" || it.accountId == accountId) }
            .groupBy { it.categoryId!! }.map { (id, list) ->
                val cat = book.categories.find { it.id == id }
                Slice(id, cat?.name ?: "Без статьи", list.sumOf { it.amount }, 0, cat?.color ?: "#6f675e", "")
            }.sortedByDescending { it.value }
        if (rows.size <= 8) rows else rows.take(8) + Slice("__rest", "Остальные", rows.drop(8).sumOf { it.value }, 0, "#6f675e", "")
    }
    val title = when (mode) {
        "day" -> anchor.take(10).split("-").reversed().joinToString(".")
        "year" -> anchor.take(4)
        else -> monthTitle(anchor)
    }
    val atEnd = when (mode) {
        "day" -> anchor.take(10) >= today
        "year" -> anchor.take(4) >= today.take(4)
        else -> anchor.take(7) >= today.take(7)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
            listOf("day" to "День", "month" to "Месяц", "year" to "Год").forEach { (id, label) ->
                Box(Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (mode == id) Surface else Color.Transparent).clickable { mode = id; anchor = today; drill = null }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (mode == id) Ink else Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = if (mode == id) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Round("‹") { drill = null; anchor = shift(mode, anchor, -1) }
            Text(title, color = Ink, fontFamily = Display, fontSize = 24.sp)
            Round("›", !atEnd) { drill = null; anchor = shift(mode, anchor, 1) }
        }
        if (book.accounts.size > 1) Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("Все", accountId == "all") { accountId = "all"; drill = null }
            book.accounts.forEach { Mini(it.name, accountId == it.id) { accountId = it.id; drill = null } }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Потрачено", spent, kind == "expense", Modifier.weight(1f)) { kindChoice = "expense"; drill = null }
            Stat("Доходы", earned, kind == "income", Modifier.weight(1f)) { kindChoice = "income"; drill = null }
        }
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (kind == "charge") Ink else Surface).clickable { kindChoice = "charge"; drill = null }.padding(16.dp, 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Зарядки", color = if (kind == "charge") Chip else Muted, fontFamily = Body, fontSize = 14.sp)
                Text("${formatKwh(chargeKwh)} кВт·ч", color = if (kind == "charge") Surface else Ink, fontFamily = Display, fontSize = 24.sp)
            }
            MoneyText(chargeCost, color = if (kind == "charge") Chip else Muted, size = 14.sp)
        }
        if (kind != "charge") Text("Разница ${formatMoney(earned - spent, true)}", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        val drillId = drill
        if (drillId != null) {
            Text("← К диаграмме", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp).clickable { drill = null })
            Text(slices.find { it.id == drillId }?.name ?: "Статья", color = Ink, fontFamily = Display, fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
            if (kind == "charge") {
                periodCharges.filter { it.stationId == drillId }.sortedByDescending { it.at }.forEach { row ->
                    Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(row.at.take(10).split("-").reversed().joinToString("."), color = Muted, fontSize = 14.sp, fontFamily = Body); Text("${formatKwh(row.kwh)} кВт·ч", color = Ink, fontFamily = Body) }
                        MoneyText(row.price)
                    }
                }
            } else {
                val rest = if (drillId == "__rest") slices.filter { it.id != "__rest" }.map { it.id }.toSet() else emptySet()
                val rows = book.entries.filter {
                    it.kind == kind && it.categoryId != null && it.at.take(10) in from..to && (accountId == "all" || it.accountId == accountId) &&
                        (if (drillId == "__rest") it.categoryId !in rest else it.categoryId == drillId)
                }.asReversed()
                DayList(rows, book, onOpen)
            }
        } else if (total == 0L) {
            Text(if (kind == "charge") "Зарядок в этом периоде нет." else "В этом периоде пусто.", color = Muted, textAlign = TextAlign.Center, fontFamily = Body, modifier = Modifier.padding(top = 40.dp).fillMaxWidth())
        } else {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    var start = -90f
                    val stroke = 28.dp.toPx()
                    slices.forEach { slice ->
                        val sweep = if (total == 0L) 0f else slice.value.toFloat() / total * 360f
                        drawArc(parseHex(slice.color), start, sweep.coerceAtLeast(0.4f), false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (kind == "charge") Text(formatKwh(chargeKwh), color = Ink, fontFamily = Display, fontSize = 20.sp) else MoneyText(total, size = 20.sp, font = Display)
                    Text(if (kind == "charge") formatMoney(total) else if (slices.size == 1) "1 статья" else "${slices.size} статей", color = Muted, fontFamily = Body, fontSize = 12.sp)
                }
            }
            if (mode == "year") YearBars(book, kind, anchor.take(4).toInt(), accountId)
            slices.forEach { slice ->
                val pct = if (total == 0L) 0 else (slice.value * 100 / total).toInt()
                Row(Modifier.fillMaxWidth().clickable { drill = slice.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (slice.logo.isNotBlank()) StationMark(Station(slice.id, slice.name, "plug", slice.color, slice.logo), 32.dp)
                    else Box(Modifier.size(12.dp).clip(CircleShape).background(parseHex(slice.color)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(slice.name, color = Ink, fontFamily = Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            MoneyText(slice.value)
                        }
                        if (kind == "charge") Text("${formatKwh(slice.kwh)} кВт·ч", color = Muted, fontFamily = Body, fontSize = 14.sp)
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Chip)) {
                            Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).background(parseHex(slice.color)))
                        }
                    }
                    Text("$pct%", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private data class Slice(val id: String, val name: String, val value: Long, val kwh: Long, val color: String, val logo: String)

@Composable
private fun YearBars(book: Book, kind: String, year: Int, accountId: String) {
    val values = LongArray(12)
    if (kind == "charge") book.charges.filter { it.at.startsWith(year.toString()) }.forEach { values[it.at.substring(5, 7).toInt() - 1] += it.price }
    else book.entries.filter { it.kind == kind && it.at.startsWith(year.toString()) && (accountId == "all" || it.accountId == accountId) }.forEach { values[it.at.substring(5, 7).toInt() - 1] += it.amount }
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val color = if (kind == "charge") Color(0xFF1A6FD4) else if (kind == "income") Income else Expense
    Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height((80f * value / max).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(color))
                Text(monthsShort[i], color = Muted, fontFamily = Body, fontSize = 9.sp)
            }
        }
    }
}

private fun shift(mode: String, anchor: String, delta: Long) = when (mode) {
    "day" -> addDays(anchor, delta)
    "year" -> addMonths(anchor, delta * 12)
    else -> addMonths(anchor, delta)
}

@Composable
private fun Round(label: String, enabled: Boolean = true, click: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Surface).clickable(enabled = enabled, onClick = click), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) Ink else Muted.copy(alpha = 0.3f), fontSize = 22.sp)
    }
}

@Composable
private fun Mini(label: String, on: Boolean, click: () -> Unit) {
    Text(label, color = if (on) Surface else Ink, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).background(if (on) Ink else Surface).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun Stat(label: String, cents: Long, on: Boolean, modifier: Modifier, click: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Ink else Surface).clickable(onClick = click).padding(16.dp, 12.dp)) {
        Text(label, color = if (on) Chip else Muted, fontFamily = Body, fontSize = 14.sp)
        MoneyText(cents, color = if (on) Surface else Ink, size = 24.sp, font = Display)
    }
}

@Composable
fun BookScreen(repo: Repo, onAccounts: () -> Unit, onCategories: () -> Unit) {
    val book = repo.book
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else "file"
        } ?: "file"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val result = if (name.endsWith(".json", true)) importBackup(bytes.toString(Charsets.UTF_8)) else importMoneyOk(name, bytes)
        when (result) {
            is ImportResult.Ok -> { repo.replace(result.book); message = if (name.endsWith(".json", true)) "Копия загружена." else "Загружено ${formatCount(result.book.entries.size)} операций. Данные как в файле." }
            is ImportResult.Err -> message = result.message
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pending = pendingExport
        if (uri != null && pending != null) context.contentResolver.openOutputStream(uri)?.use { it.write(pending.toByteArray()) }
        pendingExport = null
    }
    val first = book.entries.firstOrNull()?.at?.take(4)
    val last = book.entries.lastOrNull()?.at?.take(4)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Книга", color = Ink, fontFamily = Display, fontSize = 30.sp)
        Text(
            if (book.entries.isEmpty()) "Пока пусто. Загрузите выгрузку Деньги OK или добавьте операцию."
            else "${formatCount(book.entries.size)} операций${if (first != null && last != null) ", $first–$last" else ""}.",
            color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp),
        )
        SwitchRow("Показывать остаток", book.showBalance) { repo.update { it.copy(showBalance = !it.showBalance) } }
        Text("Это текущий остаток счёта, не оборот за всё время.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        Text("Расходы и доходы всегда в меню. Остальные разделы можно выключить.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
        SwitchRow("Зарядки", book.showCharges) { repo.update { it.copy(showCharges = !it.showCharges) } }
        SwitchRow("Отчёт", book.showReport) { repo.update { it.copy(showReport = !it.showReport) } }
        SwitchRow("Другое", book.showReserve) {
            if (book.showReserve) repo.update { it.copy(showReserve = false) }
            else if (book.reserveHash.isEmpty()) pass = "create"
            else repo.update { it.copy(showReserve = true) }
        }
        Text("«Другое» стоит в меню и открывается паролем.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        if (book.reserveHash.isNotEmpty()) BookRow("Сменить пароль «Другое»") { pass = "change" }
        BookRow("Счета") { onAccounts() }
        BookRow("Статьи") { onCategories() }
        BookRow("Скачать копию JSON") { pendingExport = repo.book.toJson().toString(); save.launch("groshyki.json") }
        BookRow("Скачать CSV") { pendingExport = toCsv(repo.book); save.launch("groshyki.csv") }
        Text("Лучше Excel из Деньги OK: в нём есть время операции. CSV тоже подойдёт, но без времени суток.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        BookRow("Загрузить выгрузку MoneyOK") { open.launch(arrayOf("*/*")) }
        BookRow("Загрузить копию JSON") { open.launch(arrayOf("application/json", "text/plain")) }
        message?.let { Text(it, color = Ink, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Text("Очистить книгу", color = Expense, fontFamily = Body, modifier = Modifier.padding(top = 24.dp).clickable { confirm = true })
        if (confirm) Column(Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp)) {
            Text("Удалятся все операции, счета и статьи на этом устройстве.", color = Ink, fontFamily = Body, fontSize = 14.sp)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Удалить", color = Surface, modifier = Modifier.clip(CircleShape).background(Expense).clickable { repo.clear(); confirm = false; message = "Книга очищена." }.padding(horizontal = 16.dp, vertical = 10.dp))
                Text("Отмена", color = Ink, modifier = Modifier.clickable { confirm = false }.padding(10.dp))
            }
        }
        Text("Версия 1.5.1", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
    }
    if (pass != null) Column(Modifier.fillMaxSize().background(Desk)) {
        LockBody(repo, if (pass == "create") "create" else "change", "Другое") { pass = null }
    }
}

private var pendingExport: String? = null

private fun toCsv(book: Book): String {
    val lines = mutableListOf("Дата;Сумма;Валюта;Счёт;Статья;Группа;Заметка")
    book.entries.forEach { e ->
        val cat = book.categories.find { it.id == e.categoryId }
        val acc = book.accounts.find { it.id == e.accountId }?.name ?: ""
        val sign = if (e.kind == "income") "" else "-"
        lines.add("${e.at};$sign${formatMoney(e.amount)};BYN;$acc;${cat?.name ?: ""};${cat?.group ?: ""};${e.note}")
    }
    return lines.joinToString("\n")
}

@Composable
private fun SwitchRow(label: String, on: Boolean, click: () -> Unit) {
    Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Surface).clickable(onClick = click).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontFamily = Body)
        Box(Modifier.size(48.dp, 28.dp).clip(CircleShape).background(if (on) Ink else Chip).padding(4.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Surface))
        }
    }
}

@Composable
private fun BookRow(label: String, click: () -> Unit) {
    Text(label, color = Ink, fontFamily = Body, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable(onClick = click).padding(16.dp))
}

@Composable
fun AccountsScreen(repo: Repo, onBack: () -> Unit) {
    val book = repo.book
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var pad by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("← Назад", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBack))
        Text("Счета", color = Ink, fontFamily = Display, fontSize = 30.sp, modifier = Modifier.padding(top = 8.dp))
        book.accounts.forEach { account ->
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface).padding(20.dp)) {
                Text(account.name, color = Muted, fontFamily = Body, fontSize = 14.sp)
                if (book.showBalance) MoneyText(accountBalance(account, book.entries), size = 36.sp, font = Display, modifier = Modifier.padding(top = 4.dp))
                Text("Начальный остаток", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                var raw by remember(account.id) { mutableStateOf(centsToInput(account.opening)) }
                var open by remember(account.id) { mutableStateOf(false) }
                AmountButton(raw, open, "0") { open = !open }
                if (open) Column(Modifier.padding(top = 8.dp)) { Keypad(signed = true) { key -> raw = applyKey(raw, key); parseLooseCents(raw)?.let { repo.updateAccount(account.id, opening = it) } } }
            }
        }
        Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface).padding(20.dp)) {
            Text("Новый счёт", color = Ink, fontFamily = Body, fontWeight = FontWeight.Medium)
            Field(name, { name = it }, "Название")
            Text("Начальный остаток", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            AmountButton(opening, pad, "0") { pad = true }
            if (pad) Column(Modifier.padding(top = 8.dp)) { Keypad(signed = true) { opening = applyKey(opening, it) } }
            Text("Добавить", color = Surface, fontFamily = Body, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp).clip(CircleShape).background(Ink).clickable {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) return@clickable
                val cents = if (opening.isBlank()) 0L else parseLooseCents(opening)
                if (cents == null) error = "Не понял начальный остаток."
                else { repo.addAccount(trimmed, cents); name = ""; opening = ""; error = null }
            }.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        error?.let { Text(it, color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CategoriesScreen(repo: Repo, onBack: () -> Unit) {
    val book = repo.book
    var side by remember { mutableStateOf("expense") }
    var editing by remember { mutableStateOf<Category?>(null) }
    val shown = book.categories.filter { it.kind == side || it.kind == "both" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("← Назад", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBack))
        Text("Статьи", color = Ink, fontFamily = Display, fontSize = 30.sp, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
            listOf("expense" to "Расходы", "income" to "Доходы").forEach { (id, label) ->
                Box(Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (side == id) Surface else Color.Transparent).clickable { side = id }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (side == id) Ink else Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = if (side == id) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        (shown + Category("", "Новая", side, "", "ellipsis", SWATCHES[0])).chunked(4).forEach { row ->
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cat ->
                    Column(Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(16.dp)).background(if (cat.id.isEmpty()) Chip else Surface).clickable {
                        editing = if (cat.id.isEmpty()) Category("", "", side, "", "ellipsis", SWATCHES[0]) else cat
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (cat.id.isNotEmpty()) LineIcon(cat.icon, tint = parseHex(cat.color), size = 24.dp)
                        Text(if (cat.id.isEmpty()) "Новая" else cat.name, color = Ink, fontFamily = Body, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    editing?.let { CategorySheet(repo, it) { editing = null } }
}

@Composable
private fun CategorySheet(repo: Repo, category: Category, onClose: () -> Unit) {
    var name by remember { mutableStateOf(category.name) }
    var group by remember { mutableStateOf(category.group) }
    var kind by remember { mutableStateOf(category.kind) }
    var icon by remember { mutableStateOf(category.icon) }
    var color by remember { mutableStateOf(category.color) }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet(if (category.id.isEmpty()) "Новая статья" else "Статья", name.isNotBlank(), onClose, {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) error = "Нужно название."
        else if (category.id.isEmpty()) { repo.addCategory(trimmed, kind, group.trim(), icon, color); onClose() }
        else { repo.updateCategory(category.id, Category(category.id, trimmed, kind, group.trim(), icon, color)); onClose() }
    }, Ink) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Field(name, { name = it }, "Название")
            Field(group, { group = it }, "Группа, если нужна")
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
                listOf("expense" to "Расход", "income" to "Доход", "both" to "Обе").forEach { (id, label) ->
                    Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (kind == id) Surface else Color.Transparent).clickable { kind = id }, contentAlignment = Alignment.Center) {
                        Text(label, color = if (kind == id) Ink else Muted, fontSize = 14.sp, fontFamily = Body)
                    }
                }
            }
            Swatches(color) { color = it }
            IconGrid(icon, parseHex(color)) { icon = it }
            error?.let { Text(it, color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
            if (category.id.isNotEmpty()) Text("Удалить статью", color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp).clickable {
                val message = repo.removeCategory(category.id)
                if (message != null) error = message else onClose()
            })
        }
    }
}

@Composable
fun Swatches(color: String, onPick: (String) -> Unit) {
    Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SWATCHES.forEach { swatch ->
            Box(Modifier.size(36.dp).clip(CircleShape).background(parseHex(swatch)).then(if (swatch == color) Modifier.border(2.dp, Ink, CircleShape) else Modifier).clickable { onPick(swatch) })
        }
    }
}

@Composable
fun IconGrid(icon: String, tint: Color, onPick: (String) -> Unit) {
    ICON_KEYS.chunked(6).forEach { row ->
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { key ->
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (icon == key) Ink else Chip).clickable { onPick(key) }, contentAlignment = Alignment.Center) {
                    LineIcon(key, tint = if (icon == key) Surface else tint, size = 20.dp)
                }
            }
            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(value, onChange, textStyle = fieldStyle(), cursorBrush = caret, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(44.dp).border(1.dp, Line, RoundedCornerShape(12.dp)).background(Surface, RoundedCornerShape(12.dp)).padding(12.dp), decorationBox = { inner ->
        if (value.isEmpty()) Text(placeholder, color = Muted, fontFamily = Body)
        inner()
    })
}

@Composable
private fun PickerBox(value: String, modifier: Modifier, click: () -> Unit) {
    Text(value, color = Ink, fontFamily = Body, modifier = modifier.height(44.dp).border(1.dp, Line, RoundedCornerShape(12.dp)).background(Surface, RoundedCornerShape(12.dp)).clickable(onClick = click).padding(12.dp))
}

@Composable
fun OtherScreen(repo: Repo, draftKey: Int, onBack: () -> Unit) {
    val book = repo.book
    var side by remember { mutableStateOf("aid") }
    var editing by remember { mutableStateOf<MoneyRow?>(null) }
    var creating by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(draftKey) { if (draftKey > 0) creating = true }
    val rows = when (side) { "hold" -> book.holds; "deposit" -> book.deposits; else -> book.aids }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("← Назад", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBack))
        Text("Другое", color = Ink, fontFamily = Display, fontSize = 30.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Не входит в доходы, расходы и остаток книги. Курс не считается.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
            listOf("aid" to "Помощь", "hold" to "Валюта", "deposit" to "Депозит").forEach { (id, label) ->
                Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (side == id) Surface else Color.Transparent).clickable { side = id }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (side == id) Ink else Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = if (side == id) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        val sums = rows.groupBy { it.currency }.mapValues { (_, list) -> list.sumOf { it.amount } }
        listOf("EUR" to "Евро", "USD" to "Доллар", "RUB" to "Росс. рубль", "BYN" to "Бел. рубль").filter { sums[it.first] != null && sums[it.first] != 0L }.forEach { (id, label) ->
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp, 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = Muted, fontFamily = Body, fontSize = 14.sp)
                ForeignSum(sums[id] ?: 0, id)
            }
        }
        rows.sortedByDescending { it.at }.forEach { row ->
            val title = when (side) { "aid" -> row.who.ifBlank { "Помощь" }; "deposit" -> row.bank.ifBlank { "Депозит" }; else -> row.note.ifBlank { "Отложено" } }
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable { editing = row }.padding(16.dp, 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, color = Ink, fontFamily = Body, fontWeight = FontWeight.Medium)
                    ForeignSum(row.amount, row.currency)
                }
                Text(buildString {
                    append(dayLabel(row.at.take(10)))
                    if (side == "deposit" && row.rate > 0) append(" · ${formatMoney(row.rate.toLong())} %")
                    if (row.note.isNotBlank() && side != "hold") append(" · ${row.note}")
                }, color = Muted, fontFamily = Body, fontSize = 14.sp)
            }
        }
        if (rows.isEmpty()) Text("Пока пусто.", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
    }
    if (creating || editing != null) OtherSheet(repo, side, if (creating) null else editing) { creating = false; editing = null }
}

@Composable
private fun OtherSheet(repo: Repo, side: String, row: MoneyRow?, onClose: () -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(if (row == null) "" else centsToInput(row.amount)) }
    var rate by remember { mutableStateOf(if (row != null && row.rate > 0) centsToInput(row.rate.toLong()) else "") }
    var currency by remember { mutableStateOf(row?.currency ?: if (side == "hold") "USD" else "BYN") }
    var who by remember { mutableStateOf(row?.who ?: "") }
    var bank by remember { mutableStateOf(row?.bank ?: "") }
    var note by remember { mutableStateOf(row?.note ?: "") }
    var date by remember { mutableStateOf((row?.at ?: nowStamp()).take(10)) }
    var until by remember { mutableStateOf(row?.until ?: "") }
    var field by remember { mutableStateOf("amount") }
    var error by remember { mutableStateOf<String?>(null) }
    val title = if (row != null) "Редактирование" else if (side == "aid") "Помощь" else if (side == "hold") "Валюта" else "Депозит"
    Sheet(title, true, onClose, {
        val cents = parseLooseCents(amount)
        when {
            cents == null || cents <= 0 -> error = "Укажите сумму."
            side == "aid" && who.isBlank() -> error = "Напишите, от кого помощь."
            side == "deposit" && bank.isBlank() -> error = "Напишите банк."
            else -> {
                val next = MoneyRow(row?.id ?: newId(side), cents, currency, note.trim(), "${date}T12:00:00", who.trim(), bank.trim(), parseLooseCents(rate)?.toDouble() ?: 0.0, until)
                when (side) {
                    "aid" -> repo.saveAid(next, row != null)
                    "hold" -> repo.saveHold(next, row != null)
                    else -> repo.saveDeposit(next, row != null)
                }
                onClose()
            }
        }
    }, Ink) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EUR" to "Евро", "USD" to "Доллар", "RUB" to "Росс. рубль", "BYN" to "Бел. рубль").forEach { (id, label) ->
                    Text(label, color = if (currency == id) Surface else Ink, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).background(if (currency == id) Ink else Surface).clickable { currency = id }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Text("Сумма", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            AmountButton(amount, field == "amount", "0") { field = "amount" }
            if (side == "aid") Field(who, { who = it }, "От кого")
            if (side == "deposit") {
                Field(bank, { bank = it }, "Банк")
                Text("Процент", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                AmountButton(rate, field == "rate", "0") { field = "rate" }
                PickerBox(until.ifBlank { "До какой даты" }, Modifier.padding(top = 8.dp).fillMaxWidth()) { pickDate(context, until.ifBlank { todayKey() }) { until = it } }
            }
            Field(note, { note = it }, "Заметка")
            PickerBox(date, Modifier.padding(top = 8.dp).fillMaxWidth()) { pickDate(context, date) { date = it } }
            error?.let { Text(it, color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
            if (row != null) Text("Удалить", color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp).clickable {
                when (side) { "aid" -> repo.removeAid(row.id); "hold" -> repo.removeHold(row.id); else -> repo.removeDeposit(row.id) }
                onClose()
            })
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.padding(16.dp)) { Keypad { key -> if (field == "rate") rate = applyKey(rate, key) else amount = applyKey(amount, key) } }
    }
}

@Composable
fun LockScreen(repo: Repo, onUnlock: () -> Unit) {
    LockBody(repo, if (repo.book.reserveHash.isEmpty()) "create" else "unlock", "Другое", onUnlock)
}

@Composable
fun LockBody(repo: Repo, mode: String, title: String, onDone: () -> Unit) {
    var step by remember { mutableStateOf(if (mode == "unlock") "now" else if (mode == "change") "now" else "next") }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val hint = when {
        step == "again" -> "Ещё раз"
        step == "now" && mode == "unlock" -> ""
        step == "now" -> "Текущий пароль"
        mode == "create" -> "Установите пароль"
        else -> "Новый пароль"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(title, color = Ink, fontFamily = Display, fontSize = 30.sp)
        if (hint.isNotEmpty()) Text(hint, color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp).border(2.dp, Ink, RoundedCornerShape(12.dp)).background(Surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("•".repeat(pin.length), color = Ink, fontFamily = Display, fontSize = 24.sp)
            Box(Modifier.padding(start = 2.dp).size(2.dp, 28.dp).background(Ink))
        }
        error?.let { Text(it, color = Expense, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Column(Modifier.padding(top = 16.dp)) {
            Keypad { key ->
                if (key == "," || key == "±") return@Keypad
                if (key == "⌫") pin = pin.dropLast(1) else if (pin.length < 12) pin += key
                error = null
            }
        }
        Text("Дальше", color = Surface, fontFamily = Body, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(CircleShape).background(Ink).clickable {
            if (pin.length < 4) { error = "Минимум 4 цифры."; return@clickable }
            if (step == "now") {
                if (hashPin(pin) != repo.book.reserveHash) { error = "Не тот пароль."; pin = "" }
                else if (mode == "unlock") onDone()
                else { pin = ""; step = "next"; error = null }
            } else if (step == "next") { first = pin; pin = ""; step = "again" }
            else if (pin != first) { error = "Пароли не совпали."; pin = ""; step = "next"; first = "" }
            else { repo.update { it.copy(reserveHash = hashPin(pin), showReserve = true) }; onDone() }
        }.padding(vertical = 14.dp))
        if (mode != "unlock") Text("Отмена", color = Muted, fontFamily = Body, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clickable(onClick = onDone))
    }
}
