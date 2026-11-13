package com.taninoni.medic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel

@Composable
fun HomeScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString().trim()
    val all = model.allItems
    val right = all.count { model.isCorrect(it.id) }
    val overall = if (all.isEmpty()) 0 else right * 100 / all.size
    val shown = model.topics.filter { t ->
        query.isBlank() || t.title.contains(query) || t.blurb.contains(query) || t.sections.any { it.title.contains(query) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(
                Modifier.fillMaxWidth().background(p.band).windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Logo()
                    Text("מסלול חובש", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Ring(overall)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("השליטה שלך בחומר", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("$right מתוך ${all.size} שאלות נענו נכון לאחרונה", color = Color.White.copy(.85f), fontSize = 14.sp)
                        Text(model.user?.let { "מחובר: ${it.name.ifBlank { it.email }} · ${model.syncMessage}" } ?: "לא מחובר · התחבר לסנכרון ›",
                            color = Color.White.copy(.85f), fontSize = 13.sp, modifier = Modifier.clickable { nav.go(Screen.Account) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroButton("תרגול שאלות", "לפי קטגוריות", solid = true, Modifier.weight(1f)) { nav.tab(Screen.Picker) }
                    HeroButton("מבחן מסכם", "${model.examSize} שאלות · עובר מ-${model.passMark}%", solid = false, Modifier.weight(1f)) {
                        nav.go(Screen.Quiz(model.pick(model.categories.map { it.id }, model.examSize), QuizConfig("מבחן מסכם", "exam", instant = false)))
                    }
                }
            }
        }
        item {
            Column(
                Modifier.offset(y = (-30).dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(p.surface)
                    .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 4.dp),
            ) {
                SearchField(queryState)
            }
        }
        model.categories.forEach { cat ->
            val topics = shown.filter { it.cat == cat.id }
            if (topics.isNotEmpty()) {
                item(key = "h-${cat.id}") {
                    val items = model.itemsOf(cat.id)
                    Row(
                        Modifier.offset(y = (-30).dp).fillMaxWidth().background(p.surface).padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(p.blueSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.forCategory(cat.id), null, tint = p.blue, modifier = Modifier.size(20.dp))
                        }
                        Text(cat.name, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        ScorePill(model.mastery(items), model.seen(items))
                    }
                }
                items(topics, key = { it.id }) { t ->
                    val items = model.allItems.filter { it.topic.id == t.id }
                    Box(Modifier.offset(y = (-30).dp).fillMaxWidth().background(p.surface).padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, p.hair, RoundedCornerShape(20.dp))
                                .clickable { nav.go(Screen.Topic(t.id)) }.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.paper), contentAlignment = Alignment.Center) {
                                Text(t.number.toString().padStart(2, '0'), color = p.blue, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(t.title, color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                                Text(t.blurb, color = p.muted, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            ScorePill(model.mastery(items), model.seen(items))
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.offset(y = (-30).dp).fillMaxWidth().background(p.surface).padding(16.dp)) {
                if (shown.isEmpty()) Text("לא נמצא נושא מתאים.", color = p.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(
                    "החומר מבוסס על מצגות קורס החובשים (פרקים א–ה). נועד לחזרה ולתרגול, ואינו מחליף את המצגות או את הפרוטוקולים הרשמיים. גרסת תוכן ${model.content?.version}.",
                    color = p.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 14.dp, bottom = 30.dp),
                )
                BottomSpace(0.dp)
            }
        }
    }
}

@Composable
private fun HeroButton(title: String, sub: String, solid: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = Pal
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).then(if (solid) Modifier.background(Color.White) else Modifier.border(2.dp, Color.White.copy(.7f), shape))
            .clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, color = if (solid) p.blue else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, color = if (solid) p.muted else Color.White.copy(.85f), fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun SearchField(state: TextFieldState) {
    val p = Pal
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.paper).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.search, null, tint = p.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (state.text.isEmpty()) Text("חיפוש נושא, למשל: לידה, כוויות, אמבו", color = p.muted, fontSize = 15.sp)
            BasicTextField(state, lineLimits = TextFieldLineLimits.SingleLine, textStyle = TextStyle(color = p.ink, fontSize = 15.sp), cursorBrush = SolidColor(p.blue), modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ---------------- category picker (progress screen) ---------------- */

@Composable
fun PickerScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val allIds = model.categories.map { it.id }
    val sel = model.selectedCats ?: allIds.toSet()
    val mistakes = model.mistakes().size
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppBar("תרגול שאלות", null)
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp)) {
                item {
                    Sheet {
                        SectionTitle(
                            "בחירת קטגוריות",
                            action = if (sel.size == allIds.size) "נקה הכול" else "בחר הכול",
                        ) { model.setSelection(if (sel.size == allIds.size) emptySet() else allIds.toSet()) }
                        model.categories.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { cat ->
                                    val items = model.itemsOf(cat.id)
                                    CategoryCard(
                                        name = cat.name, catId = cat.id, count = items.size, pct = model.mastery(items), seen = model.seen(items),
                                        on = cat.id in sel, modifier = Modifier.weight(1f),
                                    ) { model.setSelection(if (cat.id in sel) sel - cat.id else sel + cat.id) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        SectionTitle("אפשרויות", Modifier.padding(top = 12.dp))
                        Panel {
                            Eyebrow("מספר שאלות")
                            Segmented(listOf(10, 20, 30, 50).map { it.toString() to it.toString() }, model.questionCount.toString()) { model.setCount(it.toInt()) }
                            GhostButton(if (mistakes > 0) "תרגול הטעויות שלי ($mistakes)" else "אין טעויות לתרגול", Modifier.fillMaxWidth(), enabled = mistakes > 0) {
                                nav.go(Screen.Quiz(model.mistakes().shuffled().take(50), QuizConfig("תרגול טעויות", "mistakes", instant = true)))
                            }
                        }

                        if (model.progress.attempts.isNotEmpty()) {
                            SectionTitle("ניסיונות אחרונים", Modifier.padding(top = 12.dp))
                            Panel(spacing = 0.dp) {
                                model.progress.attempts.take(8).forEachIndexed { i, a ->
                                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(a.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text("${a.right}/${a.total}", color = p.muted, fontSize = 14.sp)
                                        Pill("${a.pct}%", if (a.pct >= model.passMark) p.good else p.bad, if (a.pct >= model.passMark) p.goodSoft else p.badSoft)
                                    }
                                }
                            }
                        }
                        BottomSpace(96.dp)
                    }
                }
            }
        }
        // Start button floats above the tab bar; content fades out underneath it.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(p.surface.copy(alpha = 0f), p.surface, p.surface)))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = LocalBottomInset.current + 6.dp),
        ) {
            PrimaryButton(if (sel.isEmpty()) "בחר לפחות קטגוריה אחת" else "התחל תרגול · ${model.questionCount} שאלות", Modifier.fillMaxWidth(), enabled = sel.isNotEmpty()) {
                nav.go(Screen.Quiz(model.pick(allIds.filter { it in sel }, model.questionCount), QuizConfig("תרגול שאלות", "practice", instant = true)))
            }
        }
    }
}

@Composable
private fun CategoryCard(name: String, catId: String, count: Int, pct: Int, seen: Boolean, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = Pal
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).border(if (on) 2.dp else 1.dp, if (on) p.line else p.hair, shape).background(p.surface).clickable(onClick = onClick)
            .alpha(if (on) 1f else .55f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(p.blueSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.forCategory(catId), null, tint = p.blue, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (on) p.blue else p.surface)
                    .then(if (on) Modifier else Modifier.border(2.dp, p.line, RoundedCornerShape(8.dp))),
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        Text(name, color = p.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, minLines = 2, lineHeight = 20.sp)
        ProgressBar(if (seen) pct / 100f else 0f, p.tone(pct))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$pct%", color = if (seen) p.tone(pct) else p.muted, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("$count שאלות", color = p.muted, fontSize = 12.sp)
        }
    }
}
