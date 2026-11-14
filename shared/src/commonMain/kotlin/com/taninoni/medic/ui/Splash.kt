package com.taninoni.medic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

private val UhOrange = Color(0xFFF26922)

/**
 * Opening animation: the app mark spins and grows in, two orange pulse rings spread
 * from it, then the wordmark and app name fade up. Waits until [ready] and then fades away.
 */
@Composable
fun SplashScreen(ready: () -> Boolean, onDone: () -> Unit) {
    val logoScale = remember { Animatable(0.25f) }
    val logoTurn = remember { Animatable(-140f) }
    val logoAlpha = remember { Animatable(0f) }
    val ring1 = remember { Animatable(0f) }
    val ring2 = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textShift = remember { Animatable(24f) }
    val beat = remember { Animatable(1f) }
    val overlay = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(350)) }
            launch { logoTurn.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
            logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        }
        coroutineScope {
            launch { ring1.animateTo(1f, tween(900, easing = LinearEasing)) }
            launch { delay(250); ring2.animateTo(1f, tween(900, easing = LinearEasing)) }
            launch { delay(150); textAlpha.animateTo(1f, tween(450)) }
            launch { delay(150); textShift.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
            launch { beat.animateTo(1.08f, tween(160)); beat.animateTo(1f, tween(220)); beat.animateTo(1.06f, tween(140)); beat.animateTo(1f, tween(260)) }
        }
        snapshotFlow { ready() }.first { it }
        delay(250)
        overlay.animateTo(0f, tween(350))
        onDone()
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = overlay.value }.background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    for (r in listOf(ring1.value, ring2.value)) if (r > 0f && r < 1f) {
                        drawCircle(UhOrange.copy(alpha = (1f - r) * .45f), radius = size.minDimension / 2 * (0.45f + 0.55f * r), style = Stroke(6.dp.toPx() * (1f - r) + 1f))
                    }
                }
                Image(
                    painterResource(Res.drawable.uh_icon), "מסלול חובש",
                    Modifier.size(150.dp).graphicsLayer {
                        val s = logoScale.value * beat.value
                        scaleX = s; scaleY = s; rotationZ = logoTurn.value; alpha = logoAlpha.value
                    },
                )
            }
            Column(
                Modifier.graphicsLayer { alpha = textAlpha.value; translationY = textShift.value * density },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("מסלול חובש", color = Color(0xFF1F1A17), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("לימוד ותרגול לקורס חובשים", color = Color(0xFF7A6A60), fontSize = 16.sp)
            }
        }
    }
}
