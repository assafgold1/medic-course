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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SettingsScreen(model: AppModel, nav: Nav) {
    val p = Pal
    var confirmReset by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }

    GlassPage("הגדרות", null) {
        val user = model.user
        // Profile card
        Row(
            Modifier.fillMaxWidth().bouncy { nav.go(Screen.Account) }.glass(RoundedCornerShape(30.dp), elevation = 14.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val initial = user?.name?.trim()?.firstOrNull()?.toString() ?: user?.email?.firstOrNull()?.uppercase() ?: "?"
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.bandA, p.bandB))),
                contentAlignment = Alignment.Center,
            ) { Text(initial, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user?.let { it.name.ifBlank { it.email } } ?: "חשבון וסנכרון", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user?.email.orEmpty(), color = p.muted, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.syncMessage.ifBlank { "ההתקדמות מסתנכרנת" }, color = p.good, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Chevron()
        }

        if (user?.instructor == true) GlassRow("התקדמות החניכים", subtitle = "מי תרגל, כמה ובאילו נושאים", icon = Icons.special) { nav.go(Screen.Instructor) }

        Group("תצוגה") {
            Label(Icons.moon, "ערכת צבעים")
            Segmented(listOf("system" to "אוטומטי", "light" to "בהיר", "dark" to "כהה"), model.themeMode) { model.updateTheme(it) }
            Label(Icons.textSize, "גודל טקסט")
            val step = TEXT_STEPS.indices.minBy { kotlin.math.abs(TEXT_STEPS[it] - model.textScale) }
            Segmented(listOf("0" to "קטן", "1" to "רגיל", "2" to "גדול", "3" to "ענק"), step.toString()) {
                model.updateTextScale(TEXT_STEPS[it.toInt()])
            }
            Text("דוגמה: מה הקצב המומלץ לעיסויים במבוגר?", color = p.ink, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }

        Group("תרגול ומבחן") {
            ToggleRow(
                Icons.eye, "תשובה מיד אחרי כל שאלה",
                "כבוי: התרגול מתנהג כמו מבחן, והתשובות מוצגות רק בסוף.",
                model.instantFeedback,
            ) { model.updateInstantFeedback(it) }
            Divider()
            Label(Icons.check, "מספר שאלות בתרגול")
            Segmented(listOf(10, 20, 30, 50).map { it.toString() to it.toString() }, model.questionCount.toString()) { model.setCount(it.toInt()) }
            Label(Icons.timer, "מספר שאלות במבחן המסכם")
            Segmented(listOf(20, 30, 50, 100).map { it.toString() to it.toString() }, model.examSize.toString()) { model.updateExamSize(it.toInt()) }
            Text("ציון עובר: ${model.passMark}%", color = p.muted, fontSize = 13.5.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }

        Group("צלילים") {
            ToggleRow(Icons.sound, "צלילים", "צליל הצלחה או טעות אחרי כל תשובה, וצלילי הסימולטור", model.soundOn) { model.updateSound(it) }
        }

        Group("נתונים") {
            val answered = model.progress.answers.size
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("$answered", "שאלות נענו", Modifier.weight(1f))
                Stat("${model.progress.attempts.size}", "תרגולים ומבחנים", Modifier.weight(1f))
            }
            if (resetDone) Text("ההתקדמות אופסה.", color = p.good, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).bouncy { confirmReset = true }.clip(RoundedCornerShape(50)).background(p.badSoft).padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.trash, null, tint = p.bad, modifier = Modifier.size(18.dp))
                Text("איפוס התקדמות", color = p.bad, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        Group("אודות") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Logo(44.dp)
                Column(Modifier.weight(1f)) {
                    Text("מסלול חובש · גרסה 2.0", color = p.ink, fontWeight = FontWeight.ExtraBold)
                    Text("גרסת תוכן ${model.content?.version.orEmpty()} · ${model.topics.size} נושאים · ${model.allItems.size} שאלות", color = p.muted, fontSize = 13.sp)
                }
            }
            Text("החומר נועד לחזרה ולתרגול, ואינו מחליף את מצגות הקורס או את הפרוטוקולים הרשמיים.", color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            "לאפס את ההתקדמות?",
            if (model.user != null) "כל התשובות והמבחנים יימחקו, גם בשרת ובשאר המכשירים שמחוברים לחשבון. אי אפשר לבטל."
            else "כל התשובות והמבחנים במכשיר הזה יימחקו. אי אפשר לבטל.",
            confirm = "אפס", destructive = true,
            onConfirm = { model.resetProgress(); confirmReset = false; resetDone = true },
            onDismiss = { confirmReset = false },
        )
    }
}

private val TEXT_STEPS = listOf(0.9f, 1.0f, 1.15f, 1.3f)

@Composable
private fun Group(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    SectionTitle(title)
    Panel(content = content)
}

@Composable
private fun Label(icon: ImageVector, text: String) {
    val p = Pal
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(icon, null, tint = p.blue, modifier = Modifier.size(18.dp))
        Text(text, color = p.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = Pal
    Row(Modifier.fillMaxWidth().bouncy(pressedScale = .98f) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, p.blue, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = p.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        GlassSwitch(checked)
    }
}

/** Capsule switch with a glass knob that springs across; the track fills with the accent gradient. */
@Composable
fun GlassSwitch(checked: Boolean) {
    val p = Pal
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = .55f, stiffness = 520f))
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.width(56.dp).height(34.dp).clip(shape)
            .background(if (checked) Brush.linearGradient(listOf(p.bandA, p.bandB)) else Brush.linearGradient(listOf(p.hair, p.hair)))
            .border(1.dp, if (checked) Color.White.copy(alpha = .35f) else p.hair, shape)
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = x).size(28.dp)
                .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.25f))
                .clip(CircleShape).background(Color.White)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color.Black.copy(alpha = .08f))), CircleShape),
        )
    }
}
