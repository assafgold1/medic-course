package com.taninoni.medic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.Spot
import com.taninoni.medic.data.Topic
import com.taninoni.medic.data.TopicImage
import com.taninoni.medic.res.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

private val BLITZ_LETTERS = listOf("א", "ב", "ג", "ד")

/* ---------------- 60-Second Speed Blitz ---------------- */

@Composable
fun BlitzScreen(model: AppModel, nav: Nav, s: Screen.Blitz) {
    val p = Pal
    val items = remember(s) { s.items.shuffled() }
    val orders = remember(s) { items.map { it.question.options.indices.shuffled() } }
    var index by remember(s) { mutableIntStateOf(0) }
    var picked by remember(s) { mutableStateOf<Int?>(null) }
    var secondsLeft by remember(s) { mutableIntStateOf(60) }
    var score by remember(s) { mutableIntStateOf(0) }
    var right by remember(s) { mutableIntStateOf(0) }
    var answered by remember(s) { mutableIntStateOf(0) }
    var streak by remember(s) { mutableIntStateOf(0) }
    var bestStreak by remember(s) { mutableIntStateOf(0) }
    var finished by remember(s) { mutableStateOf(false) }
    var isNewRecord by remember(s) { mutableStateOf(false) }
    val scroll = rememberScrollState()

    LaunchedEffect(s) {
        AnswerSound.preload()
        while (secondsLeft > 0 && !finished) {
            delay(1000)
            secondsLeft--
            if (secondsLeft in 1..5) AnswerSound.play(Sfx.TICK)
        }
        if (!finished) {
            finished = true
            isNewRecord = model.recordBlitzScore(score)
            AnswerSound.play(if (score >= 5 || isNewRecord) Sfx.PASS else Sfx.FAIL)
        }
    }

    LaunchedEffect(picked) {
        if (picked != null && !finished) {
            delay(360)
            picked = null
            if (index + 1 >= items.size) {
                finished = true
                isNewRecord = model.recordBlitzScore(score)
                AnswerSound.play(if (score >= 5 || isNewRecord) Sfx.PASS else Sfx.FAIL)
            } else {
                index++
            }
        }
    }

    LaunchedEffect(index) { scroll.scrollTo(0) }

    GlassPage("אתגר 60 שניות", { nav.back() }, scroll = scroll, showLargeTitle = false) {
        if (items.isEmpty()) {
            Panel {
                Text("אין שאלות זמינות כרגע.", color = p.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            return@GlassPage
        }
        if (finished) {
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(if (isNewRecord) "שיא אישי חדש!" else "נגמר הזמן")
                        IconTile(Icons.bolt, p.mid, size = 64.dp, solid = true)
                        Text("$score", color = p.blue, fontSize = 64.sp, fontWeight = FontWeight.Black)
                        Text("נקודות ב-60 שניות", color = p.muted, fontSize = 15.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("$right/$answered", "נכונות", Modifier.weight(1f))
                        Stat("$bestStreak", "רצף שיא", Modifier.weight(1f))
                        Stat("${model.blitzBest}", "שיא אישי", Modifier.weight(1f))
                    }
                    PrimaryButton("שחק שוב", Modifier.fillMaxWidth()) {
                        nav.replace(Screen.Blitz(model.allItems.shuffled()))
                    }
                    GhostButton("חזרה לתרגול", Modifier.fillMaxWidth()) { nav.back() }
                }
            }
            return@GlassPage
        }

        val item = items[index]
        val timerColor = if (secondsLeft <= 10) p.bad else p.blue
        val timerBg = if (secondsLeft <= 10) p.badSoft else p.blueSoft
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${secondsLeft} שנ'",
                        Modifier.clip(RoundedCornerShape(50)).background(timerBg).padding(horizontal = 12.dp, vertical = 5.dp),
                        color = timerColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("ניקוד: $score",
                        Modifier.clip(RoundedCornerShape(50)).background(p.paper).padding(horizontal = 12.dp, vertical = 5.dp),
                        color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (streak >= 2) {
                        Text("רצף $streak",
                            Modifier.clip(RoundedCornerShape(50)).background(p.midSoft).padding(horizontal = 10.dp, vertical = 5.dp),
                            color = p.mid, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(item.topic.title, color = p.muted, fontSize = 12.5.sp, maxLines = 1)
                }
                ProgressBar(secondsLeft / 60f, timerColor)
                Text(item.question.q, color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                orders[index].forEachIndexed { k, o ->
                    val chosen = picked
                    val state = when {
                        chosen == null -> 0
                        o == 0 -> 1
                        o == chosen -> 2
                        else -> 0
                    }
                    val border = when (state) { 1 -> p.good; 2 -> p.bad; else -> p.hair }
                    val shape = RoundedCornerShape(24.dp)
                    Row(
                        Modifier.fillMaxWidth().glass(shape, tint = if (state == 0) Color.Unspecified else border.copy(alpha = .13f), elevation = if (state == 0) 4.dp else 12.dp)
                            .then(if (state != 0) Modifier.border(2.dp, border, shape) else Modifier)
                            .bouncy(enabled = chosen == null) {
                                picked = o
                                answered++
                                val ok = o == 0
                                model.recordAnswer(item.id, ok)
                                if (ok) {
                                    right++
                                    streak++
                                    if (streak > bestStreak) bestStreak = streak
                                    val mult = when { streak >= 5 -> 3; streak >= 3 -> 2; else -> 1 }
                                    score += mult
                                } else {
                                    streak = 0
                                }
                                if (ok && streak in setOf(3, 5, 10, 15, 20)) AnswerSound.play(Sfx.STREAK)
                                else AnswerSound.play(ok)
                            }.padding(horizontal = 16.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(if (state == 0) p.hair else border), contentAlignment = Alignment.Center) {
                            Text(BLITZ_LETTERS[k], color = if (state == 0) p.ink.copy(alpha = .7f) else Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Text(item.question.options[o], color = p.ink, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/* ---------------- Interactive Anatomy Spotter ---------------- */

private data class SpotQuestion(val topic: Topic, val image: TopicImage, val targetIndex: Int, val spot: Spot)

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AnatomyQuizScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val pool = remember(model.content) {
        model.topics.flatMap { t ->
            t.images.filter { it.spots.size >= 2 }.flatMap { img ->
                img.spots.mapIndexed { idx, sp -> SpotQuestion(t, img, idx, sp) }
            }
        }
    }
    var roundSeed by remember { mutableIntStateOf(0) }
    val deck = remember(pool, roundSeed) { pool.shuffled().take(12) }
    var index by remember(roundSeed) { mutableIntStateOf(0) }
    var pickedDot by remember(roundSeed, index) { mutableStateOf<Int?>(null) }
    var right by remember(roundSeed) { mutableIntStateOf(0) }
    var streak by remember(roundSeed) { mutableIntStateOf(0) }
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) { AnswerSound.preload() }
    LaunchedEffect(index) { scroll.scrollTo(0) }

    GlassPage("זיהוי אנטומיה", { nav.back() }, scroll = scroll, showLargeTitle = false) {
        if (deck.isEmpty()) {
            Panel {
                Text("אין תמונות אנטומיה זמינות.", color = p.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            return@GlassPage
        }
        if (index >= deck.size) {
            LaunchedEffect(index) {
                AnswerSound.play(if (right * 100 / deck.size >= 70) Sfx.PASS else Sfx.FAIL)
            }
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow("סיימת סבב זיהוי")
                        Text("$right/${deck.size}", color = p.ink, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                        Text("נקודות אנטומיה שזוהו נכון", color = p.muted, fontSize = 15.sp)
                    }
                    PrimaryButton("סבב חדש", Modifier.fillMaxWidth()) { roundSeed++; index = 0; right = 0; streak = 0 }
                    GhostButton("חזרה", Modifier.fillMaxWidth()) { nav.back() }
                }
            }
            return@GlassPage
        }

        val q = deck[index]
        val bitmap by produceState<ImageBitmap?>(null, q.image.file) {
            value = withContext(Dispatchers.Default) {
                runCatching { Res.readBytes("files/images/${q.image.file}").decodeToImageBitmap() }.getOrNull()
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("זיהוי ${index + 1}/${deck.size}", color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (streak >= 2) {
                        Text("רצף $streak",
                            Modifier.clip(RoundedCornerShape(50)).background(p.midSoft).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = p.mid, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(q.topic.title,
                        Modifier.clip(RoundedCornerShape(50)).background(p.blueSoft).padding(horizontal = 10.dp, vertical = 4.dp),
                        color = p.blue, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                ProgressBar(index.toFloat() / deck.size, p.blue)
                Text("הקש על הנקודה המתאימה:", color = p.muted, fontSize = 14.sp)
                Text(q.spot.name, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

                val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: 1.4f
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio).glass(RoundedCornerShape(26.dp), elevation = 14.dp).background(Color.White)) {
                    bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    val w = maxWidth; val h = maxHeight
                    q.image.spots.forEachIndexed { i, sp ->
                        val chosen = pickedDot
                        val isTarget = i == q.targetIndex
                        val isPicked = i == chosen
                        val bgColor = when {
                            chosen == null -> Color.White
                            isTarget -> p.good
                            isPicked -> p.bad
                            else -> Color.White.copy(alpha = 0.75f)
                        }
                        val borderColor = when {
                            chosen == null -> p.blue
                            isTarget -> p.good
                            isPicked -> p.bad
                            else -> p.muted
                        }
                        val textColor = if (chosen != null && (isTarget || isPicked)) Color.White else p.blue
                        val size = if (chosen != null && (isTarget || isPicked)) 32.dp else 26.dp
                        Box(
                            Modifier.offset(x = w * (sp.x / 100f) - size / 2, y = h * (sp.y / 100f) - size / 2)
                                .size(size).clip(CircleShape).background(bgColor).border(2.5.dp, borderColor, CircleShape)
                                .clickable(enabled = chosen == null) {
                                    pickedDot = i
                                    val ok = i == q.targetIndex
                                    if (ok) { right++; streak++ } else { streak = 0 }
                                    if (ok && streak in setOf(3, 5, 10)) AnswerSound.play(Sfx.STREAK)
                                        else AnswerSound.play(ok)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val chosen = pickedDot
                if (chosen != null) {
                    val ok = chosen == q.targetIndex
                    Column(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), tint = if (ok) p.goodSoft else p.badSoft).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(if (ok) "בדיוק!" else "זה היה: ${q.image.spots[chosen].name}", color = if (ok) p.good else p.bad, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("${q.spot.name}: ${q.spot.info}", color = p.ink, fontSize = 15.sp, lineHeight = 23.sp)
                    }
                    PrimaryButton(if (index + 1 < deck.size) "הבא" else "לתוצאות", Modifier.fillMaxWidth()) {
                        pickedDot = null
                        index++
                    }
                }
            }
        }
    }
}
