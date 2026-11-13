package com.taninoni.medic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.graphicsLayer
import com.taninoni.medic.data.ApiException
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.Sound
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- CPR metronome ---------------- */

@Composable
fun MetronomeScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val sound = model.soundOn
    var bpm by remember { mutableIntStateOf(110) }
    var mode by remember { mutableStateOf("30") }        // "30", "15" or "cont"
    var running by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    var breath by remember { mutableIntStateOf(0) }      // 0 = compressions, 1..2 = breath number
    var elapsed by remember { mutableLongStateOf(0L) }
    var cycleStart by remember { mutableLongStateOf(0L) }
    var cycles by remember { mutableIntStateOf(0) }
    var alarmUntil by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }
    val target = when (mode) { "30" -> 30; "15" -> 15; else -> 0 }

    LaunchedEffect(running, bpm, mode) {
        if (!running) return@LaunchedEffect
        var lastBreath = nowMillis()
        var last = nowMillis()
        while (true) {
            if (target > 0 && count >= target) {
                for (b in 1..2) { breath = b; if (sound) Sound.breath(); delay(1800) }
                breath = 0; count = 0
            }
            count++
            if (sound) Sound.beat(accent = count == 1 || count == target)
            if (target == 0 && nowMillis() - lastBreath >= 6000) { lastBreath = nowMillis(); if (sound) Sound.breath() }
            delay(60_000L / bpm)
        }
    }
    LaunchedEffect(running) {
        var last = nowMillis()
        while (running) {
            delay(250)
            val now = nowMillis()
            elapsed += now - last; last = now
            tick = now
            if (now - cycleStart >= 120_000) { cycles++; cycleStart = now; alarmUntil = now + 6000; if (sound) Sound.alarm() }
        }
    }
    fun fmt(ms: Long) = (ms / 1000).let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }

    Column(Modifier.fillMaxSize()) {
        AppBar("מטרונום החייאה", null)
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                Text("ספירה קולית וחזותית לתרגול על בובה. כל 2 דקות מתקבלת התראה לניתוח קצב ולהחלפת מעסה.", color = p.muted)
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    val frac = when { breath > 0 -> breath / 2f; target > 0 -> count / target.toFloat(); else -> (count % 20) / 20f }
                    val col = if (breath > 0) p.good else p.blue
                    Canvas(Modifier.size(270.dp)) {
                        val st = 8.dp.toPx()
                        drawArc(p.hair, 0f, 360f, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st))
                        drawArc(col, -90f, 360f * frac, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (breath > 0) "$breath" else "$count", color = if (breath > 0) p.good else p.ink, fontSize = 72.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when { breath > 0 -> "הנשמה"; running && target > 0 -> "עיסויים מתוך $target"; running -> "עיסויים רציפים"; count > 0 -> "בהשהיה"; else -> "מוכן" },
                            color = p.muted, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (tick < alarmUntil) {
                    Text("2 דקות: ניתוח קצב והחלפת מעסה", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.bad).padding(14.dp),
                        color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, fontSize = 17.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(if (running) "השהה" else if (count > 0) "המשך" else "התחל", Modifier.weight(1f)) {
                        if (!running && cycleStart == 0L) cycleStart = nowMillis()
                        running = !running
                    }
                    GhostButton("איפוס") { running = false; count = 0; breath = 0; elapsed = 0; cycles = 0; cycleStart = 0; alarmUntil = 0 }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(fmt(elapsed), "זמן כולל", Modifier.weight(1f))
                    Stat(if (cycleStart == 0L) "0:00" else fmt(tick - cycleStart), "מחזור נוכחי", Modifier.weight(1f))
                    Stat("$cycles", "מחזורים", Modifier.weight(1f))
                }
                SectionTitle("מצב", Modifier.padding(top = 8.dp))
                Panel {
                    Segmented(listOf("30" to "30:2", "15" to "15:2", "cont" to "רציף"), mode) { mode = it; count = 0; breath = 0 }
                    Text(
                        when (mode) {
                            "30" -> "מבוגר: 30 עיסויים ו-2 הנשמות"
                            "15" -> "ילד ותינוק בשני מטפלים: 15 עיסויים ו-2 הנשמות"
                            else -> "עיסויים רציפים עם הנשמה כל 6 שניות (נתיב אוויר מתקדם)"
                        },
                        color = p.muted, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                    Eyebrow("קצב (לדקה)")
                    Segmented(listOf("100" to "100", "110" to "110", "120" to "120"), bpm.toString()) { bpm = it.toInt() }
                }
                BottomSpace(40.dp)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val p = Pal
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.paper).padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = p.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/* ---------------- account & server ---------------- */

@Composable
fun AccountScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("login") }
    val emailState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()
    val nameState = rememberTextFieldState()
    val email = emailState.text.toString()
    val password = passwordState.text.toString()
    val name = nameState.text.toString()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        AppBar("חשבון וסנכרון") { nav.back() }
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                val user = model.user
                if (user != null) {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(54.dp).clip(CircleShape).background(p.blue), contentAlignment = Alignment.Center) {
                                Text(user.name.ifBlank { user.email }.take(1).uppercase(), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(user.name.ifBlank { "משתמש" }, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(user.email, color = p.muted, fontSize = 14.sp)
                            }
                        }
                        val state = model.syncState
                        Text(
                            when (state) {
                                AppModel.SyncState.Working -> "מסנכרן…"
                                AppModel.SyncState.Ok -> "✓ ההתקדמות מסונכרנת עם השרת"
                                AppModel.SyncState.Error -> "שגיאת סנכרון: ${model.syncMessage}"
                                AppModel.SyncState.Idle -> "ההתקדמות תסונכרן בסוף כל תרגול"
                            },
                            color = when (state) { AppModel.SyncState.Error -> p.bad; AppModel.SyncState.Ok -> p.good; else -> p.muted },
                            fontSize = 14.5.sp,
                        )
                    }
                    PrimaryButton("סנכרן עכשיו", Modifier.fillMaxWidth()) { scope.launch { model.sync() } }
                    UserSettings(model)
                } else {
                    Text("התחברות שומרת את ההתקדמות והמבחנים בשרת, כדי שיהיו זמינים בכל מכשיר.", color = p.muted, fontSize = 15.sp, lineHeight = 22.sp)
                    Segmented(listOf("login" to "התחברות", "register" to "הרשמה"), mode) { mode = it; error = null }
                    if (mode == "register") Field("שם", nameState, KeyboardType.Text)
                    Field("מייל", emailState, KeyboardType.Email)
                    Field("סיסמה (לפחות 8 תווים)", passwordState, KeyboardType.Password, secret = true)
                    error?.let { Text(it, color = p.bad) }
                    PrimaryButton(if (busy) "רגע…" else if (mode == "login") "התחבר" else "צור חשבון", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
                        busy = true; error = null
                        scope.launch {
                            runCatching { if (mode == "login") model.login(email, password) else model.register(email, password, name) }
                                .onFailure { error = (it as? com.taninoni.medic.data.ApiException)?.message ?: "אין חיבור לשרת. נסה שוב בעוד רגע." }
                            busy = false
                        }
                    }
                }
                BottomSpace(40.dp)
            }
        }
    }
}

/* ---------------- user settings (signed in) ---------------- */

@Composable
private fun UserSettings(model: AppModel) {
    val p = Pal
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }          // "name", "password" or "delete"
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // (ok, text)
    var confirmDelete by remember { mutableStateOf(false) }
    val nameState = rememberTextFieldState(model.user?.name.orEmpty())
    val currentState = rememberTextFieldState()
    val newState = rememberTextFieldState()
    val deleteState = rememberTextFieldState()

    fun toggle(section: String) { open = if (open == section) null else section; message = null }
    fun run(okText: String, block: suspend () -> Unit) {
        busy = true; message = null
        scope.launch {
            runCatching { block() }
                .onSuccess { message = true to okText; open = null }
                .onFailure { message = false to ((it as? ApiException)?.message ?: "אין חיבור לשרת. נסה שוב בעוד רגע.") }
            busy = false
        }
    }

    SectionTitle("הגדרות משתמש", Modifier.padding(top = 12.dp))
    message?.let { (ok, text) -> Text(text, color = if (ok) p.good else p.bad, fontWeight = FontWeight.SemiBold) }

    SectionRow("שם תצוגה", model.user?.name?.ifBlank { null } ?: "לא הוגדר", open == "name") { toggle("name") }
    if (open == "name") {
        Field("שם", nameState, KeyboardType.Text)
        PrimaryButton(if (busy) "רגע…" else "שמור שם", Modifier.fillMaxWidth(), enabled = !busy && nameState.text.isNotBlank()) {
            run("השם עודכן") { model.updateName(nameState.text.toString()) }
        }
    }

    SectionRow("שינוי סיסמה", "שאר המכשירים יתנתקו מהחשבון", open == "password") { toggle("password") }
    if (open == "password") {
        Field("סיסמה נוכחית", currentState, KeyboardType.Password, secret = true)
        Field("סיסמה חדשה (לפחות 8 תווים)", newState, KeyboardType.Password, secret = true)
        PrimaryButton(if (busy) "רגע…" else "שנה סיסמה", Modifier.fillMaxWidth(), enabled = !busy && currentState.text.isNotEmpty() && newState.text.length >= 8) {
            run("הסיסמה שונתה. שאר המכשירים נותקו.") {
                model.changePassword(currentState.text.toString(), newState.text.toString())
                currentState.clearText(); newState.clearText()
            }
        }
    }

    GhostButton("התנתק", Modifier.fillMaxWidth()) { model.logout() }

    SectionRow("מחיקת חשבון", "מוחק את החשבון ואת ההתקדמות השמורה בשרת", open == "delete", danger = true) { toggle("delete") }
    if (open == "delete") {
        Text("ההתקדמות במכשיר הזה נשארת. אי אפשר לבטל את המחיקה.", color = p.muted, fontSize = 14.sp)
        Field("סיסמה לאישור", deleteState, KeyboardType.Password, secret = true)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (deleteState.text.isNotEmpty() && !busy) p.bad else p.bad.copy(alpha = .4f))
                .clickable(enabled = deleteState.text.isNotEmpty() && !busy) { confirmDelete = true }.padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) { Text("מחק את החשבון", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את החשבון?") },
            text = { Text("החשבון ${model.user?.email.orEmpty()} וכל ההתקדמות השמורה בשרת יימחקו לצמיתות.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    run("החשבון נמחק") { model.deleteAccount(deleteState.text.toString()) }
                }) { Text("מחק", color = p.bad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
            containerColor = p.surface, titleContentColor = p.ink, textContentColor = p.ink,
        )
    }
}

@Composable
private fun SectionRow(title: String, subtitle: String, expanded: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val p = Pal
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.paper).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) p.bad else p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = p.muted, fontSize = 13.5.sp)
        }
        // Chevron: points forward (left in RTL) when closed, down when open.
        Icon(Icons.back, null, tint = p.muted, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (expanded) 90f else 180f })
    }
}

@Composable
private fun Field(label: String, state: TextFieldState, type: KeyboardType, secret: Boolean = false) {
    val options = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None)
    if (secret) {
        OutlinedSecureTextField(state = state, label = { Text(label) }, keyboardOptions = options, modifier = Modifier.fillMaxWidth())
    } else {
        OutlinedTextField(state = state, label = { Text(label) }, lineLimits = TextFieldLineLimits.SingleLine, keyboardOptions = options, modifier = Modifier.fillMaxWidth())
    }
}
