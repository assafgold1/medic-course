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
    val dark: Boolean,
    val bandA: Color, val bandB: Color, val blue: Color, val blueSoft: Color,
    val paper: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color, val hair: Color,
    val good: Color, val goodSoft: Color, val mid: Color, val midSoft: Color, val bad: Color, val badSoft: Color,
    /** Opaque fill for dialogs and other surfaces that must hide what is behind them. */
    val solid: Color,
    /** Ambient backdrop: base gradient and the glowing blobs that drift behind the glass. */
    val baseTop: Color, val baseBottom: Color, val blobs: List<Color>,
    /** Glass material: fill gradient and the specular rim. */
    val glassTop: Color, val glassBottom: Color, val rimHi: Color, val rimLo: Color, val glassShadow: Color,
) {
    /** Accent gradient (brand orange to warm coral), used by primary buttons, rings and bars. */
    val band get() = Brush.linearGradient(listOf(bandA, bandB))
    fun tone(p: Int) = if (p >= 80) good else if (p >= 50) mid else bad
    fun toneSoft(p: Int) = if (p >= 80) goodSoft else if (p >= 50) midSoft else badSoft
}

// Brand orange (#F26922) as liquid light under frosted glass. Token names kept from the first design: "blue" is the brand accent.
private val Light = Palette(
    dark = false,
    bandA = Color(0xFFFF7A1F), bandB = Color(0xFFFF3D6E), blue = Color(0xFFE2560B), blueSoft = Color(0x24F26922),
    paper = Color(0x66FFFFFF), surface = Color(0x9EFFFFFF), ink = Color(0xFF1C1411), muted = Color(0xFF7C6A62),
    line = Color(0x55F26922), hair = Color(0x14000000),
    good = Color(0xFF0E9F55), goodSoft = Color(0x2416A34A), mid = Color(0xFFC97A00), midSoft = Color(0x26F5A400),
    bad = Color(0xFFE0263B), badSoft = Color(0x22E0263B),
    solid = Color(0xFFFFFBF8),
    baseTop = Color(0xFFFFF3EA), baseBottom = Color(0xFFFCE7EE),
    blobs = listOf(Color(0xFFFF8A3D), Color(0xFFFF4F7B), Color(0xFFFFC34D), Color(0xFFA996FF), Color(0xFFFF6A2A)),
    glassTop = Color(0xB8FFFFFF), glassBottom = Color(0x80FFFFFF), rimHi = Color(0xF2FFFFFF), rimLo = Color(0x33FFFFFF),
    glassShadow = Color(0x22B4501E),
)
private val Dark = Palette(
    dark = true,
    bandA = Color(0xFFFF7A1F), bandB = Color(0xFFFF3D6E), blue = Color(0xFFFF8E4D), blueSoft = Color(0x2EFF7A1F),
    paper = Color(0x0FFFFFFF), surface = Color(0x17FFFFFF), ink = Color(0xFFFBF2EC), muted = Color(0xFFB8A39A),
    line = Color(0x66FF8E4D), hair = Color(0x1AFFFFFF),
    good = Color(0xFF3EE08A), goodSoft = Color(0x2E3EE08A), mid = Color(0xFFFFC147), midSoft = Color(0x2EFFC147),
    bad = Color(0xFFFF5C6E), badSoft = Color(0x30FF5C6E),
    solid = Color(0xFF1C1316),
    baseTop = Color(0xFF0E0807), baseBottom = Color(0xFF150A12),
    blobs = listOf(Color(0xFFE5550F), Color(0xFFD0245C), Color(0xFFB36B00), Color(0xFF4B3BCB), Color(0xFFFF6A2A)),
    glassTop = Color(0x26FFFFFF), glassBottom = Color(0x0DFFFFFF), rimHi = Color(0x66FFFFFF), rimLo = Color(0x0FFFFFFF),
    glassShadow = Color(0x40000000),
)

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun MedicTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(primary = p.blue, background = p.baseTop, surface = p.solid, onSurface = p.ink, onBackground = p.ink)
    else lightColorScheme(primary = p.blue, background = p.baseTop, surface = p.solid, onSurface = p.ink, onBackground = p.ink)
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

    val forward = icon("forward", "M15 6l-6 6 6 6")
    val close = icon("close", "M6 6l12 12M18 6 6 18")
    val bolt = icon("bolt", "M13 2.5 4.5 13.5H11l-1 8 8.5-11H12l1-8z")
    val flame = icon("flame", "M12 21c-3.9 0-6.5-2.6-6.5-6.2 0-3.4 2.4-5.4 3.6-8.3.9 1.6 1.2 2.8 1.2 4 1.2-.7 2.3-2.6 2.3-5.5 3 2.2 5.9 5.6 5.9 9.8 0 3.6-2.6 6.2-6.5 6.2z")
    val bookmark = icon("bookmark", "M6.5 3.5h11v17l-5.5-4-5.5 4z")
    val sparkle = icon("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z", "M19 16l.7 1.8 1.8.7-1.8.7L19 21l-.7-1.8-1.8-.7 1.8-.7z")
    val trophy = icon("trophy", "M7.5 4h9v5a4.5 4.5 0 0 1-9 0V4z", "M7.5 6H4.5a3 3 0 0 0 3 4M16.5 6h3a3 3 0 0 1-3 4M12 13.5V17M8.5 20.5h7M9.5 17h5v3.5h-5z")
    val shield = icon("shield", "M12 3l7.5 3v5.5c0 4.6-3.2 8.2-7.5 9.5-4.3-1.3-7.5-4.9-7.5-9.5V6L12 3z", "M12 8v4.5M12 15.8v.2")
    val flow = icon("flow", "M9 3.5h6v4H9zM4 16.5h6v4H4zM14 16.5h6v4h-6z", "M12 7.5v4.5M7 16.5V12h10v4.5")
    val cards = icon("cards", "M8 4h10a2 2 0 0 1 2 2v11", "M4 8a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8z")
    val eye = icon("eye", "M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z", "M14.5 12a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z")
    val target = icon("target", "M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M12.8 12a.8.8 0 1 1-1.6 0a.8.8 0 1 1 1.6 0z")
    val half = icon("half", "M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 4v16")
    val moon = icon("moon", "M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z")
    val sound = icon("sound", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11")
    val textSize = icon("textSize", "M3.5 18 8 6l4.5 12M5.2 14h5.6", "M14 18l3-8 3 8M15.1 15.5h3.8")
    val logout = icon("logout", "M14 4.5H6.5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2H14", "M10 12h10M16.5 8.5 20 12l-3.5 3.5")
    val trash = icon("trash", "M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13")
    val info = icon("info", "M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 11v5.5M12 7.8v.2")

    fun forCategory(id: String) = when (id) {
        "anat" -> anat; "cpr" -> cpr; "dev" -> dev; "basics" -> basics; "medical" -> medical
        "trauma" -> trauma; "env" -> env; "special" -> special; else -> ops
    }
}
