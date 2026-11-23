package com.taninoni.medic.ui

import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val searching = query.isNotEmpty()
    val firstName = model.user?.name?.trim()?.substringBefore(' ').orEmpty()

    val listState = rememberLazyListState()
    // The header drifts slower than the cards and fades as it goes; the backdrop light drifts slower still.
    val headerScroll by remember { derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f } }
    // Cards rise into place once, when the screen first appears.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(900); entered = true }

    Box(Modifier.fillMaxSize()) {
    GlassLazyPage("מסלול חובש", null, state = listState, showTopTitle = false) {
        if (searching) item { Spacer(Modifier.height(10.dp)) }
        if (!searching) item {
            // The orange block continues from the top bar, like the opening screen.
            Box(
                Modifier.fillMaxWidth().padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp)).background(Brand)
                    .padding(top = 10.dp, bottom = 26.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Row(
                    Modifier.pageItem().padding(horizontal = 4.dp).graphicsLayer {
                        val k = (headerScroll / 220f).coerceIn(0f, 1f)
                        translationY = headerScroll * .4f
                        alpha = 1f - k
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (firstName.isNotEmpty()) "שלום, $firstName" else "שלום", color = Color.White.copy(alpha = .88f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("מסלול חובש", color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black)
                    }
                    BrandMark(64.dp)
                }
            }
        }
        if (!searching && model.profileIncomplete) item {
            GlassRow(
                model.g("ספר לנו עוד עליך", "ספרי לנו עוד עלייך"),
                Modifier.pageItem().padding(bottom = 14.dp),
                subtitle = "איך לפנות אליך ובאיזה ארגון, כדי שהמדריכים יכירו",
                icon = Icons.special,
            ) { nav.go(Screen.Account) }
        }
        if (!searching) item {
            StreakCard(model, Modifier.pageItem().padding(bottom = 16.dp).rise(0, entered)) { nav.go(Screen.Streak) }
        }
        if (!searching) item {
            CollectionCard(model, Modifier.pageItem().padding(bottom = 16.dp).rise(1, entered)) { nav.go(Screen.Stickers) }
            FriendsCard(model, Modifier.pageItem().padding(bottom = 16.dp).rise(1, entered)) { nav.go(Screen.Friends) }
        }
        if (!searching) item {
            HeroCard(model, nav, overall, right, all.size, Modifier.pageItem().rise(1, entered))
        }
        if (!searching) item {
            Column(Modifier.pageItem().padding(top = 16.dp).rise(2, entered), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                QuickTiles(model, nav)
            }
        }
        if (!searching) item(key = "chips") {
            CategoryChips(model, Modifier.padding(top = 26.dp, bottom = 2.dp))
        }
        val filter = if (searching) null else model.homeFilter
        model.categories.filter { filter == null || it.id == filter }.forEach { cat ->
            val topics = shown.filter { it.cat == cat.id }
            if (topics.isNotEmpty()) {
                // Searching or showing one chapter always unfolds it.
                val folded = !searching && filter == null && cat.id in model.collapsedCats
                item(key = "h-${cat.id}") {
                    val items = model.itemsOf(cat.id)
                    val turn by animateFloatAsState(if (folded) 90f else 0f, spring(dampingRatio = .7f, stiffness = 400f))
                    Row(
                        Modifier.pageItem().padding(top = 20.dp, bottom = 8.dp).clip(RoundedCornerShape(18.dp))
                            .bouncy(pressedScale = .98f, enabled = !searching && filter == null) { tick(); model.toggleCollapsed(cat.id) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.forCategory(cat.id), null, tint = p.blue, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text(
                                "${topics.size} נושאים" + if (model.seen(items)) " · ${model.mastery(items)}% שליטה" else "",
                                color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (!searching && filter == null) Icon(Icons.forward, if (folded) "פתיחה" else "קיפול", tint = p.muted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -90f + turn })
                    }
                }
                if (!folded) items(topics, key = { it.id }) { t ->
                    val items = model.allItems.filter { it.topic.id == t.id }
                    val seen = model.seen(items)
                    val pct = model.mastery(items)
                    Row(
                        Modifier.pageItem().padding(vertical = 6.dp).bouncy { nav.go(Screen.Topic(t.id)) }
                            .deepGlass(RoundedCornerShape(26.dp), elevation = 8.dp, radius = 28.dp).padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        NumberBadge(t.number, pct, seen)
                        Text(
                            t.title, color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (seen) Pill("$pct%", p.tone(pct), p.toneSoft(pct)) else Chevron()
                    }
                }
            }
        }
        item {
            Column(Modifier.pageItem().padding(top = 16.dp)) {
                if (shown.isEmpty()) Text("לא נמצא נושא מתאים.", color = p.muted, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center)
                Text(
                    "החומר מבוסס על מצגות קורס החובשים (פרקים א–ה). נועד לחזרה ולתרגול, ואינו מחליף את המצגות או את הפרוטוקולים הרשמיים. גרסת תוכן ${model.content?.version}.",
                    color = p.muted, fontSize = 12.5.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 12.dp, end = 12.dp),
                )
                BottomSpace(16.dp)
            }
        }
    }
    // Pinned at the top: the list scrolls under it and the glass bends what passes beneath.
    SearchField(queryState, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 6.dp).pageItem())
    }
}

/** Mastery ring and the two main actions. */
@Composable
private fun HeroCard(model: AppModel, nav: Nav, overall: Int, right: Int, total: Int, modifier: Modifier) {
    val p = Pal
    Column(
        modifier.deepGlass(RoundedCornerShape(36.dp), elevation = 22.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // Glass in glass: the ring sits in a clear lens that bends the light behind the card.
            Box(Modifier.size(128.dp).liquidGlass(CircleShape, frost = .8f), contentAlignment = Alignment.Center) {
                Ring(overall, size = 110.dp, label = "שליטה")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("השליטה שלך בחומר", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("$right/$total נענו נכון", color = p.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                ProgressBar(if (total == 0) 0f else right.toFloat() / total, p.blue)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("תרגול חכם", Modifier.weight(1f), icon = Icons.sparkle) {
                nav.go(Screen.Quiz(model.smartPick(20), QuizConfig("תרגול חכם", "smart", instant = true)))
            }
            GhostButton("מבחן מסכם", Modifier.weight(1f), icon = Icons.timer) {
                nav.go(Screen.Quiz(model.pick(model.categories.map { it.id }, model.examSize),
                    QuizConfig("מבחן מסכם", "exam", instant = false, timeLimitSec = model.examSize * 60)))
            }
        }
    }
}

/** Scene safety, protocols and "continue where you left off". */
@Composable
private fun QuickTiles(model: AppModel, nav: Nav) {
    val p = Pal
    val safety = model.topic("approach")?.images?.firstOrNull { it.file.endsWith("gfx-safety.webp") }
    val protocols = model.content?.protocols.orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Scene safety comes before everything else, so it stays one tap away on the home screen.
        if (safety != null) Tile(Icons.shield, "בטיחות בזירה", "עוצרים · סוקרים · מתמגנים", p.blue, Modifier.weight(1f)) { ViewerState.show(listOf(safety), 0) }
        if (protocols.isNotEmpty()) Tile(Icons.flow, "פרוטוקולים", "${protocols.size} תרשימי זרימה", p.blue, Modifier.weight(1f)) { nav.go(Screen.Protocols) }
    }
    val due = model.reviewDueCount
    if (due > 0) Row(
        Modifier.fillMaxWidth().bouncy { nav.go(Screen.Quiz(model.reviewDue().take(40), QuizConfig("חזרה יומית", "review", instant = true))) }
            .deepGlass(RoundedCornerShape(26.dp), elevation = 12.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(Icons.repeat, p.blue, solid = true)
        Column(Modifier.weight(1f)) {
            Text("חזרה יומית", color = p.blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(if (due == 1) "שאלה אחת מחכה לחזרה" else "$due שאלות מחכות לחזרה", color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Chevron(p.blue)
    }
    model.lastTopicId?.let { model.topic(it) }?.let { t ->
        Row(
            Modifier.fillMaxWidth().bouncy { nav.go(Screen.Topic(t.id)) }
                .deepGlass(RoundedCornerShape(26.dp), elevation = 12.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(Icons.basics, p.blue, solid = true)
            Column(Modifier.weight(1f)) {
                Text("להמשיך מאיפה שעצרת", color = p.blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(t.title, color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Chevron(p.blue)
        }
    }
    model.nextTopic()?.takeIf { it.id != model.lastTopicId }?.let { t ->
        val items = model.allItems.filter { it.topic.id == t.id }
        val started = model.seen(items)
        Row(
            Modifier.fillMaxWidth().bouncy { nav.go(Screen.Topic(t.id)) }
                .deepGlass(RoundedCornerShape(26.dp), elevation = 12.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(Icons.target, p.blue)
            Column(Modifier.weight(1f)) {
                Text(if (started) "כדאי לחזור על" else "הנושא הבא שלך", color = p.blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(t.title, color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (started) "שליטה ${model.mastery(items)}%" else "עוד לא התחלת · ${items.size} שאלות", color = p.muted, fontSize = 12.5.sp)
            }
            Chevron(p.blue)
        }
    }
}

/** One chip per chapter, with "all" first; picking one shows only that chapter. */
@Composable
private fun CategoryChips(model: AppModel, modifier: Modifier) {
    val p = Pal
    val rowState = rememberLazyListState()
    val selected = (model.categories.indexOfFirst { it.id == model.homeFilter } + 1).coerceAtLeast(0)
    // Keep the chosen chip in view.
    LaunchedEffect(selected) { rowState.animateScrollToItem(maxOf(0, selected - 1)) }
    androidx.compose.foundation.lazy.LazyRow(
        modifier.fillMaxWidth(),
        state = rowState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val all = listOf<Pair<String?, String>>(null to "הכל") + model.categories.map { it.id to it.name }
        items(all.size) { i ->
            val (id, name) = all[i]
            val on = model.homeFilter == id
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.bouncy(pressedScale = .94f) { if (!on) tick(); model.chooseHomeFilter(id) }
                    .glass(shape, tint = if (on) p.blueSoft else Color.Unspecified, elevation = if (on) 8.dp else 3.dp)
                    .border(if (on) 1.5.dp else 0.dp, if (on) p.blue else Color.Transparent, shape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (id != null) Icon(Icons.forCategory(id), null, tint = if (on) p.blue else p.muted, modifier = Modifier.size(16.dp))
                Text(name, color = if (on) p.blue else p.ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = Pal
    Column(
        modifier.heightIn(min = 132.dp).bouncy(onClick = onClick).deepGlass(RoundedCornerShape(28.dp), elevation = 14.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, color, solid = true)
        Spacer(Modifier.weight(1f))
        Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(sub, color = p.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Topic number in a small glass disc whose rim glows in the mastery tone once answered. */
@Composable
private fun NumberBadge(n: Int, pct: Int, seen: Boolean) {
    val p = Pal
    val c = if (seen) p.tone(pct) else p.muted
    Box(
        Modifier.size(40.dp).liquidGlass(CircleShape, tint = if (seen) c.copy(alpha = .14f) else Color.Transparent)
            .border(1.5.dp, c.copy(alpha = if (seen) .6f else .15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("$n", color = if (seen) c else p.ink.copy(alpha = .7f), fontSize = 14.sp, fontWeight = FontWeight.Black) }
}

@Composable
fun SearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    val p = Pal
    val src = remember { MutableInteractionSource() }
    val focus = remember { FocusRequester() }
    Row(
        modifier.fillMaxWidth().height(54.dp)
            .softShadow(RoundedCornerShape(50), 14.dp, p.glassShadow)
            .liquidGlass(
                RoundedCornerShape(50), interaction = src, frost = .45f,
                fill = if (p.dark) Color(0xFF1D1A19).copy(alpha = .38f) else Color.White.copy(alpha = .34f),
            )
            .clickable(src, indication = null) { focus.requestFocus() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.search, null, tint = p.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (state.text.isEmpty()) Text("חיפוש נושא: לידה, כוויות, אמבו…", color = p.muted, fontSize = 15.sp, maxLines = 1)
            BasicTextField(state, lineLimits = TextFieldLineLimits.SingleLine, textStyle = TextStyle(color = p.ink, fontSize = 15.sp), cursorBrush = SolidColor(p.blue), modifier = Modifier.fillMaxWidth().focusRequester(focus))
        }
        if (state.text.isNotEmpty()) Box(
            Modifier.size(26.dp).clip(CircleShape).background(p.hair).bouncy(label = "ניקוי") { state.clearText() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.close, "ניקוי", tint = p.muted, modifier = Modifier.size(14.dp)) }
    }
}

/* ---------------- practice setup ---------------- */

@Composable
fun PickerScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val allIds = model.categories.map { it.id }
    val sel = model.selectedCats ?: allIds.toSet()
    val mistakes = model.mistakes().size
    val all = model.allItems
    val answered = all.count { model.isAnswered(it.id) }
    val readiness = model.mastery(all)
    val chosenQuestions = all.count { it.cat in sel }
    GlassPage(
        "תרגול", null, subtitle = "בונים תרגול לפי הנושאים שלכם",
        bottomSpace = 84.dp,
        bottom = {
            FloatingDock {
                PrimaryButton(
                    if (sel.isEmpty()) "בחרו לפחות קטגוריה אחת" else "התחלת תרגול · ${model.questionCount} שאלות", Modifier.fillMaxWidth(),
                    enabled = sel.isNotEmpty(), icon = if (sel.isEmpty()) null else Icons.bolt,
                ) {
                    nav.go(Screen.Quiz(model.pick(allIds.filter { it in sel }, model.questionCount), QuizConfig("תרגול שאלות", "practice", instant = true)))
                }
            }
        },
    ) {
        ReviewCard(model, nav)

        // Key numbers, like a dashboard: how ready you are and how much you've done.
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 6.dp).padding(vertical = 14.dp)) {
            Kpi("$readiness%", "מוכנות למבחן", p.tone(readiness), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(p.hair).align(Alignment.CenterVertically))
            Kpi("$answered", "שאלות שנענו", p.ink, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(40.dp).background(p.hair).align(Alignment.CenterVertically))
            Kpi("${model.progress.attempts.size}", "תרגולים", p.ink, Modifier.weight(1f))
        }

        // The builder: categories, then how many questions.
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("קטגוריות", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("נבחרו ${sel.size} מתוך ${allIds.size} · $chosenQuestions שאלות במאגר", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (sel.size == allIds.size) "נקה הכול" else "בחר הכול", color = p.blue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).bouncy { tick(); model.setSelection(if (sel.size == allIds.size) emptySet() else allIds.toSet()) }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            model.categories.forEachIndexed { i, cat ->
                if (i > 0) Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(p.hair))
                val items = model.itemsOf(cat.id)
                CategoryRow(cat.name, cat.id, items.size, model.mastery(items), model.seen(items), cat.id in sel) {
                    tick(); model.setSelection(if (cat.id in sel) sel - cat.id else sel + cat.id)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("מספר שאלות", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Segmented(listOf(10, 20, 30, 50).map { it.toString() to it.toString() }, model.questionCount.toString()) { model.setCount(it.toInt()) }
            }
        }

        SectionTitle("מצבי למידה")
        val savedCount = model.bookmarks.size
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeTile(Icons.target, "הטעויות שלי", if (mistakes > 0) "$mistakes שאלות לתיקון" else "אין טעויות כרגע", mistakes > 0, Modifier.weight(1f)) {
                nav.go(Screen.Quiz(model.mistakes().shuffled().take(50), QuizConfig("תרגול טעויות", "mistakes", instant = true)))
            }
            ModeTile(Icons.bolt, "אתגר 60 שניות", if (model.blitzBest > 0) "שיא: ${model.blitzBest} נק'" else "כמה שיותר בדקה", true, Modifier.weight(1f)) {
                nav.go(Screen.Blitz(model.pick(allIds.filter { it in sel }.ifEmpty { allIds }, 80)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeTile(Icons.anat, "זיהוי אנטומיה", "מצביעים על התמונה", true, Modifier.weight(1f)) { nav.go(Screen.AnatomyQuiz) }
            ModeTile(Icons.bookmark, "שאלות שמורות", if (savedCount > 0) "$savedCount שאלות" else "סמנו בזמן תרגול", savedCount > 0, Modifier.weight(1f)) {
                nav.go(Screen.Quiz(model.bookmarkedItems().shuffled(), QuizConfig("שאלות שמורות", "bookmarks", instant = true)))
            }
        }

        if (model.progress.attempts.isNotEmpty()) {
            SectionTitle("תרגולים אחרונים")
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 6.dp).padding(horizontal = 16.dp)) {
                model.progress.attempts.take(8).forEachIndexed { i, a ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                    val ok = a.pct >= model.passMark
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(a.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${a.right} מתוך ${a.total} נכונות", color = p.muted, fontSize = 12.5.sp)
                        }
                        Text(
                            "${a.pct}%", color = if (ok) p.good else p.bad, fontSize = 16.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (ok) p.goodSoft else p.badSoft).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spaced review: each mistake returns after a day, then 3 days, then a week, and leaves once it's
 * answered right at the last stage. Shows what's due today and how many questions sit at each stage.
 */
@Composable
private fun ReviewCard(model: AppModel, nav: Nav) {
    val p = Pal
    val due = model.reviewDueCount
    val stages = model.reviewStages()
    val inReview = stages.sum()
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 6.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(if (due > 0) Brand else p.blueSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.repeat, null, tint = if (due > 0) Color.White else p.blue, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("חזרה יומית", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        due > 0 -> if (due == 1) "שאלה אחת מחכה לחזרה היום" else "$due שאלות מחכות לחזרה היום"
                        model.reviewTomorrow > 0 -> "סיימת להיום · מחר ${model.reviewTomorrow} שאלות"
                        inReview > 0 -> "סיימת להיום · הבאות בעוד כמה ימים"
                        else -> "שאלות שטעית בהן יחזרו לכאן"
                    },
                    color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // The three stages with how many questions wait in each.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("אחרי יום", "אחרי 3 ימים", "אחרי שבוע").forEachIndexed { i, label ->
                val n = stages.getOrElse(i) { 0 }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (p.dark) Color.White.copy(alpha = .06f) else Color.Black.copy(alpha = .04f)).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("$n", color = if (n > 0) p.ink else p.muted, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(label, color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (due > 0) PrimaryButton("להתחיל חזרה · ${minOf(due, 40)} שאלות", Modifier.fillMaxWidth(), icon = Icons.repeat) {
            nav.go(Screen.Quiz(model.reviewDue().take(40), QuizConfig("חזרה יומית", "review", instant = true)))
        }
    }
}

@Composable
private fun Kpi(value: String, label: String, color: Color, modifier: Modifier) {
    val p = Pal
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(label, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** One category in the builder: icon, name, size and mastery, and a checkbox. */
@Composable
private fun CategoryRow(name: String, catId: String, count: Int, pct: Int, seen: Boolean, on: Boolean, onClick: () -> Unit) {
    val p = Pal
    Row(
        Modifier.fillMaxWidth().bouncy(pressedScale = .985f, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (p.dark) Color.White.copy(alpha = .07f) else Color.Black.copy(alpha = .045f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.forCategory(catId), null, tint = if (on) p.ink else p.muted, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = if (on) p.ink else p.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (seen) "$pct%" else "$count שאלות", color = if (seen) p.tone(pct) else p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            }
            ProgressBar(if (seen) pct / 100f else 0f, p.tone(pct), height = 4.dp)
        }
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                .background(if (on) Brand else Color.Transparent)
                .border(if (on) 0.dp else 2.dp, p.muted.copy(alpha = .45f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

/** A learning mode: neutral tile, monochrome icon, title and a short status line. */
@Composable
private fun ModeTile(icon: ImageVector, title: String, sub: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = Pal
    Column(
        modifier.alpha(if (enabled) 1f else .5f).bouncy(enabled = enabled, onClick = onClick)
            .glass(RoundedCornerShape(20.dp), elevation = 4.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(p.blueSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.blue, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(sub, color = p.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Slides a card up into place with a short stagger the first time the screen appears. */
private fun Modifier.rise(index: Int, entered: Boolean): Modifier = composed {
    val anim = remember { Animatable(if (entered) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!entered) { delay(90L * index + 60L); anim.animateTo(1f, spring(dampingRatio = .72f, stiffness = 220f)) }
    }
    graphicsLayer {
        val v = anim.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 60.dp.toPx()
        val sc = .94f + .06f * v
        scaleX = sc; scaleY = sc
    }
}

/** The app mark drawn flat: a white cross with the orange heartbeat across it, as on the opening screen. */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val thick = s * .34f
        val c = center
        val corner = androidx.compose.ui.geometry.CornerRadius(thick * .22f)
        drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(c.x - thick / 2, c.y - s / 2), androidx.compose.ui.geometry.Size(thick, s), corner)
        drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(c.x - s / 2, c.y - thick / 2), androidx.compose.ui.geometry.Size(s, thick), corner)
        val a = thick * .36f
        val pts = listOf(.07f to 0f, .33f to 0f, .41f to -.55f, .50f to 1f, .58f to -.25f, .64f to 0f, .93f to 0f)
        val path = androidx.compose.ui.graphics.Path().apply { pts.forEachIndexed { i, (x, dy) -> val px = c.x - s / 2 + s * x; val py = c.y + a * dy; if (i == 0) moveTo(px, py) else lineTo(px, py) } }
        drawPath(path, Brand, style = androidx.compose.ui.graphics.drawscope.Stroke(s * .055f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
