package com.taninoni.medic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Space a tab screen leaves at its bottom so content can scroll clear of the floating bar. */
val TAB_BAR_SPACE = 96.dp
val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }

/** Keeps the end of a scrolling screen above the floating tab bar (or the system bar). */
@Composable
fun BottomSpace(extra: Dp = 24.dp) {
    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(LocalBottomInset.current + extra))
}

/** Floating, rounded tab bar; the selected tab sits in a soft pill. */
@Composable
fun TabBar(nav: Nav, modifier: Modifier = Modifier) {
    val p = Pal
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier.windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 560.dp).fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.25f))
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.line.copy(alpha = .7f), shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TABS.forEach { tab ->
            val on = nav.current == tab.screen
            val tint by animateColorAsState(if (on) p.blue else p.muted)
            val bg by animateColorAsState(if (on) p.blueSoft else Color.Transparent)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(bg)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = tab.label) { nav.tab(tab.screen) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(23.dp))
                Text(tab.label, color = tint, fontSize = 11.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
