package com.taninoni.medic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.Celebration
import com.taninoni.medic.data.StreakNotice
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/* ---------------- colours and calendar helpers ---------------- */

private val FlameTop = Color(0xFFFFD43B)
private val FlameMid = Color(0xFFFF8A1F)
private val FlameLow = Color(0xFFFF3D3D)
private val Ice = Color(0xFF4FB8F2)

private val DAY_LETTERS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")
private val MONTHS = listOf("ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני", "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר")

/** Day of week for an epoch day, Sunday = 0 (1 Jan 1970 was a Thursday). */
private fun dayOfWeek(day: Long) = ((day + 4) % 7 + 7).toInt() % 7

private data class Ymd(val y: Int, val m: Int, val d: Int)

/** Epoch day to civil date (proleptic Gregorian). */
private fun civil(day: Long): Ymd {
    val z = day + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
    return Ymd(y, m, d)
}

/** Civil date to epoch day. */
private fun epochDay(y: Int, m: Int, d: Int): Long {
    val yy = if (m <= 2) y - 1 else y
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468
}

private fun daysWord(n: Int) = if (n == 1) "יום אחד" else "$n ימים"

/* ---------------- flame ---------------- */

private val outerFlame = PathParser().parsePathString(
    "M12 21.5c-4.2 0-7-2.8-7-6.7 0-3.7 2.6-5.8 3.9-9 1 1.7 1.3 3 1.3 4.3 1.3-.8 2.5-2.8 2.5-6 3.2 2.4 6.3 6.1 6.3 10.6 0 3.9-2.8 6.8-7 6.8z",
).toPath()
private val innerFlame = PathParser().parsePathString(
    "M12 21c-2.2 0-3.6-1.4-3.6-3.4 0-1.9 1.4-3.1 2.1-4.7.5 1 .8 1.7.8 2.5.8-.4 1.4-1.5 1.4-3.2 1.7 1.3 2.8 3.1 2.8 5.4 0 2-1.4 3.4-3.5 3.4z",
).toPath()

/** A drawn flame that flickers. Unlit, it is a grey ember (today's goal not met yet). */
@Composable
fun Flame(size: Dp, lit: Boolean = true, modifier: Modifier = Modifier) {
    val p = Pal
    val t = rememberInfiniteTransition()
    val flicker by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart))
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val wave = sin(flicker * 2 * PI).toFloat()
        val sy = if (lit) 1f + .035f * wave else 1f
        val sx = if (lit) 1f - .02f * wave else 1f
        if (lit) drawCircle(
            Brush.radialGradient(listOf(FlameMid.copy(alpha = .45f), Color.Transparent), Offset(center.x, center.y * 1.15f), this.size.minDimension * .55f),
            radius = this.size.minDimension * .55f, center = Offset(center.x, center.y * 1.15f),
        )
        scale(sx, sy, pivot = Offset(center.x, this.size.height * .9f)) {
            scale(k, k, pivot = Offset.Zero) {
                if (lit) {
                    drawPath(outerFlame, Brush.verticalGradient(listOf(FlameTop, FlameMid, FlameLow), startY = 3f, endY = 21.5f))
                    translate(0f, -.4f * wave) {
                        drawPath(innerFlame, Brush.verticalGradient(listOf(Color(0xFFFFF6C2), FlameTop), startY = 11f, endY = 21f))
                    }
                } else {
                    drawPath(outerFlame, Brush.verticalGradient(listOf(p.muted.copy(alpha = .55f), p.muted.copy(alpha = .35f)), startY = 3f, endY = 21.5f))
                    drawPath(innerFlame, p.muted.copy(alpha = .25f))
                }
            }
        }
    }
}

/* ---------------- week strip ---------------- */

/** The last seven days ending today: met days burn, frozen days are ice, today waits in a ring. */
@Composable
fun WeekStrip(model: AppModel, modifier: Modifier = Modifier, revealToday: Boolean = false) {
    val p = Pal
    val today = model.today
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val day = today - 6 + i
            val met = day in model.metDays
            val frozen = day in model.frozenDays
            val isToday = day == today
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (isToday) "היום" else DAY_LETTERS[dayOfWeek(day)], color = if (isToday) p.ink else p.muted, fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold)
                DayBubble(met, frozen, isToday, future = false, size = 34.dp, reveal = revealToday && isToday && met)
            }
        }
    }
}

@Composable
private fun DayBubble(met: Boolean, frozen: Boolean, isToday: Boolean, future: Boolean, size: Dp, reveal: Boolean = false) {
    val p = Pal
    val pop = remember { Animatable(if (reveal) 0f else 1f) }
    LaunchedEffect(reveal) {
        if (reveal) { delay(650); pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f)) }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // The empty slot sits underneath, so a revealed day pops out of it.
        Box(
            Modifier.size(size).liquidGlass(CircleShape, clear = true)
                .then(if (isToday) Modifier.border(2.dp, p.blue, CircleShape) else Modifier),
        )
        if (met || frozen) Box(
            Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                .clip(CircleShape)
                .background(if (met) Brush.linearGradient(listOf(p.bandA, p.bandB)) else Brush.linearGradient(listOf(Ice.copy(alpha = .85f), Ice)))
                .border(1.dp, Color.White.copy(alpha = .45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (met) Icons.check else Icons.snow, null, tint = Color.White, modifier = Modifier.size(size * .52f))
        }
    }
}

/* ---------------- home card ---------------- */

/** The streak on the home screen: flame, count, what's left today and this week. Opens the streak page. */
@Composable
fun StreakCard(model: AppModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Pal
    val streak = model.studyStreakDays
    val done = model.doneToday
    val risk = model.streakAtRisk
    Column(
        modifier.bouncy(pressedScale = .98f, onClick = onClick)
            .deepGlass(RoundedCornerShape(32.dp), elevation = 20.dp)
            .then(if (risk) Modifier.border(1.5.dp, p.blue.copy(alpha = .55f), RoundedCornerShape(30.dp)) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Flame(58.dp, lit = done || streak > 0 && !risk)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "$streak", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black,
                        style = TextStyle(brush = if (streak > 0) Brush.verticalGradient(listOf(p.bandA, p.bandB)) else Brush.linearGradient(listOf(p.muted, p.muted))),
                    )
                    Text(if (streak == 1) "יום ברצף" else "ימים ברצף", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    when {
                        done -> "שמרת על הרצף היום. נתראה מחר!"
                        risk -> "עוד ${model.leftToday} שאלות ושמרת על הרצף"
                        else -> model.g("ענה", "עני") + " על ${model.dailyGoal} שאלות כדי להדליק את הרצף"
                    },
                    color = when { done -> p.blue; risk -> p.bad; else -> p.muted },
                    fontSize = 13.5.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp,
                )
            }
            if (model.freezes > 0) Row(
                Modifier.clip(RoundedCornerShape(50)).background(Ice.copy(alpha = .16f)).padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.snow, "הקפאות רצף", tint = Ice, modifier = Modifier.size(15.dp))
                Text("${model.freezes}", color = Ice, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
        if (!done) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("היום", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${model.todayCount}/${model.dailyGoal}", color = p.ink, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            }
            ProgressBar(model.todayCount.toFloat() / model.dailyGoal, p.blue)
        }
        WeekStrip(model)
    }
}

/* ---------------- streak page ---------------- */

@Composable
fun StreakScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val streak = model.studyStreakDays
    GlassPage("הרצף שלי", onBack = { nav.back() }) {
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(34.dp), elevation = 16.dp).padding(vertical = 26.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Flame(120.dp, lit = model.doneToday || streak > 0)
            Text(
                "$streak", fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.Black,
                style = TextStyle(brush = Brush.verticalGradient(listOf(p.bandA, p.bandB))),
            )
            Text(if (streak == 1) "יום ברצף" else "ימים ברצף", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                when {
                    model.doneToday -> "היעד של היום הושלם. " + model.g("חזור", "חזרי") + " מחר כדי להמשיך."
                    model.streakAtRisk -> "הרצף בסכנה! עוד ${model.leftToday} שאלות עד חצות."
                    else -> model.g("ענה היום על ${model.dailyGoal} שאלות ותדליק רצף חדש.", "עני היום על ${model.dailyGoal} שאלות ותדליקי רצף חדש.")
                },
                color = p.muted, fontSize = 14.5.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
            )
            if (!model.doneToday) {
                Spacer(Modifier.height(10.dp))
                PrimaryButton("לתרגל עכשיו", Modifier.fillMaxWidth(), icon = Icons.sparkle) {
                    nav.go(Screen.Quiz(model.smartPick(maxOf(model.leftToday, 10)), QuizConfig("תרגול חכם", "smart", instant = true)))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("שיא אישי", daysWord(model.longestStreak), Icons.trophy, p.blue, Modifier.weight(1f))
            StatTile("הקפאות", "${model.freezes} מתוך 2", Icons.snow, Ice, Modifier.weight(1f))
            StatTile("ימי תרגול", "${model.metDays.size}", Icons.check, p.blue, Modifier.weight(1f))
        }

        MonthCalendar(model)

        SectionTitle("יעד יומי")
        Panel {
            Text("כמה שאלות ביום שומרות על הרצף? 5 קליל, 10 רגיל, 20 רציני, 40 אינטנסיבי.", color = p.muted, fontSize = 14.sp, lineHeight = 20.sp)
            Segmented(
                listOf(5, 10, 20, 40).map { it.toString() to it.toString() },
                model.dailyGoal.toString(),
            ) { model.setGoal(it.toInt()) }
        }

        SectionTitle("תזכורת יומית")
        Panel {
            Row(
                Modifier.fillMaxWidth().bouncy(pressedScale = .98f) { model.setReminder(!model.reminderOn) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(Icons.bell, p.blue, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text("תזכיר לי לתרגל", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("התראה כל יום עד שהיעד הושלם, ועוד אחת ב-22:30 אם הרצף בסכנה", color = p.muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
                GlassSwitch(model.reminderOn)
            }
            if (model.reminderOn) {
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("שעה", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    GlassIconButton(Icons.minus, "מוקדם יותר", size = 40.dp) { model.setReminderTime(model.reminderMinute - 30) }
                    val m = model.reminderMinute
                    Text(
                        "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}",
                        color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 76.dp), textAlign = TextAlign.Center,
                    )
                    GlassIconButton(Icons.plus, "מאוחר יותר", size = 40.dp) { model.setReminderTime(model.reminderMinute + 30) }
                }
            }
        }

        Panel(tint = Ice.copy(alpha = .08f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.snow, Ice, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("הקפאת רצף", color = p.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "על כל 7 ימים ברצף מקבלים הקפאה (עד 2). יום שפספסת נשמר אוטומטית בהקפאה, והרצף ממשיך.",
                        color = p.muted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    val p = Pal
    Column(
        modifier.glass(RoundedCornerShape(24.dp), elevation = 6.dp).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(value, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A month of practice: burning days, frozen days and today, with month paging. */
@Composable
private fun MonthCalendar(model: AppModel) {
    val p = Pal
    val now = civil(model.today)
    var offset by remember { mutableIntStateOf(0) }
    val total = now.y * 12 + (now.m - 1) + offset
    val y = total / 12
    val m = total % 12 + 1
    val first = epochDay(y, m, 1)
    val next = if (m == 12) epochDay(y + 1, 1, 1) else epochDay(y, m + 1, 1)
    val length = (next - first).toInt()
    val lead = dayOfWeek(first)
    val count = (first until next).count { it in model.metDays }
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), elevation = 8.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassIconButton(Icons.back, "החודש הקודם", size = 38.dp) { offset-- }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${MONTHS[m - 1]} $y", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("$count ימי תרגול", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.size(38.dp)) {
                if (offset < 0) GlassIconButton(Icons.forward, "החודש הבא", size = 38.dp) { offset++ }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DAY_LETTERS.forEach { Text(it, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        val cells = lead + length
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val idx = row * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (idx in 0 until length) {
                            val day = first + idx
                            val met = day in model.metDays
                            val frozen = day in model.frozenDays
                            val isToday = day == model.today
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .then(
                                        when {
                                            met -> Modifier.background(Brush.linearGradient(listOf(p.bandA, p.bandB)))
                                            frozen -> Modifier.background(Ice)
                                            else -> Modifier
                                        },
                                    )
                                    .then(if (isToday && !met) Modifier.border(2.dp, p.blue, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${idx + 1}",
                                    color = if (met || frozen) Color.White else if (day > model.today) p.muted.copy(alpha = .5f) else p.ink,
                                    fontSize = 13.sp, fontWeight = if (met || isToday) FontWeight.Black else FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- celebration and notices ---------------- */

/**
 * The streak moments: the full-screen celebration when today's goal is met (held back until a quiz ends),
 * and the one-time dialog when a freeze saved the streak or the streak was lost.
 */
@Composable
fun StreakOverlays(model: AppModel, nav: Nav) {
    val busy = nav.current is Screen.Quiz || nav.current is Screen.Blitz || nav.current == Screen.AnatomyQuiz
    val c = model.celebration
    var shown by remember { mutableStateOf<Celebration?>(null) }
    if (c != null && !busy) shown = c
    AnimatedVisibility(
        c != null && !busy,
        enter = fadeIn(tween(250)) + scaleIn(tween(320), initialScale = 1.06f),
        exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = .97f),
    ) { shown?.let { CelebrationView(model, it) } }

    val notice = model.streakNotice
    if (notice != null && c == null && nav.current == Screen.Home) {
        when (notice) {
            is StreakNotice.Frozen -> NoticeDialog(
                Icons.snow, Ice, "הקפאה שמרה על הרצף",
                "פספסת ${daysWord(notice.days)}, אבל הקפאת רצף נכנסה לפעולה. הרצף של ${daysWord(notice.streak)} עדיין בוער. בוא נמשיך היום!",
                "ממשיכים", onGo = { model.dismissStreakNotice() },
            )
            is StreakNotice.Lost -> NoticeDialog(
                Icons.flame, Pal.blue, "הרצף נכבה",
                "הרצף של ${daysWord(notice.days)} נגמר. כל רצף גדול מתחיל מחדש ביום אחד, ואפשר להדליק אותו כבר היום.",
                "מתחילים מחדש",
                onGo = {
                    model.dismissStreakNotice()
                    nav.go(Screen.Quiz(model.smartPick(maxOf(model.dailyGoal, 10)), QuizConfig("תרגול חכם", "smart", instant = true)))
                },
                onLater = { model.dismissStreakNotice() },
            )
        }
    }
}

@Composable
private fun NoticeDialog(
    icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, text: String, go: String,
    onGo: () -> Unit, onLater: (() -> Unit)? = null,
) {
    val p = Pal
    androidx.compose.ui.window.Dialog(onDismissRequest = onLater ?: onGo) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Column(
                Modifier.widthIn(max = 380.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp)).background(p.solid)
                    .border(1.dp, Brush.verticalGradient(listOf(p.rimHi, p.rimLo)), RoundedCornerShape(32.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(40.dp))
                }
                Text(title, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(text, color = p.muted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                PrimaryButton(go, Modifier.fillMaxWidth(), onClick = onGo)
                if (onLater != null) Text(
                    "אחר כך", color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onLater).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CelebrationView(model: AppModel, c: Celebration) {
    val p = Pal
    val flame = remember { Animatable(.2f) }
    var number by remember { mutableIntStateOf(c.streak - 1) }
    LaunchedEffect(c) {
        AnswerSound.play(Sfx.STREAK)
        flame.animateTo(1f, spring(dampingRatio = .42f, stiffness = 260f))
    }
    LaunchedEffect(c) { delay(550); number = c.streak }
    val message = when {
        c.streak == 1 -> "כל רצף גדול מתחיל ביום אחד. נתראה מחר!"
        c.streak == 3 -> "שלושה ימים ברצף. ההרגל מתחיל להיבנות!"
        c.streak == 7 -> "שבוע שלם של תרגול. זה כבר הרגל!"
        c.streak == 14 -> "שבועיים ברצף. החומר נשאר טרי!"
        c.streak == 30 -> model.g("חודש שלם! אתה בדרך להיות חובש מעולה.", "חודש שלם! את בדרך להיות חובשת מעולה.")
        c.streak == 50 -> "50 ימים. זו כבר התמדה של אלופים."
        c.streak == 100 -> "100 ימים ברצף. אגדה!"
        c.record -> model.g("שיא אישי חדש! תמשיך ככה.", "שיא אישי חדש! תמשיכי ככה.")
        else -> model.g("חזור", "חזרי") + " מחר כדי שהרצף ימשיך לבעור."
    }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.baseTop, p.baseBottom)))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Embers(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Flame(140.dp, modifier = Modifier.graphicsLayer { scaleX = flame.value; scaleY = flame.value })
            AnimatedContent(
                number,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = .55f, stiffness = 300f)) { it } + fadeIn()) togetherWith
                        (slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)))
                },
                label = "streak",
            ) { n ->
                Text(
                    "$n", fontSize = 84.sp, lineHeight = 88.sp, fontWeight = FontWeight.Black,
                    style = TextStyle(brush = Brush.verticalGradient(listOf(p.bandA, p.bandB))),
                )
            }
            Text(if (c.streak == 1) "הרצף התחיל!" else "ימים ברצף!", color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(message, color = p.muted, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().glass(RoundedCornerShape(26.dp), elevation = 8.dp).padding(16.dp)) {
                WeekStrip(model, revealToday = true)
            }
            if (c.freezeEarned) Row(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp), tint = Ice.copy(alpha = .1f), elevation = 4.dp).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(Icons.snow, Ice, size = 40.dp)
                Text("קיבלת הקפאת רצף! היא תשמור על הרצף ביום שתפספס.", color = p.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
            if (!model.reminderOn && !model.reminderAsked) Row(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 4.dp).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(Icons.bell, p.blue, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text("שנזכיר לך מחר?", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("התראה יומית שלא תשבור את הרצף", color = p.muted, fontSize = 13.sp)
                }
                Text(
                    "הפעל", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.band).bouncy { model.setReminder(true) }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
            PrimaryButton("המשך", Modifier.padding(horizontal = 24.dp, vertical = 12.dp).widthIn(max = 420.dp).fillMaxWidth()) { model.dismissCelebration() }
        }
    }
}

/** Sparks drifting up behind the celebration. */
@Composable
private fun Embers(modifier: Modifier) {
    val t = rememberInfiniteTransition()
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)))
    val seeds = remember { List(26) { i -> Triple((i * 0.6180339f) % 1f, (i * 0.3819660f + .13f) % 1f, 2.5f + (i % 5)) } }
    Canvas(modifier) {
        seeds.forEach { (x0, phase, r) ->
            val prog = (time + phase) % 1f
            val x = size.width * (x0 + .04f * sin((prog * 4 + x0 * 10) * PI.toFloat()))
            val y = size.height * (1.05f - prog * 1.1f)
            val a = (sin(prog * PI.toFloat()) * .8f).coerceIn(0f, 1f)
            drawCircle(if ((r.toInt() % 2) == 0) FlameTop.copy(alpha = a) else FlameMid.copy(alpha = a * .85f), radius = r.dp.toPx() * .6f, center = Offset(x, y))
        }
    }
}
