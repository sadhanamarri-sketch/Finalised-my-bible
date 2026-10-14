"""How each recording names its books, and what its narrator says before each chapter."""
import os, re

from timing import synth, KEEP_GAP


def kjv_heading(book, chapter):
    """What Faith Comes By Hearing's KJV narrator says before a chapter: "Genesis chapter 1"."""
    return f'Psalm {chapter}.' if book == 'Psalms' else f'{book} chapter {chapter}.'


TELUGU_ALIASES = {'Michah': 'Micah', 'Zecharia': 'Zechariah'}
TELUGU_ORDINALS = ['మొదటి', 'రెండవ', 'మూడవ', 'నాలుగవ', 'అయిదవ', 'ఆరవ', 'ఏడవ', 'ఎనిమిదవ', 'తొమ్మిదవ', 'పదియవ']


def telugu_file(member, books):
    """(book, first chapter, last chapter or None, the book's Telugu name) from a Telugu file's name,
    like "Book of Genesis  - ఆదికాండము  Telugu Audio Bible   YGFM - Apostle Ratnaker.mp3" or
    "Psalms Part 2 - కీర్తనలు 73 - 150 అధ్యాయములు.mp3". [books]: the app's book names."""
    base = os.path.basename(member)[:-4]
    english, _, rest = base.partition(' - ')
    english = ' '.join(english.replace('Book of', '').split())
    first, last = 1, None
    part = re.match(r'Psalms Part (\d)', english)
    if part:
        english = 'Psalms'
        nums = [int(n) for n in re.findall(r'\d+', rest)]
        first, last = nums[0], nums[1]
    english = TELUGU_ALIASES.get(english, english)
    if english not in books:
        raise ValueError(f'no book for {base!r}')
    telugu = rest.split('Telugu')[0]
    telugu = re.split(r'\s\d', ' ' + telugu.strip())[0].strip() if part else telugu.strip()
    return english, first, last, ' '.join(telugu.split())


def telugu_heading(telugu, chapter):
    """What the Telugu narrator says before a chapter: the book's name, then "the Nth chapter"."""
    nth = TELUGU_ORDINALS[chapter - 1] if chapter <= len(TELUGU_ORDINALS) else f'{chapter} వ'
    return f'{telugu} {nth} అధ్యాయము'


def spoken_length(text, voice):
    """How long espeak takes to say [text], in seconds."""
    x, _, sr = synth([text], voice, 160)
    return len(x) / sr - KEEP_GAP
