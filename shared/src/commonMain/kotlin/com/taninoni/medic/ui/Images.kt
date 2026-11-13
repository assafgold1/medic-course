package com.taninoni.medic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
private fun TopicBitmap(file: String, modifier: Modifier, scale: ContentScale) {
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
