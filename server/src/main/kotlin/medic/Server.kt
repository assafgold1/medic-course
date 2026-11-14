package medic

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import io.ktor.http.Cookie
import io.ktor.server.plugins.origin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** The website keeps the login token in this HttpOnly cookie; the apps send it as a Bearer header. */
const val TOKEN_COOKIE = "medic_token"

/** At most [SIGNUP_LIMIT] new accounts per client IP per hour. */
private const val SIGNUP_LIMIT = 30
private val signupCounts = ConcurrentHashMap<String, Pair<Long, Int>>()
private fun allowSignup(ip: String): Boolean {
    val hour = System.currentTimeMillis() / 3_600_000
    val next = signupCounts.compute(ip) { _, cur -> if (cur == null || cur.first != hour) hour to 1 else hour to cur.second + 1 }!!
    return next.second <= SIGNUP_LIMIT
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8787
    val dbPath = System.getenv("MEDIC_DB") ?: "data/medic.db"
    File(dbPath).parentFile?.mkdirs()
    embeddedServer(Netty, port = port, host = System.getenv("HOST") ?: "0.0.0.0") { module(Db(dbPath)) }.start(wait = true)
}

fun Application.module(db: Db, instructors: Set<String> = System.getenv("MEDIC_INSTRUCTORS").orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()) {
    val content = Application::class.java.getResource("/content.json")!!.readText()
    val course = Json { ignoreUnknownKeys = true }.decodeFromString<Course>(content)
    val questionCat = course.topics.flatMap { t -> t.questions.map { it.id to t.cat } }.toMap()
    val catTotals = questionCat.values.groupingBy { it }.eachCount()
    fun UserDto.withRole() = copy(instructor = email.lowercase() in instructors)
    fun summary(u: UserDto, joined: Long, p: Progress): StudentSummary {
        val answered = p.answers.count { it.key in questionCat }
        val correct = p.answers.count { it.key in questionCat && it.value.v == 1 }
        val exams = p.attempts.filter { it.kind == "exam" }
        return StudentSummary(
            u.id, u.name, u.email, answered, correct, if (questionCat.isEmpty()) 0 else correct * 100 / questionCat.size,
            p.attempts.size, exams.count { it.pct >= course.passMark }, exams.maxByOrNull { it.at }?.pct,
            maxOf(p.updatedAt, p.attempts.maxOfOrNull { it.at } ?: 0), joined,
        )
    }
    val version = Json.parseToJsonElement(content).let { (it as kotlinx.serialization.json.JsonObject)["version"].toString().trim('"') }
    val etag = "\"" + MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }.take(16) + "\""

    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("שגיאת שרת"))
        }
    }

    routing {
        get("/health") { call.respond(HealthDto("ok", version, db.userCount())) }

        route("/api") {
            get("/content/version") { call.respond(VersionDto(version)) }
            get("/content") {
                if (call.request.header(HttpHeaders.IfNoneMatch) == etag) return@get call.respond(HttpStatusCode.NotModified)
                call.response.header(HttpHeaders.ETag, etag)
                call.respondText(content, ContentType.Application.Json)
            }

            post("/auth/register") {
                val req = call.receive<RegisterRequest>()
                val email = req.email.trim().lowercase()
                if (req.name.trim().length < 2) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("יש למלא שם מלא, כדי שהמדריכים יזהו אותך"))
                if (!EMAIL.matches(email)) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("כתובת המייל לא תקינה"))
                if (req.password.length < 8) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("הסיסמה צריכה להיות לפחות 8 תווים"))
                val ip = call.request.header("X-Real-IP") ?: call.request.header(HttpHeaders.XForwardedFor)?.substringBefore(',')?.trim() ?: call.request.origin.remoteHost
                if (!allowSignup(ip)) return@post call.respond(HttpStatusCode.TooManyRequests, ErrorDto("יותר מדי חשבונות חדשים, נסה שוב מאוחר יותר"))
                val user = db.createUser(email, req.password, req.name.trim().take(60))
                    ?: return@post call.respond(HttpStatusCode.Conflict, ErrorDto("כבר קיים משתמש עם המייל הזה. עבור ללשונית כניסה"))
                val token = db.newSession(user.id)
                call.setTokenCookie(token)
                call.respond(HttpStatusCode.Created, AuthResponse(token, user.withRole(), created = true))
            }
            post("/auth/login") {
                val req = call.receive<LoginRequest>()
                val email = req.email.trim().lowercase()
                if (!db.userExists(email)) return@post call.respond(HttpStatusCode.Unauthorized, ErrorDto("אין משתמש עם המייל הזה. להרשמה עבור ללשונית הרשמה"))
                val user = db.checkLogin(email, req.password)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorDto("הסיסמה שגויה"))
                val token = db.newSession(user.id)
                call.setTokenCookie(token)
                call.respond(AuthResponse(token, user.withRole()))
            }
            post("/auth/logout") {
                call.bearer()?.let(db::deleteSession)
                call.response.cookies.append(Cookie(TOKEN_COOKIE, "", maxAge = 0, path = "/", httpOnly = true))
                call.respond(HttpStatusCode.NoContent)
            }

            get("/me") {
                val user = call.user(db) ?: return@get
                call.respond(user.withRole())
            }
            put("/me") {
                val user = call.user(db) ?: return@put
                val req = call.receive<UpdateProfileRequest>()
                call.respond((db.updateName(user.id, req.name.trim().take(60)) ?: user).withRole())
            }
            // Instructors: every student's progress.
            get("/instructor/students") {
                val user = call.user(db) ?: return@get
                if (user.email.lowercase() !in instructors) return@get call.respond(HttpStatusCode.Forbidden, ErrorDto("רק למדריכים"))
                call.respond(db.allUsers().filter { it.first.email.lowercase() !in instructors }.map { (u, joined, p) -> summary(u, joined, p) })
            }
            get("/instructor/students/{id}") {
                val user = call.user(db) ?: return@get
                if (user.email.lowercase() !in instructors) return@get call.respond(HttpStatusCode.Forbidden, ErrorDto("רק למדריכים"))
                val id = call.parameters["id"]?.toLongOrNull()
                val row = db.allUsers().firstOrNull { it.first.id == id } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorDto("לא נמצא"))
                val (u, joined, p) = row
                val cats = course.categories.map { c ->
                    val mine = p.answers.filter { questionCat[it.key] == c.id }
                    CategoryProgress(c.id, c.name, mine.count { it.value.v == 1 }, mine.size, catTotals[c.id] ?: 0)
                }
                call.respond(StudentDetail(summary(u, joined, p), cats, p.attempts.sortedByDescending { it.at }))
            }
            post("/me/password") {
                val user = call.user(db) ?: return@post
                val req = call.receive<ChangePasswordRequest>()
                if (!db.checkPassword(user.id, req.current)) return@post call.respond(HttpStatusCode.Forbidden, ErrorDto("הסיסמה הנוכחית שגויה"))
                if (req.new.length < 8) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("הסיסמה צריכה להיות לפחות 8 תווים"))
                db.changePassword(user.id, req.new, call.bearer()!!)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/me/delete") {
                val user = call.user(db) ?: return@post
                val req = call.receive<DeleteAccountRequest>()
                if (!db.checkPassword(user.id, req.password)) return@post call.respond(HttpStatusCode.Forbidden, ErrorDto("הסיסמה שגויה"))
                db.deleteUser(user.id)
                call.response.cookies.append(Cookie(TOKEN_COOKIE, "", maxAge = 0, path = "/", httpOnly = true))
                call.respond(HttpStatusCode.NoContent)
            }
            get("/progress") {
                val user = call.user(db) ?: return@get
                call.respond(db.loadProgress(user.id))
            }
            put("/progress") {
                val user = call.user(db) ?: return@put
                call.respond(db.mergeProgress(user.id, call.receive<Progress>()))
            }
        }

        // The course website (docs/) is served at the root when present.
        val site = listOf(File("../docs"), File("docs")).firstOrNull { File(it, "index.html").exists() }
        // Page and scripts are revalidated on every visit so updates show up at once; images may be cached for a day.
        if (site != null) staticFiles("/", site) {
            default("index.html")
            cacheControl { f -> if (f.extension in setOf("html", "js")) listOf(io.ktor.http.CacheControl.NoCache(null)) else listOf(io.ktor.http.CacheControl.MaxAge(86_400)) }
        }
    }
}

private fun ApplicationCall.bearer(): String? =
    request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.cookies[TOKEN_COOKIE]?.takeIf { it.isNotEmpty() }

private fun ApplicationCall.setTokenCookie(token: String) {
    val https = request.header("X-Forwarded-Proto") == "https"
    response.cookies.append(Cookie(TOKEN_COOKIE, token, maxAge = 60 * 60 * 24 * 365, path = "/", httpOnly = true, secure = https, extensions = mapOf("SameSite" to "Lax")))
}

/** Returns the signed-in user, or responds 401 and returns null. */
private suspend fun ApplicationCall.user(db: Db): UserDto? {
    // Automatic "guest-…" accounts from an earlier version no longer count as signed in.
    val user = bearer()?.let(db::userForToken)?.takeUnless { it.email.startsWith("guest-") }
    if (user == null) respond(HttpStatusCode.Unauthorized, ErrorDto("צריך להתחבר"))
    return user
}

/** Just the parts of content.json the server needs for instructor statistics. */
@kotlinx.serialization.Serializable
private data class Course(val passMark: Int = 80, val categories: List<CourseCat> = emptyList(), val topics: List<CourseTopic> = emptyList())
@kotlinx.serialization.Serializable
private data class CourseCat(val id: String, val name: String)
@kotlinx.serialization.Serializable
private data class CourseTopic(val cat: String, val questions: List<CourseQ> = emptyList())
@kotlinx.serialization.Serializable
private data class CourseQ(val id: String)
