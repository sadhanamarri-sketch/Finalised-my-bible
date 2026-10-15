"""Telugu words to phones, from their letters: each consonant, then its vowel sign, or no vowel after
a virama, or the inherent a. Anusvara is a nasal like the consonant after it."""
import re

CONS = {'క': 'k', 'ఖ': 'kh', 'గ': 'g', 'ఘ': 'gh', 'ఙ': 'ng', 'చ': 'c', 'ఛ': 'ch', 'జ': 'j', 'ఝ': 'jh', 'ఞ': 'ny',
        'ట': 'T', 'ఠ': 'Th', 'డ': 'D', 'ఢ': 'Dh', 'ణ': 'N', 'త': 't', 'థ': 'th', 'ద': 'd', 'ధ': 'dh', 'న': 'n',
        'ప': 'p', 'ఫ': 'ph', 'బ': 'b', 'భ': 'bh', 'మ': 'm', 'య': 'y', 'ర': 'r', 'ఱ': 'r', 'ల': 'l', 'ళ': 'L',
        'ఴ': 'L', 'వ': 'v', 'శ': 'sh', 'ష': 'S', 'స': 's', 'హ': 'h', 'ౘ': 'c', 'ౙ': 'j', 'ౚ': 'r'}
VOWELS = {'అ': 'a', 'ఆ': 'aa', 'ఇ': 'i', 'ఈ': 'ii', 'ఉ': 'u', 'ఊ': 'uu', 'ఋ': 'ru', 'ౠ': 'ruu', 'ఌ': 'li',
          'ౡ': 'lii', 'ఎ': 'e', 'ఏ': 'ee', 'ఐ': 'ai', 'ఒ': 'o', 'ఓ': 'oo', 'ఔ': 'au'}
SIGNS = {'ా': 'aa', 'ి': 'i', 'ీ': 'ii', 'ు': 'u', 'ూ': 'uu', 'ృ': 'ru', 'ౄ': 'ruu', 'ె': 'e', 'ే': 'ee',
         'ై': 'ai', 'ొ': 'o', 'ో': 'oo', 'ౌ': 'au', 'ౢ': 'li', 'ౣ': 'lii'}
VIRAMA, ANUSVARA, VISARGA, CANDRA = '్', 'ం', 'ః', 'ఁ'
NASAL_FOR = {'k': 'ng', 'kh': 'ng', 'g': 'ng', 'gh': 'ng', 'c': 'ny', 'ch': 'ny', 'j': 'ny', 'jh': 'ny',
             'T': 'N', 'Th': 'N', 'D': 'N', 'Dh': 'N', 't': 'n', 'th': 'n', 'd': 'n', 'dh': 'n',
             'p': 'm', 'ph': 'm', 'b': 'm', 'bh': 'm', 'm': 'm'}
SPLIT = {'ru': ['r', 'u'], 'ruu': ['r', 'uu'], 'li': ['l', 'i'], 'lii': ['l', 'ii']}


def clean(word):
    """The word's Telugu letters only (punctuation, digits and marks like ‌ dropped)."""
    return re.sub(r'[^ఀ-౿]', '', word.replace('‌', '').replace('‍', ''))


def phones(word):
    w = clean(word)
    out, i = [], 0
    while i < len(w):
        ch = w[i]
        if ch in CONS:
            out.append(CONS[ch]); nxt = w[i + 1] if i + 1 < len(w) else ''
            if nxt in SIGNS: out += SPLIT.get(SIGNS[nxt], [SIGNS[nxt]]); i += 2; continue
            if nxt == VIRAMA: i += 2; continue
            out.append('a'); i += 1; continue
        if ch in VOWELS: out += SPLIT.get(VOWELS[ch], [VOWELS[ch]]); i += 1; continue
        if ch == ANUSVARA:
            nxt = w[i + 1] if i + 1 < len(w) else ''
            out.append(NASAL_FOR.get(CONS.get(nxt, ''), 'm')); i += 1; continue
        if ch == VISARGA: out.append('h'); i += 1; continue
        i += 1      # candrabindu, avagraha, stray signs: no sound of their own here
    return out


if __name__ == '__main__':
    for w in ['ఆదియందు', 'దేవుడు', 'భూమ్యాకాశములను', 'సృజించెను.', 'సంఘము', 'అల్లాడుచుండెను;', 'ద్వితీయోపదేశకాండము', 'దుఃఖము']:
        print(w, ' '.join(phones(w)))
