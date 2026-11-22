package com.taninoni.medic.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.window.Dialog
import com.taninoni.medic.res.Res
import com.taninoni.medic.res.uh_icon
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val Pal @Composable get() = LocalPalette.current

/** Groups page content. Pages already sit on glass, so this is a plain column kept for older call sites. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/* ---------------- buttons ---------------- */

/** Glowing jelly button: the accent gradient with a glassy highlight across its top half. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = Pal
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 56.dp)
            .bouncy(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else .45f)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Brand, spotColor = Brand)
            .clip(shape)
            .background(Brand)
            .padding(vertical = 15.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: a glass capsule with accent text. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = Pal
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.heightIn(min = 54.dp).alpha(if (enabled) 1f else .5f)
            .bouncy(enabled = enabled, interaction = src, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (p.dark) Brand.copy(alpha = .18f) else Brand.copy(alpha = .1f))
            .border(1.5.dp, Brand.copy(alpha = if (p.dark) .5f else .35f), RoundedCornerShape(50))
            .padding(vertical = 13.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = if (enabled) p.blue else p.muted, modifier = Modifier.size(19.dp))
        Text(text, color = if (enabled) p.blue else p.muted, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small glass capsule action (hint, bookmark, filter). */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, fg: Color = Pal.ink, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        modifier.bouncy(onClick = onClick).glass(RoundedCornerShape(50), tint = tint, elevation = 0.dp).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text, Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, fg.copy(alpha = .18f), RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 4.dp),
        color = fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
    )
}

@Composable
fun ScorePill(pct: Int, seen: Boolean) {
    val p = Pal
    if (seen) Pill("$pct%", p.tone(pct), p.toneSoft(pct)) else Pill("חדש", p.muted, p.hair)
}

/** Rounded bar with a glowing fill; animates when [fraction] changes. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = Pal.hair, height: Dp = 8.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = .8f, stiffness = 120f))
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        if (f > 0f) Box(
            Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = .75f), color)))
                .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .35f), Color.Transparent), endY = size.height * .6f)) },
        )
    }
}

/** Glass track with a liquid thumb that slides between options. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val p = Pal
    val found = options.indexOfFirst { it.first == selected }
    val index = found.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(if (p.dark) Color.White.copy(alpha = .07f) else Color.Black.copy(alpha = .05f)).padding(4.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * index, spring(dampingRatio = .72f, stiffness = 420f))
        // No thumb until something is chosen, so an unset value doesn't look like the first option.
        if (found >= 0) Box(
            Modifier.offset(x = x).width(w).heightIn(min = 44.dp).height(44.dp)
                .shadow(6.dp, RoundedCornerShape(50), ambientColor = Brand, spotColor = Brand)
                .clip(RoundedCornerShape(50))
                .background(Brand),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (key, label) ->
                val on = key == selected
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(50))
                        .bouncy(label = label, pressedScale = .93f) { if (!on) tick(); onSelect(key) }.padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) Color.White else p.muted, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 14.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/* ---------------- text ---------------- */

@Composable
fun Eyebrow(text: String) = Text(text, color = Pal.muted, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

/** Section heading with an optional trailing action. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = Pal
    Row(modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = p.blue, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(50)).bouncy(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

/* ---------------- containers ---------------- */

/** A frosted glass card that groups related content. */
@Composable
fun Panel(modifier: Modifier = Modifier, spacing: Dp = 14.dp, tint: Color = Color.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalInPanel provides true) {
        Column(
            modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), tint = tint).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** True inside a [Panel]. */
val LocalInPanel = androidx.compose.runtime.compositionLocalOf { false }

/** Hairline divider for rows inside a panel. */
@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Pal.hair))

/** Rounded square holding an icon over a glowing tint of [color]. */
@Composable
fun IconTile(icon: ImageVector, color: Color = Pal.blue, size: Dp = 44.dp, solid: Boolean = false) {
    val shape = RoundedCornerShape(size * .32f)
    Box(
        Modifier.size(size).clip(shape)
            .background(if (solid) Brush.linearGradient(listOf(color, color.copy(alpha = .7f))) else Brush.linearGradient(listOf(color.copy(alpha = .26f), color.copy(alpha = .1f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (solid) .55f else .5f), Color.White.copy(alpha = .05f))), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (solid) Color.White else color, modifier = Modifier.size(size * .52f)) }
}

/** Tappable glass row: icon tile, title and subtitle, trailing slot (a chevron by default). */
@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Pal.blue,
    enabled: Boolean = true,
    inPanel: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = { Chevron() },
    onClick: () -> Unit,
) {
    val p = Pal
    Row(
        modifier.fillMaxWidth().bouncy(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else .5f)
            .then(if (inPanel) Modifier.padding(vertical = 10.dp) else Modifier.glass(RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 14.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconTile(icon, iconColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = p.muted, fontSize = 13.5.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Forward chevron (points left in RTL). */
@Composable
fun Chevron(color: Color = Pal.muted) = Icon(Icons.forward, null, tint = color.copy(alpha = .7f), modifier = Modifier.size(18.dp))

/** Glass confirmation dialog. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = Pal
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Column(
                Modifier.widthIn(max = 380.dp).fillMaxWidth()
                    .shadow(30.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(.3f), spotColor = Color.Black.copy(.3f))
                    .clip(RoundedCornerShape(32.dp)).background(p.solid)
                    .border(1.dp, Brush.verticalGradient(listOf(p.rimHi, p.rimLo)), RoundedCornerShape(32.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(text, color = p.muted, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("ביטול", Modifier.weight(1f), onClick = onDismiss)
                    if (destructive) Box(
                        Modifier.weight(1f).heightIn(min = 54.dp).bouncy(onClick = onConfirm).clip(RoundedCornerShape(50)).background(p.bad).padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(confirm, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                    else PrimaryButton(confirm, Modifier.weight(1f), onClick = onConfirm)
                }
            }
        }
    }
}

/* ---------------- charts ---------------- */

/** Progress ring with a gradient arc and a soft glow at its head. */
@Composable
fun Ring(pct: Int, size: Dp = 104.dp, stroke: Dp = 11.dp, label: String? = null) {
    val p = Pal
    val sweep by animateFloatAsState(360f * pct / 100f, tween(1100))
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(p.hair, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
            if (sweep > 0f) {
                drawArc(p.bandA.copy(alpha = .25f), -90f, sweep, false, Offset(inset, inset), arc, style = Stroke(s * 2.2f, cap = StrokeCap.Round))
                drawArc(
                    Brush.sweepGradient(listOf(p.bandA, p.bandB, p.bandA), center), -90f, sweep, false, Offset(inset, inset), arc,
                    style = Stroke(s, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", color = p.ink, fontSize = (size.value * .24f).sp, fontWeight = FontWeight.Black)
            if (label != null) Text(label, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Radar chart of [values] (0..100) per category label. */
@Composable
fun Radar(labels: List<String>, values: List<Int>, modifier: Modifier = Modifier) {
    val p = Pal
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = p.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    val grow by animateFloatAsState(1f, tween(900))
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
        drawPath(poly { r }, p.hair)
        drawPath(poly { r }, p.muted.copy(alpha = .35f), style = Stroke(1.dp.toPx()))
        drawPath(poly { r / 2 }, p.muted.copy(alpha = .25f), style = Stroke(1.dp.toPx()))
        for (k in 0 until n) drawLine(p.muted.copy(alpha = .25f), c, pt(k, r), 1.dp.toPx())
        val data = poly { k -> maxOf(4f, r * values[k] / 100f * grow) }
        drawPath(data, Brush.linearGradient(listOf(p.bandA.copy(alpha = .45f), p.bandB.copy(alpha = .45f))))
        drawPath(data, Brush.linearGradient(listOf(p.bandA, p.bandB)), style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
        for (k in 0 until n) drawCircle(p.bandA, 4.dp.toPx(), pt(k, maxOf(4f, r * values[k] / 100f * grow)))
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
    Box(Modifier.size(size).glass(CircleShape, elevation = 8.dp).background(Color.White.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
        Image(painterResource(Res.drawable.uh_icon), "מסלול חובש", Modifier.size(size * .72f))
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
