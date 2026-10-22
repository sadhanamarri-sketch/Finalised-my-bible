package com.example.mybible.pictures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PicturesTest {

    @Test
    fun aPicturesNameSaysWhereItGoes() {
        assertEquals(PictureName("Genesis", 1..1, 3..5), PictureName.parse("Genesis 1.3-5.webp"))
        assertEquals(PictureName("John", 3..3, 16..16), PictureName.parse("John 3.16.jpg"))
        assertEquals(PictureName("Psalms", 23..23, null), PictureName.parse("Psalms 23.png"))
        assertEquals(PictureName("Genesis", 6..9, null), PictureName.parse("Genesis 6-9.jpeg"))
        assertEquals(PictureName("Psalms", null, null), PictureName.parse("Psalms.jpg"))
        // Another for the same place, and the copy a phone makes of a name already taken.
        assertEquals(PictureName("Psalms", 23..23, null, 2), PictureName.parse("Psalms 23 (2).jpg"))
        assertEquals(PictureName("Genesis", 1..1, 3..5, 1), PictureName.parse("Genesis 1.3-5 (1).webp"))
        // Any case, and the books' other names.
        assertEquals(PictureName("1 Samuel", 17..17, 40..40), PictureName.parse("1 samuel 17.40.PNG"))
        assertEquals(PictureName("Psalms", 23..23, null), PictureName.parse("Psalm 23.png"))
        assertEquals(PictureName("Song of Solomon", 2..2, null), PictureName.parse("Song of Songs 2.png"))
    }

    @Test
    fun aNameThatSaysNoPlaceIsNoneOfThese() {
        assertNull(PictureName.parse("IMG_1234.jpg"))
        assertNull(PictureName.parse("Sunrise.jpg"))
        // Unless it says it's for any card.
        assertTrue(PictureName.forAnyCard("Any sunrise.jpg"))
        assertTrue(PictureName.forAnyCard("any 2.png"))
        assertTrue(PictureName.forAnyCard("Any.jpg"))
        assertFalse(PictureName.forAnyCard("Anything.jpg"))
        assertFalse(PictureName.forAnyCard("IMG_1234.jpg"))
        // Verses belong to one chapter, and a run goes forward.
        assertNull(PictureName.parse("Genesis 6-9.3.png"))
        assertNull(PictureName.parse("Genesis 9-6.png"))
        assertNull(PictureName.parse("Genesis 1.5-3.png"))
    }

    @Test
    fun aScenesPictureIsSavedUnderItsVerses() {
        assertEquals("Genesis 1.3-5", PictureName.of("Genesis", 1, 3, 5))
        assertEquals("Genesis 2.4", PictureName.of("Genesis", 2, 4, 4))
        // Read back, it's for those verses.
        assertEquals(PictureName("Genesis", 1..1, 3..5), PictureName.parse(PictureName.of("Genesis", 1, 3, 5) + ".webp"))
        assertEquals("Genesis 1.3-5", PictureName("Genesis", 1..1, 3..5, 2).place)
    }

    @Test
    fun scenesAreReadBookByBook() {
        val scenes = PictureScenes.parse(
            """
            # notes
            Genesis	1	1	2	In the beginning	A dark sea.
            Genesis	1	3	5	Let there be light	Light breaks.
            Exodus	14	21	22	The sea parted	Walls of water.
            not a scene
            """.trimIndent()
        )
        assertEquals(listOf("Genesis", "Exodus"), scenes.books)
        assertEquals(listOf("Genesis 1.1-2", "Genesis 1.3-5"), scenes.inBook("Genesis").map { it.name })
        assertEquals("Let there be light", scenes.sceneFor("Genesis", 1, 4)?.title)
        assertEquals("1:3–5", scenes.sceneFor("Genesis", 1, 4)?.verses)
        assertNull(scenes.sceneFor("Genesis", 1, 6))
    }

    private fun picture(file: String, uri: String = file) = Picture(uri, file, PictureName.parse(file))

    @Test
    fun aVersesOwnPictureComesBeforeItsChaptersAndItsBooks() {
        val shelf = PictureShelf(
            listOf(
                picture("Psalms.jpg"),
                picture("Psalms 23.jpg"),
                picture("Psalms 23.1-3.webp"),
                picture("Psalms 23.1.png"),
                picture("Psalms 23 (2).jpg"),
                picture("Any sunrise.jpg")
            )
        )
        assertEquals(listOf("Psalms 23.1.png"), shelf.forVerse("Psalms", 23, 1).map { it.file })
        assertEquals(listOf("Psalms 23.1-3.webp"), shelf.forVerse("Psalms", 23, 2).map { it.file })
        // A chapter's two pictures, the first first.
        assertEquals(listOf("Psalms 23.jpg", "Psalms 23 (2).jpg"), shelf.forVerse("Psalms", 23, 5).map { it.file })
        assertEquals(listOf("Psalms.jpg"), shelf.forVerse("Psalms", 1, 1).map { it.file })
        assertTrue(shelf.forVerse("John", 3, 16).isEmpty())
        assertEquals(listOf("Any sunrise.jpg"), shelf.general.map { it.file })
        assertEquals(listOf("Psalms 23.1-3.webp"), shelf.forPlace("Psalms 23.1-3").map { it.file })
    }

    @Test
    fun aDarkPictureNeedsLittleDimmingUnderLightTextAndALightOneMore() {
        val black = IntArray(PictureVeil.CARD_W * PictureVeil.CARD_H) { 0xFF000000.toInt() }
        val white = IntArray(PictureVeil.CARD_W * PictureVeil.CARD_H) { 0xFFFFFFFF.toInt() }
        val onBlack = PictureVeil.measure(black)
        val onWhite = PictureVeil.measure(white)
        // Light text on black, dark text on white: as good as no dimming (the small margin it's always given).
        assertEquals(0.03f, onBlack.dark, 0.001f)
        assertEquals(0.03f, onWhite.light, 0.001f)
        assertTrue(onWhite.dark > 0.5f)
        assertTrue(onBlack.light > 0.5f)
    }

    @Test
    fun theCardShowsAPictureAsItFillsTheCard() {
        // A 9:16 picture on the card: a little off each side, all of its height.
        val (left, top, width, height) = PictureVeil.cardCrop(900, 1600)
        assertEquals(1600, height)
        assertEquals(0, top)
        assertEquals((900 - width) / 2, left)
        assertTrue(width in 730..745)
    }

    /**
     * The bundled scenes run on from verse to verse, each chapter from its first (tools/pictures/make_prompts.py
     * checks they reach each chapter's last against the KJV), and each prompt asks for the same shape.
     */
    @Test
    fun theBundledScenesRunOnFromVerseToVerse() {
        val scenes = PictureScenes.parse(File("src/main/assets/pictures/scenes.tsv").readText())
        assertTrue(scenes.books.isNotEmpty())
        for (book in scenes.books) {
            for ((chapter, these) in scenes.inBook(book).groupBy { it.chapter }) {
                var next = 1
                for (scene in these) {
                    assertEquals("$book $chapter: a scene starts at ${scene.first}", next, scene.first)
                    assertTrue(scene.last >= scene.first)
                    assertTrue(scene.prompt.contains("Vertical 9:16"))
                    next = scene.last + 1
                }
            }
        }
    }
}
