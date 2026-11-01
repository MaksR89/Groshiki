package by.smeta.app

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val Desk = Color(0xFFF4EFE6)
private val Surface = Color(0xFFFFFBF6)
private val Ink = Color(0xFF1C1712)
private val Muted = Color(0xFF6E655C)
private val Line = Color(0xFFE4D9CC)
private val Expense = Color(0xFFB6402C)
private val Income = Color(0xFF1F6B45)
private val Chip = Color(0xFFE7DFD2)
private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif
private val BynPath = "M0.26 58.54L13.02 58.28L13.02 0.26L62.54 0.26L62.54 10.06L22.82 10.06L22.69 37.13L45.00 37.13L50.80 37.78L58.54 40.62L63.57 44.10L66.40 46.93L68.85 50.29L70.92 54.41L72.21 58.80L72.59 64.34L72.08 68.60L70.40 73.62L67.44 78.65L63.95 82.39L56.60 87.16L52.22 88.71L47.32 89.61L13.02 89.61L13.02 68.47L0.26 68.21L0.26 58.54ZM22.82 46.93L22.82 58.54L45.26 58.54L45.26 68.21L22.82 68.21L22.82 79.94L46.16 79.94L49.38 79.43L52.09 78.52L56.48 75.95L60.73 71.17L62.79 65.37L62.41 59.57L59.83 54.28L55.32 50.03L51.19 47.97L46.68 46.93L22.82 46.93Z"

private enum class Tab(val label: String) { Expenses("Расходы"), Income("Доходы"), Charges("Зарядки"), Report("Отчёт"), Other("Другое"), Book("Книга") }

@Composable
fun GroshikiApp(repo: Repo) {
    val context = LocalContext.current
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (!repo.hasFile()) repo.importOld(pullOldBook(context))
        delay(2000)
        splash = false
    }
    if (splash) {
        Box(Modifier.fillMaxSize().background(Desk), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Грошыки", color = Ink, fontFamily = Display, fontSize = 48.sp)
                Text("Книга учета доходов и расходов", color = Muted, fontFamily = Body, modifier = Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    val book = repo.book
    var tab by remember { mutableStateOf(Tab.Expenses) }
    var unlocked by remember { mutableStateOf(false) }
    val tabs = buildList {
        add(Tab.Expenses); add(Tab.Income)
        if (book.showCharges) add(Tab.Charges)
        if (book.showReport) add(Tab.Report)
        if (book.showReserve) add(Tab.Other)
        add(Tab.Book)
    }
    if (tab !in tabs) tab = Tab.Expenses
    Column(Modifier.fillMaxSize().background(Desk).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.Expenses -> Feed(repo, "expense")
                Tab.Income -> Feed(repo, "income")
                Tab.Charges -> Charges(repo)
                Tab.Report -> Report(book)
                Tab.Other -> if (unlocked) Other(repo) else Lock(book.reserveHash, onSet = { repo.update { b -> b.copy(reserveHash = hashPin(it)) }; unlocked = true }, onOk = { unlocked = true })
                Tab.Book -> BookScreen(repo)
            }
        }
        Row(Modifier.fillMaxWidth().background(Surface).navigationBarsPadding().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { item ->
                Text(item.label, color = if (item == tab) Ink else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = if (item == tab) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.clickable {
                    if (item != Tab.Other) unlocked = false
                    tab = item
                }.padding(8.dp))
            }
        }
    }
}

@Composable
private fun Feed(repo: Repo, kind: String) {
    val book = repo.book
    var query by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Entry?>(null) }
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now().toString()
    val month = today.take(7)
    val rows = book.entries.filter { it.kind == kind }
    val monthSum = rows.filter { it.at.startsWith(month) }.sumOf { it.amount }
    val todaySum = rows.filter { it.at.startsWith(today) }.sumOf { it.amount }
    val yday = LocalDate.now().minusDays(1).toString()
    val ydaySum = rows.filter { it.at.startsWith(yday) }.sumOf { it.amount }
    val shown = rows.filter { query.isBlank() || book.categories.find { c -> c.id == it.categoryId }?.name?.contains(query, true) == true || it.note.contains(query, true) }
        .sortedByDescending { it.at }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(if (kind == "expense") "Расходы" else "Доходы", color = Ink, fontFamily = Display, fontSize = 34.sp)
        if (book.showBalance) MoneyText(book.balance(), Modifier.padding(top = 8.dp), font = 28)
        Text("Сегодня ${formatMoney(todaySum)}   Вчера ${formatMoney(ydaySum)}", color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text("В этом месяце ${formatMoney(monthSum)}", color = Ink, fontFamily = Body, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Surface).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Поиск", color = Muted, fontFamily = Body)
            BasicTextField(query, { query = it }, textStyle = TextStyle(color = Ink, fontFamily = Body, fontSize = 16.sp), cursorBrush = SolidColor(Ink), modifier = Modifier.fillMaxWidth())
        }
        Text(if (kind == "expense") "Добавить запись" else "Добавить доход", color = Surface, fontFamily = Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (kind == "expense") Expense else Income).clickable { creating = true }.padding(top = 10.dp))
        LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val groups = shown.groupBy { it.at.take(10) }
            groups.forEach { (day, list) ->
                item { Text(dayLabel(day), color = Muted, fontFamily = Body, fontSize = 13.sp) }
                items(list, key = { it.id }) { row ->
                    val cat = book.categories.find { it.id == row.categoryId }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable { edit = row }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(parseColor(cat?.color)), contentAlignment = Alignment.Center) {
                            Text((cat?.name ?: "•").take(1), color = Color.White, fontFamily = Body)
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(cat?.name ?: "Без статьи", color = Ink, fontFamily = Body, fontSize = 16.sp)
                            if (row.note.isNotBlank()) Text(row.note, color = Muted, fontFamily = Body, fontSize = 13.sp)
                        }
                        MoneyText(row.amount, color = if (kind == "expense") Expense else Income)
                    }
                }
            }
        }
    }
    if (creating || edit != null) EntrySheet(repo, kind, edit, onClose = { creating = false; edit = null })
}

@Composable
private fun EntrySheet(repo: Repo, kind: String, existing: Entry?, onClose: () -> Unit) {
    val book = repo.book
    var raw by remember { mutableStateOf(if (existing == null) "" else centsToRaw(existing.amount)) }
    var cat by remember { mutableStateOf(existing?.categoryId ?: book.categories.firstOrNull { it.kind == kind || it.kind == "both" }?.id) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val cats = book.categories.filter { it.kind == kind || it.kind == "both" }
    Column(Modifier.fillMaxSize().background(Desk).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (existing == null) if (kind == "expense") "Расход" else "Доход" else "Редактирование", color = Ink, fontFamily = Display, fontSize = 28.sp)
            Text("Готово", color = Ink, fontFamily = Body, modifier = Modifier.clickable {
                val amount = rawToCents(raw)
                val at = existing?.at ?: java.time.LocalDateTime.now().toString().take(19)
                repo.update { b ->
                    val account = existing?.accountId ?: b.accounts.firstOrNull()?.id
                    val accounts = if (account != null) b.accounts else b.accounts + Account(newId("acc"), "Мои деньги", 0, "#2f5573")
                    val accountId = account ?: accounts.last().id
                    val entry = Entry(existing?.id ?: newId("e"), kind, amount, cat, accountId, null, note, at)
                    val list = if (existing == null) b.entries + entry else b.entries.map { if (it.id == existing.id) entry else it }
                    b.copy(accounts = accounts, entries = list.sortedBy { it.at })
                }
                onClose()
            })
        }
        MoneyText(rawToCents(raw), Modifier.padding(horizontal = 16.dp), font = 40, color = if (kind == "expense") Expense else Income)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { item ->
                Text(item.name, color = if (item.id == cat) Surface else Ink, fontFamily = Body, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (item.id == cat) Ink else Surface).clickable { cat = item.id }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        BasicTextField(note, { note = it }, textStyle = TextStyle(color = Ink, fontFamily = Body, fontSize = 16.sp), cursorBrush = SolidColor(Ink), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Surface).padding(12.dp), decorationBox = { inner -> if (note.isEmpty()) Box { Text("Заметка", color = Muted, fontFamily = Body); inner() } else inner() })
        if (existing != null) Text("Удалить", color = Expense, fontFamily = Body, modifier = Modifier.padding(16.dp).clickable { repo.update { it.copy(entries = it.entries.filter { e -> e.id != existing.id }) }; onClose() })
        Spacer(Modifier.weight(1f))
        Keypad { key -> raw = applyKey(raw, key) }
    }
}

@Composable
private fun Charges(repo: Repo) {
    val book = repo.book
    var open by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Charge?>(null) }
    val month = LocalDate.now().toString().take(7)
    val monthRows = book.charges.filter { it.at.startsWith(month) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Зарядки", color = Ink, fontFamily = Display, fontSize = 34.sp)
        Text("Станции", color = Muted, fontFamily = Body, modifier = Modifier.padding(top = 8.dp).clickable { open = !open })
        if (open) {
            Text("Свою станцию можно добавить так же, как статью.", color = Muted, fontSize = 13.sp, fontFamily = Body, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Этот месяц", "${formatKwh(monthRows.sumOf { it.kwh })} кВт·ч")
            Stat("Цена", formatMoney(monthRows.sumOf { it.price }))
        }
        Text("Добавить зарядку", color = Surface, textAlign = TextAlign.Center, fontFamily = Body, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Ink).clickable { edit = Charge("", "", 0, 0, 0, "") }.padding(top = 10.dp))
        LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(book.charges.sortedByDescending { it.at }, key = { it.id }) { row ->
                val station = book.stations.find { it.id == row.stationId }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable { edit = row }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StationMark(station, Modifier.size(24.dp))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(station?.name ?: "Станция", color = Ink, fontFamily = Body)
                        Text("${row.at.drop(11).take(5)} · ${formatKwh(row.kwh)} кВт·ч", color = Muted, fontSize = 13.sp, fontFamily = Body)
                    }
                    MoneyText(row.price)
                }
            }
        }
    }
    if (edit != null) ChargeSheet(repo, edit!!, onClose = { edit = null })
}

@Composable
private fun ChargeSheet(repo: Repo, existing: Charge, onClose: () -> Unit) {
    val fresh = existing.id.isEmpty()
    var station by remember { mutableStateOf(existing.stationId.ifEmpty { repo.book.stations.firstOrNull()?.id.orEmpty() }) }
    var kwh by remember { mutableStateOf(if (fresh) "" else centsToRaw(existing.kwh)) }
    var rate by remember { mutableStateOf(if (fresh) "" else centsToRaw(existing.rate)) }
    var field by remember { mutableStateOf("kwh") }
    var list by remember { mutableStateOf(false) }
    val price = kotlin.math.round(rawToCents(kwh) * rawToCents(rate) / 100.0).toLong()
    Column(Modifier.fillMaxSize().background(Desk).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (fresh) "Зарядка" else "Редактирование", color = Ink, fontFamily = Display, fontSize = 28.sp)
            Text("Готово", color = Ink, modifier = Modifier.clickable {
                val row = Charge(if (fresh) newId("ch") else existing.id, station, rawToCents(kwh), rawToCents(rate), price, if (fresh) java.time.LocalDateTime.now().toString().take(19) else existing.at)
                repo.update { b -> b.copy(charges = if (fresh) b.charges + row else b.charges.map { if (it.id == row.id) row else it }) }
                onClose()
            })
        }
        val chosen = repo.book.stations.find { it.id == station }
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).background(Surface).clickable { list = !list }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StationMark(chosen, Modifier.size(28.dp))
            Text(chosen?.name ?: "Станция", color = Ink, modifier = Modifier.padding(start = 8.dp).weight(1f), fontFamily = Body)
        }
        if (list) {
            Column(Modifier.padding(horizontal = 16.dp).height(180.dp).verticalScroll(rememberScrollState()).background(Surface)) {
                repo.book.stations.forEach { item ->
                    Text(item.name, color = Ink, fontFamily = Body, modifier = Modifier.fillMaxWidth().clickable { station = item.id; list = false }.padding(12.dp))
                }
            }
        }
        FieldBox("кВт·ч", formatKwh(rawToCents(kwh)), field == "kwh") { field = "kwh" }
        FieldBox("Цена за кВт·ч", formatMoney(rawToCents(rate)), field == "rate") { field = "rate" }
        Text("Сумма ${formatMoney(price)}", color = Ink, fontFamily = Display, fontSize = 22.sp, modifier = Modifier.padding(16.dp))
        if (!fresh) Text("Удалить", color = Expense, modifier = Modifier.padding(horizontal = 16.dp).clickable { repo.update { it.copy(charges = it.charges.filter { c -> c.id != existing.id }) }; onClose() })
        Spacer(Modifier.weight(1f))
        Keypad { key -> if (field == "kwh") kwh = applyKey(kwh, key) else rate = applyKey(rate, key) }
    }
}

@Composable
private fun Report(book: Book) {
    var period by remember { mutableStateOf("month") }
    val now = LocalDate.now()
    val key = when (period) { "day" -> now.toString(); "year" -> now.year.toString(); else -> now.toString().take(7) }
    val rows = book.entries.filter { it.at.startsWith(key) && it.kind != "transfer" }
    val spent = rows.filter { it.kind == "expense" }.sumOf { it.amount }
    val income = rows.filter { it.kind == "income" }.sumOf { it.amount }
    var side by remember { mutableStateOf("expense") }
    val grouped = rows.filter { it.kind == side }.groupBy { it.categoryId }.map { (id, list) -> (book.categories.find { it.id == id }?.name ?: "Без статьи") to list.sumOf { it.amount } }.sortedByDescending { it.second }
    val max = grouped.maxOfOrNull { it.second } ?: 1
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Отчёт", color = Ink, fontFamily = Display, fontSize = 34.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("day" to "День", "month" to "Месяц", "year" to "Год").forEach { (id, label) ->
                Text(label, color = if (period == id) Surface else Ink, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (period == id) Ink else Surface).clickable { period = id }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (side == "expense") Expense else Surface).clickable { side = "expense" }.padding(12.dp)) { Column { Text("Потрачено", color = if (side == "expense") Surface else Muted); MoneyText(spent, color = if (side == "expense") Surface else Ink) } }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (side == "income") Income else Surface).clickable { side = "income" }.padding(12.dp)) { Column { Text("Доходы", color = if (side == "income") Surface else Muted); MoneyText(income, color = if (side == "income") Surface else Ink) } }
        }
        grouped.forEach { (name, sum) ->
            Column(Modifier.padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = Ink, fontFamily = Body)
                    Text(formatMoney(sum), color = Ink, fontFamily = Body)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Chip)) {
                    Box(Modifier.fillMaxWidth(sum.toFloat() / max).height(8.dp).background(if (side == "expense") Expense else Income))
                }
            }
        }
    }
}

@Composable
private fun BookScreen(repo: Repo) {
    val context = LocalContext.current
    val book = repo.book
    var message by remember { mutableStateOf("") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment.orEmpty()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val result = if (name.endsWith("json") || bytes.firstOrNull()?.toInt() == '{'.code) importBackup(String(bytes)) else importMoneyOk(name, bytes)
        when (result) {
            is ImportResult.Ok -> {
                repo.update { old ->
                    if (name.endsWith("json")) result.book else old.copy(accounts = result.book.accounts, categories = result.book.categories, entries = result.book.entries)
                }
                message = "Книга загружена."
            }
            is ImportResult.Err -> message = result.message
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(book.toJson().toString().toByteArray()) }
        message = "Копия сохранена."
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Книга", color = Ink, fontFamily = Display, fontSize = 34.sp)
        if (book.showBalance) MoneyText(book.balance(), Modifier.padding(top = 8.dp), font = 28)
        Toggle("Показывать остаток", book.showBalance) { repo.update { it.copy(showBalance = !it.showBalance) } }
        Text("Это текущий остаток счёта, не оборот за всё время.", color = Muted, fontSize = 13.sp, fontFamily = Body)
        Text("Расходы и доходы всегда в меню. Остальные разделы можно выключить.", color = Muted, fontSize = 13.sp, fontFamily = Body, modifier = Modifier.padding(top = 12.dp))
        Toggle("Зарядки", book.showCharges) { repo.update { it.copy(showCharges = !it.showCharges) } }
        Toggle("Отчёт", book.showReport) { repo.update { it.copy(showReport = !it.showReport) } }
        Toggle("Другое", book.showReserve) { repo.update { it.copy(showReserve = !it.showReserve) } }
        Text("«Другое» стоит в меню и открывается паролем.", color = Muted, fontSize = 13.sp, fontFamily = Body)
        Action("Загрузить выгрузку MoneyOK") { open.launch(arrayOf("*/*")) }
        Text("Лучше Excel из Деньги OK: в нём есть время операции.", color = Muted, fontSize = 13.sp, fontFamily = Body)
        Action("Скачать копию JSON") { save.launch("groshyki.json") }
        Action("Очистить книгу") { repo.replace(Book()) ; message = "Книга очищена." }
        if (message.isNotBlank()) Text(message, color = Muted, modifier = Modifier.padding(top = 8.dp))
        Text("Версия 1.4.0. Обновление книгу не стирает.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Lock(hash: String, onSet: (String) -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var again by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val hint = if (hash.isEmpty()) if (again) "Ещё раз" else "Установите пароль" else "Пароль"
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Другое", color = Ink, fontFamily = Display, fontSize = 34.sp)
        Text(hint, color = Muted, fontFamily = Body, modifier = Modifier.padding(top = 8.dp))
        Text(if (pin.isEmpty()) "" else "•".repeat(pin.length), color = Ink, fontSize = 28.sp, modifier = Modifier.padding(top = 12.dp))
        if (error.isNotBlank()) Text(error, color = Expense, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        Keypad(allowSign = false) { key ->
            when (key) {
                "⌫" -> pin = pin.dropLast(1)
                "±", "," -> {}
                else -> if (pin.length < 12) pin += key
            }
            error = ""
        }
        Text("Дальше", color = Surface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Ink).clickable {
            if (pin.length < 4) { error = "Минимум 4 цифры."; return@clickable }
            if (hash.isEmpty()) {
                if (!again) { first = pin; pin = ""; again = true } else if (pin != first) { error = "Пароли не совпали."; pin = ""; again = false } else onSet(pin)
            } else if (hashPin(pin) != hash) { error = "Не тот пароль."; pin = "" } else onOk()
        }.padding(top = 12.dp))
    }
}

@Composable
private fun Other(repo: Repo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Другое", color = Ink, fontFamily = Display, fontSize = 34.sp)
        Text("Помощь, отложенное и депозиты. Сумма в выбранной валюте, без курса.", color = Muted, fontFamily = Body, modifier = Modifier.padding(top = 8.dp))
        Section("Помощь")
        repo.book.aids.forEach { Text("${it.who.ifBlank { "Помощь" }}  ${formatMoney(it.amount)} ${it.currency}", color = Ink, modifier = Modifier.padding(top = 6.dp)) }
        Section("Отложенное")
        repo.book.holds.forEach { Text("${formatMoney(it.amount)} ${it.currency}  ${it.note}", color = Ink, modifier = Modifier.padding(top = 6.dp)) }
        Section("Депозиты")
        repo.book.deposits.forEach { Text("${it.bank}  ${formatMoney(it.amount)} ${it.currency}  ${it.rate}%", color = Ink, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable private fun Section(title: String) { Text(title, color = Ink, fontFamily = Display, fontSize = 22.sp, modifier = Modifier.padding(top = 16.dp)) }

@Composable
private fun Keypad(allowSign: Boolean = true, onKey: (String) -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (allowSign) "±" else "", "0", "⌫"))
    Column(Modifier.fillMaxWidth().background(Surface).navigationBarsPadding().padding(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(Modifier.weight(1f).height(52.dp).clickable(enabled = key.isNotEmpty()) { onKey(key) }, contentAlignment = Alignment.Center) {
                        Text(if (key == "⌫") "⌫" else key, color = Ink, fontSize = 22.sp, fontFamily = Body)
                    }
                }
            }
        }
    }
}

private fun applyKey(raw: String, key: String): String = when (key) {
    "⌫" -> raw.dropLast(1)
    "±" -> if (raw.startsWith("-")) raw.drop(1) else if (raw.isEmpty()) raw else "-$raw"
    "," -> if (raw.contains('.')) raw else (raw.ifEmpty { "0" }) + "."
    else -> {
        val next = raw + key
        val frac = next.substringAfter('.', "")
        if (frac.length > 2) raw else next
    }
}

@Composable
private fun MoneyText(cents: Long, modifier: Modifier = Modifier, font: Int = 16, color: Color = Ink) {
    val path = remember {
        PathParser().parsePathString(BynPath).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(formatMoney(cents), color = color, fontFamily = Display, fontSize = font.sp)
        Canvas(Modifier.padding(start = 2.dp, bottom = 2.dp).size((font * 0.42f).dp, (font * 0.62f).dp)) {
            scale(this.size.width / 73f, this.size.height / 90f) { drawPath(path, color) }
        }
    }
}

@Composable
private fun StationMark(station: Station?, modifier: Modifier) {
    val context = LocalContext.current
    val bmp = remember(station?.logo) {
        val path = station?.logo?.removePrefix("/") ?: return@remember null
        runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier.clip(RoundedCornerShape(6.dp)))
    else Box(modifier.clip(RoundedCornerShape(6.dp)).background(parseColor(station?.color)), contentAlignment = Alignment.Center) {
        Text(station?.name?.take(1) ?: "•", color = Color.White, fontSize = 11.sp)
    }
}

@Composable private fun Stat(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Surface).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, fontFamily = Body)
        Text(value, color = Ink, fontFamily = Display, fontSize = 20.sp)
    }
}
@Composable private fun FieldBox(label: String, value: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(2.dp, if (active) Ink else Line, RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onClick).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = Ink, fontFamily = Display, fontSize = 24.sp)
    }
}
@Composable private fun Toggle(label: String, on: Boolean, click: () -> Unit) {
    Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Surface).clickable(onClick = click).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Ink, fontFamily = Body)
        Box(Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(if (on) Ink else Chip).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Surface))
        }
    }
}
@Composable private fun Action(label: String, click: () -> Unit) {
    Text(label, color = Ink, fontFamily = Body, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Surface).clickable(onClick = click).padding(horizontal = 16.dp, vertical = 14.dp))
}

private fun dayLabel(day: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
    }
}

private fun parseColor(hex: String?): Color {
    val raw = hex?.removePrefix("#") ?: return Chip
    val value = raw.toLongOrNull(16) ?: return Chip
    return Color(0xFF000000 or value)
}
