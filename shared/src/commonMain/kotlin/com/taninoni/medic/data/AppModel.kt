package com.taninoni.medic.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.taninoni.medic.ReminderItem
import com.taninoni.medic.Reminders
import com.taninoni.medic.SERVER_URL
import com.taninoni.medic.nowMillis
import com.taninoni.medic.utcOffsetMinutes
import com.taninoni.medic.res.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/** App-wide state: content, local progress, account and sync. */
class AppModel(private val scope: CoroutineScope) {
    private val settings = Settings()
    val api = Api { SERVER_URL }

    var content by mutableStateOf<Content?>(null); private set
    var progress by mutableStateOf(Progress()); private set
    var user by mutableStateOf<UserDto?>(null); private set
    /** True right after a sign-in that created a new account (shows a welcome line). */
    var justCreated by mutableStateOf(false); private set
    var syncState by mutableStateOf(SyncState.Idle); private set
    var syncMessage by mutableStateOf(""); private set
    var selectedCats by mutableStateOf(settings.getStringOrNull(K_SEL)?.split(',')?.filter { it.isNotBlank() }?.toSet()); private set
    var questionCount by mutableStateOf(settings.getInt(K_COUNT, 30)); private set

    /* user preferences (Settings screen) */
    var themeMode by mutableStateOf(settings.getString(K_THEME, "system")); private set
    var textScale by mutableStateOf(settings.getFloat(K_TEXT, 1f)); private set
    var examSize by mutableStateOf(settings.getInt(K_EXAM, 30)); private set
    var instantFeedback by mutableStateOf(settings.getBoolean(K_INSTANT, true)); private set
    var soundOn by mutableStateOf(settings.getBoolean(K_SOUND, true)); private set

    /* learning extras: bookmarks, speed-blitz high score, daily study streak */
    var bookmarks by mutableStateOf(settings.getStringOrNull(K_BOOKMARKS)?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()); private set
    var blitzBest by mutableStateOf(settings.getInt(K_BLITZ_BEST, 0)); private set

    /* daily streak, Duolingo style: a day counts once the daily goal is met */
    var dailyGoal by mutableStateOf(settings.getInt(K_GOAL, 10)); private set
    var todayCount by mutableStateOf(0); private set
    /** Local epoch day, refreshed on launch, on each answer and once a minute while the app is open. */
    var today by mutableStateOf(localDay()); private set
    /** Days the goal was met, and days a streak freeze covered. */
    var metDays by mutableStateOf(readDays(K_MET)); private set
    var frozenDays by mutableStateOf(readDays(K_FROZEN)); private set
    var freezes by mutableStateOf(settings.getInt(K_FREEZES, 0)); private set
    var longestStreak by mutableStateOf(settings.getInt(K_LONGEST, 0)); private set
    var reminderOn by mutableStateOf(settings.getBoolean(K_REMIND, false)); private set
    /** Reminder time, minutes after local midnight. */
    var reminderMinute by mutableStateOf(settings.getInt(K_REMIND_AT, 20 * 60)); private set
    var reminderAsked by mutableStateOf(settings.getBoolean(K_REMIND_ASKED, false)); private set
    /** Set when today's goal is reached; the app shows the streak celebration and clears it. */
    var celebration by mutableStateOf<Celebration?>(null); private set
    /** A freeze saved the streak, or the streak was lost. Shown once on the home screen. */
    var streakNotice by mutableStateOf<StreakNotice?>(null); private set

    private var token: String? = settings.getStringOrNull(K_TOKEN)

    enum class SyncState { Idle, Working, Ok, Error }

    suspend fun load() {
        settings.remove("server_url") // address used to be user-editable; it is now SERVER_URL
        progress = settings.getStringOrNull(K_PROGRESS)?.let { runCatching { api.json.decodeFromString<Progress>(it) }.getOrNull() } ?: Progress()
        user = settings.getStringOrNull(K_USER)?.let { runCatching { api.json.decodeFromString<UserDto>(it) }.getOrNull() }
        val cachedText = settings.getStringOrNull(K_CONTENT)
        content = withContext(Dispatchers.Default) {
            // The bundled copy wins after an app update; a newer server copy arrives via refreshContent().
            val bundled = api.json.decodeFromString<Content>(Res.readBytes("files/content.json").decodeToString())
            cachedText?.let { runCatching { api.json.decodeFromString<Content>(it) }.getOrNull() }
                ?.takeIf { settings.getStringOrNull(K_BUNDLED) == bundled.version } ?: bundled
                .also { settings.putString(K_BUNDLED, it.version); settings.remove(K_CONTENT) }
        }
        scope.launch { refreshContent(); refreshUser(); sync() }
        migrateStreak()
        refreshDay()
        scheduleReminders()
    }

    /* ---------- daily streak ---------- */

    private fun localDay(): Long = (nowMillis() + utcOffsetMinutes() * 60_000L).floorDiv(DAY_MS)

    /** Current streak: met days running back from today (or from yesterday while today is still open). Frozen days bridge gaps. */
    val studyStreakDays: Int get() = streakEndingAt(if (today in metDays) today else today - 1)
    val doneToday: Boolean get() = today in metDays
    val streakAtRisk: Boolean get() = !doneToday && studyStreakDays > 0
    val leftToday: Int get() = (dailyGoal - todayCount).coerceAtLeast(0)

    private fun streakEndingAt(day: Long): Int {
        var d = day
        var n = 0
        while (d in metDays || d in frozenDays) { if (d in metDays) n++; d-- }
        return n
    }

    private fun readDays(key: String): Set<Long> =
        settings.getStringOrNull(key)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty()

    private fun saveDays(key: String, days: Set<Long>) =
        settings.putString(key, days.sorted().takeLast(800).joinToString(","))

    /** Moves to a new day if midnight passed, spends freezes on missed days, and reloads today's count. */
    fun refreshDay() {
        val now = localDay()
        if (now != today) today = now
        todayCount = if (settings.getLong(K_COUNT_DAY, -1L) == today) settings.getInt(K_TODAY_COUNT, 0) else 0
        checkMissedDays()
    }

    private fun migrateStreak() {
        // Older builds kept only a counter and the last study day (UTC). Turn that into met days once.
        if (settings.hasKey(K_MET)) return
        val last = settings.getLong(K_LAST_DAY, -1L)
        val count = settings.getInt(K_STREAK_DAYS, 0)
        if (last >= 0 && count > 0) {
            metDays = ((last - count + 1)..last).toSet()
            if (!settings.hasKey(K_COUNT_DAY)) settings.putLong(K_COUNT_DAY, last)
            longestStreak = maxOf(longestStreak, count); settings.putInt(K_LONGEST, longestStreak)
        }
        saveDays(K_MET, metDays)
    }

    private fun checkMissedDays() {
        val last = (metDays + frozenDays).maxOrNull() ?: return
        val missed = (today - 1 - last).toInt()
        if (missed <= 0) return
        val before = streakEndingAt(last)
        if (before == 0) return
        if (missed <= freezes) {
            frozenDays = frozenDays + ((last + 1)..(today - 1)); saveDays(K_FROZEN, frozenDays)
            freezes -= missed; settings.putInt(K_FREEZES, freezes)
            streakNotice = StreakNotice.Frozen(missed, before)
        } else if (settings.getLong(K_LOST_FOR, -1L) != last) {
            settings.putLong(K_LOST_FOR, last)
            if (before >= 2) streakNotice = StreakNotice.Lost(before)
        }
    }

    private fun countAnswerToday() {
        refreshDay()
        todayCount++
        settings.putLong(K_COUNT_DAY, today); settings.putInt(K_TODAY_COUNT, todayCount)
        if (!doneToday && todayCount >= dailyGoal) completeToday()
    }

    private fun completeToday() {
        metDays = metDays + today; saveDays(K_MET, metDays)
        val streak = studyStreakDays
        val record = streak > longestStreak
        if (record) { longestStreak = streak; settings.putInt(K_LONGEST, streak) }
        val earned = streak % 7 == 0 && freezes < MAX_FREEZES
        if (earned) { freezes++; settings.putInt(K_FREEZES, freezes) }
        celebration = Celebration(streak, earned, record && streak > 1)
        scheduleReminders()
    }

    /** The streak lives on the device; a different account starts its own. */
    private fun resetStreak() {
        metDays = emptySet(); frozenDays = emptySet(); freezes = 0; longestStreak = 0; todayCount = 0
        saveDays(K_MET, metDays); saveDays(K_FROZEN, frozenDays)
        settings.putInt(K_FREEZES, 0); settings.putInt(K_LONGEST, 0); settings.putInt(K_TODAY_COUNT, 0)
        celebration = null; streakNotice = null
        scheduleReminders()
    }

    fun dismissCelebration() { celebration = null }
    fun dismissStreakNotice() { streakNotice = null }

    fun setGoal(goal: Int) {
        dailyGoal = goal; settings.putInt(K_GOAL, goal)
        if (!doneToday && todayCount >= goal) completeToday() else scheduleReminders()
    }

    /** Turning reminders on asks for notification permission first; they stay off if it is refused. */
    fun setReminder(on: Boolean) {
        reminderAsked = true; settings.putBoolean(K_REMIND_ASKED, true)
        if (!on) { reminderOn = false; settings.putBoolean(K_REMIND, false); scheduleReminders(); return }
        Reminders.requestPermission { granted ->
            scope.launch {
                reminderOn = granted; settings.putBoolean(K_REMIND, granted)
                scheduleReminders()
            }
        }
    }

    fun setReminderTime(minute: Int) {
        reminderMinute = ((minute % (24 * 60)) + 24 * 60) % (24 * 60); settings.putInt(K_REMIND_AT, reminderMinute)
        scheduleReminders()
    }

    /**
     * Plans the next week of reminders (replacing any already set): one a day at the chosen time,
     * none today once the goal is met, plus a last call at 22:30 while a streak is at risk.
     */
    fun scheduleReminders() {
        if (!reminderOn) { Reminders.schedule(emptyList()); return }
        val offset = utcOffsetMinutes() * 60_000L
        val now = nowMillis()
        fun at(day: Long, minute: Int) = day * DAY_MS + minute * 60_000L - offset
        val streak = studyStreakDays
        val items = mutableListOf<ReminderItem>()
        for (i in 0..6) {
            if (i == 0 && doneToday) continue
            val time = at(today + i, reminderMinute)
            if (time < now + 60_000) continue
            val (title, body) = when {
                i == 0 && streak > 0 -> "🔥 הרצף שלך בסכנה" to "רצף של $streak ימים מחכה לך. עוד $leftToday שאלות ושמרת עליו."
                i == 0 -> "הגיע הזמן לתרגל 🩺" to "$dailyGoal שאלות ביום שומרות את החומר טרי. נתחיל?"
                i % 3 == 1 -> "🔥 שומרים על הרצף?" to "עוד יום, עוד $dailyGoal שאלות. ממשיכים מאיפה שעצרת."
                i % 3 == 2 -> "חובש טוב מתרגל כל יום" to "כמה דקות של תרגול, והרצף ממשיך לבעור."
                else -> "הגיע הזמן לתרגל 🩺" to "$dailyGoal שאלות ביום שומרות את החומר טרי."
            }
            items += ReminderItem(100 + i, time, title, body)
        }
        val lastCall = at(today, LAST_CALL_MINUTE)
        if (!doneToday && streak > 0 && lastCall > now + 60_000 && reminderMinute < LAST_CALL_MINUTE) {
            items += ReminderItem(200, lastCall, "⏰ הזדמנות אחרונה להיום", "הרצף של $streak ימים נגמר בחצות. שתי דקות וזה מאחוריך.")
        }
        Reminders.schedule(items)
    }

    /* ---------- content ---------- */

    val categories get() = content?.categories.orEmpty()
    val topics get() = content?.topics.orEmpty()
    val passMark get() = content?.passMark ?: 80
    private var itemsFor: Content? = null
    private var itemsCache: List<QItem> = emptyList()
    val allItems: List<QItem>
        get() {
            val c = content
            if (c !== itemsFor) { itemsCache = c?.topics.orEmpty().flatMap { t -> t.questions.map { QItem(it, t) } }; itemsFor = c }
            return itemsCache
        }

    fun topic(id: String) = topics.firstOrNull { it.id == id }
    fun category(id: String) = categories.firstOrNull { it.id == id }
    fun itemsOf(cat: String) = allItems.filter { it.cat == cat }

    suspend fun refreshContent() {
        runCatching { api.content() }.onSuccess { fresh ->
            if (fresh.version != content?.version) {
                content = fresh
                settings.putString(K_CONTENT, api.json.encodeToString(fresh))
            }
        }
    }

    /* ---------- progress ---------- */

    fun isAnswered(id: String) = progress.answers.containsKey(id)
    fun isCorrect(id: String) = progress.answers[id]?.v == 1
    fun mistakes() = allItems.filter { progress.answers[it.id]?.v == 0 }

    /** Percent of [items] whose latest answer was correct. */
    fun mastery(items: List<QItem>): Int =
        if (items.isEmpty()) 0 else items.count { isCorrect(it.id) } * 100 / items.size

    fun seen(items: List<QItem>) = items.any { isAnswered(it.id) }

    fun recordAnswer(id: String, correct: Boolean) {
        progress = progress.copy(answers = progress.answers + (id to Answer(if (correct) 1 else 0, nowMillis())), updatedAt = nowMillis())
        saveProgress()
        countAnswerToday()
    }

    fun isBookmarked(id: String) = id in bookmarks

    fun toggleBookmark(id: String) {
        bookmarks = if (id in bookmarks) bookmarks - id else bookmarks + id
        settings.putString(K_BOOKMARKS, bookmarks.joinToString(","))
    }

    fun bookmarkedItems(): List<QItem> = allItems.filter { it.id in bookmarks }

    /** Records a 60-second blitz score and returns true if it beat the previous record. */
    fun recordBlitzScore(score: Int): Boolean {
        if (score <= blitzBest) return false
        blitzBest = score
        settings.putInt(K_BLITZ_BEST, score)
        return true
    }

    fun recordAttempt(title: String, kind: String, log: List<LogEntry>) {
        val right = log.count { it.ok }
        val byCat = log.groupBy { it.item.cat }.mapValues { (_, l) -> listOf(l.count { it.ok }, l.size) }
        val attempt = Attempt(
            id = "${nowMillis()}-${(0..99999).random()}", title = title, kind = kind,
            total = log.size, right = right, pct = if (log.isEmpty()) 0 else right * 100 / log.size,
            at = nowMillis(), byCategory = byCat,
        )
        progress = progress.copy(attempts = (listOf(attempt) + progress.attempts).take(100), updatedAt = nowMillis())
        saveProgress()
        scope.launch { sync() }
    }

    private fun saveProgress() = settings.putString(K_PROGRESS, api.json.encodeToString(progress))

    /** Picks [n] questions spread evenly over [cats]. */
    fun pick(cats: Collection<String>, n: Int, onlyWrong: Boolean = false): List<QItem> {
        val pools = cats.map { c -> itemsOf(c).filter { !onlyWrong || progress.answers[it.id]?.v == 0 }.shuffled().toMutableList() }
            .filter { it.isNotEmpty() }
        val out = mutableListOf<QItem>()
        var i = 0
        while (out.size < n && pools.any { it.isNotEmpty() }) {
            val p = pools[i % pools.size]
            if (p.isNotEmpty()) out += p.removeAt(p.lastIndex)
            i++
        }
        return out.shuffled()
    }

    /**
     * Smart practice: questions most worth reviewing now. Wrong answers first (the most recent mistakes
     * lead), then questions never answered, then correct answers that are getting old (due for review).
     * At most a third of a run comes from any single topic so one weak topic doesn't take over.
     */
    fun smartPick(n: Int): List<QItem> {
        val now = nowMillis()
        val day = 86_400_000.0
        fun score(it: QItem): Double {
            val a = progress.answers[it.id] ?: return 2.0 + kotlin.random.Random.nextDouble() * 0.5
            val ageDays = (now - a.t) / day
            return if (a.v == 0) 3.0 + 1.0 / (1.0 + ageDays) else minOf(1.9, ageDays / 14.0)
        }
        val ranked = allItems.map { it to score(it) }.sortedByDescending { it.second }.map { it.first }
        val perTopic = mutableMapOf<String, Int>()
        val cap = maxOf(3, n / 3)
        val out = mutableListOf<QItem>()
        for (q in ranked) {
            if (out.size >= n) break
            val c = perTopic.getOrElse(q.topic.id) { 0 }
            if (c < cap) { out += q; perTopic[q.topic.id] = c + 1 }
        }
        return out.shuffled()
    }

    /** Number of questions due now: wrong ones plus correct answers older than two weeks. */
    fun dueCount(): Int {
        val old = nowMillis() - 14 * 86_400_000L
        return progress.answers.count { (id, a) -> a.v == 0 || a.t < old }.coerceAtMost(allItems.size)
    }

    /** The last topic the user opened, for "continue" on the home screen. */
    var lastTopicId by mutableStateOf(settings.getStringOrNull(K_LAST_TOPIC)); private set
    fun openedTopic(id: String) { if (lastTopicId != id) { lastTopicId = id; settings.putString(K_LAST_TOPIC, id) } }

    fun setSelection(cats: Set<String>) { selectedCats = cats; settings.putString(K_SEL, cats.joinToString(",")) }
    fun setCount(n: Int) { questionCount = n; settings.putInt(K_COUNT, n) }
    fun updateTheme(mode: String) { themeMode = mode; settings.putString(K_THEME, mode) }
    fun updateTextScale(scale: Float) { textScale = scale; settings.putFloat(K_TEXT, scale) }
    fun updateExamSize(n: Int) { examSize = n; settings.putInt(K_EXAM, n) }
    fun updateInstantFeedback(on: Boolean) { instantFeedback = on; settings.putBoolean(K_INSTANT, on) }
    fun updateSound(on: Boolean) { soundOn = on; settings.putBoolean(K_SOUND, on) }

    /** Wipes answers and attempts. The reset time syncs, so other devices drop their older data too. */
    fun resetProgress() {
        progress = Progress(updatedAt = nowMillis(), resetAt = nowMillis())
        saveProgress()
        scope.launch { sync() }
    }

    /* ---------- account & sync ---------- */


    /** Returns true when a new account was created. */
    suspend fun login(email: String, password: String) {
        val r = api.login(email.trim(), password)
        justCreated = false
        signIn(r)
    }

    suspend fun register(name: String, email: String, password: String, address: String, org: String) {
        val r = api.register(email.trim(), password, name.trim(), address, org.trim())
        justCreated = true
        signIn(r)
    }

    suspend fun students() = api.students(requireToken())
    suspend fun student(id: Long) = api.student(requireToken(), id)

    private suspend fun signIn(r: AuthResponse) {
        // Progress on this device belongs to whoever used it before; never mix it into another account.
        if (settings.getLongOrNull(K_OWNER) != r.user.id) {
            progress = Progress(); saveProgress(); settings.putLong(K_OWNER, r.user.id)
            resetStreak()
        }
        token = r.token; user = r.user
        settings.putString(K_TOKEN, r.token)
        settings.putString(K_USER, api.json.encodeToString(r.user))
        sync()
    }

    private fun requireToken() = token ?: throw ApiException("צריך להתחבר")

    suspend fun updateName(name: String) = updateProfile(name = name.trim())

    /** Saves the profile fields that are not null. */
    suspend fun updateProfile(name: String? = null, address: String? = null, org: String? = null) {
        val u = api.updateProfile(requireToken(), name, address, org?.trim())
        user = u
        settings.putString(K_USER, api.json.encodeToString(u))
    }

    /** Reloads the account from the server, so profile or role changes made elsewhere show up. */
    private suspend fun refreshUser() {
        val t = token ?: return
        runCatching { api.me(t) }.onSuccess { u -> user = u; settings.putString(K_USER, api.json.encodeToString(u)) }
    }

    /** Picks the masculine or feminine form of a phrase, by how the user asked to be addressed. */
    fun g(m: String, f: String) = if (user?.address == "f") f else m

    /** Older accounts were created before sign-up asked how to address the user and where they're from. */
    val profileIncomplete get() = user?.let { it.address.isEmpty() || it.org.isEmpty() } ?: false

    /** Other devices are signed out by the server; this one stays signed in. */
    suspend fun changePassword(current: String, new: String) = api.changePassword(requireToken(), current, new)

    /** Deletes the account and its synced data on the server. Progress on this device is kept. */
    /** Deletes the account on the server and the progress on this device; a fresh automatic account follows. */
    suspend fun deleteAccount(password: String) {
        api.deleteAccount(requireToken(), password)
        token = null; user = null
        settings.remove(K_TOKEN); settings.remove(K_USER)
        clearLocal()
    }

    fun logout() {
        val t = token
        token = null; user = null
        settings.remove(K_TOKEN); settings.remove(K_USER)
        syncState = SyncState.Idle; syncMessage = ""
        clearLocal()
        if (t != null) scope.launch { api.logout(t) }
    }

    /** After sign-out or deletion the next person on this device starts clean (their data comes from the server). */
    private fun clearLocal() {
        progress = Progress(); saveProgress(); settings.remove(K_OWNER)
        syncState = SyncState.Idle; syncMessage = ""
    }

    suspend fun sync() {
        val t = token ?: return
        syncState = SyncState.Working
        runCatching { api.syncProgress(t, progress) }
            .onSuccess { merged ->
                progress = merged; saveProgress()
                syncState = SyncState.Ok; syncMessage = "סונכרן"
            }
            .onFailure {
                syncState = SyncState.Error
                syncMessage = (it as? ApiException)?.message ?: "אין חיבור לשרת"
                if (it is ApiException && it.message == "צריך להתחבר") logout()
            }
    }

    private companion object {
        const val K_CONTENT = "content"
        const val K_BUNDLED = "bundled_version"
        const val K_PROGRESS = "progress"
        const val K_TOKEN = "token"
        const val K_OWNER = "progress_owner"
        const val K_USER = "user"
        const val K_SEL = "selected_cats"
        const val K_COUNT = "question_count"
        const val K_THEME = "theme"
        const val K_TEXT = "text_scale"
        const val K_EXAM = "exam_size"
        const val K_INSTANT = "instant_feedback"
        const val K_SOUND = "sound"
        const val K_BOOKMARKS = "bookmarks"
        const val K_BLITZ_BEST = "blitz_best"
        const val K_LAST_TOPIC = "last_topic"
        const val K_LAST_DAY = "last_study_day"
        const val K_TODAY_COUNT = "today_count"
        const val K_STREAK_DAYS = "study_streak_days"
        const val K_COUNT_DAY = "today_count_day"
        const val K_GOAL = "daily_goal"
        const val K_MET = "streak_met_days"
        const val K_FROZEN = "streak_frozen_days"
        const val K_FREEZES = "streak_freezes"
        const val K_LONGEST = "streak_longest"
        const val K_LOST_FOR = "streak_lost_for"
        const val K_REMIND = "reminder_on"
        const val K_REMIND_AT = "reminder_minute"
        const val K_REMIND_ASKED = "reminder_asked"
        const val DAY_MS = 86_400_000L
        const val MAX_FREEZES = 2
        const val LAST_CALL_MINUTE = 22 * 60 + 30
    }
}

/** Today's goal was just met: the streak now stands at [streak]. */
data class Celebration(val streak: Int, val freezeEarned: Boolean, val record: Boolean)

sealed interface StreakNotice {
    /** [days] missed days were covered by freezes, keeping a streak of [streak]. */
    data class Frozen(val days: Int, val streak: Int) : StreakNotice
    /** A streak of [days] ended. */
    data class Lost(val days: Int) : StreakNotice
}
