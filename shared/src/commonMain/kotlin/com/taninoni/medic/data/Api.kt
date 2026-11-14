package com.taninoni.medic.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiException(message: String) : Exception(message)

/** Client for the medic server. [baseUrl] is read on every call so the user can change it. */
class Api(private val baseUrl: () -> String) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = com.taninoni.medic.pinnedHttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    private fun url(path: String) = baseUrl().trimEnd('/') + path

    private suspend inline fun <reified T> HttpResponse.ok(): T {
        if (status.isSuccess()) return body()
        val msg = runCatching { body<ErrorDto>().error }.getOrNull() ?: "השרת החזיר שגיאה (${status.value})"
        throw ApiException(msg)
    }

    /** For calls with no response body (204). */
    private suspend fun HttpResponse.done() {
        if (status.isSuccess()) return
        throw ApiException(runCatching { body<ErrorDto>().error }.getOrNull() ?: "השרת החזיר שגיאה (${status.value})")
    }

    suspend fun health(): Boolean = runCatching { client.get(url("/health")).status.isSuccess() }.getOrDefault(false)

    suspend fun content(): Content = client.get(url("/api/content")).ok()

    suspend fun login(email: String, password: String): AuthResponse =
        client.post(url("/api/auth/login")) {
            contentType(ContentType.Application.Json); setBody(LoginRequest(email, password))
        }.ok()

    suspend fun register(email: String, password: String, name: String): AuthResponse =
        client.post(url("/api/auth/register")) {
            contentType(ContentType.Application.Json); setBody(RegisterRequest(email, password, name))
        }.ok()

    suspend fun students(token: String): List<StudentSummary> =
        client.get(url("/api/instructor/students")) { bearerAuth(token) }.ok()

    suspend fun student(token: String, id: Long): StudentDetail =
        client.get(url("/api/instructor/students/$id")) { bearerAuth(token) }.ok()

    suspend fun logout(token: String) {
        runCatching { client.post(url("/api/auth/logout")) { bearerAuth(token) } }
    }

    suspend fun updateProfile(token: String, name: String): UserDto =
        client.put(url("/api/me")) {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(UpdateProfileRequest(name))
        }.ok()

    suspend fun changePassword(token: String, current: String, new: String) =
        client.post(url("/api/me/password")) {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(ChangePasswordRequest(current, new))
        }.done()

    suspend fun deleteAccount(token: String, password: String) =
        client.post(url("/api/me/delete")) {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(DeleteAccountRequest(password))
        }.done()

    suspend fun syncProgress(token: String, progress: Progress): Progress =
        client.put(url("/api/progress")) {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(progress)
        }.ok()
}
