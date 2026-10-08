package com.example.mybible.search

import java.io.File

/**
 * A handful of real King James verses, with Search's real bundled data, whose Greek and Hebrew
 * verse lists and Nave's topics name real verses: the ones here are found through them.
 */
object TestBible {
    private val books = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
        "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
        "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah",
        "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah",
        "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
        "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
        "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians",
        "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter",
        "1 John", "2 John", "3 John", "Jude", "Revelation"
    )

    val verses = listOf(
        Triple("Genesis 1:1", 0, "In the beginning God created the heaven and the earth."),
        Triple("Genesis 1:17", 0, "And God set them in the firmament of the heaven to give light upon the earth,"),
        Triple("Leviticus 19:18", 0, "Thou shalt not avenge, nor bear any grudge against the children of thy people, but thou shalt love thy neighbour as thyself: I am the LORD."),
        Triple("Judges 16:15", 0, "And she said unto him, How canst thou say, I love thee, when thine heart is not with me? thou hast mocked me these three times, and hast not told me wherein thy great strength lieth."),
        Triple("Psalms 18:1", 0, "I will love thee, O LORD, my strength."),
        Triple("Psalms 23:1", 0, "The LORD is my shepherd; I shall not want."),
        Triple("Matthew 1:18", 0, "Now the birth of Jesus Christ was on this wise: When as his mother Mary was espoused to Joseph, before they came together, she was found with child of the Holy Ghost."),
        Triple("Matthew 4:20", 0, "And they straightway left their nets, and followed him."),
        Triple("Matthew 6:14", 0, "For if ye forgive men their trespasses, your heavenly Father will also forgive you:"),
        Triple("Matthew 6:25", 0, "Therefore I say unto you, Take no thought for your life, what ye shall eat, or what ye shall drink; nor yet for your body, what ye shall put on. Is not the life more than meat, and the body than raiment?"),
        Triple("Matthew 6:34", 0, "Take therefore no thought for the morrow: for the morrow shall take thought for the things of itself. Sufficient unto the day is the evil thereof."),
        Triple("Matthew 11:28", 0, "Come unto me, all ye that labour and are heavy laden, and I will give you rest."),
        Triple("Luke 10:41", 0, "And Jesus answered and said unto her, Martha, Martha, thou art careful and troubled about many things:"),
        Triple("Luke 11:13", 0, "If ye then, being evil, know how to give good gifts unto your children: how much more shall your heavenly Father give the Holy Spirit to them that ask him?"),
        Triple("John 3:16", 0, "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life."),
        Triple("John 8:32", 0, "And ye shall know the truth, and the truth shall make you free."),
        Triple("John 20:23", 0, "Whose soever sins ye remit, they are remitted unto them; and whose soever sins ye retain, they are retained."),
        Triple("1 Corinthians 13:4", 0, "Charity suffereth long, and is kind; charity envieth not; charity vaunteth not itself, is not puffed up,"),
        Triple("Philippians 4:6", 0, "Be careful for nothing; but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God."),
        Triple("Hebrews 11:1", 0, "Now faith is the substance of things hoped for, the evidence of things not seen."),
        Triple("1 Peter 5:7", 0, "Casting all your care upon him; for he careth for you."),
        Triple("1 John 4:8", 0, "He that loveth not knoweth not God; for God is love.")
    ).map { (ref, _, text) ->
        val book = ref.substringBeforeLast(' ')
        val (chapter, verse) = ref.substringAfterLast(' ').split(':').map(String::toInt)
        IndexedVerse(book, books.indexOf(book), chapter, verse, text)
    }

    private fun open(name: String) = File("src/main/assets/search/$name").inputStream()

    val index = BibleIndex(verses)
    val lexicon = SearchLexicon.load(::open)
    val topics = TopicBook.load(::open)
}
