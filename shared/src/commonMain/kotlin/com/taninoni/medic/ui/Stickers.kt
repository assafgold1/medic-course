package com.taninoni.medic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.StickerBook
import com.taninoni.medic.data.StickerDef
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Badge colours per chapter, so stickers from different chapters look different at a glance. */
private fun catColors(cat: String): Pair<Color, Color> = when (cat) {
    "anat" -> Color(0xFF8B73FF) to Color(0xFF5B3FE0)
    "cpr" -> Color(0xFFFF5A6A) to Color(0xFFD61F3A)
    "dev" -> Color(0xFFFFC23D) to Color(0xFFF08A00)
    "basics" -> Color(0xFF2DC9B6) to Color(0xFF0E8F82)
    "medical" -> Color(0xFF5B9DFF) to Color(0xFF1E5FD6)
    "trauma" -> Color(0xFFFF7A4D) to Color(0xFFC0391B)
    "env" -> Color(0xFF3CCB80) to Color(0xFF13894F)
    "special" -> Color(0xFFFF77B3) to Color(0xFFD63A7E)
    "ops" -> Color(0xFF6B7A90) to Color(0xFF34404F)
    else -> Color(0xFFFFDE6B) to Color(0xFFF2A10E)
}

fun stickerColor(def: StickerDef) = catColors(def.cat).second

/**
 * A sticker: a die-cut badge in its chapter's colour with the topic's picture, a gloss, the topic number,
 * and a slight tilt of its own. Locked stickers are a grey outline with a question mark.
 */
@Composable
fun Sticker(def: StickerDef, size: Dp, modifier: Modifier = Modifier, locked: Boolean = false) {
    val tilt = ((def.id.hashCode() % 9) - 4) * 2f
    val (c1, c2) = catColors(def.cat)
    Box(modifier.size(size).graphicsLayer { rotationZ = if (locked) 0f else tilt }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val outer = CornerRadius(s * .3f)
            if (locked) {
                drawRoundRect(Color(0x1F888888), Offset(s * .06f, s * .06f), Size(s * .88f, s * .88f), outer)
                drawRoundRect(Color(0x55888888), Offset(s * .06f, s * .06f), Size(s * .88f, s * .88f), outer, style = Stroke(s * .02f))
                return@Canvas
            }
            // Die-cut white border with a drop shadow, then the coloured badge.
            drawRoundRect(Color.Black.copy(alpha = .16f), Offset(s * .04f, s * .07f), Size(s * .92f, s * .92f), outer)
            drawRoundRect(Color.White, Offset(s * .04f, s * .04f), Size(s * .92f, s * .92f), outer)
            val inset = s * .1f
            drawRoundRect(Brush.linearGradient(listOf(c1, c2), Offset(inset, inset), Offset(s - inset, s - inset)), Offset(inset, inset), Size(s - inset * 2, s - inset * 2), CornerRadius(s * .24f))
            // Gloss across the top half.
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .38f), Color.Transparent), inset, s * .5f), Offset(inset, inset), Size(s - inset * 2, (s - inset * 2) * .5f), CornerRadius(s * .24f))
            if (def.cat == "special") for ((dx, dy) in listOf(.2f to .22f, .8f to .3f, .74f to .78f)) {
                val o = Offset(s * dx, s * dy); val r = s * .045f
                val p = Path().apply { moveTo(o.x, o.y - r); quadraticTo(o.x, o.y, o.x + r, o.y); quadraticTo(o.x, o.y, o.x, o.y + r); quadraticTo(o.x, o.y, o.x - r, o.y); quadraticTo(o.x, o.y, o.x, o.y - r); close() }
                drawPath(p, Color.White.copy(alpha = .9f))
            }
        }
        if (locked) Text("?", color = Color(0xAA888888), fontSize = (size.value * .34f).sp, fontWeight = FontWeight.Black)
        else {
            Text(def.emoji, fontSize = (size.value * .44f).sp, modifier = Modifier.graphicsLayer { translationY = -size.toPx() * .01f })
            if (def.number > 0) Box(
                Modifier.align(Alignment.BottomStart).padding(size * .05f).size(size * .3f).clip(CircleShape).background(Color.White)
                    .border(size * .02f, c2, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${def.number}", color = c2, fontSize = (size.value * .14f).sp, fontWeight = FontWeight.Black) }
        }
    }
}

/* ---------------- gift opening ---------------- */

/** The gift box: shakes until tapped, then bursts open to reveal the sticker inside. Shown over everything. */
@Composable
fun GiftOverlay(model: AppModel, nav: Nav) {
    val busy = nav.current is Screen.Quiz || nav.current is Screen.Blitz || nav.current == Screen.AnatomyQuiz
    val pending = model.pendingGifts.firstOrNull()
    val show = pending != null && model.celebration == null && !busy
    var opened by remember { mutableStateOf<StickerDef?>(null) }
    var wasNew by remember { mutableStateOf(true) }
    AnimatedVisibility(show || opened != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        val p = Pal
        val wobble = rememberInfiniteTransition()
        val w by wobble.animateFloat(-1f, 1f, infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse))
        val pop = remember { Animatable(0f) }
        val rays = rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)))
        LaunchedEffect(opened) { if (opened != null) { pop.snapTo(0f); pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 220f)) } }
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0E0807).copy(alpha = .9f))
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp).widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val got = opened
                if (got == null) {
                    Text("יש לך מתנה!", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    val next = model.sticker(pending ?: "")
                    Text(
                        when { next == null -> ""; next.cat == "special" -> next.how; else -> "על שליטה בנושא: ${next.name}" },
                        color = Color.White.copy(.8f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    )
                    Canvas(
                        Modifier.size(200.dp).graphicsLayer { rotationZ = w * 6f }
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                val before = model.stickers
                                model.openGift()?.let { id -> wasNew = id !in before; opened = model.sticker(id); AnswerSound.play(Sfx.STREAK) }
                            },
                    ) { drawGift(size.minDimension) }
                    Text("הקש כדי לפתוח", color = Color.White.copy(.85f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    val col = stickerColor(got)
                    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(260.dp)) {
                            rotate(rays.value) {
                                for (i in 0 until 12) {
                                    val a = i * 30f * PI.toFloat() / 180f
                                    val p2 = Path().apply {
                                        moveTo(center.x, center.y)
                                        lineTo(center.x + cos(a - .09f) * size.width, center.y + sin(a - .09f) * size.width)
                                        lineTo(center.x + cos(a + .09f) * size.width, center.y + sin(a + .09f) * size.width); close()
                                    }
                                    drawPath(p2, col.copy(alpha = .16f * pop.value))
                                }
                            }
                            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .55f), Color.Transparent)), size.minDimension / 2 * pop.value)
                        }
                        Sticker(got, 190.dp, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1f - pop.value) * -25f })
                    }
                    Text(
                        if (got.cat == "special") "מדבקה מיוחדת" else "נושא ${got.number}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(col).padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                    Text(got.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text(
                        if (wasNew) "מדבקה חדשה נוספה לאוסף!" else "כבר יש לך אותה (×${model.stickers[got.id] ?: 1})",
                        color = Color.White.copy(.8f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("לאוסף", Modifier.weight(1f)) { opened = null; nav.go(Screen.Stickers) }
                        PrimaryButton(if (model.pendingGifts.isNotEmpty()) "עוד מתנה" else "סגור", Modifier.weight(1f)) { opened = null }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGift(s: Float) {
    val c = center
    val w = s * .62f
    val bodyTop = c.y - w * .05f
    val body = Rect(c.x - w / 2, bodyTop, c.x + w / 2, bodyTop + w * .62f)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFF8A3D), Color(0xFFE5541A)), body.top, body.bottom), body.topLeft, body.size, CornerRadius(w * .08f))
    val lid = Rect(c.x - w * .56f, bodyTop - w * .2f, c.x + w * .56f, bodyTop + w * .02f)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFA05C), Color(0xFFF26922)), lid.top, lid.bottom), lid.topLeft, lid.size, CornerRadius(w * .06f))
    drawRect(Color.White, Offset(c.x - w * .07f, lid.top), Size(w * .14f, body.bottom - lid.top))
    drawRect(Color.White, Offset(body.left, body.top + body.height * .4f), Size(body.width, w * .12f))
    for (side in listOf(-1f, 1f)) {
        val p = Path().apply {
            moveTo(c.x, lid.top)
            cubicTo(c.x + side * w * .15f, lid.top - w * .35f, c.x + side * w * .42f, lid.top - w * .2f, c.x + side * w * .05f, lid.top)
            close()
        }
        drawPath(p, Color.White)
    }
    drawCircle(Color.White, w * .06f, Offset(c.x, lid.top))
}

/* ---------------- collection ---------------- */

@Composable
fun StickersScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val owned = model.stickers
    val book = model.stickerBook
    GlassPage("האוסף שלי", onBack = { nav.back() }, subtitle = "${book.count { it.id in owned }} מתוך ${book.size} מדבקות") {
        Panel {
            Text(
                "לכל נושא יש מדבקה משלו. מגיעים ל-80% בנושא ומקבלים אותה, ויש מדבקות מיוחדות על הישגים.",
                color = p.muted, fontSize = 14.5.sp, lineHeight = 21.sp,
            )
            ProgressBar(book.count { it.id in owned }.toFloat() / book.size.coerceAtLeast(1), p.blue)
        }
        val groups = listOf("special" to "מיוחדות") + model.categories.map { it.id to it.name }
        for ((cat, title) in groups) {
            val list = book.filter { it.cat == cat }
            if (list.isEmpty()) continue
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(catColors(cat).second))
                Text(title, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("${list.count { it.id in owned }}/${list.size}", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            list.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { def ->
                        val n = owned[def.id] ?: 0
                        val topic = StickerBook.topicId(def.id)
                        Column(
                            Modifier.weight(1f).glass(RoundedCornerShape(22.dp), elevation = 4.dp)
                                .then(if (topic != null) Modifier.bouncy(pressedScale = .96f) { nav.go(Screen.Topic(topic)) } else Modifier)
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Sticker(def, 76.dp, locked = n == 0)
                            Text(
                                def.name, color = if (n > 0) p.ink else p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, maxLines = 2, lineHeight = 16.sp,
                            )
                            if (n == 0) Text(def.how, color = p.muted.copy(alpha = .8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Home-screen entry to the collection: the latest stickers and the count. */
@Composable
fun CollectionCard(model: AppModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Pal
    val latest = model.stickerOrder.takeLast(4).reversed().mapNotNull { model.sticker(it) }
    Row(
        modifier.fillMaxWidth().bouncy(onClick = onClick).deepGlass(RoundedCornerShape(26.dp), elevation = 12.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("האוסף שלי", color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (latest.isEmpty()) "מגיעים ל-80% בנושא ומקבלים את המדבקה שלו" else "${model.stickers.size} מתוך ${model.stickerBook.size} מדבקות",
                color = p.muted, fontSize = 13.sp,
            )
        }
        if (latest.isEmpty()) model.stickerBook.firstOrNull()?.let { Sticker(it, 46.dp, locked = true) }
        else Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) { latest.forEach { Sticker(it, 46.dp) } }
        Chevron()
    }
}
