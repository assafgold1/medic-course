package com.taninoni.medic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.TopicImage
import com.taninoni.medic.res.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Decoded images are kept so scrolling back and the full-screen viewer don't decode again. */
private val bitmapCache = mutableMapOf<String, ImageBitmap>()

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberTopicBitmap(file: String): ImageBitmap? {
    val bitmap by produceState(bitmapCache[file], file) {
        if (value == null) value = withContext(Dispatchers.Default) {
            runCatching { Res.readBytes("files/images/$file").decodeToImageBitmap() }.getOrNull()
        }?.also { bitmapCache[file] = it }
    }
    return bitmap
}

@Composable
internal fun TopicBitmap(file: String, modifier: Modifier, scale: ContentScale) {
    val bitmap = rememberTopicBitmap(file)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = scale)
    }
}

/** Horizontal strip of image cards; tapping one opens a full-screen, swipeable viewer. */
@Composable
fun ImageGallery(images: List<TopicImage>) {
    if (images.isEmpty()) return
    val p = Pal
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("תמונות להמחשה", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${images.size} תמונות", color = p.muted, fontSize = 13.5.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
            itemsIndexed(images, key = { _, it -> it.file }) { i, img ->
                Column(
                    Modifier.width(220.dp).clip(RoundedCornerShape(18.dp)).background(p.paper).clickable { ViewerState.show(images, i) },
                ) {
                    TopicBitmap(img.file, Modifier.fillMaxWidth().height(150.dp).background(Color.White), ContentScale.Crop)
                    Text(
                        img.caption, color = p.ink, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).height(40.dp),
                    )
                }
            }
        }
    }
}

/** Full-screen image viewer state, shown above the whole app (including the tab bar). */
object ViewerState {
    var images by mutableStateOf<List<TopicImage>>(emptyList()); private set
    var start by mutableStateOf(0); private set
    val isOpen get() = images.isNotEmpty()
    fun show(list: List<TopicImage>, index: Int) { start = index; images = list }
    fun close() { images = emptyList() }
}

@Composable
fun ImageViewerHost() {
    val images = ViewerState.images
    if (images.isEmpty()) return
    val pager = rememberPagerState(initialPage = ViewerState.start) { images.size }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val img = images[page]
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), verticalArrangement = Arrangement.Center) {
                TopicBitmap(img.file, Modifier.fillMaxWidth().weight(1f).padding(top = 64.dp), ContentScale.Fit)
                Text(
                    img.caption, color = Color.White, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                )
                Text("${page + 1} / ${images.size}", color = Color.White.copy(.6f), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
            }
        }
        Box(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).size(44.dp).clip(CircleShape)
                .background(Color.White.copy(.18f)).clickable { ViewerState.close() }.align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

/**
 * The question's illustration. Before answering only the picture is shown (its caption could give the
 * answer away); after answering in practice mode the caption appears. Tap opens it full screen.
 */
@Composable
fun QuestionImage(img: TopicImage, showCaption: Boolean) {
    val p = Pal
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.paper).clickable { ViewerState.show(listOf(if (showCaption) img else img.copy(caption = "")), 0) }) {
        TopicBitmap(img.file, Modifier.fillMaxWidth().height(190.dp).background(Color.White), ContentScale.Fit)
        if (showCaption) Text(img.caption, color = p.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/**
 * An image inside the study text. Anatomy images with points show numbered markers:
 * tapping one highlights it and shows the part's name and explanation below the image.
 */
@Composable
fun InlineImage(img: TopicImage) {
    val p = Pal
    var selected by remember(img.file) { mutableStateOf<Int?>(null) }
    val bitmap = rememberTopicBitmapPublic(img.file)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.paper)) {
        val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: 1.4f
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().clickable { ViewerState.show(listOf(img), 0) }, contentScale = ContentScale.FillBounds)
            val w = maxWidth; val h = maxHeight
            img.spots.forEachIndexed { i, s ->
                val on = selected == i
                val size = if (on) 30.dp else 26.dp
                Box(
                    Modifier.offset(x = w * (s.x / 100f) - size / 2, y = h * (s.y / 100f) - size / 2).size(size).clip(CircleShape)
                        .background(if (on) p.blue else Color.White).border(2.5.dp, p.blue, CircleShape)
                        .clickable { selected = if (on) null else i },
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = if (on) Color.White else p.blue, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        val sel = selected?.let { img.spots.getOrNull(it) }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sel != null) {
                Text(sel.name, color = p.blue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(sel.info, color = p.ink, fontSize = 15.sp, lineHeight = 22.sp)
            } else {
                Text(img.caption, color = p.muted, fontSize = 14.sp, lineHeight = 20.sp)
                if (img.spots.isNotEmpty()) Text("הקש על המספרים כדי לראות את שם החלק והסבר", color = p.blue, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (img.spots.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                img.spots.forEachIndexed { i, s ->
                    val on = selected == i
                    Text("${i + 1}. ${s.name}", color = if (on) Color.White else p.ink, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) p.blue else p.surface)
                            .clickable { selected = if (on) null else i }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun rememberTopicBitmapPublic(file: String) = rememberTopicBitmap(file)
