package com.example.mybible.model

/**
 * The Bible's books by their Telugu names, as the Telugu Bible (the Bible Society's) prints them, for
 * references shown beside the Telugu text: "యోహాను 3:16" for John 3:16.
 */
object TeluguBooks {
    private val NAMES = mapOf(
        "Genesis" to "ఆదికాండము",
        "Exodus" to "నిర్గమకాండము",
        "Leviticus" to "లేవీయకాండము",
        "Numbers" to "సంఖ్యాకాండము",
        "Deuteronomy" to "ద్వితీయోపదేశకాండము",
        "Joshua" to "యెహోషువ",
        "Judges" to "న్యాయాధిపతులు",
        "Ruth" to "రూతు",
        "1 Samuel" to "1 సమూయేలు",
        "2 Samuel" to "2 సమూయేలు",
        "1 Kings" to "1 రాజులు",
        "2 Kings" to "2 రాజులు",
        "1 Chronicles" to "1 దినవృత్తాంతములు",
        "2 Chronicles" to "2 దినవృత్తాంతములు",
        "Ezra" to "ఎజ్రా",
        "Nehemiah" to "నెహెమ్యా",
        "Esther" to "ఎస్తేరు",
        "Job" to "యోబు",
        "Psalms" to "కీర్తనలు",
        "Proverbs" to "సామెతలు",
        "Ecclesiastes" to "ప్రసంగి",
        "Song of Solomon" to "పరమగీతము",
        "Isaiah" to "యెషయా",
        "Jeremiah" to "యిర్మీయా",
        "Lamentations" to "విలాపవాక్యములు",
        "Ezekiel" to "యెహెజ్కేలు",
        "Daniel" to "దానియేలు",
        "Hosea" to "హోషేయ",
        "Joel" to "యోవేలు",
        "Amos" to "ఆమోసు",
        "Obadiah" to "ఓబద్యా",
        "Jonah" to "యోనా",
        "Micah" to "మీకా",
        "Nahum" to "నహూము",
        "Habakkuk" to "హబక్కూకు",
        "Zephaniah" to "జెఫన్యా",
        "Haggai" to "హగ్గయి",
        "Zechariah" to "జెకర్యా",
        "Malachi" to "మలాకీ",
        "Matthew" to "మత్తయి",
        "Mark" to "మార్కు",
        "Luke" to "లూకా",
        "John" to "యోహాను",
        "Acts" to "అపొస్తలుల కార్యములు",
        "Romans" to "రోమీయులకు",
        "1 Corinthians" to "1 కొరింథీయులకు",
        "2 Corinthians" to "2 కొరింథీయులకు",
        "Galatians" to "గలతీయులకు",
        "Ephesians" to "ఎఫెసీయులకు",
        "Philippians" to "ఫిలిప్పీయులకు",
        "Colossians" to "కొలొస్సయులకు",
        "1 Thessalonians" to "1 థెస్సలొనీకయులకు",
        "2 Thessalonians" to "2 థెస్సలొనీకయులకు",
        "1 Timothy" to "1 తిమోతికి",
        "2 Timothy" to "2 తిమోతికి",
        "Titus" to "తీతుకు",
        "Philemon" to "ఫిలేమోనుకు",
        "Hebrews" to "హెబ్రీయులకు",
        "James" to "యాకోబు",
        "1 Peter" to "1 పేతురు",
        "2 Peter" to "2 పేతురు",
        "1 John" to "1 యోహాను",
        "2 John" to "2 యోహాను",
        "3 John" to "3 యోహాను",
        "Jude" to "యూదా",
        "Revelation" to "ప్రకటన గ్రంథము"
    )

    /** [book]'s Telugu name ([book] as the app names it, "Song of Solomon"); null for a name it doesn't know. */
    fun nameOf(book: String): String? = NAMES[book]

    /** The English names it knows, in the Bible's order. */
    val books: List<String> get() = NAMES.keys.toList()
}
