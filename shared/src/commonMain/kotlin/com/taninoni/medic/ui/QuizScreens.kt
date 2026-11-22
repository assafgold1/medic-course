package com.taninoni.medic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.LogEntry
import com.taninoni.medic.data.Topic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LETTERS = listOf("א", "ב", "ג", "ד")

@Composable
fun QuizScreen(model: AppModel, nav: Nav, s: Screen.Quiz) {
    val p = Pal
    // Practice can be switched to exam-style (no answer after each question) in Settings.
    val cfg = s.config.let { if (it.instant && !model.instantFeedback) it.copy(instant = false) else it }
    val items = s.items
    val orders = remember(s) { items.map { it.question.options.indices.shuffled() } }
    // Never show the exact same image on two consecutive questions in a run.
    val shownImages = remember(s) {
        var prev: String? = null
        items.map { item ->
            val f = item.question.image
            if (f != null && f != prev) { prev = f; f } else null
        }
    }
    var index by remember(s) { mutableIntStateOf(0) }
    var picked by remember(s) { mutableStateOf<Int?>(null) }
    var eliminated by remember(s, index) { mutableStateOf<Set<Int>>(emptySet()) }
    var streak by remember(s) { mutableIntStateOf(0) }
    var bestStreak by remember(s) { mutableIntStateOf(0) }
    val log = remember(s) { mutableStateListOf<LogEntry>() }
    val scroll = rememberScrollState()

    fun finish() {
        if (cfg.kind != "topic") model.recordAttempt(cfg.title, cfg.kind, log.toList())
        nav.replace(Screen.Results(log.toList(), cfg, bestStreak))
    }
    fun next() {
        picked = null
        if (index + 1 >= items.size) finish() else index++
    }
    LaunchedEffect(index) { scroll.scrollTo(0) }
    LaunchedEffect(Unit) { AnswerSound.preload() }
    // Timed exam: the clock runs for the whole test; at zero the test ends with what was answered.
    var left by remember(s) { mutableIntStateOf(cfg.timeLimitSec) }
    if (cfg.timeLimitSec > 0) LaunchedEffect(s) {
        while (left > 0) { delay(1000); left-- }
        finish()
    }
    if (!cfg.instant) LaunchedEffect(picked) { if (picked != null) { delay(380); next() } }

    if (items.isEmpty()) {
        GlassPage(cfg.title, { nav.back() }) {
            Panel {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile(Icons.trophy, p.mid, size = 60.dp, solid = true)
                    Text("אין כרגע שאלות לתרגול כאן. כל הכבוד!", color = p.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
        return
    }
    val item = items[index]
    val saved = model.isBookmarked(item.id)
    val chosen = picked
    val showVerdict = cfg.instant && chosen != null

    GlassPage(
        cfg.title, { nav.back() }, scroll = scroll, showLargeTitle = false,
        actions = {
            if (cfg.timeLimitSec > 0) {
                val low = left <= 60
                Row(
                    Modifier.liquidGlass(RoundedCornerShape(50), tint = if (low) p.bad.copy(alpha = .2f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.timer, null, tint = if (low) p.bad else p.ink, modifier = Modifier.size(16.dp))
                    Text("${left / 60}:${(left % 60).toString().padStart(2, '0')}", color = if (low) p.bad else p.ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        },
        bottomSpace = if (showVerdict) 230.dp else 0.dp,
        bottom = {
            // The verdict and the next button float pinned at the bottom, so they never need scrolling.
            AnimatedVisibility(
                showVerdict, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = .78f, stiffness = 420f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(120)),
            ) {
                val ok = chosen == 0
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)
                        .frostedGlass(RoundedCornerShape(34.dp), tint = (if (ok) p.good else p.bad).copy(alpha = .12f))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(if (ok) p.good else p.bad), contentAlignment = Alignment.Center) {
                            Icon(if (ok) Icons.check else Icons.close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text(if (ok) "נכון!" else "לא נכון", color = if (ok) p.good else p.bad, fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.weight(1f))
                        if (ok && streak >= 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.flame, null, tint = p.mid, modifier = Modifier.size(16.dp))
                            Text("$streak ברצף", color = p.mid, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Text(
                        item.question.explanation, color = p.ink, fontSize = 15.sp, lineHeight = 22.sp,
                        modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                    )
                    PrimaryButton(if (index + 1 < items.size) "השאלה הבאה" else "לתוצאות", Modifier.fillMaxWidth()) { next() }
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("שאלה ${index + 1}", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("מתוך ${items.size}", color = p.muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (cfg.instant && streak >= 2) Pill("רצף $streak", p.mid, p.midSoft)
        }
        ProgressBar((index + if (chosen != null) 1 else 0).toFloat() / items.size, p.blue, height = 7.dp)
        // Photos sit above the question; text diagrams would be unreadable (and give the answer away)
        // in that strip, so they open full size with the explanation instead.
        val qImg = shownImages.getOrNull(index)?.let { file -> item.topic.images.firstOrNull { it.file == file } ?: com.taninoni.medic.data.TopicImage(file, "") }
        val diagram = qImg != null && ("--gfx-" in qImg.file || "--proto-" in qImg.file)
        if (qImg != null && !diagram) QuestionImage(qImg, showCaption = cfg.instant && chosen != null)
        Text(item.question.q, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp, modifier = Modifier.padding(vertical = 4.dp))
        orders[index].forEachIndexed { k, o ->
            val hidden = o in eliminated
            val state = when {
                chosen == null -> 0
                cfg.instant && o == 0 -> 1
                cfg.instant && o == chosen -> 2
                !cfg.instant && o == chosen -> 3
                else -> 0
            }
            val accent = when (state) { 1 -> p.good; 2 -> p.bad; 3 -> p.blue; else -> Color.Transparent }
            val shape = RoundedCornerShape(24.dp)
            val dim = chosen != null && state == 0
            AnimatedVisibility(!hidden, exit = fadeOut(tween(220))) {
                Row(
                    Modifier.fillMaxWidth().bouncy(enabled = chosen == null) {
                        picked = o
                        log += LogEntry(item, o)
                        val ok = o == 0
                        model.recordAnswer(item.id, ok)
                        if (ok) {
                            streak++
                            if (streak > bestStreak) bestStreak = streak
                        } else {
                            streak = 0
                        }
                        // In exam mode the answer stays hidden until the end, so no sound there.
                        if (cfg.instant && model.soundOn) {
                            if (ok && streak in setOf(3, 5, 10, 15, 20, 30)) AnswerSound.play(Sfx.STREAK)
                            else AnswerSound.play(ok)
                        }
                    }.alpha(if (dim) .55f else 1f)
                        .glass(shape, tint = if (state == 0) Color.Unspecified else accent.copy(alpha = .13f), elevation = if (state == 0) 4.dp else 12.dp)
                        .then(if (state != 0) Modifier.border(2.dp, accent, shape) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (state == 0) Brush.linearGradient(listOf(p.hair, p.hair)) else Brush.linearGradient(listOf(accent, accent.copy(alpha = .75f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (state) {
                            1 -> Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            2 -> Icon(Icons.close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            else -> Text(LETTERS[k], color = if (state == 0) p.ink.copy(alpha = .7f) else Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                    Text(item.question.options[o], color = p.ink, fontSize = 16.5.sp, lineHeight = 24.sp, fontWeight = if (state != 0) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cfg.instant && chosen == null && eliminated.isEmpty() && item.question.options.size >= 4) {
                Chip("רמז 50/50", icon = Icons.half, fg = p.blue) {
                    eliminated = item.question.options.indices.filter { it != 0 }.shuffled().take(2).toSet()
                    if (model.soundOn) AnswerSound.play(Sfx.TAP)
                }
            }
            Spacer(Modifier.weight(1f))
            Chip(if (saved) "נשמר" else "שמור לחזרה", icon = Icons.bookmark, fg = if (saved) p.mid else p.muted, tint = if (saved) p.midSoft else Color.Unspecified) {
                model.toggleBookmark(item.id)
                if (model.soundOn) AnswerSound.play(Sfx.TAP)
            }
        }
        if (cfg.instant && chosen != null && diagram) AnswerImage(qImg!!)
    }
}

@Composable
fun ResultsScreen(model: AppModel, nav: Nav, s: Screen.Results) {
    val p = Pal
    val log = s.log
    val right = log.count { it.ok }
    val pct = if (log.isEmpty()) 0 else right * 100 / log.size
    val pass = pct >= model.passMark
    LaunchedEffect(s) {
        AnswerSound.preload()
        if (model.soundOn && log.isNotEmpty()) AnswerSound.play(if (pass) Sfx.PASS else Sfx.FAIL)
    }
    val cats = model.categories.filter { c -> log.any { it.item.cat == c.id } }
    val byCat = cats.associate { c -> c.id to log.filter { it.item.cat == c.id }.let { l -> l.count { it.ok } to l.size } }
    var filter by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val review = when (filter) {
        null -> emptyList()
        "wrong" -> log.filter { !it.ok }
        else -> log.filter { it.item.cat == filter }
    }
    val tone = if (pass) p.good else p.bad

    GlassLazyPage(s.config.title, { nav.back() }, state = list) {
        item {
            Column(Modifier.pageItem(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LargeTitle(if (pass) "עברת!" else "עוד קצת", s.config.title)
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(34.dp), tint = tone.copy(alpha = .07f), elevation = 18.dp).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Ring(pct, size = 118.dp, label = "הצלחה")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
                                    Icon(if (pass) Icons.check else Icons.close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Text(if (pass) "עברת" else "נכשלת", color = tone, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                            Text("$right נכונות · ${log.size - right} טעויות", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("ציון עובר: ${model.passMark}%", color = p.muted, fontSize = 13.5.sp)
                            if (s.bestStreak >= 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.flame, null, tint = p.mid, modifier = Modifier.size(16.dp))
                                Text("רצף שיא: ${s.bestStreak}", color = p.mid, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
                        if (pct > 0) Box(Modifier.weight(pct.toFloat()).height(10.dp).background(Brush.horizontalGradient(listOf(p.good.copy(alpha = .7f), p.good))))
                        if (pct < 100) Box(Modifier.weight((100 - pct).toFloat()).height(10.dp).background(Brush.horizontalGradient(listOf(p.bad, p.bad.copy(alpha = .7f)))))
                    }
                }
                if (cats.size >= 3) Panel(spacing = 4.dp) {
                    Text("לפי קטגוריה", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Radar(cats.map { it.short }, cats.map { c -> byCat[c.id]!!.let { it.first * 100 / it.second } })
                } else if (cats.isNotEmpty()) Panel {
                    cats.forEach { c ->
                        val (r, n) = byCat[c.id]!!
                        Row { Text(c.name, color = p.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${r * 100 / n}%", color = p.tone(r * 100 / n), fontWeight = FontWeight.Black) }
                        ProgressBar(r.toFloat() / n, p.tone(r * 100 / n))
                    }
                }
                SectionTitle("צפייה בתשובות")
                cats.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            val (r, n) = byCat[c.id]!!
                            val on = filter == c.id
                            val shape = RoundedCornerShape(24.dp)
                            Column(
                                Modifier.weight(1f).bouncy { filter = c.id; scope.launch { list.animateScrollToItem(1) } }
                                    .glass(shape, tint = if (on) p.blueSoft else Color.Unspecified, elevation = if (on) 10.dp else 4.dp)
                                    .then(if (on) Modifier.border(1.5.dp, p.line, shape) else Modifier).padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(c.short, color = p.blue, fontWeight = FontWeight.Black, fontSize = 17.sp)
                                Text("$r/$n", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (right < log.size) {
                    PrimaryButton("התשובות השגויות", Modifier.fillMaxWidth(), icon = Icons.eye) { filter = "wrong"; scope.launch { list.animateScrollToItem(1) } }
                    GhostButton("תרגול חוזר על הטעויות", Modifier.fillMaxWidth(), icon = Icons.target) {
                        nav.replace(Screen.Quiz(log.filter { !it.ok }.map { it.item }.shuffled(), QuizConfig("תרגול חוזר", "practice", instant = true)))
                    }
                }
                GhostButton("לבחירת קטגוריות", Modifier.fillMaxWidth()) { nav.tab(Screen.Picker) }
            }
        }
        if (filter != null) {
            item {
                SectionTitle(if (filter == "wrong") "התשובות השגויות" else model.category(filter!!)?.name.orEmpty(), Modifier.pageItem().padding(top = 18.dp, bottom = 6.dp))
            }
            items(review) { e ->
                Column(
                    Modifier.pageItem().padding(vertical = 6.dp).glass(RoundedCornerShape(26.dp), elevation = 6.dp).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(e.item.question.q, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp)
                    if (!e.ok) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.close, null, tint = p.bad, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                        Text(e.item.question.options[e.picked], color = p.bad, textDecoration = TextDecoration.LineThrough, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.check, null, tint = p.good, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                        Text(e.item.question.options[0], color = p.good, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    Text(e.item.question.explanation, color = p.muted, fontSize = 14.5.sp, lineHeight = 22.sp)
                }
            }
        }
        item { BottomSpace(40.dp) }
    }
}
