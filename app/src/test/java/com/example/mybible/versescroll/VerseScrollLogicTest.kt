package com.example.mybible.versescroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VerseScrollLogicTest {

    @Test
    fun hashAndRandomMatchTheDesignPreview() {
        // Reference values from the preview's JavaScript (hashOf / rngFrom), so scenes and their motion
        // are chosen the same way they were reviewed.
        assertEquals(2166136261L, fnv1a("").toLong() and 0xffffffffL)
        assertEquals(776767736L, fnv1a("Isaiah 41:10").toLong() and 0xffffffffL)
        assertEquals(3753434785L, fnv1a("Psalms 23:1").toLong() and 0xffffffffL)
        val r = SeededRandom(fnv1a("Isaiah 41:10"))
        assertEquals(0.6455197f, r.next(), 1e-6f)
        assertEquals(0.8219322f, r.next(), 1e-6f)
        assertEquals(0.5527708f, r.next(), 1e-6f)
        val r2 = SeededRandom(12345)
        assertEquals(0.9797283f, r2.next(), 1e-6f)
    }

    @Test
    fun readsTheDiscoverPool() {
        val pool = parseDiscoverPool("# book\tchapter\tverse\tweight\nIsaiah\t41\t10\t65.45\n1 Peter\t2\t9\t57.11\n\nbroken line\n")
        assertEquals(listOf(VerseRef("Isaiah", 41, 10), VerseRef("1 Peter", 2, 9)), pool.map { it.ref })
        assertEquals(65.45, pool[0].weight, 1e-9)
    }

    @Test
    fun discoverUsesEveryVerseOncePerRoundAndAvoidsRecentOnes() {
        val entries = (1..50).map { DiscoverEntry(VerseRef("Psalms", 1, it), weight = it.toDouble()) }
        val pool = DiscoverPool(entries, Random(7), recentLimit = 20)
        val round = (1..50).map { pool.next()!!.key }
        assertEquals(50, round.toSet().size)
        // The next round starts only with verses not among the last 20 shown.
        val lastTwenty = round.takeLast(20).toSet()
        val next = (1..30).map { pool.next()!!.key }
        assertTrue(next.none { it in lastTwenty })
        assertNull(DiscoverPool(emptyList()).next())
    }

    @Test
    fun recentVersesCarryOverToTheNextVisit() {
        val entries = (1..10).map { DiscoverEntry(VerseRef("John", 3, it), 1.0) }
        val first = DiscoverPool(entries, Random(1), recentLimit = 5)
        repeat(5) { first.next() }
        val second = DiscoverPool(entries, Random(2), recentLimit = 5)
        second.restoreRecent(first.recentKeys())
        val shown = (1..5).map { second.next()!!.key }
        assertTrue(shown.none { it in first.recentKeys() })
    }

    private val scenes = listOf(
        SceneSpec("sea-dusk-31", 0.4f, 0.4f, kind = "sea", horizon = 0.5f, sun = listOf(0.5f, 0.4f), glint = "#ffeedd"),
        SceneSpec("sea-teal-1105", 0.4f, 0.4f, kind = "sea", horizon = 0.5f, sun = listOf(0.5f, 0.4f), glint = "#ffeedd"),
        SceneSpec("night-night-41", 0.4f, 0.4f, kind = "night"),
        SceneSpec("moon-night-42", 0.4f, 0.4f, kind = "moon", moon = listOf(0.3f, 0.2f, 0.1f)),
        SceneSpec("hills-golden-22", 0.4f, 0.4f, kind = "hills", mist = listOf(0.6f, 0.7f), haze = "#ffffff"),
        SceneSpec("abstract-ember-81", 0.4f, 0.4f, kind = "abstract", blobs = listOf("#c0607a", "#f29a70"))
    )

    @Test
    fun aVersesWordsPickItsScene() {
        val sea = SceneMatcher.pick(scenes, "They that go down to the sea in ships", "Psalms 107:23", previousType = null)
        assertEquals("sea", sea.type)
        val night = SceneMatcher.pick(scenes, "The moon and stars to rule by night", "Psalms 136:9", previousType = null)
        assertTrue(night.type == "night" || night.type == "moon")
        // The same verse always gets the same scene.
        assertEquals(sea, SceneMatcher.pick(scenes, "They that go down to the sea in ships", "Psalms 107:23", null))
    }

    @Test
    fun neverTheSameKindTwiceInARow() {
        val pick = SceneMatcher.pick(scenes, "the sea roared", "Psalms 98:7", previousType = "sea")
        assertNotEquals("sea", pick.type)
    }

    @Test
    fun eachKindGetsItsMovingTouch() {
        val seed = fnv1a("Isaiah 41:10")
        assertEquals(12, sceneEffects(scenes[0], seed).count { it is SceneFx.Glint })
        assertTrue(sceneEffects(scenes[2], seed).all { it is SceneFx.Twinkle })
        // Stars stay off the moon.
        val moon = scenes[3].moon!!
        sceneEffects(scenes[3], seed).filterIsInstance<SceneFx.Twinkle>().forEach {
            assertTrue(kotlin.math.hypot(it.x - moon[0], (it.y - moon[1]) / 0.46f) >= moon[2])
        }
        assertEquals(2, sceneEffects(scenes[4], seed).count { it is SceneFx.Mist })
        assertEquals(2, sceneEffects(scenes[5], seed).count { it is SceneFx.Blob })
        // Same verse, same motion.
        assertEquals(sceneEffects(scenes[0], seed), sceneEffects(scenes[0], seed))
    }

    @Test
    fun passagesAndWordCounts() {
        val content = VerseCardContent(
            ref = VerseRef("Isaiah", 40, 29),
            lines = listOf(
                VerseLine(29, "He giveth power to the faint;", null),
                VerseLine(30, "Even the youths shall faint and be weary,", null)
            ),
            before = null,
            after = null,
            linkCount = 3
        )
        assertEquals("Isaiah 40:29–30", content.label)
        assertEquals(listOf(VerseRef("Isaiah", 40, 29), VerseRef("Isaiah", 40, 30)), content.refs)
        assertEquals(14, content.wordCount)
    }
}
