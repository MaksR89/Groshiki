package by.smeta.app

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

sealed class ImportResult {
    data class Ok(val book: Book) : ImportResult()
    data class Err(val message: String) : ImportResult()
}

fun importMoneyOk(name: String, bytes: ByteArray): ImportResult {
    val lower = name.lowercase()
    return if (lower.endsWith(".xlsx")) fromXlsx(bytes) else fromCsv(String(bytes, StandardCharsets.UTF_8))
}

fun importBackup(text: String): ImportResult {
    return try {
        val book = bookFromJson(org.json.JSONObject(text))
        if (book.accounts.isEmpty() || book.entries.isEmpty()) ImportResult.Err("В копии нет операций.")
        else ImportResult.Ok(book)
    } catch (_: Exception) {
        ImportResult.Err("Файл не похож на JSON.")
    }
}

private fun fromCsv(text: String): ImportResult {
    val clean = text.removePrefix("\uFEFF")
    val first = clean.lineSequence().firstOrNull().orEmpty()
    val delim = when {
        first.contains('\t') -> '\t'
        first.contains(';') -> ';'
        else -> ','
    }
    val accounts = linkedMapOf<String, Acc>()
    val cats = linkedMapOf<String, Cat>()
    val drafts = mutableListOf<Draft>()
    for (cols in parseRows(clean, delim)) {
        if (cols.all { it.isBlank() }) continue
        val dateRaw = cols.getOrElse(0) { "" }.trim()
        if (dateRaw.isEmpty() || dateRaw == "Дата") continue
        val amount = parseLooseCents(cols.getOrElse(1) { "" })
        val accountName = cols.getOrElse(3) { "" }.trim().ifEmpty { "Мои деньги" }
        val categoryName = cols.getOrElse(4) { "" }.trim()
        val group = cols.getOrElse(5) { "" }.trim()
        val note = cols.getOrElse(6) { "" }.trim()
        if (categoryName == "Остаток на счете") {
            val acc = accounts.getOrPut(accountName) { Acc(accountName) }
            acc.balance = amount
            continue
        }
        if (amount == null || amount == 0L || categoryName.isEmpty()) continue
        val at = parseWhen(dateRaw) ?: continue
        val kind = if (amount < 0) "expense" else "income"
        val acc = accounts.getOrPut(accountName) { Acc(accountName) }
        acc.net += amount
        val cat = cats.getOrPut(categoryName) { Cat(categoryName) }
        if (group.isNotEmpty()) cat.group = group
        if (kind == "income") cat.income += 1 else cat.expense += 1
        drafts.add(Draft(at, kind, kotlin.math.abs(amount), accountName, categoryName, note))
    }
    if (drafts.isEmpty()) return ImportResult.Err("В файле не нашлось операций.")
    return ImportResult.Ok(assemble(accounts.values, cats.values, drafts, emptyList()))
}

private fun fromXlsx(bytes: ByteArray): ImportResult {
    val files = try {
        unzip(bytes)
    } catch (_: Exception) {
        return ImportResult.Err("Файл Excel не открылся.")
    }
    fun read(path: String) = files[path]?.let { String(it, StandardCharsets.UTF_8) }.orEmpty()
    val sheets = sheetIndex(read("xl/workbook.xml"), read("xl/_rels/workbook.xml.rels"))
    val opsPath = sheets["Операции"] ?: return ImportResult.Err("В файле нет листа «Операции». Это не выгрузка Деньги OK.")
    val shared = sharedStrings(read("xl/sharedStrings.xml"))
    val ops = sheetRows(read(opsPath), shared)
    val opHead = headers(ops.firstOrNull())
    val accountsSheet = sheetRows(read(sheets["Счета"] ?: ""), shared)
    val accHead = headers(accountsSheet.firstOrNull())
    val transfers = sheetRows(read(sheets["Переводы"] ?: ""), shared)
    val trHead = headers(transfers.firstOrNull())
    val balances = linkedMapOf<String, Long>()
    for (row in accountsSheet.drop(1)) {
        val name = cell(row, accHead, "Счет").trim()
        val balance = parseLooseCents(cell(row, accHead, "Остаток").trim()) ?: centsNumber(cell(row, accHead, "Остаток"))
        if (name.isNotEmpty() && balance != null) balances[name] = balance
    }
    val net = linkedMapOf<String, Long>()
    val cats = linkedMapOf<String, Cat>()
    val drafts = mutableListOf<Draft>()
    fun touch(name: String, delta: Long) { net[name] = (net[name] ?: 0) + delta }
    for (row in ops.drop(1)) {
        val at = excelStamp(cell(row, opHead, "Дата")) ?: continue
        val kindRaw = cell(row, opHead, "Вид").trim().lowercase()
        val account = cell(row, opHead, "Счет").trim().ifEmpty { "Мои деньги" }
        val group = cell(row, opHead, "Группа").trim()
        val category = cell(row, opHead, "Статья").trim()
        val amount = centsNumber(cell(row, opHead, "Сумма")) ?: continue
        val note = cell(row, opHead, "Комментарий")
        if (amount == 0L || category.isEmpty()) continue
        val kind = if (kindRaw.startsWith("дох")) "income" else "expense"
        touch(account, if (kind == "income") amount else -amount)
        val cat = cats.getOrPut(category) { Cat(category) }
        if (group.isNotEmpty()) cat.group = group
        if (kind == "income") cat.income += 1 else cat.expense += 1
        drafts.add(Draft(at, kind, amount, account, category, note))
    }
    val transfersDraft = mutableListOf<Draft>()
    for (row in transfers.drop(1)) {
        val at = excelStamp(cell(row, trHead, "Дата")) ?: continue
        val from = cell(row, trHead, "Со счета").trim()
        val to = cell(row, trHead, "На счет").trim()
        val amount = centsNumber(cell(row, trHead, "Списано")) ?: continue
        if (from.isEmpty() || to.isEmpty() || amount == 0L) continue
        touch(from, -amount)
        touch(to, amount)
        transfersDraft.add(Draft(at, "transfer", amount, from, "", cell(row, trHead, "Комментарий"), to))
    }
    if (drafts.isEmpty() && transfersDraft.isEmpty()) return ImportResult.Err("В файле не нашлось операций.")
    val names = mutableListOf<String>()
    for (row in accountsSheet.drop(1)) {
        val name = cell(row, accHead, "Счет").trim()
        if (name.isNotEmpty() && name !in names) names.add(name)
    }
    for (name in net.keys) if (name !in names) names.add(name)
    val accs = names.map { name ->
        val flow = net[name] ?: 0
        val balance = balances[name]
        Acc(name, 0, if (balance == null) null else balance - flow)
    }
    return ImportResult.Ok(assemble(accs, cats.values, drafts + transfersDraft, emptyList()))
}

private data class Acc(val name: String, var net: Long = 0, var balance: Long? = null)
private data class Cat(val name: String, var group: String = "", var income: Int = 0, var expense: Int = 0)
private data class Draft(val at: String, val kind: String, val amount: Long, val account: String, val category: String, val note: String, val to: String? = null)

private fun assemble(accs: Collection<Acc>, cats: Collection<Cat>, drafts: List<Draft>, unused: List<Nothing>): Book {
    val accountId = linkedMapOf<String, String>()
    val accounts = accs.map { acc ->
        val id = newId("acc")
        accountId[acc.name] = id
        val opening = if (acc.balance == null) 0 else acc.balance!! - acc.net
        Account(id, acc.name, opening, guessColor(acc.name))
    }
    val categoryId = linkedMapOf<String, String>()
    val categories = cats.map { cat ->
        val id = newId("cat")
        categoryId[cat.name] = id
        val kind = when {
            cat.income > 0 && cat.expense > 0 -> "both"
            cat.income > 0 -> "income"
            else -> "expense"
        }
        Category(id, cat.name, kind, cat.group, guessIcon(cat.name), guessColor(cat.name))
    }
    val fallback = accounts.firstOrNull()?.id ?: newId("acc")
    val entries = drafts.map { d ->
        Entry(
            newId("e"),
            d.kind,
            d.amount,
            if (d.category.isEmpty()) null else categoryId[d.category],
            accountId[d.account] ?: fallback,
            d.to?.let { accountId[it] },
            d.note,
            d.at,
        )
    }.sortedBy { it.at }
    val accountList = if (accounts.isEmpty()) listOf(Account(fallback, "Мои деньги", 0, guessColor("Мои деньги"))) else accounts
    return Book(accounts = accountList, categories = categories, entries = entries).withOperators()
}

private fun parseWhen(raw: String): String? {
    val m = Regex("""^(\d{4})[.\-/](\d{2})[.\-/](\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?""").find(raw.trim()) ?: return null
    val time = if (m.groupValues[4].isNotEmpty()) "T${m.groupValues[4]}:${m.groupValues[5]}:${m.groupValues[6].ifEmpty { "00" }}" else "T12:00:00"
    return "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}$time"
}

private fun parseRows(text: String, delim: Char): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cur = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (quoted) {
            if (ch == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') { cur.append('"'); i += 1 } else quoted = false
            } else cur.append(ch)
        } else when (ch) {
            '"' -> quoted = true
            delim -> { row.add(cur.toString()); cur.clear() }
            '\n' -> { row.add(cur.toString()); rows.add(row); row = mutableListOf(); cur.clear() }
            '\r' -> {}
            else -> cur.append(ch)
        }
        i += 1
    }
    if (cur.isNotEmpty() || row.isNotEmpty()) { row.add(cur.toString()); rows.add(row) }
    return rows
}

private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val out = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) out[entry.name] = zip.readBytes()
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return out
}

private fun decodeXml(value: String): String {
    val amp = "&"
    return value
        .replace(amp + "lt;", "<")
        .replace(amp + "gt;", ">")
        .replace(amp + "quot;", "\"")
        .replace(amp + "apos;", "'")
        .replace(Regex(amp + "#(\\d+);")) { String(Character.toChars(it.groupValues[1].toInt())) }
        .replace(amp + "amp;", amp)
}

private fun textOf(xml: String) = Regex("""<t\b[^>]*>([\s\S]*?)</t>""").findAll(xml).joinToString("") { decodeXml(it.groupValues[1]) }
private fun sharedStrings(xml: String) = Regex("""<si\b[^>]*>([\s\S]*?)</si>""").findAll(xml).map { textOf(it.groupValues[1]) }.toList()

private fun sheetRows(xml: String, shared: List<String>): List<Map<String, String>> {
    val rows = mutableListOf<Map<String, String>>()
    for (row in Regex("""<row\b[^>]*>([\s\S]*?)</row>""").findAll(xml)) {
        val cells = linkedMapOf<String, String>()
        for (cell in Regex("""<c\b([^>]*)(?:/>|>([\s\S]*?)</c>)""").findAll(row.groupValues[1])) {
            val attrs = cell.groupValues[1]
            val ref = Regex("""r="([^"]+)"""").find(attrs)?.groupValues?.get(1).orEmpty()
            val type = Regex("""t="([^"]+)"""").find(attrs)?.groupValues?.get(1).orEmpty()
            val body = cell.groupValues.getOrElse(2) { "" }
            var value = if (type == "inlineStr") textOf(body) else decodeXml(Regex("""<v>([\s\S]*?)</v>""").find(body)?.groupValues?.get(1).orEmpty())
            if (type == "s" && value.isNotEmpty()) value = shared.getOrElse(value.toIntOrNull() ?: -1) { "" }
            val col = ref.replace(Regex("\\d+"), "")
            if (col.isNotEmpty()) cells[col] = value
        }
        if (cells.isNotEmpty()) rows.add(cells)
    }
    return rows
}

private fun headers(row: Map<String, String>?) = row?.entries?.associate { it.value.trim() to it.key } ?: emptyMap()
private fun cell(row: Map<String, String>, head: Map<String, String>, name: String) = head[name]?.let { row[it] }.orEmpty()

private fun centsNumber(raw: String): Long? {
    val n = raw.trim().replace(",", ".").toDoubleOrNull() ?: return null
    return kotlin.math.round(n * 100).toLong()
}

private fun excelStamp(serialRaw: String): String? {
    val serial = serialRaw.trim().replace(",", ".").toDoubleOrNull() ?: return null
    val ms = java.util.Date.UTC(1899 - 1900, 11, 30, 0, 0, 0) + kotlin.math.round(serial * 86400).toLong() * 1000
    val date = java.util.Date(ms)
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.time = date
    fun pad(n: Int) = n.toString().padStart(2, '0')
    val frac = kotlin.math.abs(serial - kotlin.math.truncate(serial))
    val time = if (frac > 1e-6) "${pad(cal.get(java.util.Calendar.HOUR_OF_DAY))}:${pad(cal.get(java.util.Calendar.MINUTE))}:${pad(cal.get(java.util.Calendar.SECOND))}" else "12:00:00"
    return "${cal.get(java.util.Calendar.YEAR)}-${pad(cal.get(java.util.Calendar.MONTH) + 1)}-${pad(cal.get(java.util.Calendar.DAY_OF_MONTH))}T$time"
}

private fun sheetIndex(workbook: String, rels: String): Map<String, String> {
    val byId = Regex("""Id="([^"]+)"[^>]*Target="([^"]+)"""").findAll(rels).associate {
        val target = it.groupValues[2].removePrefix("/")
        it.groupValues[1] to if (target.startsWith("xl/")) target else "xl/$target"
    }
    val sheets = linkedMapOf<String, String>()
    for (tag in Regex("""<sheet\b([^>]*)/>""").findAll(workbook)) {
        val body = tag.groupValues[1]
        val name = Regex("""name="([^"]+)"""").find(body)?.groupValues?.get(1) ?: continue
        val id = Regex("""(?:r:id|[A-Za-z0-9]+:id)="([^"]+)"""").find(body)?.groupValues?.get(1) ?: continue
        val path = byId[id] ?: continue
        sheets[decodeXml(name)] = path
    }
    return sheets
}
