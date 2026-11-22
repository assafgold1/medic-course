package com.taninoni.medic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.res.Res
import com.taninoni.medic.res.uh_icon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private val Spark = Color(0xFFFFB061)
private val Ember = Color(0xFFFF6A2A)

/** Heartbeat height at [x] (0..1 across the screen): flat, then P wave, a tall QRS spike at the middle, and a T wave. */
private fun beatAt(x: Float): Float {
    val d = x - .5f
    fun bump(c: Float, w: Float, h: Float) = h * exp(-((d - c) / w).let { it * it })
    return bump(-.09f, .025f, .10f) + // P
        bump(-.012f, .006f, -.16f) + // Q
        bump(0f, .009f, 1f) + // R
        bump(.016f, .007f, -.32f) + // S
        bump(.11f, .035f, .2f) // T
}

/**
 * Opening: a monitor trace sweeps across the screen; at the heartbeat a flash and a shockwave go off,
 * the app mark pops out of the spike in a glass orb with sparks flying outward, and the name rises in
 * letter by letter. Waits until [ready], then the whole scene zooms forward into the app.
 */
@Composable
fun SplashScreen(ready: () -> Boolean, onDone: () -> Unit) {
    val trace = remember { Animatable(0f) }
    val traceFade = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }
    val turn = remember { Animatable(-120f) }
    val title = remember { Animatable(0f) }
    val sub = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { trace.animateTo(1f, tween(1100, easing = LinearEasing)) }
            // The beat sits at the middle of the screen: everything fires as the trace reaches it.
            launch {
                snapshotFlow { trace.value }.first { it >= .5f }
                tick()
                launch { flash.animateTo(1f, tween(70)); flash.animateTo(0f, tween(420)) }
                launch { burst.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing)) }
                launch { turn.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
                logo.animateTo(1f, spring(dampingRatio = .42f, stiffness = 260f))
            }
            launch { snapshotFlow { trace.value }.first { it >= .72f }; title.animateTo(1f, tween(700, easing = LinearEasing)) }
            launch { snapshotFlow { title.value }.first { it >= .6f }; sub.animateTo(1f, tween(450)) }
            launch { snapshotFlow { trace.value }.first { it >= 1f }; traceFade.animateTo(.28f, tween(600)) }
        }
        // A heartbeat on the orb while the app finishes loading.
        launch { while (true) { pulse.snapTo(0f); pulse.animateTo(1f, tween(900, easing = LinearEasing)); delay(250) } }
        snapshotFlow { ready() }.first { it }
        delay(550)
        exit.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        onDone()
    }

    val p = Pal
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val e = exit.value
                alpha = 1f - e
                scaleX = 1f + e * .35f; scaleY = scaleX
            }
            .pointerInput(Unit) { },
        contentAlignment = Alignment.Center,
    ) {
        AmbientBackdrop()

        // Monitor trace, shockwave and sparks, drawn across the whole screen.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cy = h * .42f
            val amp = h * .13f
            val head = trace.value
            if (head > 0f) {
                val path = Path()
                val n = 220
                val last = (n * head).toInt()
                for (i in 0..last) {
                    val x = i / n.toFloat()
                    val y = cy - beatAt(x) * amp
                    if (i == 0) path.moveTo(x * w, y) else path.lineTo(x * w, y)
                }
                val a = traceFade.value
                drawPath(path, Ember.copy(alpha = .22f * a), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Spark.copy(alpha = .9f * a), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (head < 1f) {
                    val hx = head * w
                    val hy = cy - beatAt(head) * amp
                    drawCircle(Brush.radialGradient(listOf(Color.White, Spark.copy(alpha = .6f), Color.Transparent), Offset(hx, hy), 26.dp.toPx()), 26.dp.toPx(), Offset(hx, hy))
                }
            }
            val b = burst.value
            if (b > 0f && b < 1f) {
                val c = Offset(w / 2, cy)
                // Two shockwave rings.
                for ((delayK, maxR) in listOf(0f to .75f, .18f to .55f)) {
                    val k = ((b - delayK) / (1f - delayK)).coerceIn(0f, 1f)
                    if (k > 0f) drawCircle(
                        Spark.copy(alpha = (1f - k) * .6f), radius = w * maxR * k, center = c,
                        style = Stroke((10.dp.toPx() * (1f - k)) + 1f),
                    )
                }
                // Sparks flying out and fading.
                val sparks = 28
                for (i in 0 until sparks) {
                    val ang = (i / sparks.toFloat()) * 2 * PI + (i % 3) * .21
                    val speed = .28f + (i * 37 % 11) / 30f
                    val r = w * speed * b
                    val x = c.x + (cos(ang) * r).toFloat()
                    val y = c.y + (sin(ang) * r).toFloat() + h * .05f * b * b
                    drawCircle(if (i % 2 == 0) Spark.copy(alpha = 1f - b) else Ember.copy(alpha = (1f - b) * .9f), (2.5f + (i % 4)) .dp.toPx() * (1f - b * .6f), Offset(x, y))
                }
            }
            if (flash.value > 0f) drawRect(Color.White.copy(alpha = flash.value * (if (p.dark) .35f else .55f)))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                // Heartbeat glow behind the orb.
                Canvas(Modifier.size(230.dp)) {
                    val k = pulse.value
                    val beat = (exp(-((k - .1f) / .06f).let { it * it }) + .6f * exp(-((k - .3f) / .06f).let { it * it }))
                    drawCircle(
                        Brush.radialGradient(listOf(Ember.copy(alpha = .45f * logo.value * (.5f + .5f * beat)), Color.Transparent)),
                        radius = size.minDimension / 2 * (.8f + .12f * beat),
                    )
                }
                Box(
                    Modifier.size(168.dp).graphicsLayer {
                        val s = logo.value
                        scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f)
                    }.liquidGlass(CircleShape, frost = .55f),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(Res.drawable.uh_icon), "מסלול חובש", Modifier.size(120.dp).graphicsLayer { rotationZ = turn.value })
                }
            }
            Spacer(Modifier.height(26.dp))
            // The name rises in one letter at a time (Hebrew letters don't join, so they can be split).
            val name = "מסלול חובש"
            Row {
                name.forEachIndexed { i, ch ->
                    val k = ((title.value * (name.length + 4) - i) / 4f).coerceIn(0f, 1f)
                    Text(
                        ch.toString(), color = p.ink, fontSize = 40.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.graphicsLayer { alpha = k; translationY = (1f - k) * 28.dp.toPx(); val s = .7f + .3f * k; scaleX = s; scaleY = s },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "לימוד ותרגול לקורס חובשים", color = p.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer { alpha = sub.value; translationY = (1f - sub.value) * 12.dp.toPx() },
            )
        }
    }
}
