package medic

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
    @Test
    fun registerSyncAndMerge() = testApplication {
        val dbFile = File.createTempFile("medic", ".db").apply { deleteOnExit() }
        application { module(Db(dbFile.path)) }
        val client = createClient { install(ContentNegotiation) { json() } }

        assertEquals(HttpStatusCode.OK, client.get("/api/content").status)
        val reg = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json); setBody(RegisterRequest("a@b.co", "password1", "Test"))
        }
        assertEquals(HttpStatusCode.Created, reg.status)
        val token = reg.body<AuthResponse>().token

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/progress").status)
        client.put("/api/progress") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(Progress(mapOf("aed#0" to Answer(0, 100)), listOf(Attempt("x1", "t", "practice", 10, 8, 80, 100))))
        }
        val merged = client.put("/api/progress") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(Progress(mapOf("aed#0" to Answer(1, 200), "aed#1" to Answer(1, 50)), listOf(Attempt("x1", "t", "practice", 10, 8, 80, 100))))
        }.body<Progress>()
        assertEquals(1, merged.answers["aed#0"]?.v)
        assertEquals(2, merged.answers.size)
        assertEquals(1, merged.attempts.size)

        // A reset on one device wipes older data, even when another device later uploads its stale copy.
        client.put("/api/progress") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(Progress(resetAt = 150)) }
        val afterReset = client.put("/api/progress") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(merged) }.body<Progress>()
        assertEquals(setOf("aed#0"), afterReset.answers.keys)
        assertEquals(0, afterReset.attempts.size)
        assertEquals(150, afterReset.resetAt)

        val login = client.post("/api/auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest("A@B.co", "password1")) }
        assertEquals(HttpStatusCode.OK, login.status)
        val bad = client.post("/api/auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest("a@b.co", "wrong")) }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
        assertTrue(client.get("/health").status == HttpStatusCode.OK)

        // Profile: rename, change password (signs out other devices), delete account.
        val otherToken = login.body<AuthResponse>().token
        val renamed = client.put("/api/me") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateProfileRequest("  New Name ")) }.body<UserDto>()
        assertEquals("New Name", renamed.name)

        val wrongCurrent = client.post("/api/me/password") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(ChangePasswordRequest("nope", "newpassword")) }
        assertEquals(HttpStatusCode.Forbidden, wrongCurrent.status)
        val tooShort = client.post("/api/me/password") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(ChangePasswordRequest("password1", "short")) }
        assertEquals(HttpStatusCode.BadRequest, tooShort.status)
        val changed = client.post("/api/me/password") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(ChangePasswordRequest("password1", "newpassword")) }
        assertEquals(HttpStatusCode.NoContent, changed.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/me") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/me") { bearerAuth(otherToken) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest("a@b.co", "newpassword")) }.status)

        val wrongDelete = client.post("/api/me/delete") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(DeleteAccountRequest("password1")) }
        assertEquals(HttpStatusCode.Forbidden, wrongDelete.status)
        val deleted = client.post("/api/me/delete") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(DeleteAccountRequest("newpassword")) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/me") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest("a@b.co", "newpassword")) }.status)
    }
}
