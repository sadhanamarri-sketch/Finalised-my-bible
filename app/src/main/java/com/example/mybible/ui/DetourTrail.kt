package com.example.mybible.ui

/** A detour from the Reader: a list or page a verse was picked from, with its "Return to …" banner. */
enum class Detour { CROSS_REFERENCES, SEARCH, WORD, NOTE, HIGHLIGHTS, STUDIED, VERSE_SCROLL }

/**
 * The detours taken, oldest first (backlog 65), so that the banners and the phone's Back go back through
 * them in the order they were taken, newest first: to the newest one's list, then to the verse that list
 * was opened from, where the one before it shows, and so on back to where the reading started.
 *
 * A kind can be on it more than once. A list opened from a verse its own banner is up on (the Reader's
 * menu, or a verse's cross references), rather than gone back to by that banner's Return, is a detour
 * of its own, with the one before it kept below it, as it was.
 */
class DetourTrail(steps: List<Detour> = emptyList()) {
    val steps: List<Detour> = steps.toList()

    /** A detour of [kind] begun: its page opened (a cross references list, a word), or a list opened again. */
    fun began(kind: Detour): DetourTrail = DetourTrail(steps + kind)

    /** A verse picked from [kind]'s list: still the newest detour if it's [kind]'s, else a new one. */
    fun picked(kind: Detour): DetourTrail = if (steps.lastOrNull() == kind) this else began(kind)

    /** [kind]'s newest detour over: its list left for the verse it was opened from, or its banner dismissed. */
    fun left(kind: Detour): DetourTrail {
        val at = steps.lastIndexOf(kind)
        return if (at < 0) this else DetourTrail(steps.take(at) + steps.drop(at + 1))
    }

    /** Every detour of [kind] over at once. */
    fun without(kind: Detour): DetourTrail = DetourTrail(steps.filter { it != kind })

    /** The newest detour whose banner is up ([up]): the one the Reader's banner shows and Back goes back from. */
    fun shown(up: (Detour) -> Boolean): Detour? = steps.lastOrNull(up)

    override fun equals(other: Any?): Boolean = other is DetourTrail && other.steps == steps
    override fun hashCode(): Int = steps.hashCode()
    override fun toString(): String = "DetourTrail($steps)"
}
