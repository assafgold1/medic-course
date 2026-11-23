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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Space a tab screen leaves at its bottom so content scrolls clear of the tab bar (the system inset is added separately). */
val TAB_BAR_SPACE = 60.dp
val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }

/** Keeps the end of a scrolling screen above the tab bar (or the system bar). */
@Composable
fun BottomSpace(extra: Dp = 24.dp) {
    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(LocalBottomInset.current + extra))
}

/** The tab shown as the raised round button in the middle of the bar: the defibrillator. */
private val CENTER_TAB = Screen.Aed

/**
 * Docked tab bar with rounded top corners. The defibrillator, the app's signature tool, is a raised
 * orange button in the middle; the other tabs have outline icons that fill in when picked, and a short
 * orange line glides above the selected one.
 */
@Composable
fun TabBar(nav: Nav, modifier: Modifier = Modifier) {
    val p = Pal
    val index = TABS.indexOfFirst { it.screen == nav.current }.coerceAtLeast(0)
    val center = TABS.indexOfFirst { it.screen == CENTER_TAB }
    val src = remember { MutableInteractionSource() }
    val idle = if (p.dark) Color(0xFF8E8A87) else Color(0xFF8A8580)
    val surface = if (p.dark) Color(0xFF1C1918) else Color.White
    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .shadow(16.dp, topShape, clip = false, ambientColor = Color.Black.copy(alpha = .14f), spotColor = Color.Black.copy(alpha = .14f))
            .background(surface, topShape),
    ) {
        val slot = maxWidth.coerceAtMost(560.dp) / TABS.size
        val side = (maxWidth - slot * TABS.size) / 2
        // The glide line: under the raised button it fades away, since the button speaks for itself.
        val lineX by animateDpAsState(side + slot * index + (slot - 22.dp) / 2, spring(dampingRatio = .72f, stiffness = 420f))
        val lineAlpha by animateFloatAsState(if (index == center) 0f else 1f)
        Box(
            Modifier.offset(x = lineX).width(22.dp).height(3.dp)
                .graphicsLayer { alpha = lineAlpha }
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)).background(Brand),
        )
        Row(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).widthIn(max = 560.dp).fillMaxWidth()
                .height(58.dp).align(Alignment.TopCenter),
        ) {
            TABS.forEachIndexed { i, tab ->
                val on = i == index
                val color by animateColorAsState(if (on) Brand else idle)
                val pop by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = .4f, stiffness = 600f))
                Column(
                    Modifier.weight(1f).fillMaxHeight().bouncy(label = tab.label, interaction = src, pressedScale = .9f) { if (!on) tick(); nav.tab(tab.screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom),
                ) {
                    if (i == center) {
                        // Raised round button sitting half above the bar, ringed in the bar's colour.
                        Box(
                            Modifier.padding(bottom = 7.dp).offset(y = (-14).dp).size(58.dp)
                                .graphicsLayer { val sc = 1f + .06f * pop; scaleX = sc; scaleY = sc }
                                .shadow(if (on) 14.dp else 8.dp, CircleShape, ambientColor = Brand, spotColor = Brand)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(Color(0xFFFF8A3D), Brand, Color(0xFFDD5512))))
                                .border(4.dp, surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(tab.iconOn, tab.label, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    } else {
                        Icon(
                            if (on) tab.iconOn else tab.icon, tab.label, tint = color,
                            modifier = Modifier.size(25.dp).graphicsLayer { val sc = 1f + .1f * pop * (1f - pop) * 4f; scaleX = sc; scaleY = sc },
                        )
                        Text(
                            tab.label, color = color, fontSize = 10.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                }
            }
        }
    }
}
