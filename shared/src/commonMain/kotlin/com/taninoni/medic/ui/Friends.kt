package com.taninoni.medic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.FriendDto
import com.taninoni.medic.data.orgLabel
import com.taninoni.medic.shareText
import kotlinx.coroutines.launch

/** Avatar colours, picked by user id so a friend keeps the same one everywhere. */
private val AvatarColors = listOf(
    Color(0xFFF26922), Color(0xFF2E7DD7), Color(0xFF1FA36B), Color(0xFF8E5BD8),
    Color(0xFFE0435A), Color(0xFF0FA3B1), Color(0xFFD99A1E), Color(0xFF5C6BC0),
)

/** A round avatar with the first letter of the name. */
@Composable
private fun Avatar(f: FriendDto, size: Dp, ring: Color = Color.Transparent) {
    val c = AvatarColors[(f.id % AvatarColors.size).toInt()]
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.copy(alpha = .85f), c)))
            .border(if (ring == Color.Transparent) 0.dp else 2.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(f.name.trim().take(1).ifEmpty { "?" }, color = Color.White, fontSize = (size.value * .42f).sp, fontWeight = FontWeight.Black)
    }
}

/** Home-screen entry: the top friends' faces and where you stand this week. */
@Composable
fun FriendsCard(model: AppModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Pal
    LaunchedEffect(Unit) { model.loadFriends() }
    val board = model.friends?.friends.orEmpty()
    val others = board.filter { !it.me }
    val myRank = board.indexOfFirst { it.me } + 1
    Row(
        modifier.fillMaxWidth().bouncy(onClick = onClick).deepGlass(RoundedCornerShape(26.dp), elevation = 12.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("חברים", color = p.ink, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (others.isEmpty()) "מזמינים חברים ומתחרים בתרגול" else model.g("אתה", "את") + " במקום $myRank מתוך ${board.size} השבוע",
                color = p.muted, fontSize = 13.sp,
            )
        }
        if (others.isEmpty()) IconTile(Icons.special, p.blue)
        else Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
            board.take(4).forEach { Avatar(it, 40.dp, ring = if (p.dark) Color(0xFF1F1B1A) else Color.White) }
        }
        Chevron()
    }
}

@Composable
fun FriendsScreen(model: AppModel, nav: Nav) {
    val p = Pal
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.loadFriends() }
    val data = model.friends
    val board = data?.friends.orEmpty()
    val code = rememberTextFieldState()
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Tapping a friend opens [acting]; from there one of the three confirmations.
    var acting by remember { mutableStateOf<FriendDto?>(null) }
    var removing by remember { mutableStateOf<FriendDto?>(null) }
    var blocking by remember { mutableStateOf<FriendDto?>(null) }
    var reporting by remember { mutableStateOf<FriendDto?>(null) }
    var boardMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    GlassPage("חברים", onBack = { nav.back() }, subtitle = "לומדים יחד, מתחרים כל שבוע") {
        // My code, big and easy to read out loud, with a share button.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF8A3D), Brand, Color(0xFFDD5512))))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("הקוד שלך", color = Color.White.copy(alpha = .85f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                data?.code?.toList()?.joinToString(" ") ?: "· · · · · ·", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black,
                letterSpacing = 2.sp, textAlign = TextAlign.Center,
            )
            Text("שלחו לחברים מהקורס, והם יוסיפו אתכם בקוד", color = Color.White.copy(alpha = .85f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Row(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color.White)
                    .bouncy(enabled = data != null) {
                        data?.let { shareText("בואו נתרגל יחד במסלול חובש! הקוד שלי להוספת חבר: ${it.code}") }
                    }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.share, null, tint = Brand, modifier = Modifier.size(19.dp))
                Text("שיתוף הקוד", color = Brand, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Add a friend by their code.
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 6.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("הוספת חבר", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Field("הקוד של החבר", code, KeyboardType.Ascii)
            message?.let { (ok, text) -> Text(text, color = if (ok) p.good else p.bad, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
            PrimaryButton(if (busy) "מוסיף…" else "הוספה", Modifier.fillMaxWidth(), enabled = !busy && code.text.trim().length >= 6, icon = Icons.special) {
                busy = true
                scope.launch {
                    val before = model.friends?.friends?.size ?: 0
                    val err = model.addFriend(code.text.toString())
                    busy = false
                    message = if (err != null) false to err
                    else if ((model.friends?.friends?.size ?: 0) > before) true to "נוסף! עכשיו אתם חברים 🎉"
                    else true to "אתם כבר חברים"
                    if (err == null) code.edit { replace(0, length, "") }
                }
            }
        }

        // The weekly board.
        SectionTitle("הטבלה השבועית")
        when {
            data == null -> Text(model.friendsError ?: "טוען…", color = p.muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
            board.size <= 1 -> Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 4.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("👋", fontSize = 34.sp)
                Text("עוד אין חברים בטבלה", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("שתפו את הקוד שלכם, וכל מי שיוסיף אותו יופיע כאן", color = p.muted, fontSize = 13.5.sp, textAlign = TextAlign.Center)
            }
            else -> Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), elevation = 6.dp).padding(vertical = 6.dp)) {
                board.forEachIndexed { i, f ->
                    if (i > 0) Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(p.hair))
                    BoardRow(i + 1, f, model) { if (!f.me) acting = f }
                }
            }
        }
        boardMessage?.let { (ok, text) ->
            Text(text, color = if (ok) p.good else p.bad, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "החברים רואים את השם, הארגון, הרצף, מספר השאלות השבוע ואחוז השליטה שלך. נגיעה בחבר מאפשרת להסיר, לחסום או לדווח עליו.",
            color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        BottomSpace()
    }

    acting?.let { f ->
        FriendActions(
            f,
            onRemove = { acting = null; removing = f },
            onBlock = { acting = null; blocking = f },
            onReport = { acting = null; reporting = f },
            onDismiss = { acting = null },
        )
    }
    removing?.let { f ->
        ConfirmDialog(
            "להסיר את ${f.name}?", "תוסרו זה מרשימת החברים של זה. אפשר להוסיף שוב בקוד.", "הסרה", destructive = true,
            onConfirm = { removing = null; scope.launch { model.removeFriend(f.id) } },
            onDismiss = { removing = null },
        )
    }
    blocking?.let { f ->
        ConfirmDialog(
            "לחסום את ${f.name}?", "${f.name} יוסר מהטבלה שלך, לא יראה יותר את הנתונים שלך ולא יוכל להוסיף אותך שוב. כדי לבטל את החסימה, מוסיפים אותו שוב בקוד שלו.",
            "חסימה", destructive = true,
            onConfirm = {
                blocking = null
                scope.launch {
                    val err = model.blockFriend(f.id)
                    boardMessage = if (err != null) false to err else true to "${f.name} נחסם"
                }
            },
            onDismiss = { blocking = null },
        )
    }
    reporting?.let { f ->
        ReportDialog(
            f,
            onSend = { reason ->
                reporting = null
                scope.launch {
                    val err = model.reportFriend(f.id, reason)
                    boardMessage = if (err != null) false to err else true to "תודה. הדיווח נשלח לבדיקה, ו${f.name} נחסם"
                }
            },
            onDismiss = { reporting = null },
        )
    }
}

/** A glass dialog shell, the same look as [ConfirmDialog]. */
@Composable
private fun SheetDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = Pal
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Column(
                Modifier.widthIn(max = 380.dp).fillMaxWidth()
                    .shadow(30.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(.3f), spotColor = Color.Black.copy(.3f))
                    .clip(RoundedCornerShape(32.dp)).background(p.solid)
                    .border(1.dp, Brush.verticalGradient(listOf(p.rimHi, p.rimLo)), RoundedCornerShape(32.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
    }
}

/** One full-width choice in a [SheetDialog]. */
@Composable
private fun SheetOption(title: String, subtitle: String, color: Color, selected: Boolean = false, onClick: () -> Unit) {
    val p = Pal
    Column(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).bouncy(pressedScale = .985f, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) color.copy(alpha = .14f) else p.hair.copy(alpha = .5f))
            .border(if (selected) 2.dp else 0.dp, if (selected) color else Color.Transparent, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle.isNotEmpty()) Text(subtitle, color = p.muted, fontSize = 13.sp)
    }
}

/** What you can do with a friend: remove, block or report. */
@Composable
private fun FriendActions(f: FriendDto, onRemove: () -> Unit, onBlock: () -> Unit, onReport: () -> Unit, onDismiss: () -> Unit) {
    val p = Pal
    SheetDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(f, 44.dp)
            Text(f.name, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SheetOption("הסרה מהחברים", "אפשר להוסיף שוב בקוד", p.ink, onClick = onRemove)
        SheetOption("חסימה", "לא יוכל להוסיף אותך שוב", p.bad, onClick = onBlock)
        SheetOption("דיווח", "על שם או פרטים פוגעניים או לא הולמים", p.bad, onClick = onReport)
        GhostButton("ביטול", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

/** The reasons a user can be reported for; the key goes to the server. */
private val ReportReasons = listOf(
    "name" to "שם פוגעני או לא הולם",
    "org" to "ארגון או פרטים מטעים",
    "impersonation" to "מתחזה למישהו אחר",
    "other" to "סיבה אחרת",
)

/** Pick a reason (and optionally add details), then send the report; the user is blocked as well. */
@Composable
private fun ReportDialog(f: FriendDto, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val p = Pal
    var reason by remember { mutableStateOf<String?>(null) }
    val details = rememberTextFieldState()
    SheetDialog(onDismiss) {
        Text("דיווח על ${f.name}", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text("הדיווח נשלח לצוות האפליקציה ונבדק תוך 24 שעות. ${f.name} ייחסם אצלך מיד.", color = p.muted, fontSize = 14.sp, lineHeight = 20.sp)
        ReportReasons.forEach { (key, label) -> SheetOption(label, "", p.ink, selected = reason == key) { reason = key } }
        Field("פרטים נוספים (לא חובה)", details, KeyboardType.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("ביטול", Modifier.weight(1f), onClick = onDismiss)
            val ready = reason != null
            Box(
                Modifier.weight(1f).heightIn(min = 54.dp).bouncy(enabled = ready) {
                    reason?.let { r -> onSend(listOf(r, details.text.toString().trim()).filter { it.isNotEmpty() }.joinToString(": ")) }
                }.clip(RoundedCornerShape(50)).background(if (ready) p.bad else p.bad.copy(alpha = .4f)).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("דיווח וחסימה", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        }
    }
}

/** One place on the board: rank, face, name and organisation, then the week's count and the streak. */
@Composable
private fun BoardRow(rank: Int, f: FriendDto, model: AppModel, onClick: () -> Unit) {
    val p = Pal
    val medal = when (rank) { 1 -> Color(0xFFF5B301); 2 -> Color(0xFFA7B1BC); 3 -> Color(0xFFCD7F32); else -> null }
    Row(
        Modifier.fillMaxWidth().bouncy(pressedScale = .985f, onClick = onClick)
            .then(if (f.me) Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(16.dp)).background(Brand.copy(alpha = if (p.dark) .16f else .08f)) else Modifier.padding(horizontal = 6.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (medal != null) Box(Modifier.size(26.dp).clip(CircleShape).background(medal), contentAlignment = Alignment.Center) {
                Text("$rank", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            } else Text("$rank", color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Avatar(f, 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.name, color = p.ink, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (f.me) Text(model.g("אתה", "את"), color = Brand, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                listOfNotNull(f.org.takeIf { it.isNotEmpty() }?.let { orgLabel(it) }, "שליטה ${f.mastery}%").joinToString(" · "),
                color = p.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${f.week}", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("שאלות", color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.flame, null, tint = if (f.streak > 0) Brand else p.muted.copy(alpha = .5f), modifier = Modifier.size(13.dp))
                Text("${f.streak}", color = if (f.streak > 0) Brand else p.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
