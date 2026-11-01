package by.smeta.app

object Operators {
    val all = listOf(
        Station("st-malanka", "Malanka", "plug", "#2f9e45", "/stations/malanka.png"),
        Station("st-batteryfly", "BatteryFly", "plug", "#10281f", "/stations/batteryfly.png"),
        Station("st-forevo", "forEVo", "plug", "#242424", "/stations/forevo.png"),
        Station("st-evika", "Evika", "plug", "#6a2d91", "/stations/evika.png"),
        Station("st-zaryadka", "Zaryadka", "plug", "#1a6fd4", "/stations/zaryadka.png"),
        Station("st-united", "United Company", "plug", "#14161c", "/stations/united.png"),
        Station("st-chargego", "Charge&GO", "plug", "#0c6b4c", "/stations/chargego.png"),
        Station("st-csms", "ЦСМС", "plug", "#d64545", "/stations/csms.png"),
        Station("st-evon", "EVON", "plug", "#1497b8", "/stations/evon.png"),
        Station("st-orange", "Orange", "plug", "#f08a12", "/stations/orange.png"),
        Station("st-skat", "SKAT", "plug", "#1aa8c4", "/stations/skat.png"),
        Station("st-prizma", "Prizma", "plug", "#5c4d8a", "/stations/prizma.png"),
        Station("st-belgto", "Белтехосмотр", "plug", "#1e7a45", "/stations/belgto.png"),
        Station("st-iskra", "ISKRA", "plug", "#e2b000", "/stations/iskra.png"),
        Station("st-home", "Дом", "plug", "#8c4a32", "/stations/home.png"),
    )
}

val SWATCHES = listOf("#b6402c", "#1f6b45", "#c4892a", "#2f5573", "#8a4d68", "#3f6f62", "#a15a32", "#4c5d8a", "#6d5a3a", "#2a6f86", "#9a3f4a", "#5c5348")
val ICON_KEYS = listOf("basket", "fuel", "ellipsis", "wrench", "phone", "bus", "house", "landmark", "ticket", "receipt", "gift", "hammer", "package", "warehouse", "key", "bag", "cpu", "shirt", "heart", "sofa", "target", "droplets", "wifi", "monitor", "coins", "bitcoin", "alert", "ruler", "baby", "printer", "server", "briefcase", "badge", "car", "tv", "users", "trend", "gem", "store", "coffee", "clover", "repeat", "tags", "plug")

fun guessColor(name: String): String {
    var h = 0
    for (ch in name) h = (h * 31 + ch.code)
    return SWATCHES[(h and Int.MAX_VALUE) % SWATCHES.size]
}

private val BY_NAME = mapOf(
    "продукты" to "basket", "бензин" to "fuel", "другое" to "ellipsis", "сервис" to "wrench", "мобильный" to "phone",
    "проезд" to "bus", "коммуналка" to "house", "рассрочка" to "landmark", "отдых" to "ticket", "налоги" to "receipt",
    "подарки" to "gift", "стройка" to "hammer", "расходка" to "package", "гараж" to "warehouse", "аренда квартиры" to "key",
    "алиэкспрес" to "bag", "одежда" to "shirt", "здоровье" to "heart", "дом" to "sofa", "охота" to "target",
    "гигиена" to "droplets", "интернет" to "wifi", "wb" to "bag", "долг" to "coins", "штраф" to "alert",
    "детям" to "baby", "монитор" to "monitor", "печать" to "printer", "бизнес" to "briefcase", "подработка" to "wrench",
    "автостекло" to "car", "телевизор" to "tv", "дивиденды" to "trend", "кофемастер" to "coffee", "подписки" to "repeat",
)

fun guessIcon(name: String): String {
    BY_NAME[name.trim().lowercase()]?.let { return it }
    var h = 0
    for (ch in name) h = (h * 33 + ch.code)
    return ICON_KEYS[(h and Int.MAX_VALUE) % ICON_KEYS.size]
}

