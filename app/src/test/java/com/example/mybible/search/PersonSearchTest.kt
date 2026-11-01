package com.example.mybible.search

import com.example.mybible.model.RelatedKind
import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A person and a subject, the subject in the person's own words (backlog 55), on real King James verses. */
class PersonSearchTest {

    private val books = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel",
        "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job", "Psalms", "Proverbs",
        "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
        "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark",
        "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians",
        "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews",
        "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"
    )

    private fun verse(ref: String, text: String, red: Boolean = false): IndexedVerse {
        val book = ref.substringBeforeLast(' ')
        val (chapter, number) = ref.substringAfterLast(' ').split(':').map(String::toInt)
        return IndexedVerse(book, books.indexOf(book), chapter, number, text, red)
    }

    private val index = BibleIndex(
        listOf(
            verse("Psalms 23:1", "The LORD is my shepherd; I shall not want."),
            verse("Psalms 80:1", "Give ear, O Shepherd of Israel, thou that leadest Joseph like a flock;"),
            verse("Proverbs 15:8", "The sacrifice of the wicked is an abomination to the LORD: but the prayer of the upright is his delight."),
            verse("Matthew 6:9", "After this manner therefore pray ye: Our Father which art in heaven, Hallowed be thy name.", red = true),
            verse("John 17:9", "I pray for them: I pray not for the world, but for them which thou hast given me; for they are thine.", red = true),
            verse("Acts 16:25", "And at midnight Paul and Silas prayed, and sang praises unto God: and the prisoners heard them."),
            verse("Romans 1:9", "For God is my witness, whom I serve with my spirit in the gospel of his Son, that without ceasing I make mention of you always in my prayers;"),
            verse("Romans 8:26", "Likewise the Spirit also helpeth our infirmities: for we know not what we should pray for as we ought: but the Spirit itself maketh intercession for us with groanings which cannot be uttered."),
            verse("1 Corinthians 4:13", "Being defamed, we intreat: we are made as the filth of the world, and are the offscouring of all things unto this day."),
            verse("1 Corinthians 13:4", "Charity suffereth long, and is kind; charity envieth not; charity vaunteth not itself, is not puffed up,"),
            verse("Ephesians 1:16", "Cease not to give thanks for you, making mention of you in my prayers;"),
            verse("Ephesians 3:14", "For this cause I bow my knees unto the Father of our Lord Jesus Christ,"),
            verse("Philippians 4:6", "Be careful for nothing; but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God."),
            verse("Colossians 1:9", "For this cause we also, since the day we heard it, do not cease to pray for you, and to desire that ye might be filled with the knowledge of his will in all wisdom and spiritual understanding;"),
            verse("James 5:16", "Confess your faults one to another, and pray one for another, that ye may be healed. The effectual fervent prayer of a righteous man availeth much.")
        )
    )
    private val smart = SmartSearch(index, TestBible.lexicon)
    private val people = PersonSearch(index, TestBible.lexicon, smart)

    private fun SearchHit.ref() = "${verse.book} ${verse.chapter}:${verse.number}"
    private fun find(query: String): SearchOutcome = people.search(query, smart.search(query))

    @Test
    fun paulIntercessionIsHisPrayersForOthers() {
        val paul = find("Paul intercession")
        assertEquals(RelatedKind.PERSON, paul.relatedKind)
        assertEquals("In Paul's letters", paul.relatedHeading)
        assertEquals(0, paul.exactCount)
        // Those praying for someone first, then the rest; Acts isn't his letters, Ephesians 3:14 names no
        // prayer, and his "intreat" never is one (1 Corinthians 4:13).
        assertEquals(
            listOf("Romans 1:9", "Romans 8:26", "Ephesians 1:16", "Colossians 1:9", "Philippians 4:6"),
            paul.hits.map { it.ref() }
        )
        val romans = paul.hits[0]
        assertEquals(listOf("mention", "prayers"), romans.highlights.map { romans.text.substring(it) })
        assertTrue(paul.relatedNote!!.startsWith("Romans to Philemon. Intercession read as praying for others"))
        // The way people say it: Paul's, intercessory, prayers.
        assertEquals(paul.hits.map { it.ref() }, find("Paul's intercessory prayers").hits.map { it.ref() }.sortedBy { r -> paul.hits.indexOfFirst { it.ref() == r } })
    }

    @Test
    fun theVersesWithEveryWordComeFirst() {
        // Acts 16:25 says "Paul" and "prayed": the verses with both, then his letters.
        val paul = find("Paul prayed")
        assertEquals(listOf("Acts 16:25"), paul.hits.take(paul.exactCount).map { it.ref() })
        assertTrue(paul.hits.drop(paul.exactCount).map { it.ref() }.containsAll(listOf("Romans 1:9", "Colossians 1:9")))
        assertFalse(paul.hits.drop(paul.exactCount).any { it.ref() == "Acts 16:25" })
    }

    @Test
    fun othersOwnWords() {
        // Jesus: his words in red.
        assertEquals(listOf("Matthew 6:9", "John 17:9"), find("Jesus prayer").hits.map { it.ref() })
        // David: the psalms titled his (23, not 80, Asaph's).
        assertEquals(listOf("Psalms 23:1"), find("David shepherd").hits.map { it.ref() })
        assertEquals("In David's psalms", find("David shepherd").relatedHeading)
        // Solomon: Proverbs.
        assertEquals(listOf("Proverbs 15:8"), find("Solomon prayer").hits.map { it.ref() })
        // James: his letter.
        assertEquals(listOf("James 5:16"), find("James prayer").hits.map { it.ref() })
    }

    @Test
    fun aNameAloneOrNothingMore() {
        val alone = smart.search("Paul")
        assertEquals(alone, people.search("Paul", alone))
        assertEquals(RelatedKind.SAME_MEANING, find("the Paul").relatedKind)
        // "Jesus Christ": both his names, no subject.
        assertNull(find("Jesus Christ").relatedHeading)
        // Not "mark": the mark of the beast.
        assertNull(find("mark prayer").relatedHeading)
    }

    @Test
    fun intercessoryIsIntercession() {
        assertEquals(listOf("Romans 8:26"), smart.search("intercessory").hits.map { it.ref() })
    }

    @Test
    fun teluguNames() {
        val (paul, subject) = PersonSearch.teluguPerson(listOf("పౌలు", "ప్రార్థన"))!!
        assertEquals(PersonSearch.PAUL, paul)
        assertEquals(listOf("ప్రార్థన"), subject)
        assertEquals(PersonSearch.PAUL, PersonSearch.teluguPerson(listOf("పౌలునకు", "ప్రార్థన"))!!.first)
        assertEquals(PersonSearch.JESUS, PersonSearch.teluguPerson(listOf("యేసుక్రీస్తు", "ప్రేమ"))!!.first)
        // యోనాతాను is Jonathan, not Jonah (యోనా).
        assertNull(PersonSearch.teluguPerson(listOf("యోనాతాను", "ప్రేమ")))
        // A name alone: nothing more to look for.
        assertNull(PersonSearch.teluguPerson(listOf("పౌలు")))
        assertEquals(listOf("ప్రార్థ", "విజ్ఞాపన", "విన్నప"), PersonSearch.teluguReading("ప్రార్థన"))
        assertEquals(listOf("ప్రేమ"), PersonSearch.teluguReading("ప్రేమ"))
    }

    @Test
    fun teluguHitsAndMerge() {
        val colossians = Verse("Colossians", 1, 9, "", teluguText = "అందుచేత ఈ సంగతి వినిననాటనుండి మేమును మీ నిమిత్తము ప్రార్థన చేయుట మానక,")
        val acts = Verse("Acts", 16, 25, "", teluguText = "అయితే మధ్యరాత్రి వేళ పౌలును సీలయు దేవునికి ప్రార్థనచేయుచు")
        val readings = listOf(PersonSearch.teluguReading("ప్రార్థన"))
        val hits = PersonSearch.teluguHits(PersonSearch.PAUL, readings, listOf(colossians, acts))
        assertEquals(listOf("Colossians 1:9"), hits.map { it.ref() })
        assertEquals(listOf("ప్రార్థన"), hits[0].highlights.map { hits[0].text.substring(it) })
        // After the verses with both words; one that's there already isn't again.
        val both = SearchOutcome(hits = listOf(SearchHit(acts, acts.teluguText!!)), exactCount = 1)
        val merged = PersonSearch.merge(both, PersonSearch.PAUL, hits + SearchHit(acts, acts.teluguText!!), "note")
        assertEquals(listOf("Acts 16:25", "Colossians 1:9"), merged.hits.map { it.ref() })
        assertEquals(1, merged.exactCount)
        assertEquals("In Paul's letters", merged.relatedHeading)
        assertTrue(PersonSearch.teluguNote(PersonSearch.PAUL, readings).contains("ప్రార్థన"))
    }
}
