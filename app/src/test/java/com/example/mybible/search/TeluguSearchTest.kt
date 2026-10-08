package com.example.mybible.search

import com.example.mybible.model.RelatedKind
import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Searches typed in Telugu, over a few real verses of the bundled Telugu Bible. */
class TeluguSearchTest {

    private val verses = listOf(
        Triple("Genesis 4:1", "ఆదాము తన భార్యయైన హవ్వను కూడినప్పుడు ఆమె గర్భవతియై కయీనును కని యెహోవా దయవలన నేనొక మనుష్యుని సంపాదించుకొన్నాననెను.", 0),
        Triple("Genesis 6:12", "దేవుడు భూలోకమును చూచినప్పుడు అది చెడిపోయి యుండెను; భూమిమీద సమస్త శరీరులు తమ మార్గమును చెరిపివేసుకొని యుండిరి.", 0),
        Triple("1 Chronicles 16:36", "ఈలాగున వారు పాడగా జనులందరు ఆమేన్‌ అని చెప్పి యెహోవాను స్తుతించిరి.", 0),
        Triple("Psalms 51:10", "దేవా, నాయందు శుద్ధహృదయము కలుగజేయుము నా అంతరంగములో స్థిరమైన మనస్సును నూతనముగా పుట్టించుము.", 0),
        Triple("Matthew 1:1", "అబ్రాహాము కుమారుడగు దావీదు కుమారుడైన యేసు క్రీస్తు వంశావళి.", 0),
        Triple("John 3:16", "దేవుడు లోకమును ఎంతో ప్రేమించెను. కాగా ఆయన తన అద్వితీయకుమారునిగా పుట్టిన వానియందు విశ్వాసముంచు ప్రతివాడును నశింపక నిత్యజీవము పొందునట్లు ఆయనను అనుగ్రహించెను.", 0),
        Triple("Acts 3:20", "మీకొరకు నియమించిన క్రీస్తుయేసును ఆయన పంపునట్లును మీ పాపములు తుడిచివేయబడు నిమిత్తమును మారుమనస్సు నొంది తిరుగుడి.", 0),
        Triple("2 Corinthians 5:19", "అదేమనగా, దేవుడు వారి అపరాధములను వారిమీద మోపక, క్రీస్తునందు లోకమును తనతో  సమాధానపరచుకొనుచు, ఆ సమాధానవాక్యమును మాకు అప్పగించెను.", 0)
    ).map { (ref, telugu, _) ->
        val book = ref.substringBeforeLast(' ')
        val (chapter, verse) = ref.substringAfterLast(' ').split(':').map(String::toInt)
        Verse(book, chapter, verse, text = "", teluguText = telugu)
    }

    private fun search(query: String, disabled: Set<String> = emptySet()) = TeluguSearch.search(query, verses, disabled)
    private fun SearchHit.ref() = "${verse.book} ${verse.chapter}:${verse.number}"
    private fun SearchOutcome.exact() = hits.take(exactCount).map { it.ref() }
    private fun SearchOutcome.inside() = hits.drop(exactCount).map { it.ref() }
    private fun SearchOutcome.hit(ref: String) = hits.first { it.ref() == ref }
    private fun SearchHit.marked() = highlights.map { text.substring(it) }

    @Test
    fun aWordWhereAWordStarts() {
        // దయ, mercy, with its ending: దయవలన. Not inside హృదయము, heart.
        val mercy = search("దయ")
        assertEquals(listOf("Genesis 4:1"), mercy.exact())
        assertEquals(listOf("దయవలన"), mercy.hit("Genesis 4:1").marked())
        // Too short to look for inside longer words.
        assertTrue(mercy.inside().isEmpty())
        assertTrue(mercy.sources.isEmpty())
    }

    @Test
    fun aLongerWordInsideLongerOnes() {
        val jesus = search("యేసు")
        assertEquals(listOf("Matthew 1:1"), jesus.exact())
        assertEquals(listOf("Acts 3:20"), jesus.inside())
        assertEquals(RelatedKind.INSIDE_LONGER_WORDS, jesus.relatedKind)
        assertEquals(listOf("Inside “క్రీస్తుయేసును”"), jesus.hit("Acts 3:20").reasons)
        assertEquals(listOf("క్రీస్తుయేసును"), jesus.hit("Acts 3:20").marked())
        assertEquals("Inside longer words" to 1, jesus.sources.single().let { it.label to it.count })

        // Switched off, they're gone, and the chip says what it would bring back.
        val off = search("యేసు", disabled = setOf(TeluguSearch.INSIDE))
        assertTrue(off.inside().isEmpty())
        assertFalse(off.sources.single().enabled)
        assertEquals(1, off.sources.single().count)
    }

    @Test
    fun theWordsInARowFirst() {
        // God ... the world: in a row in John 3:16, apart in 2 Corinthians 5:19, and in Genesis
        // 6:12 the world only inside భూలోకమును, the earth.
        val world = search("దేవుడు లోకమును")
        assertEquals(listOf("John 3:16", "2 Corinthians 5:19"), world.exact())
        assertEquals(listOf("Genesis 6:12"), world.inside())
        assertEquals(listOf("దేవుడు", "లోకమును"), world.hit("John 3:16").marked())
    }

    @Test
    fun theTextsInvisibleJoiners() {
        // The text writes ఆమేన్ with a zero-width non-joiner after it; the query doesn't have one.
        val amen = search("ఆమేన్")
        assertEquals(listOf("1 Chronicles 16:36"), amen.exact())
        assertEquals(listOf("ఆమేన్‌"), amen.hit("1 Chronicles 16:36").marked())
        assertEquals(listOf("ఆమేన్"), TeluguSearch.wordsOf("ఆమేన్‌"))
        assertTrue(hasTeluguLetter("ప్రేమ") && !hasTeluguLetter("love"))
    }
}
