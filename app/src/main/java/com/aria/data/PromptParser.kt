package com.aria.data

import java.util.Locale

/**
 * Превращает текст на естественном языке в параметры генерации.
 * Не ML, а честный маппинг ключевых слов. Работает и на русском, и на английском.
 */
object PromptParser {

    data class GenParams(
        val temperature: Float,
        val noteMs: Int,
        val length: Int,
        val seedNote: Int,
        val mood: String,
        val matched: List<String>,
    )

    // -------- Словари --------

    private val fastWords = listOf(
        "быстр", "быстр", "энерг", "танц", "пляс", "погон", "скор",
        "fast", "quick", "energetic", "dance", "upbeat", "driving",
    )
    private val slowWords = listOf(
        "медл", "спокой", "тих", "нежн", "лирич", "задум", "уют",
        "slow", "calm", "quiet", "gentle", "lyrical", "soft", "peaceful",
    )
    private val sadWords = listOf(
        "груст", "печаль", "тоск", "минор", "меланхол", "драм", "плач", "слез",
        "sad", "melancholy", "minor", "mournful", "somber", "blue",
    )
    private val happyWords = listOf(
        "весел", "радост", "мажор", "светл", "солнеч", "улыб", "праздн",
        "happy", "joyful", "major", "bright", "sunny", "cheerful",
    )
    private val darkWords = listOf(
        "тёмн", "темн", "мрач", "зловещ", "таинств", "жутк", "мистич", "холод",
        "dark", "eerie", "mysterious", "sinister", "cold", "haunted",
    )
    private val epicWords = listOf(
        "эпич", "величеств", "героич", "мощн", "грандиоз", "батал", "битв",
        "epic", "heroic", "powerful", "grand", "battle", "cinematic",
    )
    private val playfulWords = listOf(
        "игрив", "забавн", "шутл", "лёгк", "легк", "детск", "мил",
        "playful", "funny", "light", "childish", "cute", "whimsical",
    )
    private val longWords = listOf(
        "длинн", "долг", "продолж", "симфон", "больш",
        "long", "extended", "symphony", "grand",
    )
    private val shortWords = listOf(
        "коротк", "быстреньк", "мини", "мгновен",
        "short", "brief", "tiny", "mini",
    )

    // Простая гамма — базовая нота для seed по настроению
    private val minorSeeds = intArrayOf(57, 60, 62, 64, 67, 69)   // A3, C4, D4, E4, G4, A4
    private val majorSeeds = intArrayOf(60, 62, 64, 65, 67, 69)   // C4 .. A4

    // -------- Парсер --------

    fun parse(text: String): GenParams {
        val lower = text.lowercase(Locale.ROOT)
        val matched = mutableListOf<String>()

        var temp = 0.8f
        var noteMs = 350
        var length = 40
        var seed = 60
        var mood = "neutral"

        if (containsAny(lower, fastWords)) {
            noteMs = 180
            temp = 0.9f
            matched += "fast"
        }
        if (containsAny(lower, slowWords)) {
            noteMs = 700
            temp = 0.6f
            matched += "slow"
        }
        if (containsAny(lower, sadWords)) {
            temp = 0.7f
            seed = minorSeeds.random()
            mood = "minor"
            matched += "sad"
        }
        if (containsAny(lower, happyWords)) {
            temp = 0.9f
            seed = majorSeeds.random()
            mood = "major"
            matched += "happy"
        }
        if (containsAny(lower, darkWords)) {
            temp = 1.1f
            seed = minorSeeds.random()
            mood = "dark"
            matched += "dark"
        }
        if (containsAny(lower, epicWords)) {
            temp = 1.0f
            length = 96
            noteMs = 500
            seed = 60
            mood = "epic"
            matched += "epic"
        }
        if (containsAny(lower, playfulWords)) {
            temp = 1.2f
            noteMs = 220
            seed = majorSeeds.random()
            mood = "playful"
            matched += "playful"
        }
        if (containsAny(lower, longWords)) {
            length = (length * 2).coerceAtMost(256)
            matched += "long"
        }
        if (containsAny(lower, shortWords)) {
            length = (length / 2).coerceAtLeast(16)
            matched += "short"
        }

        return GenParams(
            temperature = temp.coerceIn(0.3f, 2.0f),
            noteMs = noteMs.coerceIn(100, 1000),
            length = length.coerceIn(16, 256),
            seedNote = seed,
            mood = mood,
            matched = matched,
        )
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { haystack.contains(it) }
}