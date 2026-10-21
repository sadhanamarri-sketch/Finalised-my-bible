# The Old Testament's quotations in the New

`app/src/main/assets/quotations.tsv` lists the New Testament's real quotations of the Old: no
allusions or echoes. The app marks a verse's cross-reference dagger in purple when it quotes the Old
Testament or is quoted in the New, and lists those links first, tagged "Quote", in Cross References
and in Verse Scroll's links sheet (however few votes they have). The cross references themselves are
left as they are; the list only labels links among them.

It's made in two steps, Python 3, no extra packages:

```sh
python3 find_quotations.py eng-kjv.osis.xml cross_references.txt ../../app/src/main/assets/search/topics.tsv candidates.tsv
# read candidates.tsv, and write the quotations into quotations.txt
python3 make_quotations.py eng-kjv.osis.xml cross_references.txt quotations.txt ../../app/src/main/assets
```

- `eng-kjv.osis.xml` and `cross_references.txt` are the files the app downloads (see `KjvImporter`
  and `CrossReferenceImporter`).
- `find_quotations.py` lists the candidates: the cross references between the Testaments that
  Nave's Topical Bible pairs under "Quotations and allusions" (from the app's `topics.tsv`), and
  those whose KJV texts share five words or more in a row, two of them not little words. Nave's
  list mixes quotations with allusions, and shared words find stock phrases as well as quotations
  (and miss quotations the King James words differently in the New Testament, translated from the
  Greek), so every candidate is read.
- `quotations.txt` is the reviewed list, a quotation a line: the New Testament verse, then the
  Old Testament verses it quotes (`Matt 2:15 = Hos 11:1`). A quotation counts when the verse
  reproduces a particular passage's words as a citation: introduced as one ("it is written", "that
  it might be fulfilled which was spoken by the prophet"), or close to word for word without that.
  Each verse holding quoted words counts, the formula's verse alone doesn't.
- `make_quotations.py` checks every reference against the KJV and every pair against the cross
  references, and writes the app's list with the app's book names.

## Sources and licenses

- King James Version: public domain.
- Cross references: openbible.info, CC BY.
- Nave's Topical Bible (Orville J. Nave): public domain, as structured by BibleData (Brady
  Stephenson), CC BY 4.0.
