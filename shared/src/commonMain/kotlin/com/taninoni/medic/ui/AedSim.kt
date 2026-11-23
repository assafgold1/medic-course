package com.taninoni.medic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.Sound
import com.taninoni.medic.Speech
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.nowMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/*
 * A semi-automatic AED training simulator, modelled on the course's LifeLine routine:
 * green button → pads → "analysing, don't touch" → shock advised or not (random rhythm) →
 * charging, stand clear → flashing shock button → 2 minutes of CPR with a metronome → analyse again.
 */

private enum class Rhythm(val label: String, val shockable: Boolean) {
    VF("פרפור חדרים (VF)", true),
    VT("טכיקרדיה חדרית ללא דופק (VT)", true),
    ASYSTOLE("אסיסטולה", false),
    ROSC("קצב סדיר: חזרה של דופק (ROSC)", false),
}

private enum class Phase { OFF, PADS, ANALYZE, CHARGING, READY, CPR, CHECK, DONE }

private data class LogLine(val ok: Boolean?, val text: String)

/**
 * The device's voice: fixed recordings in one voice through a small loudspeaker, like a real AED
 * (files/sounds/aed, made by tools/aed_voice.py). Falls back to text-to-speech if a file is missing.
 */
private object AedVoice {
    private val ids = listOf("pads", "analyze", "advised", "press", "cancelled", "cpr", "no_shock_check", "no_shock", "motion", "shocked", "clear")
    private val cache = mutableMapOf<String, ByteArray>()
    suspend fun preload() {
        for (id in ids) if (id !in cache) runCatching { com.taninoni.medic.res.Res.readBytes("files/sounds/aed/$id.wav") }.getOrNull()?.let { cache[id] = it }
    }
    fun play(id: String, fallback: String) {
        val wav = cache[id]
        if (wav != null) { Speech.stop(); Sound.voice(wav) } else { Sound.stopVoice(); Speech.say(fallback) }
    }
    fun stop() { Sound.stopVoice(); Speech.stop() }
}

/** Generated device sounds (16-bit mono PCM WAV). */
private object AedTones {
    private const val RATE = 22050
    private fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        val b = ByteArray(44 + data)
        fun put(i: Int, v: Int, n: Int) { for (k in 0 until n) b[i + k] = (v shr (8 * k)).toByte() }
        "RIFF".forEachIndexed { i, c -> b[i] = c.code.toByte() }; put(4, 36 + data, 4)
        "WAVEfmt ".forEachIndexed { i, c -> b[8 + i] = c.code.toByte() }
        put(16, 16, 4); put(20, 1, 2); put(22, 1, 2); put(24, RATE, 4); put(28, RATE * 2, 4); put(32, 2, 2); put(34, 16, 2)
        "data".forEachIndexed { i, c -> b[36 + i] = c.code.toByte() }; put(40, data, 4)
        samples.forEachIndexed { i, s -> put(44 + i * 2, s.toInt(), 2) }
        return b
    }
    private fun tone(sec: Double, vol: Double = .45, f: (Double) -> Double): ByteArray {
        val n = (sec * RATE).toInt()
        var phase = 0.0
        return wav(ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            phase += 2 * PI * f(t) / RATE
            val env = minOf(1.0, t / .01, (sec - t) / .02)
            (sin(phase) * vol * env * Short.MAX_VALUE).toInt().toShort()
        })
    }
    val power by lazy { tone(1.1) { 960.0 } }
    val charge by lazy { tone(3.0, .3) { t -> 500 + 1400 * (t / 3.0) } }
    val ready by lazy { tone(.18) { 1500.0 } }
    val tick by lazy { tone(.045, .5) { 1250.0 } }
    val breath by lazy { tone(.35, .35) { 660.0 } }
    val shock by lazy {
        val n = (.35 * RATE).toInt(); val rnd = Random(7)
        wav(ShortArray(n) { i -> val t = i.toDouble() / RATE; ((rnd.nextDouble() * 2 - 1) * exp(-t * 14) * .8 * Short.MAX_VALUE).toInt().toShort() })
    }
}

@Composable
fun AedScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val scope = rememberCoroutineScope()
    val sound = model.soundOn
    var phase by remember { mutableStateOf(Phase.OFF) }
    var rhythm by remember { mutableStateOf(Rhythm.VF) }
    var child by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("לחצו על הכפתור הירוק כדי להפעיל") }
    var shocks by remember { mutableIntStateOf(0) }
    var cycles by remember { mutableIntStateOf(0) }
    var cleared by remember { mutableStateOf(false) }
    var cprLeft by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var breathing by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var padsType by remember { mutableStateOf<String?>(null) }
    val spots = remember { mutableStateListOf<Int>() }
    val log = remember { mutableStateListOf<LogLine>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var shockReadyAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { AedVoice.preload() }
    /** Shows [text] on the device screen and plays the recorded prompt [id] (spoken by TTS if the file is missing). */
    fun say(id: String, text: String) { prompt = text; if (sound) AedVoice.play(id, text) }
    fun play(w: ByteArray) { if (sound) Sound.clip(w) }
    fun go(block: suspend () -> Unit) { job?.cancel(); job = scope.launch { block() } }
    DisposableEffect(Unit) { onDispose { job?.cancel(); AedVoice.stop() } }

    fun nextRhythm(afterShock: Boolean) {
        val r = Random.nextDouble()
        rhythm = when {
            rhythm == Rhythm.ROSC -> Rhythm.ROSC
            afterShock && shocks >= 1 && r < .4 -> Rhythm.ROSC
            afterShock && r < .55 -> Rhythm.ASYSTOLE
            afterShock -> if (Random.nextBoolean()) Rhythm.VF else rhythm
            rhythm == Rhythm.ASYSTOLE && r < .3 -> Rhythm.VF
            else -> rhythm
        }
    }

    lateinit var analyze: () -> Unit
    fun cpr() = go {
        phase = Phase.CPR; cycles++
        say("cpr", "התחילו החייאה. 30 לחיצות ו-2 הנשמות.")
        delay(2600)
        val end = nowMillis() + 120_000
        count = 0
        while (nowMillis() < end) {
            cprLeft = ((end - nowMillis()) / 1000).toInt()
            if (count >= 30) {
                breathing = true; count = 0
                repeat(2) { play(AedTones.breath); delay(1000) }
                breathing = false
            }
            count++; play(AedTones.tick)
            delay(60_000L / 110)
        }
        cprLeft = 0
        analyze()
    }

    analyze = {
        go {
            phase = Phase.ANALYZE; cleared = false
            say("analyze", "מנתח את קצב הלב. אל תגעו במטופל.")
            delay(6500)
            if (rhythm.shockable) {
                phase = Phase.CHARGING
                say("advised", "מומלץ שוק. התרחקו מהמטופל.")
                play(AedTones.charge); delay(3200)
                phase = Phase.READY; shockReadyAt = nowMillis()
                say("press", "לחצו על כפתור השוק המהבהב.")
                val until = nowMillis() + 20_000
                while (nowMillis() < until) { flash = !flash; if (flash) play(AedTones.ready); delay(500) }
                flash = false
                log += LogLine(false, "לא ניתן שוק תוך 20 שניות, והמכשיר ביטל את הטעינה.")
                say("cancelled", "השוק בוטל. התחילו החייאה.")
                delay(2000); cpr()
            } else if (rhythm == Rhythm.ROSC) {
                phase = Phase.CHECK
                say("no_shock_check", "לא מומלץ שוק. בדקו נשימה ודופק.")
            } else {
                say("no_shock", "לא מומלץ שוק.")
                delay(2200); cpr()
            }
        }
    }

    fun powerOn() {
        log.clear(); shocks = 0; cycles = 0; spots.clear(); padsType = null
        child = Random.nextDouble() < .25
        rhythm = if (Random.nextDouble() < .7) (if (Random.nextDouble() < .75) Rhythm.VF else Rhythm.VT) else Rhythm.ASYSTOLE
        startedAt = nowMillis()
        go {
            phase = Phase.PADS
            play(AedTones.power); tick(); delay(1200)
            say("pads", "חשפו את חזה המטופל. בחרו מדבקות והדביקו אותן כמו בציור.")
        }
    }

    fun padsDone() {
        val wantChild = child
        val gotChild = padsType == "child"
        when {
            gotChild && !wantChild -> log += LogLine(false, "מדבקות ילדים על מבוגר: אסור. האנרגיה תהיה נמוכה מדי.")
            !gotChild && wantChild -> log += LogLine(null, "מדבקות מבוגר על ילד: מותר רק כשאין מדבקות ילדים, ובמיקום קדמי-אחורי.")
            else -> log += LogLine(true, "בחרת את המדבקות הנכונות.")
        }
        if (!gotChild) {
            val right = spots.toSet() == setOf(0, 1)
            log += if (right) LogLine(true, "מיקום המדבקות נכון: מתחת לעצם הבריח מימין, ובצד שמאל מתחת לבית השחי.")
            else LogLine(false, "מיקום מדבקות שגוי. מבוגר: מתחת לעצם הבריח הימנית, ובצד שמאל כ-8 ס״מ מתחת לבית השחי.")
        }
        val secs = (nowMillis() - startedAt) / 1000
        log += LogLine(if (secs <= 40) true else null, "המדבקות הודבקו אחרי $secs שניות מההפעלה.")
        analyze()
    }

    fun touch() {
        when (phase) {
            Phase.ANALYZE -> {
                log += LogLine(false, "נגעת במטופל בזמן ניתוח הקצב. האבחון התחיל מחדש.")
                 go { say("motion", "זוהתה תנועה. הפסיקו כל מגע במטופל."); delay(2500); analyze() }
            }
            Phase.CHARGING, Phase.READY -> { cleared = false; log += LogLine(false, "נגעת במטופל בזמן טעינה. לפני שוק אף אחד לא נוגע.") }
            else -> {}
        }
    }

    fun shock() {
        if (phase != Phase.READY) return
        shocks++
        log += if (cleared) LogLine(true, "הכרזת \"התרחקו\" לפני השוק.") else LogLine(false, "נתת שוק בלי לוודא שאף אחד לא נוגע ובלי להכריז.")
        val secs = (nowMillis() - shockReadyAt) / 1000
        log += LogLine(if (secs <= 5) true else null, "שוק $shocks ניתן $secs שניות אחרי שהמכשיר היה מוכן (${if (padsType == "child") "50" else "150"} ג׳אול).")
        go {
            flash = false
            play(AedTones.shock)
            if (FeedbackPrefs.haptics) runCatching { com.taninoni.medic.Haptics.heavy() }
            say("shocked", "השוק ניתן.")
            delay(1600)
            nextRhythm(afterShock = true)
            cpr()
        }
    }

    fun skipCpr() {
        log += LogLine(null, "דילגת על סבב החייאה (בתרגול בלבד).")
        go { nextRhythm(afterShock = false); analyze() }
    }

    fun finish(pulse: Boolean) {
        job?.cancel(); AedVoice.stop()
        log += if (pulse) LogLine(true, "בדקת נשימה ודופק אחרי שהמכשיר לא המליץ על שוק. המטופל חזר לדופק.")
        else LogLine(false, "לפי המכשיר הקצב סדיר: בודקים דופק לפני שממשיכים עיסויים.")
        phase = Phase.DONE
        prompt = "הסימולציה הסתיימה"
    }

    fun stopAll() { job?.cancel(); AedVoice.stop(); phase = Phase.OFF; flash = false; prompt = "לחצו על הכפתור הירוק כדי להפעיל" }

    GlassPage("דפיברילטור", null, subtitle = "סימולטור AED חצי-אוטומטי") {
            ProtocolSteps(phase)
            if (phase == Phase.OFF) Text(
                "תרגול מלא של הפעלת המכשיר. בכל הפעלה המטופל והקצב נבחרים באקראי.",
                color = p.muted, fontSize = 14.5.sp, lineHeight = 21.sp,
            )
            if (phase != Phase.OFF && phase != Phase.DONE) Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), elevation = 3.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("מטופל", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${if (child) "ילד בן 5" else "מבוגר"} · דום לב", color = p.ink, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (shocks > 0) Text("שוקים: $shocks", color = p.blue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            AedDevice(
                phase = phase, prompt = prompt, rhythm = rhythm, flash = flash, pads = if (phase == Phase.PADS) null else padsType,
                count = count, breathing = breathing, cprLeft = cprLeft,
                onPower = { if (phase == Phase.OFF || phase == Phase.DONE) powerOn() else stopAll() },
                onShock = { shock() },
            )
            when (phase) {
                Phase.PADS -> PadsStep(padsType, spots, onType = { padsType = it; spots.clear() }, onDone = { padsDone() })
                Phase.ANALYZE, Phase.CHARGING, Phase.READY -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SimButton("נוגע במטופל / ממשיך לעסות", p.paper, p.ink, Modifier.weight(1f)) { touch() }
                    if (phase != Phase.ANALYZE) SimButton(if (cleared) "הוכרז: כולם התרחקו" else "להכריז: \"התרחקו!\"", if (cleared) p.goodSoft else p.badSoft, if (cleared) p.good else p.bad, Modifier.weight(1f)) {
                        if (!cleared) { cleared = true; if (sound) AedVoice.play("clear", "זהירות, אני נותן שוק. כולם להתרחק.") }
                    }
                }
                Phase.CPR -> SimButton("דלג לסוף 2 הדקות (תרגול)", p.paper, p.muted, Modifier.fillMaxWidth()) { skipCpr() }
                Phase.CHECK -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("המכשיר לא ממליץ על שוק. מה עושים עכשיו?", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    SimButton("בודקים נשימה ודופק", p.goodSoft, p.good, Modifier.fillMaxWidth()) { finish(true) }
                    SimButton("ממשיכים עיסויים בלי לבדוק", p.paper, p.ink, Modifier.fillMaxWidth()) { finish(false) }
                }
                else -> {}
            }
            if (phase == Phase.DONE || (phase == Phase.OFF && log.isNotEmpty())) Debrief(log, shocks, cycles)
            if (phase == Phase.OFF) {
                SectionTitle("זוכרים", Modifier.padding(top = 4.dp))
                Panel {
                    listOf(
                        "קודם בטיחות: הזירה בטוחה, כפפות, ואז מתחילים.",
                        "מחברים דפיברילטור מוקדם, ובן הזוג ממשיך לעסות בזמן ההדבקה.",
                        "בזמן ניתוח הקצב ולפני שוק: אף אחד לא נוגע.",
                        "אחרי שוק, או כשלא מומלץ שוק: מיד חוזרים לעיסויים, 2 דקות.",
                    ).forEach { Text("• $it", color = p.ink, fontSize = 15.sp, lineHeight = 22.sp) }
                }
                SimButton("מטרונום חופשי לתרגול על בובה", p.paper, p.blue, Modifier.fillMaxWidth()) { nav.go(Screen.Metronome) }
            }
    }
}

@Composable
private fun SimButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = Pal
    Box(
        modifier.heightIn(min = 54.dp).bouncy(onClick = onClick)
            .glass(RoundedCornerShape(50), tint = if (bg == p.paper) Color.Unspecified else bg, elevation = 8.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, textAlign = TextAlign.Center) }
}

private val Lcd = Color(0xFF9BF5BD)
private val LcdGlow = Color(0xFF34D27A)

private val Body1 = Color(0xFF3A3F47)
private val Body2 = Color(0xFF23272D)
private val Bezel = Color(0xFF15181C)

/** Where the rescuer is in the AED routine, as a row of steps. */
@Composable
private fun ProtocolSteps(phase: Phase) {
    val p = Pal
    val steps = listOf("הפעלה", "מדבקות", "ניתוח", "שוק", "החייאה")
    val current = when (phase) {
        Phase.OFF -> -1; Phase.PADS -> 1; Phase.ANALYZE -> 2; Phase.CHARGING, Phase.READY -> 3; Phase.CPR -> 4; Phase.CHECK, Phase.DONE -> 5
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { i, label ->
            val done = i < current
            val now = i == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == 0) Color.Transparent else if (i <= current) Brand else p.hair))
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(if (done || now) Brand else Color.Transparent)
                            .border(2.dp, if (done || now) Brand else p.muted.copy(alpha = .35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        else Text("${i + 1}", color = if (now) Color.White else p.muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == steps.lastIndex) Color.Transparent else if (i < current) Brand else p.hair))
                }
                Text(label, color = if (now) p.ink else p.muted, fontSize = 11.5.sp, fontWeight = if (now) FontWeight.Black else FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The trainer: a charcoal unit with a status light, a monitor (grid, readouts, prompt and trace)
 * and two recessed buttons: green power and a shock button that lights up and pulses only when armed.
 */
@Composable
private fun AedDevice(
    phase: Phase, prompt: String, rhythm: Rhythm, flash: Boolean, pads: String?,
    count: Int, breathing: Boolean, cprLeft: Int, onPower: () -> Unit, onShock: () -> Unit,
) {
    val on = phase != Phase.OFF
    val armed = phase == Phase.READY
    val (statusText, statusColor) = when (phase) {
        Phase.OFF -> "כבוי" to Color(0xFF8A9099)
        Phase.CHARGING -> "טוען" to Color(0xFFFFB020)
        Phase.READY -> "מוכן לשוק" to Brand
        Phase.ANALYZE -> "מנתח" to Color(0xFFFFB020)
        else -> "פעיל" to LcdGlow
    }
    Column(
        Modifier.fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(.35f), spotColor = Color.Black.copy(.35f))
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Body1, Body2)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), Color.White.copy(alpha = .03f))), RoundedCornerShape(30.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Label strip: model mark on one side, the status light on the other.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Brand), contentAlignment = Alignment.Center) {
                Icon(Icons.dev, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("AED", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, lineHeight = 20.sp)
                Text("מכשיר אימון", color = Color.White.copy(alpha = .55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            var blink by remember { mutableStateOf(true) }
            LaunchedEffect(phase) { while (phase == Phase.OFF || phase == Phase.READY) { blink = !blink; delay(if (phase == Phase.READY) 350 else 900) }; blink = true }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .3f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (blink) statusColor else statusColor.copy(alpha = .25f)))
                Text(statusText, color = Color.White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Monitor.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF040806))
                .border(2.dp, Bezel, RoundedCornerShape(16.dp))
                .drawBehind {
                    val step = 14.dp.toPx()
                    var x = 0f; while (x < size.width) { drawLine(LcdGlow.copy(alpha = .07f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                    var y = 0f; while (y < size.height) { drawLine(LcdGlow.copy(alpha = .07f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Readout("אנרגיה", if (on && pads != null) (if (pads == "child") "50J" else "150J") else "--", Modifier.weight(1f))
                Readout("מדבקות", if (on && pads != null) (if (pads == "child") "ילד" else "מבוגר") else "--", Modifier.weight(1f))
                Readout("זמן", if (phase == Phase.CPR) "${cprLeft / 60}:${(cprLeft % 60).toString().padStart(2, '0')}" else "--:--", Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LcdGlow.copy(alpha = .2f)))
            Text(
                if (phase == Phase.CPR) (if (breathing) "הנשמה" else "לחיצה $count מתוך 30") else prompt,
                color = Color(0xFFE6FFF0), fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
            Ecg(phase, rhythm, Modifier.fillMaxWidth().height(64.dp))
        }
        // Controls.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            DeviceButton("הפעלה / כיבוי", Color(0xFF22B45E), lit = true, pulsing = !on, onClick = onPower) { PowerGlyph(Color.White) }
            DeviceButton("שוק", if (armed) Brand else Color(0xFF41464E), lit = armed, pulsing = armed, enabled = armed, onClick = onShock) {
                Icon(Icons.dev, "שוק", tint = if (armed) Color.White else Color.White.copy(alpha = .35f), modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Lcd.copy(alpha = .55f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Lcd, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

/** A round button sunk into a dark bezel, with its label under it. */
@Composable
private fun DeviceButton(label: String, color: Color, lit: Boolean, pulsing: Boolean, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition()
    val k by pulse.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200), androidx.compose.animation.core.RepeatMode.Restart))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            if (pulsing) Box(
                Modifier.size(92.dp).graphicsLayer { val sc = 1f + .14f * k; scaleX = sc; scaleY = sc; alpha = (1f - k) * .6f }
                    .border(3.dp, color, CircleShape),
            )
            Box(Modifier.size(92.dp).clip(CircleShape).background(Bezel).border(1.dp, Color.White.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(72.dp).bouncy(enabled = enabled, pressedScale = .92f, onClick = onClick)
                        .shadow(if (lit) 12.dp else 0.dp, CircleShape, ambientColor = color, spotColor = color)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(color.copy(alpha = 1f), color.copy(red = color.red * .8f, green = color.green * .8f, blue = color.blue * .8f))))
                        .border(1.dp, Color.White.copy(alpha = if (lit) .35f else .08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { content() }
            }
        }
        Text(label, color = Color.White.copy(alpha = .8f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** The standard power symbol: a broken ring with a bar through the gap. */
@Composable
private fun PowerGlyph(color: Color) {
    Canvas(Modifier.size(30.dp)) {
        val w = size.minDimension; val st = w * .11f
        drawArc(color, -60f, 300f, false, Offset(st, st), androidx.compose.ui.geometry.Size(w - st * 2, w - st * 2), style = Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawLine(color, Offset(w / 2, st * .4f), Offset(w / 2, w * .48f), st, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun Ecg(phase: Phase, rhythm: Rhythm, modifier: Modifier) {
    var t by remember { mutableStateOf(0.0) }
    LaunchedEffect(phase) { while (phase != Phase.OFF) { delay(40); t += .04 } }
    val showRhythm = phase == Phase.ANALYZE || phase == Phase.CHARGING || phase == Phase.READY || phase == Phase.CHECK || phase == Phase.DONE
    Canvas(modifier) {
        val w = size.width; val h = size.height; val mid = h / 2
        val path = Path()
        val span = 3.0 // seconds on screen
        val n = 160
        for (i in 0..n) {
            val x = w * i / n
            val s = t - span + span * i / n
            val y = when {
                phase == Phase.OFF -> 0.0
                phase == Phase.PADS -> 0.0
                phase == Phase.CPR -> .55 * sin(2 * PI * s * 110 / 60)
                !showRhythm -> 0.0
                rhythm == Rhythm.VF -> .45 * sin(2 * PI * 5.3 * s) + .3 * sin(2 * PI * 7.9 * s + 1) + .2 * sin(2 * PI * 3.1 * s)
                rhythm == Rhythm.VT -> .85 * sin(2 * PI * 3.2 * s)
                rhythm == Rhythm.ASYSTOLE -> .04 * sin(2 * PI * .7 * s)
                else -> { val ph = (s * 1.25) % 1.0; val d = ph - .3; if (abs(d) < .02) (if (d < 0) -1.0 else 1.0) * (1 - abs(d) / .02) * .9 else .12 * exp(-((ph - .55) * 12).let { it * it }) }
            }
            val yy = (mid - y * h * .45).toFloat()
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        drawPath(path, LcdGlow.copy(alpha = .25f), style = Stroke(9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(path, Lcd, style = Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Choose pads and place them: adult pads go on two spots on the bare chest; child pads front-and-back. */
@Composable
private fun PadsStep(type: String?, spots: MutableList<Int>, onType: (String) -> Unit, onDone: () -> Unit) {
    val p = Pal
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("1. בחרו מדבקות", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Segmented(listOf("adult" to "מדבקות מבוגר", "child" to "מדבקות ילד"), type ?: "") { onType(it) }
        if (type == "adult") {
            Text("2. הקישו על שני המקומות הנכונים להדבקה", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Torso(spots)
        } else if (type == "child") {
            Text("מדבקות ילדים: אחת במרכז החזה ואחת במרכז הגב, מולה.", color = p.muted, fontSize = 15.sp)
        }
        val ready = type == "child" || (type == "adult" && spots.size == 2)
        PrimaryButton("הדבקתי, המשך", Modifier.fillMaxWidth(), enabled = ready) { onDone() }
    }
}

/** Front of a torso with four candidate pad spots (0 and 1 are correct for an adult). */
@Composable
private fun Torso(spots: MutableList<Int>) {
    val p = Pal
    // Fractions of the canvas, drawn in screen space (the patient's right is the viewer's left).
    val cands = listOf(Offset(.36f, .27f), Offset(.70f, .62f), Offset(.64f, .27f), Offset(.34f, .70f))
    val skin = Color(0xFFF2C7A5); val line = Color(0xFFC08A63)
    Box(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(18.dp)).background(p.paper)) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { pos ->
                val i = cands.indexOfFirst { c -> (Offset(c.x * size.width, c.y * size.height) - pos).getDistance() < size.width * .1f }
                if (i >= 0) { if (i in spots) spots.remove(i) else if (spots.size < 2) spots.add(i) }
            }
        }) {
            val w = size.width; val h = size.height
            val body = Path().apply {
                moveTo(w * .42f, h * .04f); lineTo(w * .58f, h * .04f)
                lineTo(w * .60f, h * .12f); lineTo(w * .84f, h * .18f)
                lineTo(w * .86f, h * .96f); lineTo(w * .14f, h * .96f)
                lineTo(w * .16f, h * .18f); lineTo(w * .40f, h * .12f); close()
            }
            drawPath(body, skin)
            drawPath(body, line, style = Stroke(2.dp.toPx()))
            drawLine(line, Offset(w * .22f, h * .19f), Offset(w * .46f, h * .2f), 2.dp.toPx())
            drawLine(line, Offset(w * .54f, h * .2f), Offset(w * .78f, h * .19f), 2.dp.toPx())
            drawLine(line, Offset(w * .5f, h * .22f), Offset(w * .5f, h * .58f), 2.dp.toPx())
            drawCircle(line, 4.dp.toPx(), Offset(w * .38f, h * .44f)); drawCircle(line, 4.dp.toPx(), Offset(w * .62f, h * .44f))
            cands.forEachIndexed { i, c ->
                val o = Offset(c.x * w, c.y * h)
                if (i in spots) {
                    drawRoundRect(Color.White, topLeft = o - Offset(w * .08f, h * .07f), size = androidx.compose.ui.geometry.Size(w * .16f, h * .14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f))
                    drawRoundRect(Color(0xFF6B7280), topLeft = o - Offset(w * .08f, h * .07f), size = androidx.compose.ui.geometry.Size(w * .16f, h * .14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f), style = Stroke(2.dp.toPx()))
                } else {
                    drawCircle(Color(0x33EA580C), w * .055f, o)
                    drawCircle(Color(0xFFEA580C), w * .055f, o, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Text("ימין המטופל", Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).padding(10.dp), color = p.muted, fontSize = 12.sp)
    }
}

@Composable
private fun Debrief(log: List<LogLine>, shocks: Int, cycles: Int) {
    val p = Pal
    val good = log.count { it.ok == true }; val bad = log.count { it.ok == false }
    SectionTitle("סיכום התרגול", Modifier.padding(top = 4.dp))
    Panel {
        Text("$shocks שוקים · $cycles סבבי החייאה · $good נכון · $bad לתיקון", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        log.forEach { l ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when (l.ok) { true -> "✓"; false -> "✗"; null -> "•" }, color = when (l.ok) { true -> p.good; false -> p.bad; null -> p.muted }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(l.text, color = p.ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
