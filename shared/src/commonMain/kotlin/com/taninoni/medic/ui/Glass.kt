package com.taninoni.medic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid Glass, in three layers:
 * 1. [AmbientBackdrop]: slowly drifting blobs of warm light, the colour the glass bends.
 * 2. Page content, registered as a Haze source so floating chrome can refract it.
 * 3. Chrome ([liquidGlass]): tab bar, top-bar buttons and floating actions, real refracting glass.
 * Cards inside the content use [glass], a lighter frosted material that reads the backdrop through translucency.
 */
val LocalHaze = staticCompositionLocalOf<HazeState> { error("No HazeState") }

/** Height of the floating top bar below the status bar. */
val TOP_BAR = 58.dp

/* ---------------- backdrop ---------------- */

/** "Reduce effects": no live blur, refraction or drifting light. For slower phones and to save battery. */
val LocalReduceEffects = androidx.compose.runtime.compositionLocalOf { false }

/** The brand orange of the app mark: headers, the opening screen and big moments. */
val Brand = Color(0xFFF26922)

/** Card surface: solid white (graphite in the dark), a hairline edge and a soft shadow. No glass. */
@Composable
private fun Modifier.plainGlass(shape: Shape, tint: Color, elevation: Dp, rim: Boolean): Modifier {
    val p = Pal
    val base = if (p.dark) Color(0xFF1F1B1A) else Color.White
    return this
        .then(if (elevation > 0.dp) Modifier.softShadow(shape, (elevation.value * .6f).dp, p.glassShadow) else Modifier)
        .clip(shape)
        .background(tint.compositeOver(base))
        .then(if (rim) Modifier.border(1.dp, if (p.dark) Color.White.copy(alpha = .07f) else Color.Black.copy(alpha = .05f), shape) else Modifier)
}

/** Scroll position of the page in front, in px, so the backdrop light can drift behind it (parallax). */
object Parallax {
    var scroll by mutableFloatStateOf(0f)
}

/** The page background: one flat, quiet colour so the orange and the cards carry the design. */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val p = Pal
    Canvas(modifier.fillMaxSize()) { drawRect(p.baseTop) }
}

/* ---------------- materials ---------------- */

/** The card material everywhere: a flat surface with an optional [tint] on top. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = Color.Unspecified,
    elevation: Dp = 10.dp,
    rim: Boolean = true,
): Modifier = composed { frosted(shape, if (tint.isSpecified()) tint else Color.Transparent, elevation, 30.dp, rim) }

private fun Color.isSpecified() = this != Color.Unspecified

/**
 * A soft shadow drawn only outside [shape], so it never darkens the translucent glass above it
 * (a platform shadow sits under the whole shape and shows through). Built from stacked strokes
 * of the outline that fade with distance, nudged down as if lit from above.
 */
fun Modifier.softShadow(shape: Shape, radius: Dp, color: Color): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
    val r = radius.toPx()
    val steps = 9
    clipPath(path, ClipOp.Difference) {
        translate(top = r * .35f) {
            for (i in steps downTo 1) {
                val f = i / steps.toFloat()
                drawPath(path, color.copy(alpha = color.alpha * (1f - f) * (1f - f) * .75f), style = Stroke(width = 2f * r * f))
            }
        }
    }
}

/**
 * Real refracting Liquid Glass. Samples whatever Haze sources sit behind it (the backdrop, and the
 * page content for chrome that floats above it), bends it at the edges, and catches light on the rim.
 * Pass the clickable's [interaction] so the glass swells under the finger.
 */
/** Small surface for round buttons, pills and fields: flat, with an optional [tint] or [fill] on top. */
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape = CircleShape,
    tint: Color = Color.Transparent,
    interaction: MutableInteractionSource? = null,
    clear: Boolean = false,
    frost: Float = .5f,
    fill: Color? = null,
): Modifier {
    val p = Pal
    val surface = if (p.dark) Color(0xFF2A2523) else Color.White
    return plainGlass(shape, tint.compositeOver(fill?.takeIf { it.alpha > .6f } ?: surface), 0.dp, rim = true)
}

/**
 * Frosted chrome (tab bar, floating panels): a real backdrop blur of whatever scrolls behind, tinted so
 * text on top always reads, with a specular rim and a soft highlight across the top like a lens catching light.
 */
/** Chrome surface (tab bar, docks): solid, with a stronger shadow so it floats over the page. */
@Composable
fun Modifier.frostedGlass(shape: RoundedCornerShape, tint: Color = Color.Transparent, radius: Dp = 26.dp): Modifier =
    plainGlass(shape, tint, 26.dp, rim = true)

/** A raised card (bigger shadow), same flat surface. */
@Composable
fun Modifier.deepGlass(
    shape: Shape = RoundedCornerShape(30.dp),
    elevation: Dp = 18.dp,
    tint: Color = Color.Transparent,
    radius: Dp = 34.dp,
): Modifier = frosted(shape, tint, elevation, radius, rim = true)

@Composable
private fun Modifier.frosted(shape: Shape, tint: Color, elevation: Dp, radius: Dp, rim: Boolean): Modifier =
    plainGlass(shape, tint, elevation, rim)

/* ---------------- motion ---------------- */

/** Click with a springy squash instead of a ripple: the element dips under the finger and bounces back. */
fun Modifier.bouncy(
    enabled: Boolean = true,
    label: String? = null,
    interaction: MutableInteractionSource? = null,
    pressedScale: Float = .955f,
    onClick: () -> Unit,
): Modifier = composed {
    val src = interaction ?: remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) pressedScale else 1f, spring(dampingRatio = .45f, stiffness = 700f))
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(src, indication = null, enabled = enabled, onClickLabel = label, onClick = onClick)
}

/* ---------------- chrome ---------------- */

/** Round button. [onBrand] makes it white-on-orange for the header; otherwise a flat surface disc. */
@Composable
fun GlassIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Dp = 46.dp, tint: Color = Pal.ink, onBrand: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(size)
            .then(if (onBrand) Modifier.clip(CircleShape).background(Color.White.copy(alpha = .22f)) else Modifier.liquidGlass(CircleShape))
            .bouncy(label = label, pressedScale = .9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (onBrand) Color.White else tint, modifier = Modifier.size(size * .48f)) }
}

/**
 * The top bar: white controls on the brand orange. Over a page's orange header it stays clear; once the
 * header scrolls away ([collapsed] goes to 1) it fills with solid orange and shows the compact title.
 */
@Composable
fun BoxScope.GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    collapsed: Float,
    actions: @Composable () -> Unit = {},
) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(inset + TOP_BAR)
            .graphicsLayer { alpha = collapsed }
            .background(Brand),
    )
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(TOP_BAR).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) GlassIconButton(Icons.back, "חזרה", onBrand = true, onClick = onBack) else Spacer(Modifier.size(46.dp))
        Text(
            title, Modifier.weight(1f).graphicsLayer { alpha = collapsed; translationY = (1f - collapsed) * 10.dp.toPx() },
            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Box(Modifier.widthIn(min = 46.dp), contentAlignment = Alignment.Center) { actions() }
    }
}

/** Large page title, white and heavy, set on the orange header block. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, color = Color.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
    }
}

/** Orange block at the top of a page, behind the status bar and top bar, with rounded bottom corners. */
@Composable
fun BrandHeader(modifier: Modifier = Modifier, bottom: Dp = 26.dp, content: @Composable ColumnScope.() -> Unit) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(Brand)
            .padding(top = inset + TOP_BAR, bottom = bottom),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 22.dp), content = content)
    }
}

/**
 * A page: an orange header with the large title, content scrolling below, and the top bar on top.
 * [bottom] floats over the end of the page (a pinned action), with [bottomSpace] kept clear below the content.
 */
@Composable
fun GlassPage(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    scroll: ScrollState = rememberScrollState(),
    showLargeTitle: Boolean = true,
    actions: @Composable () -> Unit = {},
    bottomSpace: Dp = 0.dp,
    bottom: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val collapsed by remember(showLargeTitle) { derivedStateOf { if (!showLargeTitle) 1f else (scroll.value / 110f).coerceIn(0f, 1f) } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLargeTitle) BrandHeader {
                Box(Modifier.graphicsLayer { val v = scroll.value.toFloat(); translationY = v * .35f; alpha = 1f - (v / 140f).coerceIn(0f, 1f) }) {
                    LargeTitle(title, subtitle)
                }
            } else BrandHeader(bottom = 8.dp) {}
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 18.dp).padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
                BottomSpace(24.dp + bottomSpace)
            }
        }
        GlassTopBar(title, onBack, collapsed, actions)
        bottom?.invoke(this)
    }
}

/** [GlassPage] for long lists: a solid orange top bar, items get the side padding from [pageItem]. */
@Composable
fun GlassLazyPage(
    title: String,
    onBack: (() -> Unit)?,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable () -> Unit = {},
    showTopTitle: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(top = top + TOP_BAR),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        GlassTopBar(if (showTopTitle) title else "", onBack, 1f, actions)
    }
}

/** Standard width and side padding for a lazy-list item. */
fun Modifier.pageItem(): Modifier = this.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 18.dp)

/** A pinned action floating at the bottom of a page, over a soft fade so content slides away under it. */
@Composable
fun BoxScope.FloatingDock(content: @Composable ColumnScope.() -> Unit) {
    val p = Pal
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(p.baseBottom.copy(alpha = 0f), p.baseBottom.copy(alpha = .85f), p.baseBottom)))
            .padding(top = 30.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 18.dp, end = 18.dp, bottom = LocalBottomInset.current + 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
