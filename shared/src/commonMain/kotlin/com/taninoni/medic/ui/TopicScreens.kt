package com.taninoni.medic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.Topic

@Composable
fun TopicScreen(model: AppModel, nav: Nav, id: String) {
    val p = Pal
    val t = model.topic(id) ?: return
    var tab by remember(id) { mutableStateOf("learn") }
    Column(Modifier.fillMaxSize()) {
        AppBar(t.title) { nav.back() }
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                Eyebrow("${model.category(t.cat)?.name.orEmpty()} · נושא ${t.number}")
                Segmented(listOf("learn" to "לימוד", "cards" to "כרטיסיות", "quiz" to "תרגול"), tab) { tab = it }
                when (tab) {
                    "learn" -> Learn(t) { tab = it }
                    "cards" -> FlashCards(t)
                    else -> {
                        val items = model.allItems.filter { it.topic.id == t.id }
                        val pct = model.mastery(items)
                        Text("${items.size} שאלות בנושא. שליטה נוכחית: $pct%", color = p.ink, fontSize = 16.sp)
                        ProgressBar(pct / 100f, p.tone(pct))
                        PrimaryButton("התחל תרגול", Modifier.fillMaxWidth()) {
                            nav.go(Screen.Quiz(items.shuffled(), QuizConfig(t.title, "topic", instant = true, topicId = t.id)))
                        }
                    }
                }
                Spacer(Modifier.heightIn(min = 40.dp))
            }
        }
    }
}

@Composable
private fun Learn(t: Topic, goTab: (String) -> Unit) {
    val p = Pal
    t.note?.let {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.midSoft).padding(16.dp)) { Text(it, color = p.ink, fontSize = 15.sp, lineHeight = 22.sp) }
    }
    t.nums.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { n ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(p.blueSoft).padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(n.value, color = p.blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(n.label, color = p.muted, fontSize = 13.5.sp, lineHeight = 18.sp)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    ImageGallery(t.images)
    t.sections.forEach { s ->
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.title, color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            s.items.forEachIndexed { i, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.steps) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(p.blue), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp).size(6.dp).clip(CircleShape).background(p.blue.copy(alpha = .55f)))
                    }
                    Text(line, color = p.ink, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    VSpace(12.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("לתרגול (${t.questions.size} שאלות)", Modifier.weight(1f)) { goTab("quiz") }
        GhostButton("כרטיסיות") { goTab("cards") }
    }
}

@Composable
private fun FlashCards(t: Topic) {
    val p = Pal
    var deck by remember(t.id) { mutableStateOf(t.questions.shuffled()) }
    var i by remember(t.id) { mutableIntStateOf(0) }
    var known by remember(t.id) { mutableIntStateOf(0) }
    var again by remember(t.id) { mutableStateOf(listOf<com.taninoni.medic.data.Question>()) }
    var flipped by remember(t.id, i, deck) { mutableStateOf(false) }

    if (i >= deck.size) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("סיימת סבב")
            Text("$known/${deck.size}", color = p.ink, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("כרטיסים שסימנת \"ידעתי\"", color = p.muted)
            if (again.isNotEmpty()) PrimaryButton("חזרה על ${again.size} שלא ידעתי", Modifier.fillMaxWidth()) { deck = again.shuffled(); again = emptyList(); i = 0; known = 0 }
            GhostButton("סבב חדש מלא", Modifier.fillMaxWidth()) { deck = t.questions.shuffled(); again = emptyList(); i = 0; known = 0 }
        }
        return
    }
    val q = deck[i]
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(420))
    Row { Text("כרטיס ${i + 1}/${deck.size}", color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("הקש להפוך", color = p.muted, fontSize = 14.sp) }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 300.dp)
            .graphicsLayer { rotationY = rot; cameraDistance = 14 * density }
            .clip(RoundedCornerShape(22.dp))
            .then(if (rot > 90f) Modifier.background(p.band) else Modifier.background(p.surface).border(2.dp, p.line, RoundedCornerShape(22.dp)))
            .clickable { flipped = !flipped }.padding(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.graphicsLayer { rotationY = if (rot > 90f) 180f else 0f }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rot <= 90f) {
                Text("שאלה", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(q.q, color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)
            } else {
                Text("תשובה", color = Color.White.copy(.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(q.options[0], color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)
                Text(q.explanation, color = Color.White.copy(.9f), fontSize = 15.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GhostButton("עוד לא", Modifier.weight(1f)) { again = again + q; i++ }
        PrimaryButton("ידעתי", Modifier.weight(1f)) { known++; i++ }
    }
}

/* ---------------- reference tables ---------------- */

@Composable
fun ReferenceScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val ref = model.content?.reference
    Column(Modifier.fillMaxSize()) {
        AppBar("מספרים לשינון", null)
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                Text("ההבדלים בין מבוגר, ילד ותינוק, והמספרים שחוזרים במבחנים.", color = p.muted, fontSize = 15.sp, lineHeight = 22.sp)
                if (ref != null) {
                    SectionTitle("מבוגר · ילד · תינוק")
                    ref.compare.rows.forEach { r ->
                        Panel(spacing = 10.dp) {
                            Text(r.label, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            ref.compare.columns.zip(r.values).forEach { (col, v) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(col, color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                                    Text(v, color = p.blue, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    SectionTitle("כללי", Modifier.padding(top = 8.dp))
                    Panel(spacing = 0.dp) {
                        ref.general.forEachIndexed { i, g ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(g.label, color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(g.value, color = p.blue, fontSize = 15.sp, lineHeight = 21.sp)
                            }
                        }
                    }
                }
                BottomSpace(40.dp)
            }
        }
    }
}
