package com.taninoni.medic.ui

import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
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
            Row(
                Modifier.pageItem().padding(top = 18.dp, bottom = 22.dp)
                    .graphicsLayer {
                        val k = (headerScroll / 260f).coerceIn(0f, 1f)
                        translationY = headerScroll * .45f
                        alpha = 1f - k
                        scaleX = 1f - .06f * k; scaleY = 1f - .06f * k
                    },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(66.dp).liquidGlass(CircleShape), contentAlignment = Alignment.Center) { Logo(48.dp) }
                Column(Modifier.weight(1f)) {
                    Text(if (firstName.isNotEmpty()) "שלום, $firstName" else "שלום", color = p.blue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text("מסלול חובש", color = p.ink, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black)
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
            HeroCard(model, nav, overall, right, all.size, Modifier.pageItem().rise(1, entered))
        }
        if (!searching) item {
            Column(Modifier.pageItem().padding(top = 16.dp).rise(2, entered), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                QuickTiles(model, nav)
            }
        }
        model.categories.forEach { cat ->
            val topics = shown.filter { it.cat == cat.id }
            if (topics.isNotEmpty()) {
                item(key = "h-${cat.id}") {
                    val items = model.itemsOf(cat.id)
                    Row(
                        Modifier.pageItem().padding(top = 26.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.forCategory(cat.id), null, tint = p.blue, modifier = Modifier.size(22.dp))
                        Text(cat.name, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        if (model.seen(items)) Text("${model.mastery(items)}%", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                items(topics, key = { it.id }) { t ->
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
    GlassPage(
        "תרגול", null, subtitle = "בוחרים נושאים ומתחילים",
        bottomSpace = 84.dp,
        bottom = {
            FloatingDock {
                PrimaryButton(
                    if (sel.isEmpty()) "בחר לפחות קטגוריה אחת" else "התחל תרגול · ${model.questionCount} שאלות", Modifier.fillMaxWidth(),
                    enabled = sel.isNotEmpty(), icon = if (sel.isEmpty()) null else Icons.bolt,
                ) {
                    nav.go(Screen.Quiz(model.pick(allIds.filter { it in sel }, model.questionCount), QuizConfig("תרגול שאלות", "practice", instant = true)))
                }
            }
        },
    ) {
        SectionTitle(
            "קטגוריות",
            action = if (sel.size == allIds.size) "נקה הכול" else "בחר הכול",
        ) { model.setSelection(if (sel.size == allIds.size) emptySet() else allIds.toSet()) }
        model.categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

        SectionTitle("כמה שאלות?")
        Segmented(listOf(10, 20, 30, 50).map { it.toString() to it.toString() }, model.questionCount.toString()) { model.setCount(it.toInt()) }

        val savedCount = model.bookmarks.size
        SectionTitle("מצבי למידה")
        Panel(spacing = 0.dp) {
            GlassRow(
                if (mistakes > 0) "תרגול הטעויות שלי" else "אין טעויות לתרגול",
                subtitle = if (mistakes > 0) "$mistakes שאלות שטעית בהן" else "כל הכבוד, אין מה לתקן כרגע",
                icon = Icons.target, iconColor = p.bad, enabled = mistakes > 0, inPanel = true,
            ) { nav.go(Screen.Quiz(model.mistakes().shuffled().take(50), QuizConfig("תרגול טעויות", "mistakes", instant = true))) }
            Divider()
            GlassRow(
                "אתגר 60 שניות",
                subtitle = if (model.blitzBest > 0) "שיא אישי: ${model.blitzBest} נק'" else "כמה שיותר שאלות בדקה אחת",
                icon = Icons.bolt, iconColor = p.blue, inPanel = true,
            ) {
                val cats = allIds.filter { it in sel }.ifEmpty { allIds }
                nav.go(Screen.Blitz(model.pick(cats, 80)))
            }
            Divider()
            GlassRow("זיהוי אנטומיה", subtitle = "מצביעים על הנקודה בתמונה", icon = Icons.anat, iconColor = Color(0xFF8B6CFF), inPanel = true) { nav.go(Screen.AnatomyQuiz) }
            Divider()
            GlassRow(
                "שאלות שמורות",
                subtitle = if (savedCount > 0) "$savedCount שאלות שסימנת לחזרה" else "סמנו שאלות בזמן תרגול",
                icon = Icons.bookmark, iconColor = p.blue, enabled = savedCount > 0, inPanel = true,
            ) { nav.go(Screen.Quiz(model.bookmarkedItems().shuffled(), QuizConfig("שאלות שמורות", "bookmarks", instant = true))) }
        }

        if (model.progress.attempts.isNotEmpty()) {
            SectionTitle("ניסיונות אחרונים")
            Panel(spacing = 0.dp) {
                model.progress.attempts.take(8).forEachIndexed { i, a ->
                    if (i > 0) Divider()
                    val ok = a.pct >= model.passMark
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) p.good else p.bad))
                        Text(a.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${a.right}/${a.total}", color = p.muted, fontSize = 14.sp)
                        Pill("${a.pct}%", if (ok) p.good else p.bad, if (ok) p.goodSoft else p.badSoft)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(name: String, catId: String, count: Int, pct: Int, seen: Boolean, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = Pal
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.bouncy(onClick = onClick).alpha(if (on) 1f else .62f)
            .glass(shape, tint = if (on) p.blueSoft.copy(alpha = p.blueSoft.alpha * .6f) else Color.Unspecified, elevation = if (on) 10.dp else 2.dp)
            .then(if (on) Modifier.border(1.5.dp, p.line, shape) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(Icons.forCategory(catId), p.blue, solid = on)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(if (on) Brush.linearGradient(listOf(p.bandA, p.bandB)) else SolidColor(Color.Transparent))
                    .border(if (on) 0.dp else 2.dp, p.muted.copy(alpha = .4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        Text(name, color = p.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, lineHeight = 20.sp)
        ProgressBar(if (seen) pct / 100f else 0f, p.tone(pct), height = 6.dp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (seen) "$pct%" else "חדש", color = if (seen) p.tone(pct) else p.muted, fontSize = if (seen) 24.sp else 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("$count שאלות", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
