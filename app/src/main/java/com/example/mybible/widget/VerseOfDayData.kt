package com.example.mybible.widget

/**
 * One curated verse shown on the home screen widget.
 * [book]/[chapter]/[verse] are used to deep-link the reader to this exact
 * location when the verse text (not the "Continue Reading" row) is tapped.
 */
data class VerseOfDay(
    val reference: String,
    val text: String,
    val book: String,
    val chapter: Int,
    val verse: Int
)

/**
 * Simple, dependency-free verse source for the widget: a fixed curated list
 * (KJV text, public domain), rotated by day-of-year. No Room, no network —
 * keeps the widget resilient even before the KJV import has finished, and
 * keeps widget updates cheap (Glance re-renders synchronously off this list).
 */
object VerseOfDayRepository {

    val VERSES: List<VerseOfDay> = listOf(
        VerseOfDay("John 3:16", "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life.", "John", 3, 16),
        VerseOfDay("Psalm 23:1", "The LORD is my shepherd; I shall not want.", "Psalms", 23, 1),
        VerseOfDay("Philippians 4:13", "I can do all things through Christ which strengtheneth me.", "Philippians", 4, 13),
        VerseOfDay("Proverbs 3:5-6", "Trust in the LORD with all thine heart; and lean not unto thine own understanding. In all thy ways acknowledge him, and he shall direct thy paths.", "Proverbs", 3, 5),
        VerseOfDay("Romans 8:28", "And we know that all things work together for good to them that love God, to them who are the called according to his purpose.", "Romans", 8, 28),
        VerseOfDay("Joshua 1:9", "Have not I commanded thee? Be strong and of a good courage; be not afraid, neither be thou dismayed: for the LORD thy God is with thee whithersoever thou goest.", "Joshua", 1, 9),
        VerseOfDay("Isaiah 41:10", "Fear thou not; for I am with thee: be not dismayed; for I am thy God: I will strengthen thee; yea, I will help thee.", "Isaiah", 41, 10),
        VerseOfDay("Matthew 11:28", "Come unto me, all ye that labour and are heavy laden, and I will give you rest.", "Matthew", 11, 28),
        VerseOfDay("Jeremiah 29:11", "For I know the thoughts that I think toward you, saith the LORD, thoughts of peace, and not of evil, to give you an expected end.", "Jeremiah", 29, 11),
        VerseOfDay("Psalm 46:1", "God is our refuge and strength, a very present help in trouble.", "Psalms", 46, 1),
        VerseOfDay("2 Timothy 1:7", "For God hath not given us the spirit of fear; but of power, and of love, and of a sound mind.", "2 Timothy", 1, 7),
        VerseOfDay("Galatians 5:22-23", "But the fruit of the Spirit is love, joy, peace, longsuffering, gentleness, goodness, faith, meekness, temperance: against such there is no law.", "Galatians", 5, 22),
        VerseOfDay("Psalm 119:105", "Thy word is a lamp unto my feet, and a light unto my path.", "Psalms", 119, 105),
        VerseOfDay("Romans 12:2", "And be not conformed to this world: but be ye transformed by the renewing of your mind, that ye may prove what is that good, and acceptable, and perfect, will of God.", "Romans", 12, 2),
        VerseOfDay("Matthew 6:33", "But seek ye first the kingdom of God, and his righteousness; and all these things shall be added unto you.", "Matthew", 6, 33),
        VerseOfDay("Psalm 27:1", "The LORD is my light and my salvation; whom shall I fear? the LORD is the strength of my life; of whom shall I be afraid?", "Psalms", 27, 1),
        VerseOfDay("Isaiah 40:31", "But they that wait upon the LORD shall renew their strength; they shall mount up with wings as eagles; they shall run, and not be weary; and they shall walk, and not faint.", "Isaiah", 40, 31),
        VerseOfDay("John 14:6", "Jesus saith unto him, I am the way, the truth, and the life: no man cometh unto the Father, but by me.", "John", 14, 6),
        VerseOfDay("Psalm 34:8", "O taste and see that the LORD is good: blessed is the man that trusteth in him.", "Psalms", 34, 8),
        VerseOfDay("1 Corinthians 13:4", "Charity suffereth long, and is kind; charity envieth not; charity vaunteth not itself, is not puffed up.", "1 Corinthians", 13, 4),
        VerseOfDay("Proverbs 18:10", "The name of the LORD is a strong tower: the righteous runneth into it, and is safe.", "Proverbs", 18, 10),
        VerseOfDay("Psalm 121:1-2", "I will lift up mine eyes unto the hills, from whence cometh my help. My help cometh from the LORD, which made heaven and earth.", "Psalms", 121, 1),
        VerseOfDay("Matthew 5:14", "Ye are the light of the world. A city that is set on an hill cannot be hid.", "Matthew", 5, 14),
        VerseOfDay("Romans 5:8", "But God commendeth his love toward us, in that, while we were yet sinners, Christ died for us.", "Romans", 5, 8),
        VerseOfDay("Psalm 91:1-2", "He that dwelleth in the secret place of the most High shall abide under the shadow of the Almighty. I will say of the LORD, He is my refuge and my fortress: my God; in him will I trust.", "Psalms", 91, 1),
        VerseOfDay("James 1:2-3", "My brethren, count it all joy when ye fall into divers temptations; Knowing this, that the trying of your faith worketh patience.", "James", 1, 2),
        VerseOfDay("Deuteronomy 31:6", "Be strong and of a good courage, fear not, nor be afraid of them: for the LORD thy God, he it is that doth go with thee.", "Deuteronomy", 31, 6),
        VerseOfDay("Psalm 37:4", "Delight thyself also in the LORD: and he shall give thee the desires of thine heart.", "Psalms", 37, 4),
        VerseOfDay("Hebrews 11:1", "Now faith is the substance of things hoped for, the evidence of things not seen.", "Hebrews", 11, 1),
        VerseOfDay("Colossians 3:23", "And whatsoever ye do, do it heartily, as to the Lord, and not unto men.", "Colossians", 3, 23),
        VerseOfDay("Psalm 139:14", "I will praise thee; for I am fearfully and wonderfully made: marvellous are thy works; and that my soul knoweth right well.", "Psalms", 139, 14),
        VerseOfDay("1 John 4:19", "We love him, because he first loved us.", "1 John", 4, 19),
        VerseOfDay("Ephesians 2:8-9", "For by grace are ye saved through faith; and that not of yourselves: it is the gift of God: Not of works, lest any man should boast.", "Ephesians", 2, 8),
        VerseOfDay("Psalm 118:24", "This is the day which the LORD hath made; we will rejoice and be glad in it.", "Psalms", 118, 24),
        VerseOfDay("Matthew 28:19-20", "Go ye therefore, and teach all nations, baptizing them in the name of the Father, and of the Son, and of the Holy Ghost.", "Matthew", 28, 19),
        VerseOfDay("Nahum 1:7", "The LORD is good, a strong hold in the day of trouble; and he knoweth them that trust in him.", "Nahum", 1, 7),
        VerseOfDay("Psalm 55:22", "Cast thy burden upon the LORD, and he shall sustain thee: he shall never suffer the righteous to be moved.", "Psalms", 55, 22),
        VerseOfDay("Philippians 4:6-7", "Be careful for nothing; but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God.", "Philippians", 4, 6),
        VerseOfDay("John 16:33", "These things I have spoken unto you, that in me ye might have peace. In the world ye shall have tribulation: but be of good cheer; I have overcome the world.", "John", 16, 33),
        VerseOfDay("Micah 6:8", "He hath shewed thee, O man, what is good; and what doth the LORD require of thee, but to do justly, and to love mercy, and to walk humbly with thy God?", "Micah", 6, 8)
    )

    /** Stable per-day pick: same verse all day, changes at midnight local device time. */
    fun verseForToday(): VerseOfDay {
        val dayIndex = (System.currentTimeMillis() / 86_400_000L).toInt()
        val idx = ((dayIndex % VERSES.size) + VERSES.size) % VERSES.size
        return VERSES[idx]
    }
}
