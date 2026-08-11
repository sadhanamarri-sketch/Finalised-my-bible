#!/usr/bin/env node
/**
 * scrape-telugu.js
 *
 * Pulls the Telugu O.V. Bible (Bible Society of India "Old Version" —
 * పరిశుద్ధ గ్రంథము) from wordproject.org, chapter by chapter, and writes
 * one JSON file per book into ./telugu-output/, in the SAME schema the
 * app already expects from aruljohn/Bible-telugu:
 *
 *   { "book": "Genesis",
 *     "chapters": [
 *       { "chapter": "1", "verses": [ { "verse": "1", "text": "..." }, ... ] },
 *       ...
 *     ]
 *   }
 *
 * WHY THIS EXISTS
 * aruljohn/Bible-telugu (the app's current Telugu source) is a small,
 * unmaintained community JSON conversion with a number of typos.
 * wordproject.org hosts the same underlying translation (BSI Telugu O.V.)
 * but noticeably cleaner. wordproject.org has no JSON API, only per-chapter
 * HTML pages, hence this scraper.
 *
 * LICENSE / TERMS
 * Per wordproject.org's disclaimer (wordproject.org/contact/new/disclaim.htm),
 * copying the Bible TEXT (not the whole site, not their audio) for
 * non-commercial personal/ministry use is permitted. This script is meant
 * for a one-time personal pull for your own offline Bible app — please
 * don't hammer their servers (the built-in delay below is intentional,
 * leave it in) and don't redistribute this as a public "API".
 *
 * USAGE
 *   node scrape-telugu.js                 # scrapes everything (~1,189 chapters)
 *   node scrape-telugu.js Genesis          # scrapes a single book (handy for testing)
 *
 * Requires Node 18+ (uses the built-in fetch). Takes a while — there's a
 * polite ~400ms delay between requests, so a full run is roughly 10-15 minutes.
 */

const fs = require('fs');
const path = require('path');

const BOOKS = [
  ["Genesis",50],["Exodus",40],["Leviticus",27],["Numbers",36],["Deuteronomy",34],
  ["Joshua",24],["Judges",21],["Ruth",4],["1 Samuel",31],["2 Samuel",24],
  ["1 Kings",22],["2 Kings",25],["1 Chronicles",29],["2 Chronicles",36],["Ezra",10],
  ["Nehemiah",13],["Esther",10],["Job",42],["Psalms",150],["Proverbs",31],
  ["Ecclesiastes",12],["Song of Solomon",8],["Isaiah",66],["Jeremiah",52],["Lamentations",5],
  ["Ezekiel",48],["Daniel",12],["Hosea",14],["Joel",3],["Amos",9],
  ["Obadiah",1],["Jonah",4],["Micah",7],["Nahum",3],["Habakkuk",3],
  ["Zephaniah",3],["Haggai",2],["Zechariah",14],["Malachi",4],
  ["Matthew",28],["Mark",16],["Luke",24],["John",21],["Acts",28],
  ["Romans",16],["1 Corinthians",16],["2 Corinthians",13],["Galatians",6],["Ephesians",6],
  ["Philippians",4],["Colossians",4],["1 Thessalonians",5],["2 Thessalonians",3],["1 Timothy",6],
  ["2 Timothy",4],["Titus",3],["Philemon",1],["Hebrews",13],["James",5],
  ["1 Peter",5],["2 Peter",3],["1 John",5],["2 John",1],["3 John",1],
  ["Jude",1],["Revelation",22]
];

const OUT_DIR = path.join(__dirname, 'telugu-output');
const DELAY_MS = 400;
const RETRY_COUNT = 3;

function sleep(ms){ return new Promise(r => setTimeout(r, ms)); }

// Minimal HTML entity decoder — covers everything wordproject's pages use.
function decodeEntities(s){
  return s
    .replace(/&nbsp;/g, '\u00A0')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#x([0-9a-fA-F]+);/g, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(parseInt(d, 10)));
}

function stripTags(html){
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ');
}

// Pull just the verse text out of a chapter page's full plain text.
// The chapter's text sits between the "చాప్టర్ N" heading and the
// "Wordproject" footer trademark line — both reliably present on every page.
function extractChapterBlock(plainText){
  const startMarker = /చాప్టర్\s*\d+/;
  const startMatch = plainText.match(startMarker);
  const startIdx = startMatch ? startMatch.index + startMatch[0].length : 0;
  // Cut at "Courtesy of" (footer start), not "Wordproject" — the footer
  // sentence itself contains the word "Wordproject" partway through
  // ("Courtesy of Wordproject, a registered domain of..."), so cutting on
  // "Wordproject" let "Courtesy of " bleed into the last verse of every
  // chapter.
  let endIdx = plainText.indexOf('Courtesy of', startIdx);
  if(endIdx < 0) endIdx = plainText.indexOf('Wordproject', startIdx);
  const slice = endIdx > startIdx ? plainText.slice(startIdx, endIdx) : plainText.slice(startIdx);
  return slice;
}

// Split a chapter's raw text into { "1": "...", "2": "...", ... } using the
// double/triple-space-before-verse-number pattern wordproject renders with.
function splitVerses(chapterText){
  const parts = chapterText.split(/\s{2,}(\d{1,3})\s+/);
  const verses = {};
  const firstText = parts[0].replace(/\s+/g, ' ').trim();
  if(firstText) verses['1'] = firstText;
  for(let i = 1; i < parts.length; i += 2){
    const vnum = parts[i];
    const text = (parts[i+1] || '').replace(/\s+/g, ' ').trim();
    if(vnum && text) verses[vnum] = text;
  }
  return verses;
}

async function fetchChapter(bookNum, chapter){
  const url = `https://wordproject.org/bibles/tel/${String(bookNum).padStart(2,'0')}/${chapter}.htm`;
  for(let attempt = 1; attempt <= RETRY_COUNT; attempt++){
    try{
      const res = await fetch(url, { headers: { 'User-Agent': 'personal-bible-app-one-time-scrape' } });
      if(!res.ok) throw new Error(`HTTP ${res.status}`);
      const html = await res.text();
      const plain = decodeEntities(stripTags(html));
      const block = extractChapterBlock(plain);
      const verses = splitVerses(block);
      return { url, verses };
    }catch(err){
      if(attempt === RETRY_COUNT) throw err;
      await sleep(1000 * attempt);
    }
  }
}

async function scrapeBook(name, chapterCount, bookNum){
  const chapters = [];
  const warnings = [];
  for(let c = 1; c <= chapterCount; c++){
    process.stdout.write(`  ${name} ${c}/${chapterCount}...`);
    try{
      const { verses } = await fetchChapter(bookNum, c);
      const verseCount = Object.keys(verses).length;
      if(verseCount === 0) warnings.push(`${name} ${c}: found 0 verses — check ${bookNum}/${c}.htm by hand`);
      process.stdout.write(` ${verseCount} verses\n`);
      chapters.push({
        chapter: String(c),
        verses: Object.keys(verses)
          .sort((a, b) => Number(a) - Number(b))
          .map(v => ({ verse: v, text: verses[v] }))
      });
    }catch(err){
      warnings.push(`${name} ${c}: FAILED (${err.message}) — retry manually`);
      process.stdout.write(` FAILED (${err.message})\n`);
      chapters.push({ chapter: String(c), verses: [] });
    }
    await sleep(DELAY_MS);
  }
  return { book: name, chapters, warnings };
}

async function main(){
  const onlyBook = process.argv[2]; // optional: scrape a single book, e.g. `node scrape-telugu.js Genesis`
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const targets = onlyBook
    ? BOOKS.filter(([name]) => name.toLowerCase() === onlyBook.toLowerCase())
    : BOOKS;

  if(onlyBook && targets.length === 0){
    console.error(`No book named "${onlyBook}" found. Check spelling/casing against the BOOKS list in this script.`);
    process.exit(1);
  }

  const allWarnings = [];
  for(let i = 0; i < BOOKS.length; i++){
    const [name, chapterCount] = BOOKS[i];
    if(onlyBook && name.toLowerCase() !== onlyBook.toLowerCase()) continue;
    const bookNum = i + 1;
    console.log(`\nScraping ${name} (book ${bookNum}, ${chapterCount} chapters)...`);
    const { book, chapters, warnings } = await scrapeBook(name, chapterCount, bookNum);
    allWarnings.push(...warnings);
    const outPath = path.join(OUT_DIR, `${name}.json`);
    fs.writeFileSync(outPath, JSON.stringify({ book, chapters }));
    console.log(`  wrote ${outPath}`);
  }

  console.log(`\nDone. ${allWarnings.length} warning(s).`);
  if(allWarnings.length){
    const warnPath = path.join(OUT_DIR, '_warnings.txt');
    fs.writeFileSync(warnPath, allWarnings.join('\n'));
    console.log(`Warnings written to ${warnPath} — check these chapters by hand before shipping.`);
  }
}

main().catch(err => { console.error(err); process.exit(1); });
