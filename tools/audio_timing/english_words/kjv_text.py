"""The app's King James text as the aligner is given it (lowercase words, apostrophes kept), and what the
dramatized KJV's narrator says before the text: each book's title as the King James Version prints it,
then "Chapter one" (or "Psalm one") before a book's first chapter when it has more than one."""
import re

TITLES = {
    'Genesis': 'The First Book of Moses called Genesis', 'Exodus': 'The Second Book of Moses called Exodus',
    'Leviticus': 'The Third Book of Moses called Leviticus', 'Numbers': 'The Fourth Book of Moses called Numbers',
    'Deuteronomy': 'The Fifth Book of Moses called Deuteronomy', 'Joshua': 'The Book of Joshua', 'Judges': 'The Book of Judges',
    'Ruth': 'The Book of Ruth', '1 Samuel': 'The First Book of Samuel otherwise called the First Book of the Kings',
    '2 Samuel': 'The Second Book of Samuel otherwise called the Second Book of the Kings',
    '1 Kings': 'The First Book of the Kings commonly called the Third Book of the Kings',
    '2 Kings': 'The Second Book of the Kings commonly called the Fourth Book of the Kings',
    '1 Chronicles': 'The First Book of the Chronicles', '2 Chronicles': 'The Second Book of the Chronicles', 'Ezra': 'Ezra',
    'Nehemiah': 'The Book of Nehemiah', 'Esther': 'The Book of Esther', 'Job': 'The Book of Job', 'Psalms': 'The Book of Psalms',
    'Proverbs': 'The Proverbs', 'Ecclesiastes': 'Ecclesiastes or the Preacher', 'Song of Solomon': 'The Song of Solomon',
    'Isaiah': 'The Book of the Prophet Isaiah', 'Jeremiah': 'The Book of the Prophet Jeremiah', 'Lamentations': 'The Lamentations of Jeremiah',
    'Ezekiel': 'The Book of the Prophet Ezekiel', 'Daniel': 'The Book of Daniel', 'Hosea': 'Hosea', 'Joel': 'Joel', 'Amos': 'Amos',
    'Obadiah': 'Obadiah', 'Jonah': 'Jonah', 'Micah': 'Micah', 'Nahum': 'Nahum', 'Habakkuk': 'Habakkuk', 'Zephaniah': 'Zephaniah',
    'Haggai': 'Haggai', 'Zechariah': 'Zechariah', 'Malachi': 'Malachi', 'Matthew': 'The Gospel according to Saint Matthew',
    'Mark': 'The Gospel according to Saint Mark', 'Luke': 'The Gospel according to Saint Luke', 'John': 'The Gospel according to Saint John',
    'Acts': 'The Acts of the Apostles', 'Romans': 'The Epistle of Paul the Apostle to the Romans',
    '1 Corinthians': 'The First Epistle of Paul the Apostle to the Corinthians', '2 Corinthians': 'The Second Epistle of Paul the Apostle to the Corinthians',
    'Galatians': 'The Epistle of Paul the Apostle to the Galatians', 'Ephesians': 'The Epistle of Paul the Apostle to the Ephesians',
    'Philippians': 'The Epistle of Paul the Apostle to the Philippians', 'Colossians': 'The Epistle of Paul the Apostle to the Colossians',
    '1 Thessalonians': 'The First Epistle of Paul the Apostle to the Thessalonians', '2 Thessalonians': 'The Second Epistle of Paul the Apostle to the Thessalonians',
    '1 Timothy': 'The First Epistle of Paul the Apostle to Timothy', '2 Timothy': 'The Second Epistle of Paul the Apostle to Timothy',
    'Titus': 'The Epistle of Paul to Titus', 'Philemon': 'The Epistle of Paul to Philemon', 'Hebrews': 'The Epistle of Paul the Apostle to the Hebrews',
    'James': 'The General Epistle of James', '1 Peter': 'The First Epistle General of Peter', '2 Peter': 'The Second Epistle General of Peter',
    '1 John': 'The First Epistle General of John', '2 John': 'The Second Epistle of John', '3 John': 'The Third Epistle of John',
    'Jude': 'The General Epistle of Jude', 'Revelation': 'The Revelation of Saint John the Divine',
}

WORD = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)*")


def words(text):
    """A text's words as the aligner's dictionary has them: lowercase, a possessive's apostrophe kept."""
    return [w.lower() for w in WORD.findall(text.replace('’', "'"))]


def opening(book, chapters):
    """What's said before the book's first verse: its title, and its first chapter's heading if it has more than one."""
    said = words(TITLES[book])
    if chapters > 1: said += ['psalm' if book == 'Psalms' else 'chapter', 'one']
    return said


def load(kjv_tsv):
    """{book: {chapter: [verse text, ...]}} in the Bible's order, from kjv.tsv."""
    out = {}
    for line in open(kjv_tsv, encoding='utf-8'):
        b, c, v, t = line.rstrip('\n').split('\t')
        out.setdefault(b, {}).setdefault(int(c), []).append(t)
    return out
