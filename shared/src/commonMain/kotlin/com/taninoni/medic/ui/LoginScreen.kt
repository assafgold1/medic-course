package com.taninoni.medic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.ApiException
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.res.Res
import com.taninoni.medic.res.uh_icon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/**
 * Shown until the user signs in. Sign-in is required so instructors can follow each student's progress.
 * Sign-up is three short steps: the account itself, how the app should address you, and where you're from.
 */
@Composable
fun LoginScreen(model: AppModel) {
    val p = Pal
    val scope = rememberCoroutineScope()
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    val name = rememberTextFieldState()
    val confirm = rememberTextFieldState()
    val otherOrg = rememberTextFieldState()
    var mode by remember { mutableStateOf("login") }
    var step by remember { mutableIntStateOf(0) }
    var address by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val g = { m: String, f: String -> if (address == "f") f else m }

    fun failMessage(t: Throwable) = (t as? ApiException)?.message ?: "אין חיבור לשרת. בדוק את החיבור לאינטרנט ונסה שוב."

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // The logo shrinks once sign-up starts, to leave room for the steps.
            val big = mode == "login" || step == 0
            val logo by animateFloatAsState(if (big) 1f else .62f, spring(dampingRatio = .7f, stiffness = 300f))
            Box(
                Modifier.padding(top = 12.dp).size(132.dp * logo).liquidGlass(CircleShape, frost = .8f),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(Res.drawable.uh_icon), "מסלול חובש", Modifier.size(96.dp * logo)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("מסלול חובש", color = p.ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("לימוד, תרגול ומבחנים לקורס חובשים", color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(34.dp), elevation = 20.dp).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Segmented(listOf("login" to "כניסה", "register" to "הרשמה"), mode) { mode = it; step = 0; error = null }
                if (mode == "register") StepDots(step, 3)

                AnimatedContent(
                    targetState = if (mode == "login") -1 else step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(spring(dampingRatio = .85f, stiffness = 380f)) { w -> if (forward) -w / 3 else w / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(220)) { w -> if (forward) w / 4 else -w / 4 } + fadeOut(tween(150)))
                    },
                    label = "auth-step",
                ) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (s) {
                            -1 -> {
                                Field("מייל", email, KeyboardType.Email)
                                Field("סיסמה", password, KeyboardType.Password, secret = true)
                            }
                            0 -> {
                                StepTitle("פרטי החשבון", "השם יוצג למדריכים שלך בקורס.")
                                Field("שם מלא", name, KeyboardType.Text)
                                Field("מייל", email, KeyboardType.Email)
                                Hint(email, "כתובת המייל לא נראית תקינה") { it.isNotEmpty() && !EMAIL.matches(it.trim()) }
                                Field("סיסמה (לפחות 8 תווים)", password, KeyboardType.Password, secret = true)
                                PasswordMeter(password.text.length)
                                Field("אימות סיסמה", confirm, KeyboardType.Password, secret = true)
                                Hint(confirm, "הסיסמאות לא זהות") { it.isNotEmpty() && it != password.text.toString() }
                            }
                            1 -> {
                                StepTitle("איך לפנות אליך?", "ככה האפליקציה תדבר איתך.")
                                Choice("בלשון זכר", "\"אתה מוכן למבחן?\"", Icons.check, address == "m") { address = "m"; step = 2; error = null }
                                Choice("בלשון נקבה", "\"את מוכנה למבחן?\"", Icons.check, address == "f") { address = "f"; step = 2; error = null }
                            }
                            else -> {
                                StepTitle(g("מאיפה אתה?", "מאיפה את?"), g("באיזה ארגון אתה מתנדב או עובד?", "באיזה ארגון את מתנדבת או עובדת?"))
                                Choice("מד״א", "מגן דוד אדום", Icons.medical, org == "mda") { org = "mda"; error = null }
                                Choice("איחוד הצלה", "ארגון ההתנדבות לרפואת חירום", Icons.pulse, org == "uh") { org = "uh"; error = null }
                                Choice("אחר", "צבא, כבאות, ארגון אחר…", Icons.special, org == "other") { org = "other"; error = null }
                                AnimatedVisibility(org == "other") { Field("שם הארגון", otherOrg, KeyboardType.Text) }
                            }
                        }
                    }
                }

                error?.let {
                    Text(it, color = p.bad, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.badSoft).padding(12.dp))
                }

                val pw = password.text.toString()
                val accountOk = name.text.trim().length >= 2 && EMAIL.matches(email.text.trim()) && pw.length >= 8 && confirm.text.toString() == pw
                val orgOk = org == "mda" || org == "uh" || (org == "other" && otherOrg.text.isNotBlank())
                when {
                    mode == "login" -> PrimaryButton(
                        if (busy) "רגע…" else "כניסה", Modifier.fillMaxWidth(),
                        enabled = !busy && email.text.isNotBlank() && password.text.isNotBlank(),
                    ) {
                        busy = true; error = null
                        scope.launch {
                            runCatching { model.login(email.text.toString(), password.text.toString()) }.onFailure { error = failMessage(it) }
                            busy = false
                        }
                    }
                    step == 0 -> PrimaryButton("המשך", Modifier.fillMaxWidth(), enabled = accountOk, icon = Icons.forward) { error = null; step = 1 }
                    step == 1 -> {}
                    else -> PrimaryButton(if (busy) "רגע…" else "יצירת חשבון", Modifier.fillMaxWidth(), enabled = !busy && accountOk && orgOk && address.isNotEmpty()) {
                        busy = true; error = null
                        scope.launch {
                            runCatching {
                                model.register(name.text.toString(), email.text.toString(), pw, address, if (org == "other") otherOrg.text.toString() else org)
                            }.onFailure {
                                error = failMessage(it)
                                // A taken email or a bad field is fixed on the first step.
                                if (it is ApiException) step = 0
                            }
                            busy = false
                        }
                    }
                }
                if (mode == "register" && step > 0) Text(
                    "חזרה", color = p.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).bouncy { step--; error = null }.padding(8.dp),
                )
                if (mode == "login" || step == 0) Text(
                    if (mode == "login") "אין לך חשבון? הרשמה" else "כבר רשום? כניסה",
                    color = p.blue, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).bouncy { mode = if (mode == "login") "register" else "login"; step = 0; error = null }.padding(10.dp),
                )
            }
            Text(
                "החומר נועד לחזרה ולתרגול, ואינו מחליף את מצגות הקורס או את הפרוטוקולים הרשמיים.",
                color = p.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, sub: String) {
    val p = Pal
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Text(sub, color = p.muted, fontSize = 14.sp)
    }
}

/** Step progress: a capsule per step, the current one stretched and lit. */
@Composable
private fun StepDots(step: Int, count: Int) {
    val p = Pal
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val w by animateFloatAsState(if (i == step) 28f else 8f, spring(dampingRatio = .7f, stiffness = 400f))
            Box(
                Modifier.height(8.dp).size(width = w.dp, height = 8.dp).clip(RoundedCornerShape(50))
                    .background(if (i <= step) androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.bandA, p.bandB)) else androidx.compose.ui.graphics.SolidColor(p.hair)),
            )
        }
        Text("שלב ${step + 1} מתוך $count", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A big tappable glass option with a radio mark. */
@Composable
private fun Choice(title: String, sub: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val p = Pal
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().bouncy(onClick = onClick)
            .glass(shape, tint = if (selected) p.blueSoft else Color.Unspecified, elevation = if (selected) 12.dp else 4.dp)
            .border(if (selected) 2.dp else 0.dp, if (selected) p.blue else Color.Transparent, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, p.blue, size = 42.dp, solid = selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = p.muted, fontSize = 13.5.sp)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (selected) p.blue else Color.Transparent)
                .border(2.dp, if (selected) p.blue else p.muted.copy(alpha = .4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
    }
}

/** A short red hint under a field while [bad] holds for its text. */
@Composable
private fun Hint(state: TextFieldState, text: String, bad: (String) -> Boolean) {
    val show = bad(state.text.toString())
    AnimatedVisibility(show) { Text(text, color = Pal.bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp)) }
}

/** Password length bar: red under 8 characters, orange at 8–11, green from 12. */
@Composable
private fun PasswordMeter(length: Int) {
    val p = Pal
    if (length == 0) return
    val (frac, color, label) = when {
        length < 8 -> Triple(length / 8f * .4f, p.bad, "עוד ${8 - length} תווים")
        length < 12 -> Triple(.7f, p.mid, "סיסמה סבירה")
        else -> Triple(1f, p.good, "סיסמה חזקה")
    }
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProgressBar(frac, color, Modifier.weight(1f), height = 6.dp)
        Text(label, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.graphicsLayer { })
    }
}
