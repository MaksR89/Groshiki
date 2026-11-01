package by.smeta.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

class Repo(context: Context) {
    private val file = File(context.filesDir, "book.json")
    var book by mutableStateOf(if (file.exists()) bookFromJson(JSONObject(file.readText())) else Book())
        private set

    fun hasFile() = file.exists()

    fun replace(next: Book) {
        book = next.withOperators()
        file.writeText(book.toJson().toString())
    }

    fun update(block: (Book) -> Book) = replace(block(book))

    fun importOld(raw: String) {
        if (raw.isBlank() || file.exists()) return
        try {
            val next = bookFromJson(JSONObject(raw))
            if (next.accounts.isEmpty() && next.entries.isEmpty()) return
            replace(next)
        } catch (_: Exception) {
        }
    }

    fun addEntry(entry: Entry) {
        val last = book.lastCategory.let {
            if (entry.kind == "expense" && entry.categoryId != null) it.copy(expense = entry.categoryId)
            else if (entry.kind == "income" && entry.categoryId != null) it.copy(income = entry.categoryId)
            else it
        }
        update { it.copy(entries = (it.entries + entry).sortedBy { e -> e.at }, lastCategory = last) }
    }

    fun updateEntry(entry: Entry) = update { b -> b.copy(entries = b.entries.map { if (it.id == entry.id) entry else it }.sortedBy { it.at }) }
    fun removeEntry(id: String) = update { it.copy(entries = it.entries.filter { e -> e.id != id }) }

    fun addAccount(name: String, opening: Long): String {
        val id = newId("a")
        update { it.copy(accounts = it.accounts + Account(id, name, opening, guessColor(name))) }
        return id
    }

    fun updateAccount(id: String, name: String? = null, opening: Long? = null) = update { b ->
        b.copy(accounts = b.accounts.map { if (it.id == id) it.copy(name = name ?: it.name, opening = opening ?: it.opening) else it })
    }

    fun removeAccount(id: String): String? {
        if (book.accounts.size < 2) return "Нужен хотя бы один счёт."
        if (book.entries.any { it.accountId == id || it.toAccountId == id }) return "На счёте есть операции. Сначала перенесите их."
        update { it.copy(accounts = it.accounts.filter { a -> a.id != id }) }
        return null
    }

    fun addCategory(name: String, kind: String, group: String, icon: String, color: String): String {
        val id = newId("c")
        update { it.copy(categories = it.categories + Category(id, name, kind, group, icon, color)) }
        return id
    }

    fun updateCategory(id: String, next: Category) = update { b -> b.copy(categories = b.categories.map { if (it.id == id) next.copy(id = id) else it }) }

    fun removeCategory(id: String): String? {
        val used = book.entries.count { it.categoryId == id }
        if (used > 0) return "Статья используется в $used операциях."
        update { it.copy(categories = it.categories.filter { c -> c.id != id }) }
        return null
    }

    fun addStation(name: String, icon: String, color: String, logo: String) = update {
        it.copy(stations = it.stations + Station(newId("st"), name, icon, color, logo))
    }

    fun updateStation(id: String, name: String, icon: String, color: String) = update { b ->
        b.copy(stations = b.stations.map { if (it.id == id) it.copy(name = name, icon = icon, color = color) else it })
    }

    fun removeStation(id: String): String? {
        val used = book.charges.count { it.stationId == id }
        if (used > 0) return "Станция используется в $used зарядках."
        update { it.copy(stations = it.stations.filter { s -> s.id != id }) }
        return null
    }

    fun restoreOperators() = update { it.withOperators() }
    fun addCharge(row: Charge) = update { it.copy(charges = (it.charges + row).sortedBy { c -> c.at }) }
    fun updateCharge(row: Charge) = update { b -> b.copy(charges = b.charges.map { if (it.id == row.id) row else it }.sortedBy { it.at }) }
    fun removeCharge(id: String) = update { it.copy(charges = it.charges.filter { c -> c.id != id }) }

    fun saveAid(row: MoneyRow, existing: Boolean) = update { b ->
        val list = if (existing) b.aids.map { if (it.id == row.id) row else it } else b.aids + row
        b.copy(aids = list.sortedByDescending { it.at })
    }
    fun removeAid(id: String) = update { it.copy(aids = it.aids.filter { a -> a.id != id }) }
    fun saveHold(row: MoneyRow, existing: Boolean) = update { b ->
        val list = if (existing) b.holds.map { if (it.id == row.id) row else it } else b.holds + row
        b.copy(holds = list.sortedByDescending { it.at })
    }
    fun removeHold(id: String) = update { it.copy(holds = it.holds.filter { a -> a.id != id }) }
    fun saveDeposit(row: MoneyRow, existing: Boolean) = update { b ->
        val list = if (existing) b.deposits.map { if (it.id == row.id) row else it } else b.deposits + row
        b.copy(deposits = list.sortedByDescending { it.at })
    }
    fun removeDeposit(id: String) = update { it.copy(deposits = it.deposits.filter { a -> a.id != id }) }

    fun clear() = update {
        it.copy(accounts = emptyList(), categories = emptyList(), entries = emptyList(), lastCategory = LastCat())
    }
}

fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("groshyki:$pin".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
