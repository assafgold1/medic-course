package com.taninoni.medic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Space a tab screen leaves at its bottom so content can scroll clear of the floating bar. */
val TAB_BAR_SPACE = 92.dp
val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }

/** Keeps the end of a scrolling screen above the floating tab bar (or the system bar). */
@Composable
fun BottomSpace(extra: Dp = 24.dp) {
    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(LocalBottomInset.current + extra))
}

/**
 * Floating Liquid Glass tab bar. The selected tab sits under a glass droplet that slides between
 * tabs on a spring and stretches as it travels, the way a drop of liquid would.
 */
@Composable
fun TabBar(nav: Nav, modifier: Modifier = Modifier) {
    val p = Pal
    val shape = RoundedCornerShape(50)
    val index = TABS.indexOfFirst { it.screen == nav.current }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier.windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .widthIn(max = 520.dp).fillMaxWidth().height(68.dp)
            .frostedGlass(shape),
    ) {
        val inner = maxWidth - 12.dp
        val w = inner / TABS.size
        val x by animateDpAsState(w * index, spring(dampingRatio = .68f, stiffness = 380f))
        // Stretch the droplet while it is moving: the further from rest, the wider it gets.
        val stretch = (kotlin.math.abs((x - w * index).value) / w.value).coerceIn(0f, 1f)
        val sx by animateFloatAsState(1f + stretch * .35f, spring(dampingRatio = .6f, stiffness = 600f))
        val src = remember { MutableInteractionSource() }
        Box(
            Modifier.padding(6.dp).offset(x = x).width(w).fillMaxHeight()
                .graphicsLayer { scaleX = sx; scaleY = 1f / (1f + (sx - 1f) * .4f) }
                .softShadow(shape, 8.dp, p.glassShadow)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) .2f else .85f), Color.White.copy(alpha = if (p.dark) .08f else .45f))))
                .background(p.blue.copy(alpha = if (p.dark) .16f else .1f))
                .border(1.dp, Brush.verticalGradient(listOf(p.rimHi, p.rimLo)), shape),
        )
        Row(Modifier.padding(6.dp).fillMaxWidth().fillMaxHeight()) {
            TABS.forEachIndexed { i, tab ->
                val on = i == index
                val tint by animateColorAsState(if (on) p.blue else p.ink.copy(alpha = .62f))
                val lift by animateFloatAsState(if (on) 1.08f else 1f, spring(dampingRatio = .45f, stiffness = 500f))
                Column(
                    Modifier.weight(1f).fillMaxHeight().bouncy(label = tab.label, interaction = src, pressedScale = .9f) { if (!on) tick(); nav.tab(tab.screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = lift; scaleY = lift })
                    Text(tab.label, color = tint, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
