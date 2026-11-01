package by.smeta.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

class Repo(private val context: Context) {
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
            replace(bookFromJson(JSONObject(raw)))
        } catch (_: Exception) {
        }
    }
}

fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("groshyki:$pin".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
