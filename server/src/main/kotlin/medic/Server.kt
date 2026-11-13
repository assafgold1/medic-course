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
import java.io.File
import java.security.MessageDigest

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8787
    val dbPath = System.getenv("MEDIC_DB") ?: "data/medic.db"
    File(dbPath).parentFile?.mkdirs()
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(Db(dbPath)) }.start(wait = true)
}

fun Application.module(db: Db) {
    val content = Application::class.java.getResource("/content.json")!!.readText()
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
                if (!EMAIL.matches(email)) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("כתובת המייל לא תקינה"))
                if (req.password.length < 8) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("הסיסמה צריכה להיות לפחות 8 תווים"))
                val user = db.createUser(email, req.password, req.name.trim().take(60))
                    ?: return@post call.respond(HttpStatusCode.Conflict, ErrorDto("כבר קיים משתמש עם המייל הזה"))
                call.respond(HttpStatusCode.Created, AuthResponse(db.newSession(user.id), user))
            }
            post("/auth/login") {
                val req = call.receive<LoginRequest>()
                val user = db.checkLogin(req.email.trim().lowercase(), req.password)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorDto("מייל או סיסמה שגויים"))
                call.respond(AuthResponse(db.newSession(user.id), user))
            }
            post("/auth/logout") {
                call.bearer()?.let(db::deleteSession)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/me") {
                val user = call.user(db) ?: return@get
                call.respond(user)
            }
            put("/me") {
                val user = call.user(db) ?: return@put
                val req = call.receive<UpdateProfileRequest>()
                call.respond(db.updateName(user.id, req.name.trim().take(60)) ?: user)
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
        if (site != null) staticFiles("/", site) { default("index.html") }
    }
}

private fun ApplicationCall.bearer(): String? =
    request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }

/** Returns the signed-in user, or responds 401 and returns null. */
private suspend fun ApplicationCall.user(db: Db): UserDto? {
    val user = bearer()?.let(db::userForToken)
    if (user == null) respond(HttpStatusCode.Unauthorized, ErrorDto("צריך להתחבר"))
    return user
}
