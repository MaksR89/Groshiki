package by.smeta.app

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Account(val id: String, val name: String, val opening: Long, val color: String)
data class Category(val id: String, val name: String, val kind: String, val group: String, val icon: String, val color: String)
data class Entry(
    val id: String,
    val kind: String,
    val amount: Long,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String?,
    val note: String,
    val at: String,
)
data class Station(val id: String, val name: String, val icon: String, val color: String, val logo: String)
data class Charge(val id: String, val stationId: String, val kwh: Long, val rate: Long, val price: Long, val at: String)
data class MoneyRow(
    val id: String,
    val amount: Long,
    val currency: String,
    val note: String,
    val at: String,
    val who: String = "",
    val bank: String = "",
    val rate: Double = 0.0,
    val until: String = "",
)
data class LastCat(val expense: String? = null, val income: String? = null)

data class Book(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val stations: List<Station> = Operators.all,
    val charges: List<Charge> = emptyList(),
    val aids: List<MoneyRow> = emptyList(),
    val holds: List<MoneyRow> = emptyList(),
    val deposits: List<MoneyRow> = emptyList(),
    val lastCategory: LastCat = LastCat(),
    val showBalance: Boolean = false,
    val showCharges: Boolean = true,
    val showReport: Boolean = true,
    val showReserve: Boolean = false,
    val reserveHash: String = "",
)

fun newId(prefix: String) = "$prefix-${UUID.randomUUID()}"

fun accountBalance(account: Account, entries: List<Entry>): Long {
    var n = account.opening
    for (e in entries) {
        when {
            e.kind == "income" && e.accountId == account.id -> n += e.amount
            e.kind == "expense" && e.accountId == account.id -> n -= e.amount
            e.kind == "transfer" && e.accountId == account.id -> n -= e.amount
            e.kind == "transfer" && e.toAccountId == account.id -> n += e.amount
        }
    }
    return n
}

fun totalBalance(book: Book) = book.accounts.sumOf { accountBalance(it, book.entries) }

fun Book.toJson(): JSONObject = JSONObject().apply {
    put("accounts", JSONArray().apply { accounts.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("opening", it.opening).put("color", it.color)) } })
    put("categories", JSONArray().apply { categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind).put("group", it.group).put("icon", it.icon).put("color", it.color)) } })
    put("entries", JSONArray().apply {
        entries.forEach {
            put(JSONObject().put("id", it.id).put("kind", it.kind).put("amount", it.amount).put("categoryId", it.categoryId ?: JSONObject.NULL).put("accountId", it.accountId).put("toAccountId", it.toAccountId ?: JSONObject.NULL).put("note", it.note).put("at", it.at))
        }
    })
    put("stations", JSONArray().apply { stations.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("color", it.color).put("logo", it.logo)) } })
    put("charges", JSONArray().apply { charges.forEach { put(JSONObject().put("id", it.id).put("stationId", it.stationId).put("kwh", it.kwh).put("rate", it.rate).put("price", it.price).put("at", it.at)) } })
    put("aids", JSONArray().apply { aids.forEach { put(it.toJson(true)) } })
    put("holds", JSONArray().apply { holds.forEach { put(it.toJson(false)) } })
    put("deposits", JSONArray().apply { deposits.forEach { put(it.toJson(false).put("bank", it.bank).put("rate", it.rate).put("until", it.until)) } })
    put("lastCategory", JSONObject().put("expense", lastCategory.expense ?: JSONObject.NULL).put("income", lastCategory.income ?: JSONObject.NULL))
    put("showBalance", showBalance)
    put("showCharges", showCharges)
    put("showReport", showReport)
    put("showReserve", showReserve)
    put("reserveHash", reserveHash)
}

private fun MoneyRow.toJson(withWho: Boolean): JSONObject = JSONObject().put("id", id).put("amount", amount).put("currency", currency).put("note", note).put("at", at).apply {
    if (withWho) put("who", who)
}

fun bookFromJson(raw: JSONObject): Book {
    val root = if (raw.has("state") && raw.opt("state") is JSONObject) raw.getJSONObject("state") else raw
    val reserve = root.optJSONObject("reserve")
    val last = root.optJSONObject("lastCategory")
    return Book(
        accounts = root.optJSONArray("accounts").toList { it.toAccount() },
        categories = root.optJSONArray("categories").toList { it.toCategory() },
        entries = root.optJSONArray("entries").toList { it.toEntry() }.sortedBy { it.at },
        stations = root.optJSONArray("stations").toList { it.toStation() }.ifEmpty { Operators.all },
        charges = root.optJSONArray("charges").toList { it.toCharge() },
        aids = (reserve?.optJSONArray("aids") ?: root.optJSONArray("aids")).toList { it.toMoney(true) },
        holds = (reserve?.optJSONArray("holds") ?: root.optJSONArray("holds")).toList { it.toMoney(false) },
        deposits = (reserve?.optJSONArray("deposits") ?: root.optJSONArray("deposits")).toList { it.toMoney(false) },
        lastCategory = LastCat(last?.optString("expense")?.ifBlank { null }, last?.optString("income")?.ifBlank { null }),
        showBalance = root.optBoolean("showBalance", false),
        showCharges = if (root.has("showCharges")) root.optBoolean("showCharges") else true,
        showReport = if (root.has("showReport")) root.optBoolean("showReport") else true,
        showReserve = root.optBoolean("showReserve", false),
        reserveHash = root.optString("reserveHash", ""),
    ).withOperators()
}

fun Book.withOperators(): Book {
    val known = stations.map { it.id }.toSet()
    val extra = Operators.all.filter { it.id !in known }
    return if (extra.isEmpty()) this else copy(stations = stations + extra)
}

private fun JSONObject.toAccount() = Account(optString("id"), optString("name"), optLong("opening"), optString("color", "#2f5573"))
private fun JSONObject.toCategory() = Category(optString("id"), optString("name"), optString("kind", "expense"), optString("group"), optString("icon", "ellipsis"), optString("color", "#5c5348"))
private fun JSONObject.toEntry() = Entry(optString("id"), optString("kind"), optLong("amount"), optString("categoryId").ifBlank { null }, optString("accountId"), optString("toAccountId").ifBlank { null }, optString("note"), optString("at"))
private fun JSONObject.toStation() = Station(optString("id"), optString("name"), optString("icon", "plug"), optString("color", "#5c5348"), optString("logo"))
private fun JSONObject.toCharge() = Charge(optString("id"), optString("stationId"), optLong("kwh"), optLong("rate"), optLong("price"), optString("at"))
private fun JSONObject.toMoney(aid: Boolean) = MoneyRow(optString("id"), optLong("amount"), optString("currency", "BYN"), optString("note"), optString("at"), if (aid) optString("who") else "", optString("bank"), optDouble("rate"), optString("until"))

private inline fun <T> JSONArray?.toList(map: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) optJSONObject(i)?.let { add(map(it)) }
    }
}
