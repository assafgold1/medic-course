package com.taninoni.medic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.ApiException
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.StudentDetail
import com.taninoni.medic.data.StudentSummary
import com.taninoni.medic.nowMillis

/** "3 ימים", "היום" … for the last-activity column. */
private fun ago(t: Long): String {
    if (t <= 0) return "לא התחיל"
    val days = ((nowMillis() - t) / 86_400_000).toInt()
    return when {
        days <= 0 -> "היום"
        days == 1 -> "אתמול"
        days < 30 -> "לפני $days ימים"
        else -> "לפני ${days / 30} חודשים"
    }
}

@Composable
fun InstructorScreen(model: AppModel, nav: Nav) {
    val p = Pal
    var students by remember { mutableStateOf<List<StudentSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf("active") }
    val query = rememberTextFieldState()
    LaunchedEffect(Unit) {
        runCatching { model.students() }.onSuccess { students = it }
            .onFailure { error = (it as? ApiException)?.message ?: "אין חיבור לשרת" }
    }

    Column(Modifier.fillMaxSize()) {
        AppBar("התקדמות החניכים") { nav.back() }
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                val list = students
                when {
                    error != null -> Text(error!!, color = p.bad)
                    list == null -> Text("טוען…", color = p.muted)
                    else -> {
                        val active = list.count { nowMillis() - it.lastActive < 7 * 86_400_000L }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat2("${list.size}", "חניכים", Modifier.weight(1f))
                            Stat2("$active", "פעילים השבוע", Modifier.weight(1f))
                            Stat2(if (list.isEmpty()) "0%" else "${list.sumOf { it.mastery } / list.size}%", "שליטה ממוצעת", Modifier.weight(1f))
                        }
                        SearchField(query)
                        Segmented(listOf("active" to "פעילות אחרונה", "mastery" to "שליטה", "name" to "שם"), sort) { sort = it }
                        val q = query.text.toString().trim()
                        val shown = list.filter { q.isEmpty() || it.name.contains(q, true) || it.email.contains(q, true) }.let { l ->
                            when (sort) {
                                "mastery" -> l.sortedByDescending { it.mastery }
                                "name" -> l.sortedBy { it.name.ifBlank { it.email } }
                                else -> l.sortedByDescending { it.lastActive }
                            }
                        }
                        if (shown.isEmpty()) Text("אין חניכים להצגה.", color = p.muted)
                        shown.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, p.hair, RoundedCornerShape(20.dp))
                                    .clickable { nav.go(Screen.Student(s.id)) }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(p.blueSoft), contentAlignment = Alignment.Center) {
                                    Text(s.name.ifBlank { s.email }.take(1).uppercase(), color = p.blue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(s.name.ifBlank { s.email }, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${s.answered} שאלות · ${s.attempts} תרגולים · ${ago(s.lastActive)}", color = p.muted, fontSize = 13.sp)
                                    ProgressBar(s.mastery / 100f, p.tone(s.mastery))
                                }
                                Pill("${s.mastery}%", p.tone(s.mastery), p.toneSoft(s.mastery))
                            }
                        }
                    }
                }
                BottomSpace(40.dp)
            }
        }
    }
}

@Composable
fun StudentScreen(model: AppModel, nav: Nav, id: Long) {
    val p = Pal
    var detail by remember(id) { mutableStateOf<StudentDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        runCatching { model.student(id) }.onSuccess { detail = it }
            .onFailure { error = (it as? ApiException)?.message ?: "אין חיבור לשרת" }
    }
    val d = detail
    Column(Modifier.fillMaxSize()) {
        AppBar(d?.student?.let { it.name.ifBlank { it.email } } ?: "חניך") { nav.back() }
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                when {
                    error != null -> Text(error!!, color = p.bad)
                    d == null -> Text("טוען…", color = p.muted)
                    else -> {
                        val s = d.student
                        Text(s.email, color = p.muted, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat2("${s.mastery}%", "שליטה בחומר", Modifier.weight(1f))
                            Stat2("${s.answered}", "שאלות שנענו", Modifier.weight(1f))
                            Stat2("${s.examsPassed}", "מבחנים שעבר", Modifier.weight(1f))
                        }
                        Text("פעילות אחרונה: ${ago(s.lastActive)}", color = p.muted, fontSize = 14.sp)

                        SectionTitle("לפי קטגוריה", Modifier.padding(top = 8.dp))
                        Panel {
                            d.categories.forEach { c ->
                                val pct = if (c.total == 0) 0 else c.correct * 100 / c.total
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text("${c.correct}/${c.total}", color = p.muted, fontSize = 13.5.sp)
                                    }
                                    ProgressBar(pct / 100f, if (c.answered == 0) p.hair else p.tone(pct))
                                }
                            }
                        }

                        SectionTitle("תרגולים ומבחנים", Modifier.padding(top = 8.dp))
                        if (d.attempts.isEmpty()) Text("עוד לא סיים תרגול.", color = p.muted)
                        else Panel(spacing = 0.dp) {
                            d.attempts.take(30).forEachIndexed { i, a ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(a.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text(ago(a.at), color = p.muted, fontSize = 13.sp)
                                    }
                                    Text("${a.right}/${a.total}", color = p.muted, fontSize = 14.sp)
                                    Pill("${a.pct}%", if (a.pct >= model.passMark) p.good else p.bad, if (a.pct >= model.passMark) p.goodSoft else p.badSoft)
                                }
                            }
                        }
                    }
                }
                BottomSpace(40.dp)
            }
        }
    }
}

@Composable
private fun Stat2(value: String, label: String, modifier: Modifier) {
    val p = Pal
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.paper).padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = p.muted, fontSize = 12.5.sp, maxLines = 1)
    }
}
