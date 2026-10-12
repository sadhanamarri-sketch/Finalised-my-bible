package com.example.mybible.audio

/** A verse's text as the phone's voice should say it. */
object SpeechText {
    private val PILCROW = Regex("¶")
    private val SPACES = Regex("\\s+")

    // Words in capitals ("LORD", "GOD", "I AM THAT I AM"): said as words, not spelled out letter by letter.
    private val CAPITALS = Regex("\\b[A-Z]{2,}\\b")

    // The Telugu text carries a few stray marks from how it was typed in — é, ¸, a soft hyphen, a Latin
    // letter or digit inside a word (ఆర్బా éటము, వచ్చిన1షాపాతు) — which a voice would read out.
    private val TELUGU_STRAYS = Regex("[A-Za-z0-9\u00B8\u00E9\u00AD]")

    // A dash before someone's words (అతడిట్లనెను–నేను, సెలవిచ్చెను - నా): a pause.
    private val DASH = Regex("\\s*[\u2013-]\\s*")

    fun english(text: String): String =
        text.replace(PILCROW, " ")
            .replace(CAPITALS) { it.value.lowercase().replaceFirstChar(Char::uppercaseChar) }
            .replace(SPACES, " ")
            .trim()

    fun telugu(text: String): String =
        text.replace(TELUGU_STRAYS, "")
            .replace(DASH, ", ")
            .replace(SPACES, " ")
            .trim()
}
