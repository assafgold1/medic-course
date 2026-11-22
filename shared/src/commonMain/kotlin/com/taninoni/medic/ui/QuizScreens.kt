package com.taninoni.medic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.LogEntry
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

    Column(Modifier.fillMaxSize()) {
        AppBar(cfg.title) { nav.back() }
        if (items.isEmpty()) {
            Sheet(Modifier.padding(top = 16.dp)) { Text("אין כרגע שאלות לתרגול כאן. כל הכבוד!", color = p.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            return@Column
        }
        val item = items[index]
        val saved = model.isBookmarked(item.id)
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(scroll).padding(top = 16.dp)) {
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("שאלה ${index + 1}/${items.size}", color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (cfg.timeLimitSec > 0) {
                        val low = left <= 60
                        Text("${left / 60}:${(left % 60).toString().padStart(2, '0')}",
                            Modifier.clip(RoundedCornerShape(50)).background(if (low) p.badSoft else p.paper).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = if (low) p.bad else p.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                    if (cfg.instant && streak >= 2) {
                        Text("רצף $streak",
                            Modifier.clip(RoundedCornerShape(50)).background(p.midSoft).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = p.mid, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                ProgressBar(index.toFloat() / items.size, p.blue)
                // Photos sit above the question; text diagrams would be unreadable (and give the answer away)
                // in that strip, so they open full size with the explanation instead.
                val qImg = shownImages.getOrNull(index)?.let { file -> item.topic.images.firstOrNull { it.file == file } ?: com.taninoni.medic.data.TopicImage(file, "") }
                val diagram = qImg != null && ("--gfx-" in qImg.file || "--proto-" in qImg.file)
                if (qImg != null && !diagram) QuestionImage(qImg, showCaption = cfg.instant && picked != null)
                Text(item.question.q, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                orders[index].forEachIndexed { k, o ->
                    val hidden = o in eliminated
                    val chosen = picked
                    val state = when {
                        chosen == null -> 0
                        cfg.instant && o == 0 -> 1
                        cfg.instant && o == chosen -> 2
                        !cfg.instant && o == chosen -> 3
                        else -> 0
                    }
                    val border = when (state) { 1 -> p.good; 2 -> p.bad; 3 -> p.blue; else -> p.hair }
                    val bg = when (state) { 1 -> p.goodSoft; 2 -> p.badSoft; 3 -> p.blueSoft; else -> p.surface }
                    val shape = RoundedCornerShape(18.dp)
                    if (!hidden) Row(
                        Modifier.fillMaxWidth().clip(shape).border(if (state == 0) 1.5.dp else 2.dp, border, shape).background(bg)
                            .clickable(enabled = chosen == null) {
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
                            }.padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (state == 0) p.paper else border), contentAlignment = Alignment.Center) {
                            Text(LETTERS[k], color = if (state == 0) p.muted else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(item.question.options[o], color = p.ink, fontSize = 16.5.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (cfg.instant && picked == null && eliminated.isEmpty() && item.question.options.size >= 4) {
                        Text(
                            "רמז 50/50",
                            color = p.blue, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(p.blueSoft)
                                .clickable {
                                    eliminated = item.question.options.indices.filter { it != 0 }.shuffled().take(2).toSet()
                                    if (model.soundOn) AnswerSound.play(Sfx.TAP)
                                }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (saved) "נשמר לחזרה" else "שמור לחזרה",
                        color = if (saved) p.mid else p.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (saved) p.midSoft else p.paper)
                            .clickable {
                                model.toggleBookmark(item.id)
                                if (model.soundOn) AnswerSound.play(Sfx.TAP)
                            }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                val chosen = picked
                if (cfg.instant && chosen != null) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (chosen == 0) p.goodSoft else p.badSoft).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (chosen == 0) "נכון" else "לא נכון", color = if (chosen == 0) p.good else p.bad, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text(item.question.explanation, color = p.ink, fontSize = 15.5.sp, lineHeight = 24.sp)
                    }
                    if (diagram) AnswerImage(qImg!!)
                    PrimaryButton(if (index + 1 < items.size) "השאלה הבאה" else "לתוצאות", Modifier.fillMaxWidth()) { next() }
                }
                VSpace(40.dp)
            }
        }
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

    LazyColumn(Modifier.fillMaxSize(), state = list) {
        item {
            Column(Modifier.fillMaxWidth().background(p.band).windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 16.dp, bottom = 50.dp)) {
                Box(Modifier.fillMaxWidth().height(62.dp)) {
                    BandButton(Icons.back, "חזרה", Modifier.align(Alignment.CenterStart)) { nav.back() }
                    Text(s.config.title, Modifier.align(Alignment.Center), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (cats.size >= 3) {
                    Radar(cats.map { it.short }, cats.map { c -> byCat[c.id]!!.let { it.first * 100 / it.second } })
                } else {
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        cats.forEach { c ->
                            val (r, n) = byCat[c.id]!!
                            Row { Text(c.name, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text("${r * 100 / n}%", color = Color.White) }
                            ProgressBar(r.toFloat() / n, Color.White, track = Color.White.copy(.25f))
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 0.dp)) {
                Sheet(Modifier.padding(top = 0.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (pass) "עברת" else "נכשלת", color = if (pass) p.good else p.bad, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text("$pct% הצלחה", color = if (pass) p.good else p.bad, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50))) {
                        if (pct > 0) Box(Modifier.weight(pct.toFloat()).height(9.dp).background(p.good))
                        if (pct < 100) Box(Modifier.weight((100 - pct).toFloat()).height(9.dp).background(p.bad))
                    }
                    Row {
                        Text("$right תשובות נכונות", color = p.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${log.size - right} טעויות", color = p.ink, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ציון עובר: ${model.passMark}%", color = p.muted, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        if (s.bestStreak >= 2) Text("רצף שיא: ${s.bestStreak}", color = p.mid, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                    SectionTitle("צפייה בתשובות לפי קטגוריה", Modifier.padding(top = 8.dp))
                    cats.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { c ->
                                val (r, n) = byCat[c.id]!!
                                val shape = RoundedCornerShape(18.dp)
                                Column(
                                    Modifier.weight(1f).clip(shape).background(if (filter == c.id) p.blueSoft else p.paper)
                                        .then(if (filter == c.id) Modifier.border(2.dp, p.blue, shape) else Modifier)
                                        .clickable { filter = c.id; scope.launch { list.animateScrollToItem(2) } }.padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(c.short, color = p.blue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text("$r/$n", color = p.muted, fontSize = 13.sp)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    VSpace(4.dp)
                    if (right < log.size) {
                        PrimaryButton("צפייה בתשובות השגויות", Modifier.fillMaxWidth()) { filter = "wrong"; scope.launch { list.animateScrollToItem(2) } }
                        GhostButton("תרגול חוזר על הטעויות", Modifier.fillMaxWidth()) {
                            nav.replace(Screen.Quiz(log.filter { !it.ok }.map { it.item }.shuffled(), QuizConfig("תרגול חוזר", "practice", instant = true)))
                        }
                    }
                    GhostButton("לבחירת קטגוריות", Modifier.fillMaxWidth()) { nav.tab(Screen.Picker) }
                }
            }
        }
        if (filter != null) {
            item {
                Text(if (filter == "wrong") "התשובות השגויות" else model.category(filter!!)?.name.orEmpty(),
                    color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(p.surface).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp))
            }
            items(review) { e ->
                Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 20.dp, vertical = 7.dp)) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.paper).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(e.item.question.q, color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp)
                        if (!e.ok) Text(e.item.question.options[e.picked], color = p.bad, textDecoration = TextDecoration.LineThrough)
                        Text(e.item.question.options[0], color = p.good, fontWeight = FontWeight.SemiBold)
                        Text(e.item.question.explanation, color = p.muted, fontSize = 14.5.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.fillMaxWidth().height(60.dp).background(p.surface)) }
    }
}
