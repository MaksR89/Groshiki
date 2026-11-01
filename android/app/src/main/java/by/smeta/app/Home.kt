package by.smeta.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import java.time.LocalDate
import kotlinx.coroutines.delay

private data class Nav(val id: String, val label: String, val icon: String)

@Composable
fun GroshikiApp(repo: Repo) {
    val context = LocalContext.current
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val started = SystemClock.elapsedRealtime()
        if (!repo.hasFile()) repo.importOld(pullOldBook(context))
        val left = 2000 - (SystemClock.elapsedRealtime() - started)
        if (left > 0) delay(left)
        splash = false
    }
    if (splash) {
        Box(Modifier.fillMaxSize().background(Desk), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Грошыки", color = Ink, fontFamily = Display, fontSize = 48.sp)
                Text("Книга учета доходов и расходов", color = Muted, fontFamily = Body, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    Shell(repo)
}

@Composable
private fun Shell(repo: Repo) {
    val book = repo.book
    var tab by remember { mutableStateOf("expenses") }
    var sub by remember { mutableStateOf<String?>(null) }
    var composer by remember { mutableStateOf<Pair<String, Entry?>?>(null) }
    var chargeNew by remember { mutableStateOf(0) }
    var otherNew by remember { mutableStateOf(0) }
    var unlocked by remember { mutableStateOf(false) }
    val tabs = buildList {
        add(Nav("expenses", "Расходы", "arrow-down-left"))
        add(Nav("income", "Доходы", "arrow-up-right"))
        if (book.showCharges) add(Nav("charges", "Зарядки", "plug-zap"))
        if (book.showReport) add(Nav("report", "Отчёт", "pie-chart"))
        if (book.showReserve) add(Nav("other", "Другое", "lock"))
        add(Nav("book", "Книга", "menu"))
    }
    if (tabs.none { it.id == tab }) tab = "expenses"
    val kind = if (tab == "income") "income" else "expense"
    CompositionLocalProvider(LocalMoney provides book.currency) {
    Column(Modifier.fillMaxSize().background(Desk).statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("Грошыки", color = Ink, fontFamily = Display, fontSize = 28.sp)
                    Text(
                        when {
                            book.accounts.isEmpty() -> "Нет счетов"
                            book.accounts.size == 1 -> book.accounts[0].name
                            else -> "${book.accounts.size} счетов"
                        },
                        color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (book.showBalance && book.accounts.isNotEmpty()) MoneyText(totalBalance(book), color = Ink, size = 22.sp, font = Display, weight = FontWeight.SemiBold)
            }
            val hideAdd = tab == "other" && !unlocked
            if (!hideAdd) {
                val label = when {
                    tab == "charges" -> "Добавить зарядку"
                    tab == "other" -> "Добавить"
                    else -> "Добавить запись"
                }
                val bg = when {
                    tab == "charges" || tab == "other" -> Ink
                    kind == "income" -> Income
                    else -> Expense
                }
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable {
                        when (tab) {
                            "charges" -> chargeNew += 1
                            "other" -> otherNew += 1
                            else -> {
                                if (book.accounts.isEmpty()) { tab = "book"; sub = "accounts" }
                                else composer = kind to null
                            }
                        }
                    },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIcon("plus", tint = Surface, size = 16.dp)
                    Text("  $label", color = Surface, fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                sub == "accounts" -> AccountsScreen(repo) { sub = null }
                sub == "categories" -> CategoriesScreen(repo) { sub = null }
                tab == "expenses" -> FeedScreen(repo, "expense") { composer = "expense" to it }
                tab == "income" -> FeedScreen(repo, "income") { composer = "income" to it }
                tab == "charges" -> ChargesScreen(repo, chargeNew)
                tab == "report" -> ReportScreen(repo) { composer = it.kind to it }
                tab == "other" -> if (unlocked) OtherScreen(repo, otherNew) { tab = "expenses" } else LockScreen(repo) { unlocked = true }
                else -> BookScreen(repo, { sub = "accounts" }, { sub = "categories" })
            }
        }
        Row(Modifier.fillMaxWidth().background(Paper).navigationBarsPadding().padding(vertical = 6.dp)) {
            tabs.forEach { item ->
                val on = item.id == tab && sub == null
                Column(
                    Modifier.weight(1f).height(56.dp).clickable {
                        sub = null
                        if (item.id != "other") unlocked = false
                        tab = item.id
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LineIcon(item.icon, tint = if (on) Ink else Muted, size = 20.dp)
                    Text(item.label, color = if (on) Ink else Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }
    composer?.let { (initial, editing) ->
        ComposerSheet(repo, initial, editing) { composer = null }
    }
    }
}

@Composable
private fun FeedScreen(repo: Repo, kind: String, onOpen: (Entry) -> Unit) {
    val book = repo.book
    var mode by remember { mutableStateOf("recent") }
    var month by remember { mutableStateOf(todayKey()) }
    var query by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("all") }
    var limit by remember { mutableStateOf(40) }
    val today = todayKey()
    val yesterday = addDays(today, -1)
    val todaySum = sumKind(book.entries, kind, today, today, accountId)
    val ySum = sumKind(book.entries, kind, yesterday, yesterday, accountId)
    val shownMonth = if (mode == "month" && query.isBlank()) month else today
    val (from, to) = monthRange(shownMonth)
    val monthSum = sumKind(book.entries, kind, from, to, accountId)
    val monthLabel = if (shownMonth.take(7) == today.take(7)) "В этом месяце" else monthTitle(shownMonth)
    val pool = book.entries.filter { e ->
        if (e.kind != kind && e.kind != "transfer") return@filter false
        if (!inAccount(e, accountId)) return@filter false
        if (query.isBlank() && mode == "month" && !e.at.startsWith(month.take(7))) return@filter false
        if (query.isBlank()) return@filter true
        val cat = book.categories.find { it.id == e.categoryId }
        "${cat?.name.orEmpty()} ${cat?.group.orEmpty()} ${e.note}".contains(query.trim(), true)
    }.asReversed()
    val visible = pool.take(limit)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Summary("Сегодня", todaySum, Modifier.weight(1f))
            Summary("Вчера", ySum, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(monthLabel, color = Muted, fontFamily = Body, fontSize = 14.sp)
            MoneyText(monthSum, size = 24.sp, weight = FontWeight.SemiBold)
        }
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
            Seg("Последние", mode == "recent", Modifier.weight(1f)) { mode = "recent"; limit = 40 }
            Seg("Месяц", mode == "month", Modifier.weight(1f)) { mode = "month"; limit = 40 }
        }
        if (mode == "month" && query.isBlank()) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RoundBtn("‹") { month = addMonths(month, -1); limit = 40 }
                Text(monthTitle(month), color = Ink, fontFamily = Display, fontSize = 20.sp)
                RoundBtn("›", month.take(7) < today.take(7)) { month = addMonths(month, 1); limit = 40 }
            }
        }
        if (book.accounts.size > 1) {
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipBtn("Все счета", accountId == "all") { accountId = "all" }
                book.accounts.forEach { ChipBtn(it.name, accountId == it.id) { accountId = it.id } }
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(Surface).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LineIcon("search", tint = Muted, size = 16.dp)
            BasicTextField(
                query,
                { query = it; limit = 40 },
                textStyle = fieldStyle(),
                cursorBrush = caret,
                modifier = Modifier.padding(start = 8.dp).fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Статья или заметка", color = Muted, fontFamily = Body, fontSize = 16.sp)
                    inner()
                },
            )
        }
        if (visible.isEmpty()) {
            Text(if (query.isNotBlank()) "Ничего не нашлось." else if (kind == "expense") "Расходов здесь нет." else "Доходов здесь нет.", color = Muted, fontFamily = Body, modifier = Modifier.padding(top = 40.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            DayList(visible, book, onOpen)
            if (pool.size > visible.size) {
                Text("Ещё ${minOf(40, pool.size - visible.size)}", color = Ink, fontFamily = Body, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable { limit += 40 }.padding(vertical = 12.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Summary(label: String, cents: Long, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = Muted, fontFamily = Body, fontSize = 14.sp)
        MoneyText(cents, size = 24.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Seg(label: String, on: Boolean, modifier: Modifier, click: () -> Unit) {
    Box(modifier.height(40.dp).clip(CircleShape).background(if (on) Surface else Color.Transparent).clickable(onClick = click), contentAlignment = Alignment.Center) {
        Text(label, color = if (on) Ink else Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun RoundBtn(label: String, enabled: Boolean = true, click: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Surface).clickable(enabled = enabled, onClick = click), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) Ink else Muted, fontSize = 22.sp)
    }
}

@Composable
private fun ChipBtn(label: String, on: Boolean, click: () -> Unit) {
    Text(label, color = if (on) Surface else Ink, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).background(if (on) Ink else Surface).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
fun DayList(entries: List<Entry>, book: Book, onOpen: (Entry) -> Unit) {
    val groups = entries.groupBy { it.at.take(10) }
    groups.forEach { (day, list) ->
        Text(dayLabel(day), color = Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 20.dp))
        list.forEach { entry ->
            val cat = book.categories.find { it.id == entry.categoryId }
            val title = if (entry.kind == "transfer") "Перевод → ${book.accounts.find { it.id == entry.toAccountId }?.name ?: "счёт"}" else cat?.name ?: "Без статьи"
            val meta = buildList {
                if (entry.kind != "transfer" && !cat?.group.isNullOrBlank()) add(cat!!.group)
                if (entry.note.isNotBlank()) add(entry.note)
                val time = entry.at.drop(11).take(5)
                if (time.isNotBlank() && time != "00:00") add(time)
            }.joinToString(" · ")
            Row(Modifier.fillMaxWidth().clickable { onOpen(entry) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) {
                    LineIcon(if (entry.kind == "transfer") "repeat" else cat?.icon ?: "ellipsis", tint = if (cat != null) parseHex(cat.color) else Muted, size = 20.dp)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, color = Ink, fontFamily = Body, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (meta.isNotBlank()) Text(meta, color = Muted, fontFamily = Body, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MoneyText(entry.amount, color = if (entry.kind == "income") Income else Ink, weight = FontWeight.Medium)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

@Composable
private fun ComposerSheet(repo: Repo, initial: String, editing: Entry?, onClose: () -> Unit) {
    val book = repo.book
    var kind by remember { mutableStateOf(editing?.kind ?: initial) }
    var raw by remember { mutableStateOf(if (editing == null) "" else centsToInput(editing.amount)) }
    var categoryId by remember { mutableStateOf(editing?.categoryId ?: if (initial == "transfer") null else if (initial == "income") book.lastCategory.income else book.lastCategory.expense) }
    var accountId by remember { mutableStateOf(editing?.accountId ?: book.accounts.firstOrNull()?.id ?: "") }
    var toId by remember { mutableStateOf(editing?.toAccountId ?: book.accounts.getOrNull(1)?.id ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var date by remember { mutableStateOf((editing?.at ?: nowStamp()).take(10)) }
    var time by remember { mutableStateOf((editing?.at ?: nowStamp()).drop(11).take(5)) }
    var confirm by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val ranked = book.categories.filter { it.kind == kind || it.kind == "both" }.sortedWith(compareByDescending<Category> { cat -> book.entries.count { it.categoryId == cat.id && it.kind == kind } }.thenBy { it.name })
    val cents = inputToCents(raw)
    val canSave = cents > 0 && if (kind == "transfer") accountId.isNotBlank() && toId.isNotBlank() && accountId != toId else categoryId != null && accountId.isNotBlank()
    fun save() {
        if (!canSave) return
        val entry = Entry(editing?.id ?: newId("e"), kind, cents, if (kind == "transfer") null else categoryId, accountId, if (kind == "transfer") toId else null, note.trim(), "${date}T${time.ifBlank { "12:00" }}:00")
        if (editing == null) repo.addEntry(entry) else repo.updateEntry(entry)
        onClose()
    }
    Box(Modifier.fillMaxSize()) {
    Sheet(if (editing != null) "Редактирование" else if (kind == "income") "Новый доход" else if (kind == "transfer") "Перевод" else "Новый расход", canSave, onClose, { save() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(Chip).padding(4.dp)) {
                listOf("expense" to "Расход", "income" to "Доход", "transfer" to "Перевод").forEach { (id, label) ->
                    val enabled = id != "transfer" || book.accounts.size >= 2
                    Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (kind == id) Surface else Color.Transparent).clickable(enabled = enabled) {
                        kind = id
                        if (id != "transfer") {
                            val cat = book.categories.find { it.id == categoryId }
                            if (cat == null || (cat.kind != "both" && cat.kind != id)) {
                                val last = if (id == "income") book.lastCategory.income else book.lastCategory.expense
                                categoryId = last?.takeIf { idLast -> book.categories.any { it.id == idLast && (it.kind == id || it.kind == "both") } }
                            }
                        }
                    }, contentAlignment = Alignment.Center) {
                        Text(label, color = if (!enabled) Muted else if (kind == id) Ink else Muted, fontFamily = Body, fontSize = 14.sp, fontWeight = if (kind == id) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            Text(formatRawInput(raw), color = Ink, fontFamily = Display, fontSize = 48.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(date, Modifier.weight(1f)) { pickDate(context, date) { date = it } }
                Picker(time, Modifier.weight(1f)) { pickTime(context, time) { time = it } }
            }
            BasicTextField(note, { note = it.take(200) }, textStyle = fieldStyle(), cursorBrush = caret, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(44.dp).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp), decorationBox = { inner ->
                if (note.isEmpty()) Text("Заметка", color = Muted, fontFamily = Body)
                inner()
            })
            if (book.accounts.size > 1) {
                Text(if (kind == "transfer") "Откуда" else "Счёт", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                AccountPick(book.accounts, accountId) { accountId = it }
                if (kind == "transfer") {
                    Text("Куда", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    AccountPick(book.accounts, toId) { toId = it }
                }
            }
            if (kind != "transfer") {
                val rows = ranked.chunked(4)
                rows.forEach { row ->
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cat ->
                            val on = cat.id == categoryId
                            Column(Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Ink else Chip).clickable { categoryId = cat.id }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                LineIcon(cat.icon, tint = if (on) Surface else parseHex(cat.color), size = 24.dp)
                                Text(cat.name, color = if (on) Surface else Ink, fontFamily = Body, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("Добавить статью", color = Ink, fontFamily = Body, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Chip).clickable { adding = true }.padding(vertical = 12.dp))
            }
            if (editing != null) {
                Text(if (confirm) "Точно удалить" else "Удалить операцию", color = Expense, fontFamily = Body, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clickable {
                    if (!confirm) confirm = true else { repo.removeEntry(editing.id); onClose() }
                })
            }
        }
        Column(Modifier.navigationBarsPadding().padding(16.dp)) { Keypad { raw = applyKey(raw, it) } }
    }
    if (adding) NewCategorySheet(kind) { name, icon, color ->
        if (name != null) categoryId = repo.addCategory(name, kind, "", icon, color)
        adding = false
    }
    }
}

@Composable
private fun NewCategorySheet(kind: String, onDone: (name: String?, icon: String, color: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("ellipsis") }
    var color by remember { mutableStateOf(SWATCHES[0]) }
    Sheet("Новая статья", name.isNotBlank(), { onDone(null, icon, color) }, {
        onDone(name.trim(), icon, color)
    }, Ink) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Field(name, { name = it }, "Название")
            Text(if (kind == "income") "Значок дохода" else "Значок расхода", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            IconGrid(icon, parseHex(color)) { icon = it }
            Swatches(color) { color = it }
        }
    }
}

@Composable
fun Sheet(title: String, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit, done: Color = Expense, body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Chip).clickable(onClick = onClose), contentAlignment = Alignment.Center) { LineIcon("x", size = 20.dp) }
            Text(title, color = Ink, fontFamily = Display, fontSize = 20.sp)
            Text("Готово", color = Surface, fontFamily = Body, fontWeight = FontWeight.Medium, modifier = Modifier.clip(CircleShape).background(done.copy(alpha = if (canSave) 1f else 0.4f)).clickable(enabled = canSave, onClick = onSave).padding(horizontal = 16.dp, vertical = 12.dp))
        }
        body()
    }
}

@Composable
private fun Picker(value: String, modifier: Modifier, click: () -> Unit) {
    Text(value, color = Ink, fontFamily = Body, modifier = modifier.height(44.dp).border(1.dp, Line, RoundedCornerShape(12.dp)).background(Surface, RoundedCornerShape(12.dp)).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 12.dp))
}

@Composable
private fun AccountPick(accounts: List<Account>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { ChipBtn(it.name, it.id == selected) { onPick(it.id) } }
    }
}

fun pickDate(context: Context, current: String, onPick: (String) -> Unit) {
    val date = runCatching { LocalDate.parse(current.take(10)) }.getOrElse { LocalDate.now() }
    DatePickerDialog(context, { _, y, m, d -> onPick("%04d-%02d-%02d".format(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}

fun pickTime(context: Context, current: String, onPick: (String) -> Unit) {
    val bits = current.split(":")
    val h = bits.getOrNull(0)?.toIntOrNull() ?: 12
    val m = bits.getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(context, { _, hh, mm -> onPick("%02d:%02d".format(hh, mm)) }, h, m, true).show()
}
