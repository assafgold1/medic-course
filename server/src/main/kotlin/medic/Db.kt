package medic

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.HexFormat
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** SQLite storage for users, login tokens and synced progress. */
class Db(path: String) {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$path").apply {
        createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA foreign_keys=ON")
            st.execute(
                """CREATE TABLE IF NOT EXISTS users(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    name TEXT NOT NULL DEFAULT '',
                    pass_hash TEXT NOT NULL,
                    created_at INTEGER NOT NULL)"""
            )
            // Added later: older databases get the columns on first start.
            val cols = st.executeQuery("PRAGMA table_info(users)").use { r -> buildSet { while (r.next()) add(r.getString("name")) } }
            if ("address" !in cols) st.execute("ALTER TABLE users ADD COLUMN address TEXT NOT NULL DEFAULT ''")
            if ("org" !in cols) st.execute("ALTER TABLE users ADD COLUMN org TEXT NOT NULL DEFAULT ''")
            if ("friend_code" !in cols) st.execute("ALTER TABLE users ADD COLUMN friend_code TEXT")
            if ("streak" !in cols) st.execute("ALTER TABLE users ADD COLUMN streak INTEGER NOT NULL DEFAULT 0")
            if ("streak_day" !in cols) st.execute("ALTER TABLE users ADD COLUMN streak_day INTEGER NOT NULL DEFAULT 0")
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS users_friend_code ON users(friend_code)")
            st.execute(
                """CREATE TABLE IF NOT EXISTS sessions(
                    token TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL)"""
            )
            st.execute(
                """CREATE TABLE IF NOT EXISTS progress(
                    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                    data TEXT NOT NULL,
                    updated_at INTEGER NOT NULL)"""
            )
        }
    }
    init {
        conn.createStatement().use { st ->
            // Friendship is mutual: one row each way, so "my friends" is a single lookup.
            st.execute(
                """CREATE TABLE IF NOT EXISTS friends(
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    friend_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY(user_id, friend_id))"""
            )
            // A block is one-way: [user_id] blocked [blocked_id], who can no longer add them back.
            st.execute(
                """CREATE TABLE IF NOT EXISTS blocks(
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    blocked_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY(user_id, blocked_id))"""
            )
            // Reports keep the reported name as it was, so a later rename or deletion doesn't hide what was reported.
            st.execute(
                """CREATE TABLE IF NOT EXISTS reports(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reporter_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
                    reported_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
                    reported_name TEXT NOT NULL,
                    reported_org TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    handled_at INTEGER)"""
            )
        }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    @Synchronized
    fun userCount(): Long = conn.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM users").use { r -> r.next(); r.getLong(1) } }

    @Synchronized
    fun createUser(email: String, password: String, name: String, address: String = "", org: String = ""): UserDto? {
        val exists = conn.prepareStatement("SELECT 1 FROM users WHERE email=?").use { ps ->
            ps.setString(1, email); ps.executeQuery().use { it.next() }
        }
        if (exists) return null
        conn.prepareStatement("INSERT INTO users(email,name,pass_hash,created_at,address,org) VALUES(?,?,?,?,?,?)").use { ps ->
            ps.setString(1, email); ps.setString(2, name); ps.setString(3, hashPassword(password)); ps.setLong(4, System.currentTimeMillis())
            ps.setString(5, address); ps.setString(6, org)
            ps.executeUpdate()
        }
        return findUser(email)
    }

    @Synchronized
    fun checkLogin(email: String, password: String): UserDto? {
        val row = conn.prepareStatement("SELECT id,email,name,address,org,pass_hash FROM users WHERE email=?").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { r -> if (r.next()) Triple(user(r), r.getString(6), Unit) else null }
        } ?: return null
        return if (verifyPassword(password, row.second)) row.first else null
    }

    @Synchronized
    fun newSession(userId: Long): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = HexFormat.of().formatHex(bytes)
        conn.prepareStatement("INSERT INTO sessions(token,user_id,created_at) VALUES(?,?,?)").use { ps ->
            ps.setString(1, token); ps.setLong(2, userId); ps.setLong(3, System.currentTimeMillis()); ps.executeUpdate()
        }
        return token
    }

    @Synchronized
    fun deleteSession(token: String) {
        conn.prepareStatement("DELETE FROM sessions WHERE token=?").use { it.setString(1, token); it.executeUpdate() }
    }

    @Synchronized
    fun userForToken(token: String): UserDto? =
        conn.prepareStatement("SELECT u.id,u.email,u.name,u.address,u.org FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=?").use { ps ->
            ps.setString(1, token)
            ps.executeQuery().use { r -> if (r.next()) user(r) else null }
        }

    /** Updates the profile fields that are not null. */
    @Synchronized
    fun updateProfile(userId: Long, name: String?, address: String?, org: String?): UserDto? {
        listOf("name" to name, "address" to address, "org" to org).forEach { (col, v) ->
            if (v != null) conn.prepareStatement("UPDATE users SET $col=? WHERE id=?").use { it.setString(1, v); it.setLong(2, userId); it.executeUpdate() }
        }
        return findUserById(userId)
    }

    /** Columns 1..5 of a row must be id, email, name, address, org. */
    private fun user(r: java.sql.ResultSet) = UserDto(r.getLong(1), r.getString(2), r.getString(3), address = r.getString(4), org = r.getString(5))

    @Synchronized
    fun checkPassword(userId: Long, password: String): Boolean =
        conn.prepareStatement("SELECT pass_hash FROM users WHERE id=?").use { ps ->
            ps.setLong(1, userId)
            ps.executeQuery().use { r -> r.next() && verifyPassword(password, r.getString(1)) }
        }

    /** Sets a new password and signs out every other device (all sessions except [keepToken]). */
    @Synchronized
    fun changePassword(userId: Long, password: String, keepToken: String) {
        conn.prepareStatement("UPDATE users SET pass_hash=? WHERE id=?").use { it.setString(1, hashPassword(password)); it.setLong(2, userId); it.executeUpdate() }
        conn.prepareStatement("DELETE FROM sessions WHERE user_id=? AND token<>?").use { it.setLong(1, userId); it.setString(2, keepToken); it.executeUpdate() }
    }

    /** Deletes the user; sessions and progress go with it (ON DELETE CASCADE). */
    @Synchronized
    fun deleteUser(userId: Long) {
        conn.prepareStatement("DELETE FROM users WHERE id=?").use { it.setLong(1, userId); it.executeUpdate() }
    }

    @Synchronized
    fun loadProgress(userId: Long): Progress =
        conn.prepareStatement("SELECT data FROM progress WHERE user_id=?").use { ps ->
            ps.setLong(1, userId)
            ps.executeQuery().use { r -> if (r.next()) json.decodeFromString<Progress>(r.getString(1)) else Progress() }
        }

    /** Merges [incoming] into the stored progress (newest answer per question wins) and returns the result. */
    @Synchronized
    fun mergeProgress(userId: Long, incoming: Progress): Progress {
        val merged = merge(loadProgress(userId), incoming)
        conn.prepareStatement(
            "INSERT INTO progress(user_id,data,updated_at) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET data=excluded.data, updated_at=excluded.updated_at"
        ).use { ps ->
            ps.setLong(1, userId); ps.setString(2, json.encodeToString(merged)); ps.setLong(3, merged.updatedAt); ps.executeUpdate()
        }
        return merged
    }

    private fun findUserById(id: Long): UserDto? =
        conn.prepareStatement("SELECT id,email,name,address,org FROM users WHERE id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { r -> if (r.next()) user(r) else null }
        }

    /** Every account with its creation time and stored progress (for the instructors' view). */
    @Synchronized
    fun allUsers(): List<Triple<UserDto, Long, Progress>> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT u.id,u.email,u.name,u.address,u.org,u.created_at,p.data FROM users u LEFT JOIN progress p ON p.user_id=u.id ORDER BY u.name, u.email").use { r ->
                buildList {
                    while (r.next()) add(Triple(
                        user(r), r.getLong(6),
                        r.getString(7)?.let { json.decodeFromString<Progress>(it) } ?: Progress(),
                    ))
                }
            }
        }

    @Synchronized
    fun userExists(email: String): Boolean = findUser(email) != null

    private fun findUser(email: String): UserDto? =
        conn.prepareStatement("SELECT id,email,name,address,org FROM users WHERE email=?").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { r -> if (r.next()) user(r) else null }
        }

    private fun hashPassword(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return "pbkdf2$" + ITERATIONS + "$" + HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(derive(password, salt, ITERATIONS))
    }

    private fun verifyPassword(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val expected = HexFormat.of().parseHex(parts[3])
        val actual = derive(password, HexFormat.of().parseHex(parts[2]), parts[1].toInt())
        return java.security.MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256)).encoded

    /* ---------- friends ---------- */

    /** The user's friend code (6 letters and digits, no look-alikes such as O/0), created on first use. */
    @Synchronized
    fun friendCode(userId: Long): String {
        conn.prepareStatement("SELECT friend_code FROM users WHERE id=?").use { ps ->
            ps.setLong(1, userId); ps.executeQuery().use { r -> if (r.next()) r.getString(1)?.let { return it } }
        }
        while (true) {
            val code = (1..6).map { CODE_CHARS[random.nextInt(CODE_CHARS.length)] }.joinToString("")
            val taken = conn.prepareStatement("SELECT 1 FROM users WHERE friend_code=?").use { ps -> ps.setString(1, code); ps.executeQuery().use { it.next() } }
            if (taken) continue
            conn.prepareStatement("UPDATE users SET friend_code=? WHERE id=?").use { ps -> ps.setString(1, code); ps.setLong(2, userId); ps.executeUpdate() }
            return code
        }
    }

    @Synchronized
    fun userIdByCode(code: String): Long? =
        conn.prepareStatement("SELECT id FROM users WHERE friend_code=?").use { ps ->
            ps.setString(1, code.trim().uppercase()); ps.executeQuery().use { r -> if (r.next()) r.getLong(1) else null }
        }

    @Synchronized
    fun friendIds(userId: Long): List<Long> =
        conn.prepareStatement("SELECT friend_id FROM friends WHERE user_id=? ORDER BY created_at").use { ps ->
            ps.setLong(1, userId); ps.executeQuery().use { r -> buildList { while (r.next()) add(r.getLong(1)) } }
        }

    /** Makes [a] and [b] friends both ways. */
    @Synchronized
    fun addFriend(a: Long, b: Long) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("INSERT OR IGNORE INTO friends(user_id,friend_id,created_at) VALUES(?,?,?)").use { ps ->
            for ((x, y) in listOf(a to b, b to a)) { ps.setLong(1, x); ps.setLong(2, y); ps.setLong(3, now); ps.executeUpdate() }
        }
    }

    @Synchronized
    fun removeFriend(a: Long, b: Long) {
        conn.prepareStatement("DELETE FROM friends WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)").use { ps ->
            ps.setLong(1, a); ps.setLong(2, b); ps.setLong(3, b); ps.setLong(4, a); ps.executeUpdate()
        }
    }

    /** [a] blocks [b]: the friendship ends both ways and [b] can't add [a] again. Adding [b] by code later lifts the block. */
    @Synchronized
    fun block(a: Long, b: Long) {
        removeFriend(a, b)
        conn.prepareStatement("INSERT OR IGNORE INTO blocks(user_id,blocked_id,created_at) VALUES(?,?,?)").use { ps ->
            ps.setLong(1, a); ps.setLong(2, b); ps.setLong(3, System.currentTimeMillis()); ps.executeUpdate()
        }
    }

    @Synchronized
    fun unblock(a: Long, b: Long) {
        conn.prepareStatement("DELETE FROM blocks WHERE user_id=? AND blocked_id=?").use { ps -> ps.setLong(1, a); ps.setLong(2, b); ps.executeUpdate() }
    }

    /** Whether [a] has blocked [b]. */
    @Synchronized
    fun isBlocked(a: Long, b: Long): Boolean =
        conn.prepareStatement("SELECT 1 FROM blocks WHERE user_id=? AND blocked_id=?").use { ps ->
            ps.setLong(1, a); ps.setLong(2, b); ps.executeQuery().use { it.next() }
        }

    /** Stores a report of [reported] by [reporter] and returns its id. */
    @Synchronized
    fun addReport(reporter: Long, reported: Long, reason: String): Long {
        val (name, org) = conn.prepareStatement("SELECT name,org FROM users WHERE id=?").use { ps ->
            ps.setLong(1, reported); ps.executeQuery().use { r -> if (r.next()) r.getString(1) to r.getString(2) else "" to "" }
        }
        return conn.prepareStatement(
            "INSERT INTO reports(reporter_id,reported_id,reported_name,reported_org,reason,created_at) VALUES(?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS,
        ).use { ps ->
            ps.setLong(1, reporter); ps.setLong(2, reported); ps.setString(3, name); ps.setString(4, org)
            ps.setString(5, reason); ps.setLong(6, System.currentTimeMillis()); ps.executeUpdate()
            ps.generatedKeys.use { it.next(); it.getLong(1) }
        }
    }

    /** Reports not yet handled, oldest first. */
    @Synchronized
    fun openReports(): List<ReportRow> =
        conn.prepareStatement("SELECT id,reporter_id,reported_id,reported_name,reported_org,reason,created_at FROM reports WHERE handled_at IS NULL ORDER BY created_at").use { ps ->
            ps.executeQuery().use { r ->
                buildList { while (r.next()) add(ReportRow(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getString(5), r.getString(6), r.getLong(7))) }
            }
        }

    /** The streak lives on the device; the app reports it with the local day it was counted on. */
    @Synchronized
    fun setStreak(userId: Long, streak: Int, day: Long) {
        conn.prepareStatement("UPDATE users SET streak=?, streak_day=? WHERE id=?").use { ps ->
            ps.setInt(1, streak.coerceIn(0, 100_000)); ps.setLong(2, day); ps.setLong(3, userId); ps.executeUpdate()
        }
    }

    /** Name, organisation, reported streak and progress for each of [ids]. */
    @Synchronized
    fun friendRows(ids: List<Long>): List<FriendRow> {
        if (ids.isEmpty()) return emptyList()
        val marks = ids.joinToString(",") { "?" }
        return conn.prepareStatement(
            "SELECT u.id,u.name,u.org,u.streak,u.streak_day,p.data FROM users u LEFT JOIN progress p ON p.user_id=u.id WHERE u.id IN ($marks)"
        ).use { ps ->
            ids.forEachIndexed { i, id -> ps.setLong(i + 1, id) }
            ps.executeQuery().use { r ->
                buildList {
                    while (r.next()) add(FriendRow(
                        r.getLong(1), r.getString(2), r.getString(3), r.getInt(4), r.getLong(5),
                        r.getString(6)?.let { json.decodeFromString<Progress>(it) } ?: Progress(),
                    ))
                }
            }
        }
    }

    companion object {
        private const val ITERATIONS = 120_000

        fun merge(a: Progress, b: Progress): Progress {
            val resetAt = maxOf(a.resetAt, b.resetAt)
            val answers = HashMap<String, Answer>()
            (a.answers.entries + b.answers.entries).forEach { (id, ans) ->
                val cur = answers[id]
                if (ans.t > resetAt && (cur == null || ans.t >= cur.t)) answers[id] = ans
            }
            val attempts = (a.attempts + b.attempts).filter { it.at > resetAt }
                .associateBy { it.id }.values.sortedByDescending { it.at }.take(100)
            return Progress(answers, attempts, maxOf(a.updatedAt, b.updatedAt, System.currentTimeMillis()), resetAt)
        }
    }
}

data class ReportRow(val id: Long, val reporterId: Long, val reportedId: Long, val name: String, val org: String, val reason: String, val at: Long)

data class FriendRow(val id: Long, val name: String, val org: String, val streak: Int, val streakDay: Long, val progress: Progress)

private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
