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

@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val p = Pal
    // Drift the light at a low tick rate: the blobs move a few pixels a second, so 12 updates a second look
    // continuous, and every update re-renders all the glass sampling the backdrop.
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            delay(83)
            val now = withFrameMillis { it }
            t = ((now - start) % 48_000L) / 48_000f
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(p.baseTop, p.baseBottom)))
        val w = size.width
        val h = size.height
        val a = (t * 2 * PI).toFloat()
        // Each blob orbits its own anchor at its own speed, so the light never repeats in an obvious way.
        val specs = listOf(
            floatArrayOf(.12f, .06f, .78f, 1f, 0f),
            floatArrayOf(.95f, .24f, .70f, -1f, 1.7f),
            floatArrayOf(.18f, .55f, .62f, 2f, 3.1f),
            floatArrayOf(.88f, .74f, .66f, -1f, 4.4f),
            floatArrayOf(.45f, .98f, .72f, 1f, 5.2f),
        )
        val alpha = if (p.dark) .55f else .42f
        specs.forEachIndexed { i, s ->
            val ang = a * s[3] + s[4]
            val cx = w * s[0] + cos(ang) * w * .10f
            val cy = h * s[1] + sin(ang * 1.3f) * h * .05f
            val r = maxOf(w, h * .55f) * s[2] * (1f + .06f * sin(ang * 2f))
            val c = p.blobs[i % p.blobs.size]
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * .35f), Color.Transparent), Offset(cx, cy), r),
                radius = r, center = Offset(cx, cy),
            )
        }
    }
}

/* ---------------- materials ---------------- */

/**
 * Frosted glass for cards: a translucent fill that lets the ambient light through, a specular rim
 * that is bright where the light hits (top-start) and fades round the far side, and a soft drop shadow.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = Color.Unspecified,
    elevation: Dp = 10.dp,
    rim: Boolean = true,
): Modifier = composed {
    val p = Pal
    this
        .then(if (elevation > 0.dp) Modifier.softShadow(shape, elevation, p.glassShadow) else Modifier)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(p.glassTop, p.glassBottom)))
        .then(if (tint.isSpecified()) Modifier.background(tint) else Modifier)
        .then(
            if (rim) Modifier.border(1.dp, Brush.linearGradient(listOf(p.rimHi, p.rimLo, p.rimLo, p.rimHi.copy(alpha = p.rimHi.alpha * .5f))), shape)
            else Modifier,
        )
}

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
    val haze = LocalHaze.current
    val style = remember(shape, tint, p.dark, clear, frost, fill) {
        (if (clear) GlassStyle.clear else GlassStyle.regular).then {
            shape(shape)
            tint(tint)
            if (!clear) optics { copy(blurRadius = OpticalSizeValue.Fixed((6 + 22 * frost).dp)) }
            if (p.dark) {
                specularIntensity(.45f); edgeShadow(Color.Black.copy(alpha = .3f)); whitePoint(-.18f); contrast(.12f)
                backgroundColor(Color(0xFF5A4448).copy(alpha = .22f + .3f * frost))
            } else {
                specularIntensity(.75f); whitePoint(.25f); edgeShadow(Color(0x1E783214))
                backgroundColor(Color.White.copy(alpha = .2f + .5f * frost))
            }
            // Tint sits over the refracted content (backgroundColor sits behind it), so a fill here keeps text legible.
            if (fill != null) tint(tint.compositeOver(fill))
            pressed { scale(.96f); refractionMultiplier(1.6f); whitePointDelta(if (p.dark) .08f else .12f) }
        }
    }
    return this.hazeGlass(HazeInput.Sources(haze), style, interactionSource = interaction)
}

/**
 * Frosted chrome (tab bar, floating panels): a real backdrop blur of whatever scrolls behind, tinted so
 * text on top always reads, with a specular rim and a soft highlight across the top like a lens catching light.
 */
@Composable
fun Modifier.frostedGlass(shape: RoundedCornerShape, tint: Color = Color.Transparent, radius: Dp = 26.dp): Modifier {
    val p = Pal
    val haze = LocalHaze.current
    val fill = tint.compositeOver(if (p.dark) Color(0xFF231719).copy(alpha = .72f) else Color.White.copy(alpha = .62f))
    val style = remember(fill, radius) {
        HazeBlurStyle {
            blurRadius(radius)
            noiseFactor(.06f)
            colorEffects(listOf(HazeColorEffect.tint(fill)))
        }
    }
    return this
        .softShadow(shape, 18.dp, p.glassShadow.copy(alpha = p.glassShadow.alpha * 1.3f))
        .clip(shape)
        .hazeBlur(HazeInput.Sources(haze), style)
        .drawWithContent {
            drawContent()
            // Light from above: a bright lip that fades by mid-height.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) .14f else .5f), Color.Transparent), endY = size.height * .55f))
        }
        .border(1.dp, Brush.linearGradient(listOf(p.rimHi, p.rimLo, p.rimLo, p.rimHi.copy(alpha = p.rimHi.alpha * .6f))), shape)
}

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

/** Round glass button for the top bar (back, close, actions). */
@Composable
fun GlassIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Dp = 46.dp, tint: Color = Pal.ink, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.size(size).liquidGlass(CircleShape, interaction = src).bouncy(label = label, interaction = src, pressedScale = 1f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(size * .48f)) }
}

/**
 * The scroll-edge effect: content melts into a soft blur under the status bar instead of hitting a hard bar,
 * with round glass buttons floating on top and a compact title that fades in once the large title scrolls away.
 */
@Composable
fun BoxScope.GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    collapsed: Float,
    actions: @Composable () -> Unit = {},
) {
    val p = Pal
    val haze = LocalHaze.current
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val blurStyle = remember(p.dark) {
        HazeBlurStyle {
            blurRadius(22.dp)
            noiseFactor(0f)
            backgroundColor(p.baseTop)
            progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
        }
    }
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(inset + TOP_BAR + 10.dp)
            .graphicsLayer { alpha = .35f + .65f * collapsed }
            .hazeBlur(HazeInput.Sources(haze), blurStyle)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(p.baseTop.copy(alpha = .55f), p.baseTop.copy(alpha = 0f))))
            },
    )
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(TOP_BAR).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) GlassIconButton(Icons.back, "חזרה", onClick = onBack) else Spacer(Modifier.size(46.dp))
        Text(
            title, Modifier.weight(1f).graphicsLayer { alpha = collapsed; translationY = (1f - collapsed) * 10.dp.toPx() },
            color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Box(Modifier.widthIn(min = 46.dp), contentAlignment = Alignment.Center) { actions() }
    }
}

/** Large page title that scrolls with the content (the compact one in [GlassTopBar] takes over). */
@Composable
fun LargeTitle(title: String, subtitle: String? = null) {
    val p = Pal
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (subtitle != null) Text(subtitle, color = p.blue, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, color = p.ink, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * A page: content scrolls under a floating glass top bar, over the ambient backdrop.
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
    val haze = LocalHaze.current
    val collapsed by remember(showLargeTitle) { derivedStateOf { if (!showLargeTitle) 1f else (scroll.value / 90f).coerceIn(0f, 1f) } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().hazeSource(haze, zIndex = 1f).verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars).padding(top = TOP_BAR),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showLargeTitle) LargeTitle(title, subtitle) else Spacer(Modifier.height(6.dp))
                content()
                BottomSpace(24.dp + bottomSpace)
            }
        }
        GlassTopBar(title, onBack, collapsed, actions)
        bottom?.invoke(this)
    }
}

/** [GlassPage] for long lists. Items get the side padding from [contentPadding]. */
@Composable
fun GlassLazyPage(
    title: String,
    onBack: (() -> Unit)?,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable () -> Unit = {},
    showTopTitle: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val haze = LocalHaze.current
    val collapsed by remember { derivedStateOf { if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / 140f).coerceIn(0f, 1f) } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze, zIndex = 1f),
            state = state,
            contentPadding = PaddingValues(top = top + TOP_BAR),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        GlassTopBar(if (showTopTitle) title else "", onBack, collapsed, actions)
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
