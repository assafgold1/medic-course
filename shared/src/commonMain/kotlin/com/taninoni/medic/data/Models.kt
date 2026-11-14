package com.taninoni.medic.data

import kotlinx.serialization.Serializable

@Serializable
data class Content(
    val version: String,
    val passMark: Int = 80,
    val categories: List<Category>,
    val topics: List<Topic>,
    val reference: Reference? = null,
)

@Serializable
data class Category(val id: String, val name: String, val short: String)

@Serializable
data class Topic(
    val id: String,
    val number: Int,
    val cat: String,
    val title: String,
    val blurb: String,
    val note: String? = null,
    val nums: List<Num> = emptyList(),
    val sections: List<Section> = emptyList(),
    val questions: List<Question> = emptyList(),
    val images: List<TopicImage> = emptyList(),
)

/** A learning image from the course decks; [file] is under composeResources/files/images. */
@Serializable
data class TopicImage(val file: String, val caption: String, val spots: List<Spot> = emptyList())

/** A tappable point on an anatomy image; [x], [y] are percentages of its width and height. */
@Serializable
data class Spot(val x: Float, val y: Float, val name: String, val info: String)

@Serializable
data class Num(val value: String, val label: String)

@Serializable
data class Section(val title: String, val steps: Boolean = false, val items: List<String>, val images: List<String> = emptyList())

/** [options] always lists the correct answer first; the UI shuffles them. */
@Serializable
data class Question(val id: String, val q: String, val options: List<String>, val explanation: String, val image: String? = null)

@Serializable
data class Reference(val compare: Compare, val general: List<GeneralRow>)

@Serializable
data class Compare(val columns: List<String>, val rows: List<CompareRow>)

@Serializable
data class CompareRow(val label: String, val values: List<String>)

@Serializable
data class GeneralRow(val label: String, val value: String)

/* ---- sync models (same shape as the server) ---- */

@Serializable
data class Answer(val v: Int, val t: Long)

@Serializable
data class Attempt(
    val id: String,
    val title: String,
    val kind: String,
    val total: Int,
    val right: Int,
    val pct: Int,
    val at: Long,
    val byCategory: Map<String, List<Int>> = emptyMap(),
)

@Serializable
data class Progress(
    val answers: Map<String, Answer> = emptyMap(),
    val attempts: List<Attempt> = emptyList(),
    val updatedAt: Long = 0,
    /** Answers and attempts older than this were wiped by "reset progress" on some device. */
    val resetAt: Long = 0,
)

@Serializable
data class UserDto(val id: Long, val email: String, val name: String, val instructor: Boolean = false)

@Serializable
data class StudentSummary(
    val id: Long, val name: String, val email: String,
    val answered: Int, val correct: Int, val mastery: Int,
    val attempts: Int, val examsPassed: Int, val lastExamPct: Int? = null,
    val lastActive: Long = 0, val joined: Long = 0,
)

@Serializable
data class CategoryProgress(val id: String, val name: String, val correct: Int, val answered: Int, val total: Int)

@Serializable
data class StudentDetail(val student: StudentSummary, val categories: List<CategoryProgress>, val attempts: List<Attempt>)

@Serializable
data class UpdateProfileRequest(val name: String)

@Serializable
data class ChangePasswordRequest(val current: String, val new: String)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto, val created: Boolean = false)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String = "")

@Serializable
data class LoginRequest(val email: String, val password: String, val name: String = "")

@Serializable
data class ErrorDto(val error: String)

/** A question in a quiz run, with its topic and category resolved. */
data class QItem(val question: Question, val topic: Topic) {
    val id get() = question.id
    val cat get() = topic.cat
}

/** One answered question in a run. [picked] is the index into question.options (0 = correct). */
data class LogEntry(val item: QItem, val picked: Int) {
    val ok get() = picked == 0
}
