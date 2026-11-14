package com.taninoni.medic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import com.taninoni.medic.res.Res
import com.taninoni.medic.res.uh_icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val Pal @Composable get() = LocalPalette.current

/** Blue rounded app bar with a back button, like the web version. */
@Composable
fun AppBar(title: String, onBack: (() -> Unit)?) {
    val p = Pal
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(p.band)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(62.dp)
            .padding(horizontal = 14.dp),
    ) {
        if (onBack != null) BandButton(Icons.back, "חזרה", Modifier.align(Alignment.CenterStart), onBack)
        Text(title, Modifier.align(Alignment.Center).padding(horizontal = 52.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
fun BandButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .16f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/** White rounded sheet that holds page content. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 820.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Pal.surface)
            .padding(horizontal = 20.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = Pal
    Box(
        modifier.heightIn(min = 54.dp).clip(RoundedCornerShape(18.dp)).background(if (enabled) p.blue else p.blue.copy(alpha = .4f))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = Pal
    Box(
        modifier.heightIn(min = 54.dp).clip(RoundedCornerShape(18.dp)).border(1.5.dp, p.line, RoundedCornerShape(18.dp)).background(p.surface)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) p.blue else p.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center) }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
fun ScorePill(pct: Int, seen: Boolean) {
    val p = Pal
    if (seen) Pill("$pct%", p.tone(pct), p.toneSoft(pct)) else Pill("חדש", p.muted, p.paper)
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = Pal.hair, height: Dp = 7.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val p = Pal
    val track = if (LocalInPanel.current) p.hair else p.paper
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(track).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) p.surface else Color.Transparent)
                    .clickable { onSelect(key) }.padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) p.blue else p.muted, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun Eyebrow(text: String) = Text(text, color = Pal.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)

/** Section heading inside a sheet, with optional trailing action. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = Pal
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = p.blue, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(8.dp))
        }
    }
}

/** Soft filled panel used to group related controls (no nested borders). */
@Composable
fun Panel(modifier: Modifier = Modifier, spacing: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalInPanel provides true) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Pal.paper).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** True inside a [Panel], so controls can pick a track color that still shows on the panel's fill. */
val LocalInPanel = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun Ring(pct: Int, size: Dp = 104.dp, color: Color = Color.White, track: Color = Color.White.copy(alpha = .22f)) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * pct / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$pct%", color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Radar chart of [values] (0..100) per category label, drawn on the blue band. */
@Composable
fun Radar(labels: List<String>, values: List<Int>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Canvas(modifier.fillMaxWidth().height(280.dp)) {
        val n = labels.size
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2 - 42.dp.toPx()
        fun pt(k: Int, rr: Float): Offset {
            val a = -PI / 2 + k * 2 * PI / n
            return Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
        }
        fun poly(rr: (Int) -> Float) = Path().apply {
            for (k in 0 until n) { val o = pt(k, rr(k)); if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            close()
        }
        drawPath(poly { r }, Color.White.copy(alpha = .18f))
        drawPath(poly { r }, Color.White.copy(alpha = .85f), style = Stroke(1.5.dp.toPx()))
        drawPath(poly { r / 2 }, Color.White.copy(alpha = .35f), style = Stroke(1.dp.toPx()))
        for (k in 0 until n) drawLine(Color.White.copy(alpha = .5f), c, pt(k, r), 1.dp.toPx())
        val data = poly { k -> maxOf(4f, r * values[k] / 100f) }
        drawPath(data, Color(0xFF8A3206).copy(alpha = .55f))
        drawPath(data, Color(0xFF7A2C05), style = Stroke(4.dp.toPx(), join = StrokeJoin.Round))
        for (k in 0 until n) {
            val layout = measurer.measure(labels[k], labelStyle)
            val o = pt(k, r + 18.dp.toPx())
            val x = when {
                kotlin.math.abs(o.x - c.x) < 20f -> o.x - layout.size.width / 2
                o.x < c.x -> o.x - layout.size.width
                else -> o.x
            }
            drawText(layout, topLeft = Offset(x, o.y - layout.size.height / 2))
        }
    }
}

@Composable
fun Logo(size: Dp = 46.dp) {
    val p = Pal
    Box(Modifier.size(size).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Image(painterResource(Res.drawable.uh_icon), "מסלול חובש", Modifier.size(size * .72f))
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/**
 * Paints the area under a scrolling [Sheet] white below its rounded top, so a short page
 * doesn't end in a gray band above the bottom of the screen.
 */
@Composable
fun Modifier.sheetBackdrop(): Modifier {
    val color = Pal.surface
    return drawBehind {
        val top = 60.dp.toPx()
        if (size.height > top) drawRect(color, Offset(0f, top), Size(size.width, size.height - top))
    }
}
