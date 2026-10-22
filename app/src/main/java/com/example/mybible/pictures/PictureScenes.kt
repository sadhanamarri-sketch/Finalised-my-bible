package com.example.mybible.pictures

/**
 * A scene: a run of verses in a chapter showing one moment, with the prompt its picture is made from
 * in ChatGPT (see tools/pictures). Its picture is saved as [name].
 */
data class PictureScene(
    val book: String,
    val chapter: Int,
    val first: Int,
    val last: Int,
    val title: String,
    val prompt: String
) {
    val name: String get() = PictureName.of(book, chapter, first, last)

    /** Its verses, as shown: "1:3–5", "2:4". */
    val verses: String get() = "$chapter:$first" + if (last != first) "–$last" else ""

    fun has(chapter: Int, verse: Int): Boolean = chapter == this.chapter && verse in first..last
}

/** The scenes there are prompts for (assets/pictures/scenes.tsv), book by book. */
class PictureScenes(val scenes: List<PictureScene>) {
    private val byBook = scenes.groupBy { it.book }

    /** The books with scenes, in the Bible's order (as listed). */
    val books: List<String> = byBook.keys.toList()

    fun inBook(book: String): List<PictureScene> = byBook[book].orEmpty()

    fun sceneFor(book: String, chapter: Int, verse: Int): PictureScene? =
        byBook[book]?.firstOrNull { it.has(chapter, verse) }

    companion object {
        val None = PictureScenes(emptyList())

        /** scenes.tsv: book, chapter, first verse, last verse, title, prompt, tab-separated; "#" lines are notes. */
        fun parse(text: String): PictureScenes = PictureScenes(
            text.lineSequence().mapNotNull { line ->
                if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                val f = line.split('\t')
                if (f.size < 6) return@mapNotNull null
                PictureScene(
                    book = f[0],
                    chapter = f[1].toIntOrNull() ?: return@mapNotNull null,
                    first = f[2].toIntOrNull() ?: return@mapNotNull null,
                    last = f[3].toIntOrNull() ?: return@mapNotNull null,
                    title = f[4],
                    prompt = f[5]
                )
            }.toList()
        )
    }
}
