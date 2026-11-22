package com.taninoni.medic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Shown until the user signs in. Sign-in is required so instructors can follow each student's progress. */
@Composable
fun LoginScreen(model: AppModel) {
    val p = Pal
    val scope = rememberCoroutineScope()
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    val name = rememberTextFieldState()
    val confirm = rememberTextFieldState()
    var mode by remember { mutableStateOf("login") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.padding(top = 18.dp).size(132.dp).glass(CircleShape, elevation = 26.dp).background(Color.White.copy(alpha = if (p.dark) .08f else .4f)),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(Res.drawable.uh_icon), "מסלול חובש", Modifier.size(96.dp)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("מסלול חובש", color = p.ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("לימוד, תרגול ומבחנים לקורס חובשים", color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(34.dp), elevation = 20.dp).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Segmented(listOf("login" to "כניסה", "register" to "הרשמה"), mode) { mode = it; error = null }
                if (mode == "register") {
                    Text("חשבון חדש. השם יוצג למדריכים שלך בקורס.", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    Field("שם מלא", name, KeyboardType.Text)
                }
                Field("מייל", email, KeyboardType.Email)
                Field(if (mode == "register") "סיסמה (לפחות 8 תווים)" else "סיסמה", password, KeyboardType.Password, secret = true)
                if (mode == "register") Field("אימות סיסמה", confirm, KeyboardType.Password, secret = true)
                error?.let {
                    Text(it, color = p.bad, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.badSoft).padding(12.dp))
                }
                val ready = email.text.isNotBlank() && password.text.isNotBlank() &&
                    (mode == "login" || (name.text.isNotBlank() && confirm.text.isNotBlank()))
                PrimaryButton(
                    if (busy) "רגע…" else if (mode == "login") "כניסה" else "יצירת חשבון", Modifier.fillMaxWidth(),
                    enabled = !busy && ready,
                ) {
                    if (mode == "register" && password.text.toString() != confirm.text.toString()) { error = "הסיסמאות לא זהות"; return@PrimaryButton }
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            if (mode == "login") model.login(email.text.toString(), password.text.toString())
                            else model.register(name.text.toString(), email.text.toString(), password.text.toString())
                        }.onFailure { error = (it as? ApiException)?.message ?: "אין חיבור לשרת. בדוק את החיבור לאינטרנט ונסה שוב." }
                        busy = false
                    }
                }
                Text(
                    if (mode == "login") "אין לך חשבון? הרשמה" else "כבר רשום? כניסה",
                    color = p.blue, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).bouncy { mode = if (mode == "login") "register" else "login"; error = null }.padding(10.dp),
                )
            }
            Text(
                "החומר נועד לחזרה ולתרגול, ואינו מחליף את מצגות הקורס או את הפרוטוקולים הרשמיים.",
                color = p.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp),
            )
        }
    }
}
