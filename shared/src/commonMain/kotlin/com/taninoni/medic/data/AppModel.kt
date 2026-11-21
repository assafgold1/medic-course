package com.taninoni.medic.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.taninoni.medic.SERVER_URL
import com.taninoni.medic.nowMillis
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
    var todayCount by mutableStateOf(0); private set
    var studyStreakDays by mutableStateOf(settings.getInt(K_STREAK_DAYS, 0)); private set
    val dailyGoal = 20

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
        scope.launch { refreshContent(); sync() }
        refreshDailyStats()
    }

    private fun currentDay(): Long = nowMillis() / 86_400_000L

    private fun refreshDailyStats() {
        val today = currentDay()
        val savedDay = settings.getLong(K_LAST_DAY, -1L)
        todayCount = if (savedDay == today) settings.getInt(K_TODAY_COUNT, 0) else 0
        if (savedDay in 0 until (today - 1)) {
            studyStreakDays = 0
            settings.putInt(K_STREAK_DAYS, 0)
        }
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
        val today = currentDay()
        val savedDay = settings.getLong(K_LAST_DAY, -1L)
        if (savedDay != today) {
            val nextStreak = if (savedDay == today - 1) studyStreakDays + 1 else 1
            studyStreakDays = nextStreak
            settings.putInt(K_STREAK_DAYS, nextStreak)
            settings.putLong(K_LAST_DAY, today)
            todayCount = 1
        } else {
            todayCount++
        }
        settings.putInt(K_TODAY_COUNT, todayCount)
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

    suspend fun register(name: String, email: String, password: String) {
        val r = api.register(email.trim(), password, name.trim())
        justCreated = true
        signIn(r)
    }

    suspend fun students() = api.students(requireToken())
    suspend fun student(id: Long) = api.student(requireToken(), id)

    private suspend fun signIn(r: AuthResponse) {
        // Progress on this device belongs to whoever used it before; never mix it into another account.
        if (settings.getLongOrNull(K_OWNER) != r.user.id) { progress = Progress(); saveProgress(); settings.putLong(K_OWNER, r.user.id) }
        token = r.token; user = r.user
        settings.putString(K_TOKEN, r.token)
        settings.putString(K_USER, api.json.encodeToString(r.user))
        sync()
    }

    private fun requireToken() = token ?: throw ApiException("צריך להתחבר")

    suspend fun updateName(name: String) {
        val u = api.updateProfile(requireToken(), name.trim())
        user = u
        settings.putString(K_USER, api.json.encodeToString(u))
    }

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
        const val DAILY_GOAL = 20
    }
}
