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
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    @Synchronized
    fun userCount(): Long = conn.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM users").use { r -> r.next(); r.getLong(1) } }

    @Synchronized
    fun createUser(email: String, password: String, name: String): UserDto? {
        val exists = conn.prepareStatement("SELECT 1 FROM users WHERE email=?").use { ps ->
            ps.setString(1, email); ps.executeQuery().use { it.next() }
        }
        if (exists) return null
        conn.prepareStatement("INSERT INTO users(email,name,pass_hash,created_at) VALUES(?,?,?,?)").use { ps ->
            ps.setString(1, email); ps.setString(2, name); ps.setString(3, hashPassword(password)); ps.setLong(4, System.currentTimeMillis())
            ps.executeUpdate()
        }
        return findUser(email)
    }

    @Synchronized
    fun checkLogin(email: String, password: String): UserDto? {
        val row = conn.prepareStatement("SELECT id,email,name,pass_hash FROM users WHERE email=?").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { r -> if (r.next()) Triple(UserDto(r.getLong(1), r.getString(2), r.getString(3)), r.getString(4), Unit) else null }
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
        conn.prepareStatement("SELECT u.id,u.email,u.name FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=?").use { ps ->
            ps.setString(1, token)
            ps.executeQuery().use { r -> if (r.next()) UserDto(r.getLong(1), r.getString(2), r.getString(3)) else null }
        }

    @Synchronized
    fun updateName(userId: Long, name: String): UserDto? {
        conn.prepareStatement("UPDATE users SET name=? WHERE id=?").use { it.setString(1, name); it.setLong(2, userId); it.executeUpdate() }
        return findUserById(userId)
    }

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
        conn.prepareStatement("SELECT id,email,name FROM users WHERE id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { r -> if (r.next()) UserDto(r.getLong(1), r.getString(2), r.getString(3)) else null }
        }

    /** Every account with its creation time and stored progress (for the instructors' view). */
    @Synchronized
    fun allUsers(): List<Triple<UserDto, Long, Progress>> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT u.id,u.email,u.name,u.created_at,p.data FROM users u LEFT JOIN progress p ON p.user_id=u.id ORDER BY u.name, u.email").use { r ->
                buildList {
                    while (r.next()) add(Triple(
                        UserDto(r.getLong(1), r.getString(2), r.getString(3)), r.getLong(4),
                        r.getString(5)?.let { json.decodeFromString<Progress>(it) } ?: Progress(),
                    ))
                }
            }
        }

    @Synchronized
    fun userExists(email: String): Boolean = findUser(email) != null

    private fun findUser(email: String): UserDto? =
        conn.prepareStatement("SELECT id,email,name FROM users WHERE email=?").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { r -> if (r.next()) UserDto(r.getLong(1), r.getString(2), r.getString(3)) else null }
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
