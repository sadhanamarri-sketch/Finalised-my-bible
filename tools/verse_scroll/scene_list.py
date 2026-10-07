"""The scenes Verse Scroll ships: kind, palette and seed. Seeds fix each painting, so keep them as
they are unless a scene should change."""
SAMPLES = [
    ('ridges', 'dawn', 11), ('ridges', 'dusk', 12), ('ridges', 'mist', 13),
    ('hills', 'pasture', 21), ('hills', 'golden', 22),
    ('sea', 'dusk', 31), ('sea', 'morning', 32), ('sea', 'golden', 33),
    ('night', 'night', 41), ('moon', 'night', 42),
    ('desert', 'desert', 51), ('desert', 'golden', 52),
    ('forest', 'mist', 61), ('forest', 'dawn', 62),
    ('light', 'golden', 71), ('light', 'dawn', 72),
    ('abstract', 'ember', 81), ('abstract', 'lagoon', 82),
]
WANT = {
    'ridges': ['dawn', 'dusk', 'mist', 'golden', 'morning', 'rose', 'teal', 'storm', 'lavender', 'harvest'],
    'hills': ['pasture', 'golden', 'morning', 'rose', 'sage', 'harvest', 'mist', 'dawn'],
    'sea': ['dusk', 'morning', 'golden', 'rose', 'teal', 'storm', 'lavender', 'harvest', 'sage'],
    'night': ['night', 'nightteal', 'nightplum'],
    'moon': ['night', 'nightteal', 'nightplum'],
    'desert': ['desert', 'golden', 'dusk', 'rose', 'harvest', 'dawn', 'lavender'],
    'forest': ['mist', 'dawn', 'golden', 'dusk', 'teal', 'morning', 'storm', 'sage'],
    'light': ['golden', 'dawn', 'morning', 'rose', 'dusk', 'teal', 'storm', 'lavender'],
    'abstract': ['ember', 'lagoon', 'plum', 'ocean', 'grove', 'gold', 'blush', 'daybreak'],
    'snow': ['winter', 'winter', 'wintergold', 'winterdusk', 'winterdusk', 'winterblue', 'winternight', 'winternight', 'winternight'],
    'olives': ['golden', 'morning', 'dawn', 'harvest', 'sage', 'dusk', 'mist', 'rose', 'pasture'],
    'lake': ['dawn', 'mist', 'teal', 'dusk', 'golden', 'morning', 'rose', 'storm', 'lavender'],
    'wheat': ['golden', 'morning', 'harvest', 'dawn', 'dusk', 'storm', 'rose', 'lavender', 'pasture'],
}


def all_scenes():
    out = list(SAMPLES)
    used = {(k, p) for k, p, _ in SAMPLES}
    seed = 1000
    for kind, pals in WANT.items():
        for pal in pals:
            if (kind, pal) in used and kind not in ('snow',):
                used.discard((kind, pal))  # covered by the matching scene in SAMPLES
                continue
            seed += 7
            out.append((kind, pal, seed))
    return out


# Kinds still painted (and still taking their seeds, so the scenes after them keep theirs) but not
# shipped. Olive groves were left out after review.
LEFT_OUT = {'olives'}

SCENES = [s for s in all_scenes() if s[0] not in LEFT_OUT]

if __name__ == '__main__':
    from collections import Counter
    print(len(SCENES), Counter(k for k, _, _ in SCENES))
