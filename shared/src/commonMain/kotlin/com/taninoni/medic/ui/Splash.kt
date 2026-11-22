package com.taninoni.medic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.hypot

private val Snappy = CubicBezierEasing(.2f, 0f, 0f, 1f)

/**
 * Opening: solid brand orange. The white cross of the app mark builds itself from its two bars,
 * the heartbeat line draws through it like in the icon, the name rises in, and when the app is
 * ready a circle opens from the mark to reveal it.
 */
@Composable
fun SplashScreen(ready: () -> Boolean, onDone: () -> Unit) {
    val vBar = remember { Animatable(0f) }
    val hBar = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    val beat = remember { Animatable(1f) }
    val name = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { vBar.animateTo(1f, spring(dampingRatio = .62f, stiffness = 420f)) }
            launch { delay(110); hBar.animateTo(1f, spring(dampingRatio = .62f, stiffness = 420f)) }
            launch {
                delay(380)
                line.animateTo(1f, tween(520, easing = Snappy))
                tick()
                beat.animateTo(1.07f, tween(110)); beat.animateTo(1f, spring(dampingRatio = .4f, stiffness = 500f))
            }
            launch { delay(520); name.animateTo(1f, tween(480, easing = Snappy)) }
            launch { delay(720); tag.animateTo(1f, tween(380, easing = Snappy)) }
        }
        snapshotFlow { ready() }.first { it }
        delay(350)
        reveal.animateTo(1f, tween(560, easing = Snappy))
        onDone()
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { }, contentAlignment = Alignment.Center) {
        // Orange field with a hole that grows from the mark and uncovers the app underneath.
        Canvas(Modifier.fillMaxSize()) {
            val r = reveal.value
            if (r <= 0f) drawRect(Brand)
            else {
                val c = Offset(size.width / 2, size.height * .42f)
                val maxR = hypot(size.width, size.height)
                val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, maxR * r)) }
                val field = Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)); op(this, hole, androidx.compose.ui.graphics.PathOperation.Difference) }
                drawPath(field, Brand)
            }
        }
        Column(
            Modifier.graphicsLayer { val r = reveal.value; alpha = 1f - (r * 3f).coerceAtMost(1f); val s = 1f + r * .25f; scaleX = s; scaleY = s },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(132.dp).graphicsLayer { scaleX = beat.value; scaleY = beat.value }) {
                val s = size.minDimension
                val thick = s * .34f
                val long = s
                val c = center
                val corner = CornerRadius(thick * .22f)
                // Vertical bar grows from the middle, then the horizontal one.
                val vh = long * vBar.value
                if (vh > 0f) drawRoundRect(Color.White, Offset(c.x - thick / 2, c.y - vh / 2), Size(thick, vh), corner)
                val hw = long * hBar.value
                if (hw > 0f) drawRoundRect(Color.White, Offset(c.x - hw / 2, c.y - thick / 2), Size(hw, thick), corner)
                // The heartbeat across the horizontal bar, drawn left to right like a monitor.
                if (line.value > 0f) {
                    val l = c.x - long / 2; val w = long; val y = c.y; val a = thick * .36f
                    val pts = listOf(.07f to 0f, .33f to 0f, .41f to -.55f, .50f to 1f, .58f to -.25f, .64f to 0f, .93f to 0f)
                    val path = Path().apply { pts.forEachIndexed { i, (x, dy) -> val px = l + w * x; val py = y + a * dy; if (i == 0) moveTo(px, py) else lineTo(px, py) } }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * line.value, part, true)
                    drawPath(part, Brand, style = Stroke(s * .055f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.height(34.dp))
            // Name and tagline slide up out of a mask.
            Box(Modifier.clipToBounds()) {
                Text(
                    "מסלול חובש", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.graphicsLayer { translationY = (1f - name.value) * 60.dp.toPx() },
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clipToBounds()) {
                Text(
                    "לימוד ותרגול לקורס חובשים", color = Color.White.copy(alpha = .85f), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer { translationY = (1f - tag.value) * 30.dp.toPx() },
                )
            }
        }
    }
}
