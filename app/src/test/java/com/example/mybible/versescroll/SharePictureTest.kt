package com.example.mybible.versescroll

import com.example.mybible.model.TeluguBooks
import com.example.mybible.ui.components.BIBLE_BOOKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Backlog 53: a Verse Scroll card shared as a picture, in a choice of shapes. */
class SharePictureTest {

    @Test
    fun eachShapeIs1080PixelsOnItsShortSide() {
        assertEquals(PictureSize(1080, 1920), ShareShape.STORY.size(1080, 2400))
        assertEquals(PictureSize(1080, 1350), ShareShape.POST.size(1080, 2400))
        assertEquals(PictureSize(1080, 1080), ShareShape.SQUARE.size(1080, 2400))
        assertEquals(PictureSize(1920, 1080), ShareShape.WIDE.size(1080, 2400))
    }

    @Test
    fun thePhoneShapeIsTheShapeOfItsScreen() {
        assertEquals(PictureSize(1080, 2400), ShareShape.SCREEN.size(1080, 2400))
        // A sharper screen, or one on its side: the same shape, 1080 across.
        assertEquals(PictureSize(1080, 2400), ShareShape.SCREEN.size(1440, 3200))
        assertEquals(PictureSize(1080, 2400), ShareShape.SCREEN.size(3200, 1440))
        // A height that comes out odd is made even.
        assertEquals(PictureSize(1080, 2336), ShareShape.SCREEN.size(1170, 2532))
        // A screen not known, or squarer than 9:16 (a folding phone open): 9:16.
        assertEquals(PictureSize(1080, 1920), ShareShape.SCREEN.size(0, 0))
        assertEquals(PictureSize(1080, 1920), ShareShape.SCREEN.size(1812, 2176))
    }

    @Test
    fun aPhotoIsCroppedAsTheCardsCropIt() {
        // A scene photo (900 × 1950) into 9:16: all its width, and the part a little below the middle.
        assertEquals(PixelRect(0, 210, 900, 1810), SharePictureLayout.crop(900, 1950, 1080, 1920))
        // Into 16:9: a band across it.
        assertEquals(PixelRect(0, 866, 900, 1372), SharePictureLayout.crop(900, 1950, 1920, 1080))
        // A photo wider than the frame: all its height, the middle across.
        assertEquals(PixelRect(1156, 0, 2843, 3000), SharePictureLayout.crop(4000, 3000, 1080, 1920))
    }

    @Test
    fun theWordsKeepClearOfTheEdgesAndOfWhatAppsPutOverAPicture() {
        val story = PictureSize(1080, 1920)
        assertEquals(PixelRect(86, 250, 994, 1574), SharePictureLayout.textFrame(ShareShape.STORY, story))
        // A wallpaper's words below the lock screen's clock.
        assertEquals(PixelRect(86, 720, 994, 2064), SharePictureLayout.textFrame(ShareShape.SCREEN, PictureSize(1080, 2400)))
        // A wide picture's beside its photo, which is 9:16 and as tall as the picture.
        val wide = PictureSize(1920, 1080)
        assertEquals(606, SharePictureLayout.panelWidth(wide))
        assertEquals(PixelRect(692, 86, 1834, 994), SharePictureLayout.textFrame(ShareShape.WIDE, wide))
        // No photo (the plain page): across the whole picture.
        assertEquals(PixelRect(86, 86, 1834, 994), SharePictureLayout.textFrame(ShareShape.WIDE, wide, photo = false))
    }

    @Test
    fun theWordsAreAsLargeAsFit() {
        assertEquals(0.72f, SharePictureLayout.fit(1f, 0.5f, 0.02f) { it <= 0.73f }!!, 1e-4f)
        assertEquals(1f, SharePictureLayout.fit(1f, 0.5f, 0.02f) { true }!!, 1e-4f)
        // Too long even at the smallest: cut short instead.
        assertNull(SharePictureLayout.fit(1f, 0.5f, 0.02f) { it < 0.5f })
    }

    @Test
    fun everyBookHasItsTeluguName() {
        assertEquals(BIBLE_BOOKS, TeluguBooks.books)
        assertEquals("యోహాను", TeluguBooks.nameOf("John"))
        assertEquals("పరమగీతము", TeluguBooks.nameOf("Song of Solomon"))
        assertEquals("1 యోహాను", TeluguBooks.nameOf("1 John"))
        assertNull(TeluguBooks.nameOf("Tobit"))
    }

    @Test
    fun theTeluguReferenceHasTheBooksTeluguName() {
        assertEquals("యోహాను 3:16", teluguLabelOf(listOf(VerseRef("John", 3, 16))))
        assertEquals("యెషయా 40:29–31", teluguLabelOf((29..31).map { VerseRef("Isaiah", 40, it) }))
        assertEquals("ప్రకటన గ్రంథము 3:20", teluguLabelOf(listOf(VerseRef("Revelation", 3, 20))))
        assertNull(teluguLabelOf(listOf(VerseRef("Tobit", 1, 1))))
    }

    @Test
    fun picturesAreNamedForTheVerseAndShape() {
        assertEquals("John-3-16-9x16.jpg", SharePictureLayout.fileName("John 3:16", ShareShape.STORY))
        assertEquals("Isaiah-40-29-31-Phone.jpg", SharePictureLayout.fileName("Isaiah 40:29–31", ShareShape.SCREEN))
        assertEquals("1-John-4-8-16x9.jpg", SharePictureLayout.fileName("1 John 4:8", ShareShape.WIDE))
    }

    @Test
    fun theShapeChosenLastIsRemembered() {
        assertEquals(ShareShape.WIDE, ShareShape.named("WIDE"))
        // None saved yet, or one this version doesn't have: 9:16.
        assertEquals(ShareShape.STORY, ShareShape.named(null))
        assertEquals(ShareShape.STORY, ShareShape.named("BANNER"))
    }
}
