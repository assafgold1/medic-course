package com.taninoni.medic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel

@Composable
fun SettingsScreen(model: AppModel, nav: Nav) {
    val p = Pal
    var confirmReset by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppBar("הגדרות", null)
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                Group("חשבון") {
                    val user = model.user
                    LinkRow(
                        Icons.user,
                        title = user?.let { it.name.ifBlank { it.email } } ?: "חשבון וסנכרון",
                        subtitle = "${user?.email.orEmpty()} · ${model.syncMessage.ifBlank { "ההתקדמות מסתנכרנת" }}",
                    ) { nav.go(Screen.Account) }
                }

                if (model.user?.instructor == true) Group("מדריכים") {
                    LinkRow(Icons.special, title = "התקדמות החניכים", subtitle = "מי תרגל, כמה ובאילו נושאים") { nav.go(Screen.Instructor) }
                }

                Group("תצוגה") {
                    Eyebrow("ערכת צבעים")
                    Segmented(listOf("system" to "לפי המכשיר", "light" to "בהיר", "dark" to "כהה"), model.themeMode) { model.updateTheme(it) }
                    Eyebrow("גודל טקסט")
                    val step = TEXT_STEPS.indices.minBy { kotlin.math.abs(TEXT_STEPS[it] - model.textScale) }
                    Segmented(listOf("0" to "קטן", "1" to "רגיל", "2" to "גדול", "3" to "ענק"), step.toString()) {
                        model.updateTextScale(TEXT_STEPS[it.toInt()])
                    }
                    Text("דוגמה: מה הקצב המומלץ לעיסויים במבוגר?", color = p.ink, fontSize = 16.sp, lineHeight = 23.sp)
                }

                Group("תרגול ומבחן") {
                    ToggleRow(
                        "תשובה מיד אחרי כל שאלה",
                        "כבוי: התרגול מתנהג כמו מבחן, והתשובות מוצגות רק בסוף.",
                        model.instantFeedback,
                    ) { model.updateInstantFeedback(it) }
                    Eyebrow("מספר שאלות בתרגול")
                    Segmented(listOf(10, 20, 30, 50).map { it.toString() to it.toString() }, model.questionCount.toString()) { model.setCount(it.toInt()) }
                    Eyebrow("מספר שאלות במבחן המסכם")
                    Segmented(listOf(20, 30, 50, 100).map { it.toString() to it.toString() }, model.examSize.toString()) { model.updateExamSize(it.toInt()) }
                    Text("ציון עובר: ${model.passMark}%", color = p.muted, fontSize = 13.5.sp)
                }

                Group("צלילים") {
                    ToggleRow("צלילי מטרונום", "ספירת עיסויים, הנשמות והתראה כל 2 דקות.", model.soundOn) { model.updateSound(it) }
                }

                Group("נתונים") {
                    val answered = model.progress.answers.size
                    Text("$answered שאלות נענו · ${model.progress.attempts.size} תרגולים ומבחנים שמורים", color = p.muted, fontSize = 14.sp)
                    if (resetDone) Text("ההתקדמות אופסה.", color = p.good, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).background(p.badSoft)
                            .clickable { confirmReset = true }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("איפוס התקדמות", color = p.bad, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }

                Group("אודות") {
                    Text("מסלול חובש · גרסה 2.0", color = p.ink, fontWeight = FontWeight.SemiBold)
                    Text("גרסת תוכן ${model.content?.version.orEmpty()} · ${model.topics.size} נושאים · ${model.allItems.size} שאלות", color = p.muted, fontSize = 13.5.sp)
                    Text("החומר נועד לחזרה ולתרגול, ואינו מחליף את מצגות הקורס או את הפרוטוקולים הרשמיים.", color = p.muted, fontSize = 13.5.sp)
                }
                BottomSpace(40.dp)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("לאפס את ההתקדמות?") },
            text = {
                Text(
                    if (model.user != null) "כל התשובות והמבחנים יימחקו, גם בשרת ובשאר המכשירים שמחוברים לחשבון. אי אפשר לבטל."
                    else "כל התשובות והמבחנים במכשיר הזה יימחקו. אי אפשר לבטל."
                )
            },
            confirmButton = {
                TextButton(onClick = { model.resetProgress(); confirmReset = false; resetDone = true }) { Text("אפס", color = p.bad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("ביטול") } },
            containerColor = p.surface, titleContentColor = p.ink, textContentColor = p.ink,
        )
    }
}

private val TEXT_STEPS = listOf(0.9f, 1.0f, 1.15f, 1.3f)

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionTitle(title, Modifier.padding(top = 4.dp))
    Panel(content = content)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = Pal
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = p.muted, fontSize = 13.5.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.blue, checkedThumbColor = Color.White, uncheckedTrackColor = p.hair, uncheckedBorderColor = p.line, uncheckedThumbColor = p.muted),
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val p = Pal
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.blueSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.blue, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = p.muted, fontSize = 13.5.sp)
        }
        // The back chevron points right (RTL "back"); mirror it to point forward.
        Icon(Icons.back, null, tint = p.muted, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = -1f })
    }
}
