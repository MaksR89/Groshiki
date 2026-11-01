package by.smeta.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Desk = Color(0xFFE7DFD2)
val Paper = Color(0xFFF4EFE6)
val Surface = Color(0xFFFFFDF8)
val Ink = Color(0xFF1C1712)
val Muted = Color(0xFF6F675E)
val Line = Color(0xFFE3D8C8)
val Chip = Color(0xFFF1E9DC)
val Expense = Color(0xFFB4332A)
val Income = Color(0xFF1B6B45)

val Display = FontFamily(Font(R.font.literata_semibold, FontWeight.SemiBold))
val Body = FontFamily(Font(R.font.manrope_medium, FontWeight.Medium), Font(R.font.manrope_semibold, FontWeight.SemiBold))

fun parseHex(hex: String): Color {
    val v = hex.removePrefix("#").toLongOrNull(16) ?: return Muted
    return Color(0xFF000000 or v)
}

private val BYN = "M0.26 58.54L13.02 58.28L13.02 0.26L62.54 0.26L62.54 10.06L22.82 10.06L22.69 37.13L45.00 37.13L50.80 37.78L58.54 40.62L63.57 44.10L66.40 46.93L68.85 50.29L70.92 54.41L72.21 58.80L72.59 64.34L72.08 68.60L70.40 73.62L67.44 78.65L63.95 82.39L56.60 87.16L52.22 88.71L47.32 89.61L13.02 89.61L13.02 68.47L0.26 68.21L0.26 58.54ZM22.82 46.93L22.82 58.54L45.26 58.54L45.26 68.21L22.82 68.21L22.82 79.94L46.16 79.94L49.38 79.43L52.09 78.52L56.48 75.95L60.73 71.17L62.79 65.37L62.41 59.57L59.83 54.28L55.32 50.03L51.19 47.97L46.68 46.93L22.82 46.93Z"

@Composable
fun LineIcon(name: String, modifier: Modifier = Modifier, tint: Color = Ink, size: Dp = 22.dp) {
    val paths = GLYPHS[name] ?: GLYPHS["ellipsis"]!!
    val parsed = remember(name) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        withTransform({ scale(this.size.minDimension / 24f, this.size.minDimension / 24f, Offset.Zero) }) {
            parsed.forEach { drawPath(it, tint, style = Stroke(width = 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

@Composable
fun BynMark(modifier: Modifier = Modifier, tint: Color = Ink, height: Dp = 14.dp) {
    val path = remember { PathParser().parsePathString(BYN).toPath() }
    Canvas(modifier.size(width = height * 0.8f, height = height)) {
        withTransform({ scale(size.width / 73f, size.height / 90f, Offset.Zero) }) {
            drawPath(path, tint)
        }
    }
}

@Composable
fun MoneyText(cents: Long, modifier: Modifier = Modifier, color: Color = Ink, size: TextUnit = 16.sp, sign: Boolean = false, font: FontFamily = Body, weight: FontWeight = FontWeight.Medium) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formatMoney(cents, sign), color = color, fontFamily = font, fontSize = size, fontWeight = weight)
        BynMark(tint = color, height = (size.value * 0.62f).dp, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
fun ForeignSum(cents: Long, currency: String, size: TextUnit = 22.sp, color: Color = Ink) {
    val mark = when (currency) { "EUR" -> "€"; "USD" -> "$"; "RUB" -> "₽"; else -> "" }
    if (currency == "BYN" || mark.isEmpty()) MoneyText(cents, color = color, size = size, font = Display, weight = FontWeight.SemiBold)
    else Text("${formatMoney(cents)} $mark", color = color, fontFamily = Display, fontSize = size, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Keypad(signed: Boolean = false, onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (signed) PadKey("±", Modifier.fillMaxWidth(), onKey)
        keys.chunked(3).forEach { row ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key -> PadKey(key, Modifier.weight(1f), onKey) }
            }
        }
    }
}

@Composable
private fun PadKey(key: String, modifier: Modifier, onKey: (String) -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(if (key == "±") Chip else Surface).clickable { onKey(key) },
        contentAlignment = Alignment.Center,
    ) {
        if (key == "⌫") LineIcon("delete", tint = Ink, size = 20.dp) else Text(key, color = Ink, fontFamily = Body, fontSize = 20.sp)
    }
}

@Composable
fun AmountButton(raw: String, active: Boolean, placeholder: String, onPress: () -> Unit) {
    val shown = if (raw.isNotEmpty() && raw != "-") formatRawInput(raw) else ""
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Surface)
            .then(if (active) Modifier.padding(1.dp) else Modifier)
            .clickable(onClick = onPress)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (active && shown.isEmpty()) Box(Modifier.padding(end = 4.dp).size(width = 2.dp, height = 28.dp).background(Ink))
            if (shown.isEmpty()) Text(placeholder, color = Ink.copy(alpha = 0.25f), fontFamily = Body, fontSize = 20.sp)
            else Text(shown, color = Ink, fontFamily = Display, fontSize = 24.sp)
            if (active && shown.isNotEmpty()) Box(Modifier.padding(start = 2.dp).size(width = 2.dp, height = 28.dp).background(Ink))
        }
    }
}

object Logos {
    private val cache = HashMap<String, ImageBitmap?>()
    fun get(context: Context, logo: String): ImageBitmap? {
        if (logo.isBlank()) return null
        if (cache.containsKey(logo)) return cache[logo]
        val name = logo.substringAfterLast("/")
        val bmp = try {
            context.assets.open("stations/$name").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (_: Exception) { null }
        cache[logo] = bmp
        return bmp
    }
}

@Composable
fun StationMark(station: Station, size: Dp = 32.dp) {
    val context = LocalContext.current
    val image = remember(station.logo) { Logos.get(context, station.logo) }
    if (image != null) {
        androidx.compose.foundation.Image(image, null, Modifier.size(size).clip(RoundedCornerShape(8.dp)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Chip), contentAlignment = Alignment.Center) {
            LineIcon(station.icon.ifBlank { "plug" }, tint = parseHex(station.color), size = size * 0.6f)
        }
    }
}

fun fieldStyle() = TextStyle(color = Ink, fontFamily = Body, fontSize = 16.sp)
val caret = SolidColor(Ink)
