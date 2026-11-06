package com.example.mybible.ui

import com.example.mybible.ui.Detour.CROSS_REFERENCES
import com.example.mybible.ui.Detour.HIGHLIGHTS
import com.example.mybible.ui.Detour.NOTE
import com.example.mybible.ui.Detour.SEARCH
import com.example.mybible.ui.Detour.WORD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The trail of detours the Reader's banners and Back go back along (backlog 65). */
class DetourTrailTest {

    private fun trail(vararg steps: Detour) = DetourTrail(steps.toList())

    @Test
    fun aVersePickedAgainFromTheSameListIsStillOneDetour() {
        // Search's results, a result read, Return, another result read.
        val t = DetourTrail().picked(SEARCH).picked(SEARCH)
        assertEquals(trail(SEARCH), t)
    }

    @Test
    fun theNewestDetourShowsAndBackGoesThroughTheRestInTheOrderTaken() {
        // A cross reference followed; from there a search's result; from there a highlighted verse.
        var t = DetourTrail().began(CROSS_REFERENCES).picked(CROSS_REFERENCES).picked(SEARCH).picked(HIGHLIGHTS)
        val allUp = { _: Detour -> true }
        assertEquals(HIGHLIGHTS, t.shown(allUp))
        t = t.left(HIGHLIGHTS)
        assertEquals(SEARCH, t.shown(allUp))
        t = t.left(SEARCH)
        assertEquals(CROSS_REFERENCES, t.shown(allUp))
        t = t.left(CROSS_REFERENCES)
        assertNull(t.shown(allUp))
    }

    @Test
    fun crossReferencesFollowedTwoDeepGoBackThroughBothLists() {
        // Isaiah 59:20's references, Romans 11:26 followed; its own references, Romans 2:4 followed.
        var t = DetourTrail().began(CROSS_REFERENCES).picked(CROSS_REFERENCES)
        t = t.began(CROSS_REFERENCES).picked(CROSS_REFERENCES)
        assertEquals(trail(CROSS_REFERENCES, CROSS_REFERENCES), t)
        // Back to Romans 11:26's list, then to Romans 11:26, where Isaiah's list's banner shows again.
        t = t.left(CROSS_REFERENCES)
        assertEquals(trail(CROSS_REFERENCES), t)
        assertEquals(CROSS_REFERENCES, t.shown { true })
        t = t.left(CROSS_REFERENCES)
        assertEquals(DetourTrail(), t)
    }

    @Test
    fun aListOpenedAgainFromItsOwnBannersVerseIsADetourOfItsOwn() {
        // A result read, Search opened again from the Reader's menu, another result read.
        var t = DetourTrail().picked(SEARCH).began(SEARCH)
        assertEquals(trail(SEARCH, SEARCH), t)
        t = t.left(SEARCH)
        assertEquals(trail(SEARCH), t)
    }

    @Test
    fun aDetourWithItsBannerDownIsPassedOver() {
        // The search's banner down (gone back to its results): the cross references' is the one up.
        val t = trail(CROSS_REFERENCES, SEARCH)
        assertEquals(CROSS_REFERENCES, t.shown { it == CROSS_REFERENCES })
        assertNull(t.shown { false })
    }

    @Test
    fun leftTakesTheNewestOfItsKind() {
        assertEquals(trail(WORD, SEARCH), trail(WORD, SEARCH, WORD).left(WORD))
        assertEquals(trail(WORD, SEARCH), trail(WORD, SEARCH).left(NOTE))
    }

    @Test
    fun withoutEndsEveryOneOfAKind() {
        assertEquals(trail(NOTE), trail(SEARCH, NOTE, SEARCH).without(SEARCH))
    }
}
