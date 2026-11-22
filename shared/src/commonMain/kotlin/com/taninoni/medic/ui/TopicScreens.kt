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

@Composable
fun TopicScreen(model: AppModel, nav: Nav, id: String) {
    val p = Pal
    val t = model.topic(id) ?: return
    LaunchedEffect(id) { model.openedTopic(id) }
    var tab by remember(id) { mutableStateOf("learn") }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val goTab: (String) -> Unit = { tab = it; scope.launch { scroll.animateScrollTo(0) } }
    GlassPage(t.title, { nav.back() }, subtitle = "${model.category(t.cat)?.name.orEmpty()} · נושא ${t.number}", scroll = scroll) {
        Segmented(listOf("learn" to "לימוד", "cards" to "כרטיסיות", "quiz" to "תרגול"), tab) { tab = it }
        when (tab) {
            "learn" -> Learn(t, model.content?.protocols.orEmpty().filter { it.topic == t.id }, { nav.go(Screen.ProtocolView(it)) }, goTab)
            "cards" -> FlashCards(t)
            else -> {
                val items = model.allItems.filter { it.topic.id == t.id }
                val pct = model.mastery(items)
                Panel(spacing = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Ring(pct, size = 96.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${items.size} שאלות", color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("שליטה נוכחית בנושא", color = p.muted, fontSize = 14.sp)
                        }
                    }
                    PrimaryButton("התחל תרגול", Modifier.fillMaxWidth(), icon = Icons.bolt) {
                        nav.go(Screen.Quiz(items.shuffled(), QuizConfig(t.title, "topic", instant = true, topicId = t.id)))
                    }
                }
            }
        }
    }
}

@Composable
private fun Learn(t: Topic, protocols: List<com.taninoni.medic.data.Protocol>, openProtocol: (String) -> Unit, goTab: (String) -> Unit) {
    val p = Pal
    if (t.keyPoints.isNotEmpty()) Panel(spacing = 12.dp, tint = p.blueSoft.copy(alpha = p.blueSoft.alpha * .7f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.sparkle, null, tint = p.blue, modifier = Modifier.size(20.dp))
            Text("בקצרה", color = p.blue, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        t.keyPoints.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 3.dp).size(20.dp).clip(CircleShape).background(p.blue), contentAlignment = Alignment.Center) {
                    Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                Text(line, color = p.ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
        }
    }
    // The topic's field protocols, one tap away.
    protocols.forEach { pr ->
        GlassRow(pr.title, subtitle = "פרוטוקול שטח", icon = Icons.flow, iconColor = p.bad) { openProtocol(pr.id) }
    }
    t.note?.let {
        Panel(tint = p.midSoft) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.info, null, tint = p.mid, modifier = Modifier.size(20.dp))
                Text(it, color = p.ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
            }
        }
    }
    if (t.nums.isNotEmpty()) Panel(spacing = 0.dp) {
        t.nums.forEachIndexed { i, n ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(n.label, color = p.muted, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                Text(n.value, color = p.blue, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }
    }
    val placed = t.sections.flatMap { it.images }.toSet()
    t.sections.forEach { s ->
        Panel(spacing = 12.dp) {
            Text(s.title, color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
            s.items.forEachIndexed { i, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.steps) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.bandA, p.bandB))),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                    } else {
                        Box(Modifier.padding(top = 9.dp, start = 4.dp, end = 4.dp).size(7.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.bandA, p.bandB))))
                    }
                    Text(line, color = p.ink, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.weight(1f))
                }
            }
            s.images.forEach { f -> t.images.firstOrNull { it.file == f }?.let { InlineImage(it) } }
        }
    }
    ImageGallery(t.images.filter { it.file !in placed })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("לתרגול · ${t.questions.size}", Modifier.weight(1.3f), icon = Icons.bolt) { goTab("quiz") }
        GhostButton("כרטיסיות", Modifier.weight(1f), icon = Icons.cards) { goTab("cards") }
    }
}

@Composable
private fun FlashCards(t: Topic) {
    val p = Pal
    val soundOn = LocalSoundOn.current
    var deck by remember(t.id) { mutableStateOf(t.questions.shuffled()) }
    var i by remember(t.id) { mutableIntStateOf(0) }
    var known by remember(t.id) { mutableIntStateOf(0) }
    var again by remember(t.id) { mutableStateOf(listOf<com.taninoni.medic.data.Question>()) }
    var flipped by remember(t.id, i, deck) { mutableStateOf(false) }

    if (i >= deck.size) {
        LaunchedEffect(i) {
            if (soundOn) AnswerSound.play(if (known * 2 >= deck.size) Sfx.PASS else Sfx.FAIL)
        }
        Panel(spacing = 12.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconTile(Icons.trophy, p.mid, size = 60.dp, solid = true)
                Eyebrow("סיימת סבב")
                Text("$known/${deck.size}", color = p.ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
                Text("כרטיסים שסימנת \"ידעתי\"", color = p.muted)
            }
            if (again.isNotEmpty()) PrimaryButton("חזרה על ${again.size} שלא ידעתי", Modifier.fillMaxWidth()) { deck = again.shuffled(); again = emptyList(); i = 0; known = 0 }
            GhostButton("סבב חדש מלא", Modifier.fillMaxWidth()) { deck = t.questions.shuffled(); again = emptyList(); i = 0; known = 0 }
        }
        return
    }
    val q = deck[i]
    val rot by animateFloatAsState(if (flipped) 180f else 0f, spring(dampingRatio = .7f, stiffness = 180f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("כרטיס ${i + 1} מתוך ${deck.size}", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("הקש להפוך", color = p.muted, fontSize = 13.sp)
    }
    ProgressBar(i.toFloat() / deck.size, p.blue)
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 320.dp)
            .graphicsLayer { rotationY = rot; cameraDistance = 14 * density }
            .bouncy(pressedScale = .98f) { flipped = !flipped; if (soundOn) AnswerSound.play(Sfx.FLIP) }
            .then(
                if (rot > 90f) Modifier.glass(shape, elevation = 20.dp).background(Brush.linearGradient(listOf(p.bandA, p.bandB)))
                else Modifier.glass(shape, elevation = 16.dp),
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.graphicsLayer { rotationY = if (rot > 90f) 180f else 0f }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rot <= 90f) {
                Text("שאלה", color = p.blue, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(q.q, color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            } else {
                Text("תשובה", color = Color.White.copy(.85f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(q.options[0], color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                Text(q.explanation, color = Color.White.copy(.92f), fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("עוד לא", Modifier.weight(1f)) { if (soundOn) AnswerSound.play(Sfx.WRONG); again = again + q; i++ }
        PrimaryButton("ידעתי", Modifier.weight(1f), icon = Icons.check) { if (soundOn) AnswerSound.play(Sfx.CORRECT); known++; i++ }
    }
}

/* ---------------- reference tables ---------------- */

@Composable
fun ReferenceScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val ref = model.content?.reference
    GlassPage("מספרים לשינון", null, subtitle = "מבוגר · ילד · תינוק") {
        Text("ההבדלים בין מבוגר, ילד ותינוק, והמספרים שחוזרים במבחנים.", color = p.muted, fontSize = 15.sp, lineHeight = 22.sp)
        if (ref != null) {
            ref.compare.rows.forEach { r ->
                Panel(spacing = 12.dp) {
                    Text(r.label, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ref.compare.columns.zip(r.values).forEach { (col, v) ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(p.blueSoft.copy(alpha = p.blueSoft.alpha * .6f)).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(col, color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text(v, color = p.ink, fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            SectionTitle("כללי")
            Panel(spacing = 0.dp) {
                ref.general.forEachIndexed { i, g ->
                    if (i > 0) Divider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(g.label, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(g.value, color = p.blue, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
