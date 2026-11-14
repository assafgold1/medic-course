package com.taninoni.medic.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

@Immutable
data class Palette(
    val bandA: Color, val bandB: Color, val blue: Color, val blueSoft: Color,
    val paper: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color, val hair: Color,
    val good: Color, val goodSoft: Color, val mid: Color, val midSoft: Color, val bad: Color, val badSoft: Color,
) {
    val band get() = Brush.linearGradient(listOf(bandA, bandB))
    fun tone(p: Int) = if (p >= 80) good else if (p >= 50) mid else bad
    fun toneSoft(p: Int) = if (p >= 80) goodSoft else if (p >= 50) midSoft else badSoft
}

// Brand orange (#F26922) on white. Token names kept from the first design: "blue" is the brand accent.
private val Light = Palette(
    bandA = Color(0xFFF26922), bandB = Color(0xFFFF8F45), blue = Color(0xFFD9520E), blueSoft = Color(0xFFFFEEE3),
    paper = Color(0xFFFFF6F0), surface = Color.White, ink = Color(0xFF1F1A17), muted = Color(0xFF7A6A60),
    line = Color(0xFFF6C4A6), hair = Color(0xFFF5E5DA),
    good = Color(0xFF16A34A), goodSoft = Color(0xFFE3F6EA), mid = Color(0xFFD48806), midSoft = Color(0xFFFDF2DC),
    bad = Color(0xFFD92D2D), badSoft = Color(0xFFFBE4E4),
)
private val Dark = Palette(
    bandA = Color(0xFFB8460E), bandB = Color(0xFFE5651F), blue = Color(0xFFFF8A47), blueSoft = Color(0xFF3A2114),
    paper = Color(0xFF140D09), surface = Color(0xFF20140E), ink = Color(0xFFF6ECE6), muted = Color(0xFFBFA595),
    line = Color(0xFF5A3524), hair = Color(0xFF3A261B),
    good = Color(0xFF3DD47A), goodSoft = Color(0xFF123523), mid = Color(0xFFF2B73F), midSoft = Color(0xFF3A2C0E),
    bad = Color(0xFFFF6B6B), badSoft = Color(0xFF3D1717),
)

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun MedicTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(primary = p.blue, background = p.paper, surface = p.surface, onSurface = p.ink, onBackground = p.ink)
    else lightColorScheme(primary = p.blue, background = p.paper, surface = p.surface, onSurface = p.ink, onBackground = p.ink)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/* ---------- icons (24x24 stroke icons from SVG path data) ---------- */

private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object Icons {
    val anat = icon("anat",
        "M12 4.5c-2.8-1.6-6.5.3-6.5 3.6 0 .9.3 1.7.8 2.4-1.2.7-1.8 1.9-1.8 3.2 0 2.6 2.2 4.3 4.6 4.1.6 1.2 1.7 1.8 2.9 1.8V4.5z",
        "M12 4.5c2.8-1.6 6.5.3 6.5 3.6 0 .9-.3 1.7-.8 2.4 1.2.7 1.8 1.9 1.8 3.2 0 2.6-2.2 4.3-4.6 4.1-.6 1.2-1.7 1.8-2.9 1.8")
    val cpr = icon("cpr", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z", "M6 12.5h3l1.5-2.5 2 4.5 1.5-2h4")
    val dev = icon("dev", "M13.5 3 5.5 13.5h6l-1 7.5 8-10.5h-6l1-7.5z")
    val basics = icon("basics", "M5 4.5h10.5A3 3 0 0 1 18.5 7.5V20H8a3 3 0 0 1-3-3V4.5z", "M5 17a3 3 0 0 1 3-3h10.5M9 8.5h6")
    val medical = icon("medical", "M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4z", "M12 8v8M8 12h8")
    val trauma = icon("trauma", "M4.6 14.4 14.4 4.6a3.3 3.3 0 0 1 4.7 4.7l-9.8 9.8a3.3 3.3 0 0 1-4.7-4.7z", "M9.5 9.5l5 5M11 13l2-2")
    val env = icon("env", "M3 16c2 0 2-2 4.5-2s2.5 2 4.5 2 2-2 4.5-2 2.5 2 4.5 2M3 20c2 0 2-2 4.5-2s2.5 2 4.5 2 2-2 4.5-2 2.5 2 4.5 2", "M15 8a3 3 0 1 1-6 0a3 3 0 1 1 6 0z")
    val special = icon("special", "M12 8a3 3 0 1 1-6 0a3 3 0 1 1 6 0z", "M3.5 19a5.5 5.5 0 0 1 11 0M16 5.5a2.5 2.5 0 0 1 0 5M20.5 19a4.5 4.5 0 0 0-3.5-4.4")
    val ops = icon("ops", "M2.5 7h11v9.5h-11zM13.5 10.5h4l3 3v3h-7", "M8.3 18a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0z", "M18.8 18a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0z", "M8 9.5v4M6 11.5h4")
    val check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val back = icon("back", "M9 6l6 6-6 6")
    val pulse = icon("pulse", "M3 12.5h4l2-4.5 3.5 9 2.5-6.5 1.5 2h4.5")
    val timer = icon("timer", "M19 13a7 7 0 1 1-14 0a7 7 0 1 1 14 0z", "M12 9.5V13l2.5 2M9.5 3h5")
    val hash = icon("hash", "M5 9h14M5 15h14M10 4 8 20M16 4l-2 16")
    val user = icon("user", "M15.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0z", "M5 20a7 7 0 0 1 14 0")
    val gear = icon("gear", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z")
    val search = icon("search", "M17 10.5a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0z", "M15.5 15.5 20 20")

    fun forCategory(id: String) = when (id) {
        "anat" -> anat; "cpr" -> cpr; "dev" -> dev; "basics" -> basics; "medical" -> medical
        "trauma" -> trauma; "env" -> env; "special" -> special; else -> ops
    }
}
