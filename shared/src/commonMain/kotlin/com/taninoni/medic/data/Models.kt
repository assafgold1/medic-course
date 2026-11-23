package com.taninoni.medic.data

import kotlinx.serialization.Serializable

@Serializable
data class Content(
    val version: String,
    val passMark: Int = 80,
    val categories: List<Category>,
    val topics: List<Topic>,
    val reference: Reference? = null,
    val protocols: List<Protocol> = emptyList(),
)

/** A field protocol (tools/protocols/data.js). [file] is its rendered diagram among the topic's images. */
@Serializable
data class Protocol(val id: String, val topic: String, val group: String, val title: String, val file: String = "", val steps: List<ProtocolStep>)

/** k: alarm, step, if, stop or note. An "if" step asks [h] and has two branches. */
@Serializable
data class ProtocolStep(val k: String, val h: String, val d: String = "", val yes: ProtocolBranch? = null, val no: ProtocolBranch? = null)

@Serializable
data class ProtocolBranch(val h: String, val items: List<String>)

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
    /** Three or four lines to remember, shown first on the topic page. */
    val keyPoints: List<String> = emptyList(),
)

/** A learning image from the course decks; [file] is under composeResources/files/images. */
@Serializable
data class TopicImage(
    val file: String,
    val caption: String,
    val spots: List<Spot> = emptyList(),
    /** Animated images are a grid of [frames] frames, [cols] per row, each shown for [frameMs]. */
    val frames: Int = 0,
    val cols: Int = 0,
    val frameMs: Int = 0,
)

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

/** A question in spaced review: [stage] 1–3, due on local epoch [day]. */
@Serializable
data class ReviewCard(val stage: Int, val day: Long)

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
data class UserDto(
    val id: Long, val email: String, val name: String, val instructor: Boolean = false,
    /** How to address the user in Hebrew: "m", "f", or "" if not chosen yet. */
    val address: String = "",
    /** "mda", "uh", free text for another organisation, or "" if not chosen yet. */
    val org: String = "",
)

/** Display name of an organisation code. */
fun orgLabel(org: String) = when (org) { "mda" -> "מד״א"; "uh" -> "איחוד הצלה"; else -> org }

@Serializable
data class StudentSummary(
    val id: Long, val name: String, val email: String,
    val answered: Int, val correct: Int, val mastery: Int,
    val attempts: Int, val examsPassed: Int, val lastExamPct: Int? = null,
    val lastActive: Long = 0, val joined: Long = 0,
    val org: String = "",
)

@Serializable
data class CategoryProgress(val id: String, val name: String, val correct: Int, val answered: Int, val total: Int)

@Serializable
data class StudentDetail(val student: StudentSummary, val categories: List<CategoryProgress>, val attempts: List<Attempt>)

@Serializable
data class UpdateProfileRequest(val name: String? = null, val address: String? = null, val org: String? = null)

@Serializable
data class ChangePasswordRequest(val current: String, val new: String)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto, val created: Boolean = false)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String = "", val address: String = "", val org: String = "")

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

/* ---- friends (same shape as the server) ---- */

@Serializable
data class FriendDto(
    val id: Long, val name: String, val org: String = "", val streak: Int = 0, val week: Int = 0,
    val mastery: Int = 0, val lastActive: Long = 0, val me: Boolean = false,
)

@Serializable
data class FriendsResponse(val code: String, val friends: List<FriendDto>)

@Serializable
data class AddFriendRequest(val code: String)

@Serializable
data class ReportRequest(val reason: String)

@Serializable
data class StatsRequest(val streak: Int, val day: Long)
