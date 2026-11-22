package com.taninoni.medic.data

/**
 * One collectible sticker. Topic stickers ("t:<topicId>") are earned by mastering that topic and show
 * something from the topic itself; special stickers are earned by achievements.
 */
data class StickerDef(
    val id: String,
    val name: String,
    val emoji: String,
    /** Chapter id (sets the badge colour), or "special". */
    val cat: String,
    /** How it's earned, shown while it is still locked. */
    val how: String,
    /** Topic number for topic stickers, 0 for specials. */
    val number: Int = 0,
)

object StickerBook {
    /** What each topic's sticker shows: something from the topic itself. */
    private val topicEmoji = mapOf(
        "neuro" to "🧠", "resp" to "🫁", "cardio" to "🫀",
        "adult-cpr" to "❤️‍🩹", "cpr-exam" to "🏆", "team-cpr" to "🤝", "child-cpr" to "🧒", "infant-cpr" to "👶", "choking" to "🫢",
        "aed" to "⚡", "lifeline" to "🔋", "monitor" to "📈",
        "terminology" to "📖", "field-practice" to "🎒", "decisions" to "🧭", "approach" to "🩺", "infectious" to "🦠",
        "unconscious" to "😵", "seizures" to "🌀", "syncope" to "😵‍💫", "neuro-emerg" to "🗣️", "diabetes-tox" to "🍬",
        "resp-emerg" to "😮‍💨", "cardiac-emerg" to "💔", "abdominal-emerg" to "🤢",
        "trauma-intro" to "🚨", "trauma-approach" to "🦺", "shock-bleeding" to "🩸", "head-neck" to "🤕", "chest" to "💥",
        "abdomen" to "🩹", "skeletal" to "🦴", "spine" to "🩻", "adjunct" to "💉",
        "drowning" to "🌊", "electrical" to "🔌", "environmental" to "🌡️", "bites" to "🐍", "diving" to "🤿",
        "special-patient" to "♿", "pregnancy-birth" to "🤰", "birth-emerg" to "🍼", "prevention" to "🥗", "home-meds" to "💊",
        "iv-access" to "💧", "documentation" to "📋", "mci" to "🏷️", "hazmat" to "☢️", "team-lead" to "📣", "ambulance" to "🚑",
    )
    private val catEmoji = mapOf(
        "anat" to "🧬", "cpr" to "❤️", "dev" to "⚡", "basics" to "📘", "medical" to "🩺",
        "trauma" to "🩹", "env" to "🌍", "special" to "⭐", "ops" to "🚑",
    )

    val specials = listOf(
        StickerDef("rookie", "צעד ראשון", "🌱", "special", "היעד היומי הראשון"),
        StickerDef("fire", "בוער", "🔥", "special", "רצף של 7 ימים"),
        StickerDef("king", "מלך הרצף", "👑", "special", "רצף של 30 ימים"),
        StickerDef("medal", "עבר את המבחן", "🎓", "special", "עוברים מבחן מסכם"),
    )

    /** One sticker per topic, in course order, then the specials. */
    fun build(topics: List<Topic>): List<StickerDef> =
        topics.map { t ->
            StickerDef("t:${t.id}", t.title, topicEmoji[t.id] ?: catEmoji[t.cat] ?: "⭐", t.cat, "80% בנושא", t.number)
        } + specials

    fun topicId(stickerId: String) = stickerId.removePrefix("t:").takeIf { stickerId.startsWith("t:") }
}
