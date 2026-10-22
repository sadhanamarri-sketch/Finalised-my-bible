package com.example.mybible.pictures

/** A picture in the pictures folder: its file ([uri], [file] its name), and where in the Bible it's for. */
data class Picture(
    val uri: String,
    val file: String,
    /** Where it's for, from its name; null for a picture for any card ("Any sunrise.jpg"). */
    val name: PictureName?,
    val modified: Long = 0,
    val size: Long = 0
)

/**
 * The pictures found, sorted for finding: a verse's best fit (its own picture before its chapter's,
 * a chapter's before its book's, a short run of verses before a longer one), a scene's own, and the ones
 * for any card.
 */
class PictureShelf(val pictures: List<Picture>) {
    private val byUri = pictures.associateBy { it.uri }
    private val byBook = pictures.filter { it.name != null }.groupBy { it.name!!.book }
    private val byPlace = pictures.filter { it.name != null }.groupBy { it.name!!.place }
        .mapValues { (_, those) -> those.sortedWith(compareBy({ it.name!!.variant }, { it.file })) }

    /** The pictures for any card, now and then ("Any sunrise.jpg"). */
    val general: List<Picture> = pictures.filter { it.name == null }.sortedBy { it.file }

    val size: Int get() = pictures.size

    fun has(uri: String): Boolean = uri in byUri

    operator fun get(uri: String): Picture? = byUri[uri]

    /** The pictures of the place that fits a verse best, the first of them first, then the others in turn. */
    fun forVerse(book: String, chapter: Int, verse: Int): List<Picture> {
        val best = byBook[book].orEmpty()
            .filter { it.name!!.covers(book, chapter, verse) }
            .minWithOrNull(compareBy({ it.name!!.rank }, { span(it.name!!) }, { it.name!!.place }))
            ?: return emptyList()
        return byPlace[best.name!!.place].orEmpty()
    }

    /** A place's own pictures ("Genesis 1.3-5"), the first first. */
    fun forPlace(place: String): List<Picture> = byPlace[place].orEmpty()

    // How much it covers: chapters, then verses.
    private fun span(name: PictureName): Int {
        val chapters = name.chapters?.let { it.last - it.first + 1 } ?: 1000
        val verses = name.verses?.let { it.last - it.first + 1 } ?: 1000
        return chapters * 1000 + verses
    }

    companion object {
        val Empty = PictureShelf(emptyList())
    }
}
