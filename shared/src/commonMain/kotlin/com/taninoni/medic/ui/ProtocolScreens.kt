package com.taninoni.medic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.Protocol
import com.taninoni.medic.data.ProtocolBranch
import com.taninoni.medic.data.ProtocolStep

private val Orange = Color(0xFFEA580C)
private val Dark = Color(0xFF9A3412)
private val YesC = Color(0xFF2563EB)
private val NoC = Color(0xFF7C3AED)

/** All field protocols, grouped (resuscitation, medical, trauma…). */
@Composable
fun ProtocolsScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val list = model.content?.protocols.orEmpty()
    Column(Modifier.fillMaxSize()) {
        AppBar("פרוטוקולים", { nav.back() })
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                Text("מה עושים, בסדר הנכון. כל פרוטוקול בנוי מהחומר של הקורס.", color = p.muted, fontSize = 15.sp, lineHeight = 22.sp)
                list.groupBy { it.group }.forEach { (group, items) ->
                    SectionTitle(group, Modifier.padding(top = 6.dp))
                    Panel(spacing = 0.dp) {
                        items.forEachIndexed { i, pr ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.go(Screen.ProtocolView(pr.id)) }.padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(p.blueSoft), contentAlignment = Alignment.Center) {
                                    Text("${pr.steps.count { it.k == "step" }}", color = p.blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text(pr.title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("‹", color = p.muted, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One protocol as a native flowchart, with a link to its topic and the printable diagram. */
@Composable
fun ProtocolScreen(model: AppModel, nav: Nav, id: String) {
    val p = Pal
    val pr = model.content?.protocols?.firstOrNull { it.id == id } ?: return
    Column(Modifier.fillMaxSize()) {
        AppBar(pr.title, { nav.back() })
        Column(Modifier.weight(1f).sheetBackdrop().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Sheet {
                ProtocolFlow(pr)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    model.topic(pr.topic)?.let { t ->
                        Chip("לנושא: ${t.title}", Modifier.weight(1f)) { nav.go(Screen.Topic(t.id)) }
                    }
                    model.topic(pr.topic)?.images?.firstOrNull { it.file == pr.file }?.let { img ->
                        Chip("תרשים", Modifier) { ViewerState.show(listOf(img), 0) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier, onClick: () -> Unit) {
    val p = Pal
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(p.blueSoft).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = p.blue, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun ProtocolFlow(pr: Protocol) {
    var n = 0
    Column {
        pr.steps.forEachIndexed { i, s ->
            if (i > 0 && s.k != "note") Arrow()
            when (s.k) {
                "alarm" -> Banner("⚠", s.h, Color.White, Color(0xFFB91C1C), null)
                "stop" -> Banner("‼", s.h, Color(0xFF7F1D1D), Color(0xFFFEE2E2), Color(0xFFB91C1C))
                "note" -> Text("טיפ: ${s.h}", color = Color(0xFF7C2D12), fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFEDD5)).padding(14.dp))
                "if" -> Decision(s)
                else -> { n++; StepCard(n, s) }
            }
        }
    }
}

@Composable
private fun Arrow() {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(3.dp).height(14.dp).background(Color(0xFFF0A477)))
        Text("▼", color = Color(0xFFF0A477), fontSize = 11.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun StepCard(n: Int, s: ProtocolStep) {
    val p = Pal
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.paper).border(1.5.dp, p.line, RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(s.h, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            if (s.d.isNotEmpty()) Text(s.d, color = p.muted, fontSize = 14.5.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun Banner(icon: String, text: String, fg: Color, bg: Color, border: Color?) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(bg).let { if (border != null) it.border(2.dp, border, shape) else it }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = fg, fontSize = 20.sp)
        Text(text, color = fg, fontSize = 15.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Decision(s: ProtocolStep) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(s.h, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 21.sp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Dark).padding(horizontal = 18.dp, vertical = 11.dp))
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.yes?.let { Branch("כן", it, YesC, Modifier.weight(1f)) }
            s.no?.let { Branch("לא", it, NoC, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Branch(label: String, b: ProtocolBranch, c: Color, modifier: Modifier) {
    val p = Pal
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxHeight().clip(shape).background(p.paper).border(2.dp, c.copy(alpha = .55f), shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c).padding(horizontal = 10.dp, vertical = 2.dp))
        Text(b.h, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
        b.items.forEach { Text("• $it", color = p.ink, fontSize = 13.5.sp, lineHeight = 19.sp) }
    }
}
