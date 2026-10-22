"""Makes the picture prompts page: every scene's prompt with Copy prompt and Copy name buttons, and a mark
for each picture made (kept in the phone's browser), from the app's list of scenes.

    python3 make_page.py APP_ASSETS_DIR OUT_HTML

Reads APP_ASSETS_DIR/pictures/scenes.tsv (made by make_prompts.py). The style and framing every prompt
ends with are stored once and put back on as a prompt is copied.
"""
import html
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
assets_dir, out_path = sys.argv[1:3]

# The ending every prompt shares (see make_prompts.py).
with open(os.path.join(HERE, "make_prompts.py"), encoding="utf-8") as f:
    source = f.read()
namespace = {}
for name in ("STYLE", "FRAMING"):
    start = source.index(f"{name} = (")
    end = source.index(")\n", start) + 1
    exec(source[start:end], namespace)
ending = namespace["STYLE"] + " " + namespace["FRAMING"]

scenes = []
for line in open(os.path.join(assets_dir, "pictures", "scenes.tsv"), encoding="utf-8"):
    if line.startswith("#") or not line.strip():
        continue
    book, chapter, first, last, title, prompt = line.rstrip("\n").split("\t")
    if not prompt.endswith(" " + ending):
        sys.exit(f"{book} {chapter}:{first}: the prompt doesn't end with the usual style and framing")
    scenes.append([book, int(chapter), int(first), int(last), title, prompt[: -len(ending) - 1]])

books = list(dict.fromkeys(s[0] for s in scenes))
title = f"{books[0]} Picture Prompts" if len(books) == 1 else "Bible Picture Prompts"
data = json.dumps({"ending": ending, "scenes": scenes}, ensure_ascii=False, separators=(",", ":"))
# Safe inside a script tag.
data = data.replace("</", "<\\/")

page = """<title>__TITLE__</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,600&family=Gelasio:wght@400;600&family=Work+Sans:wght@400;500;600&display=swap">
<style>
/* One column, chapter by chapter, like a shot list; a bar that stays at the top with how many are made and the next to make. */
:root {
  --bg: #F1F2EE;
  --surface: #FFFFFF;
  --ink: #1D2027;
  --soft: #586070;
  --line: #D8DBD4;
  --accent: #2E477A;
  --on-accent: #FFFFFF;
  --gold: #8A5E0E;
  --made: #F5ECD8;
  --display: "Fraunces", Georgia, "Times New Roman", serif;
  --verse: "Gelasio", Georgia, serif;
  --ui: "Work Sans", system-ui, -apple-system, "Segoe UI", sans-serif;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #0F131B; --surface: #161B25; --ink: #ECE7DD; --soft: #A2A8B4; --line: #283041;
    --accent: #A9BEEB; --on-accent: #0F131B; --gold: #E8C27A; --made: #2A2415; color-scheme: dark;
  }
}
:root[data-theme="dark"] {
  --bg: #0F131B; --surface: #161B25; --ink: #ECE7DD; --soft: #A2A8B4; --line: #283041;
  --accent: #A9BEEB; --on-accent: #0F131B; --gold: #E8C27A; --made: #2A2415; color-scheme: dark;
}
body {
  background: var(--bg); color: var(--ink); font-family: var(--ui); font-size: 15px; line-height: 1.5;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
  padding-block: 0 48px;
}
body::before { content: ""; position: fixed; inset: 0 0 auto; height: env(safe-area-inset-top, 0px); z-index: 10; background: var(--bg); }
.wrap { max-width: 720px; margin: 0 auto; }
header { padding-block: 28px 8px; }
.eyebrow { font-size: 12px; font-weight: 600; letter-spacing: 0.14em; text-transform: uppercase; color: var(--gold); }
h1 { font-family: var(--display); font-weight: 600; font-size: clamp(30px, 7vw, 40px); line-height: 1.1; margin: 6px 0 12px; text-wrap: balance; }
.how { margin: 0; padding-left: 1.3em; color: var(--soft); max-width: 62ch; display: grid; gap: 4px; }
.how strong { color: var(--ink); font-weight: 600; }
.bar {
  position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--bg);
  border-bottom: 1px solid var(--line); padding-block: 10px; margin-top: 18px;
  display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center;
}
.count { font-variant-numeric: tabular-nums; font-weight: 600; margin-right: auto; }
.count span { color: var(--soft); font-weight: 400; }
select, .bar button, .bar label {
  font: inherit; font-size: 14px; color: var(--ink); background: var(--surface);
  border: 1px solid var(--line); border-radius: 999px; padding: 6px 12px;
}
.bar label { display: inline-flex; gap: 6px; align-items: center; cursor: pointer; }
.bar button.next { background: var(--accent); color: var(--on-accent); border-color: var(--accent); font-weight: 600; cursor: pointer; }
h2 { font-family: var(--display); font-weight: 600; font-size: 22px; margin: 28px 0 10px; }
.scene { background: var(--surface); border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px; margin-bottom: 10px; scroll-margin-top: 72px; }
.scene.made { background: var(--made); }
.head { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
.verses { font-size: 12.5px; font-weight: 600; letter-spacing: 0.06em; color: var(--gold); font-variant-numeric: tabular-nums; }
.title { font-family: var(--verse); font-size: 17.5px; font-weight: 600; }
.prompt { margin: 8px 0 12px; color: var(--soft); font-size: 14px; line-height: 1.55; min-width: 0; overflow-wrap: anywhere; }
.prompt.short { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.actions button {
  font: inherit; font-size: 14px; font-weight: 600; border-radius: 999px; padding: 7px 14px; cursor: pointer;
  border: 1px solid var(--accent); background: transparent; color: var(--accent);
}
.actions button.copy { background: var(--accent); color: var(--on-accent); }
.actions .more { border: none; padding-inline: 4px; font-weight: 500; color: var(--soft); text-decoration: underline; }
.actions label { margin-left: auto; display: inline-flex; gap: 6px; align-items: center; font-size: 14px; cursor: pointer; }
input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--gold); }
button:focus-visible, select:focus-visible, input:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.name { font-family: ui-monospace, "SFMono-Regular", Menlo, monospace; font-size: 12.5px; color: var(--soft); }
.fallback { width: 100%; margin-top: 8px; font: inherit; font-size: 13px; color: var(--ink); background: var(--bg); border: 1px solid var(--line); border-radius: 8px; padding: 8px; }
.toast {
  position: fixed; left: 50%; transform: translateX(-50%); bottom: calc(18px + env(safe-area-inset-bottom, 0px));
  background: var(--ink); color: var(--bg); padding: 8px 16px; border-radius: 999px; font-size: 14px; z-index: 20;
}
.hide-made .scene.made { display: none; }
@media (prefers-reduced-motion: no-preference) { html { scroll-behavior: smooth; } }
</style>

<div class="wrap">
  <header>
    <div class="eyebrow">Verse Scroll pictures</div>
    <h1>__TITLE__</h1>
    <ol class="how">
      <li><strong>Copy a prompt</strong> and paste it into ChatGPT. Make a book's pictures in one chat, so each person keeps the same face.</li>
      <li><strong>Save the picture</strong> it makes, then tick Made here.</li>
      <li><strong>Add it in the app:</strong> Verse Scroll, Settings, Pictures page, then Add on its scene, or Add several in order for a batch. Or save it in your pictures folder under its name (Copy name).</li>
    </ol>
  </header>

  <div class="bar" id="bar">
    <div class="count" id="count">0 made <span>of 0</span></div>
    <select id="jump" aria-label="Go to a chapter"></select>
    <label><input type="checkbox" id="hideMade"> Hide made</label>
    <button class="next" id="next" type="button">Next to make</button>
  </div>

  <main id="list"></main>
</div>
<div class="toast" id="toast" role="status" hidden></div>

<script type="application/json" id="data">__DATA__</script>
<script>
(function () {
  var data = JSON.parse(document.getElementById("data").textContent);
  var ending = data.ending;
  var scenes = data.scenes;
  var list = document.getElementById("list");
  var KEY = "picture-prompts-made";

  function load() {
    try { return new Set(JSON.parse(localStorage.getItem(KEY) || "[]")); } catch (e) { return new Set(); }
  }
  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(Array.from(made))); } catch (e) {}
  }
  var made = load();

  function nameOf(s) { return s[0] + " " + s[1] + "." + s[2] + (s[3] !== s[2] ? "-" + s[3] : ""); }
  function versesOf(s) { return s[1] + ":" + s[2] + (s[3] !== s[2] ? "\\u2013" + s[3] : ""); }
  function idOf(s) { return "s-" + s[0].replace(/\\s+/g, "") + "-" + s[1] + "-" + s[2]; }

  var toast = document.getElementById("toast");
  var toastTimer = 0;
  function say(text) {
    toast.textContent = text;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.hidden = true; }, 1600);
  }

  // Copied, or where the browser won't: the text shown, selected, to copy by hand.
  function copy(text, label, card) {
    function byHand() {
      var box = card.querySelector(".fallback");
      if (!box) {
        box = document.createElement("textarea");
        box.className = "fallback";
        box.rows = 4;
        box.setAttribute("aria-label", label + " to copy");
        card.appendChild(box);
      }
      box.value = text;
      box.focus();
      box.select();
      say("Press and hold to copy the " + label.toLowerCase());
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { say(label + " copied"); }, byHand);
    } else {
      byHand();
    }
  }

  var jump = document.getElementById("jump");
  // With more than one book, a chapter says which.
  var several = new Set(scenes.map(function (x) { return x[0]; })).size > 1;
  var chapter = null;
  var html = [];
  var chapters = [];
  scenes.forEach(function (s, i) {
    var key = s[0] + " " + s[1];
    if (key !== chapter) {
      chapter = key;
      chapters.push((several ? s[0] + " " : "") + "Chapter " + s[1]);
      html.push('<h2 id="c-' + (chapters.length - 1) + '">' + chapters[chapters.length - 1] + "</h2>");
    }
    var name = nameOf(s);
    html.push(
      '<article class="scene' + (made.has(name) ? " made" : "") + '" id="' + idOf(s) + '" data-i="' + i + '">' +
        '<div class="head"><span class="verses">' + versesOf(s) + '</span><span class="title"></span></div>' +
        '<p class="prompt short"></p>' +
        '<div class="actions">' +
          '<button type="button" class="copy">Copy prompt</button>' +
          '<button type="button" class="copyname">Copy name</button>' +
          '<button type="button" class="more" aria-expanded="false">Show all</button>' +
          '<label><input type="checkbox" class="madebox"' + (made.has(name) ? " checked" : "") + '> Made</label>' +
        "</div>" +
        '<div class="name"></div>' +
      "</article>"
    );
  });
  list.innerHTML = html.join("");
  // Text set as text, never as markup.
  list.querySelectorAll(".scene").forEach(function (card) {
    var s = scenes[+card.dataset.i];
    card.querySelector(".title").textContent = s[4];
    card.querySelector(".prompt").textContent = s[5] + " " + ending;
    card.querySelector(".name").textContent = nameOf(s);
  });
  chapters.forEach(function (c, i) {
    var o = document.createElement("option");
    o.value = "c-" + i;
    o.textContent = c;
    jump.appendChild(o);
  });

  function count() {
    var n = scenes.filter(function (s) { return made.has(nameOf(s)); }).length;
    document.getElementById("count").innerHTML = n + " made <span>of " + scenes.length + "</span>";
  }
  count();

  list.addEventListener("click", function (e) {
    var card = e.target.closest(".scene");
    if (!card) return;
    var s = scenes[+card.dataset.i];
    if (e.target.classList.contains("copy")) copy(s[5] + " " + ending, "Prompt", card);
    else if (e.target.classList.contains("copyname")) copy(nameOf(s), "Name", card);
    else if (e.target.classList.contains("more")) {
      var p = card.querySelector(".prompt");
      var open = p.classList.toggle("short") === false;
      e.target.textContent = open ? "Show less" : "Show all";
      e.target.setAttribute("aria-expanded", String(open));
    }
  });
  list.addEventListener("change", function (e) {
    if (!e.target.classList.contains("madebox")) return;
    var card = e.target.closest(".scene");
    var name = nameOf(scenes[+card.dataset.i]);
    if (e.target.checked) made.add(name); else made.delete(name);
    card.classList.toggle("made", e.target.checked);
    save();
    count();
  });

  jump.addEventListener("change", function () {
    var target = document.getElementById(jump.value);
    if (target) target.scrollIntoView();
  });
  document.getElementById("hideMade").addEventListener("change", function (e) {
    list.classList.toggle("hide-made", e.target.checked);
  });
  document.getElementById("next").addEventListener("click", function () {
    var s = scenes.find(function (x) { return !made.has(nameOf(x)); });
    if (!s) { say("Every picture is made"); return; }
    var card = document.getElementById(idOf(s));
    card.scrollIntoView();
    card.querySelector(".copy").focus({ preventScroll: true });
  });
})();
</script>
"""

page = page.replace("__TITLE__", html.escape(title)).replace("__DATA__", data)
os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
with open(out_path, "w", encoding="utf-8") as out:
    out.write(page)
print(f"{len(scenes)} prompts, {len(page) // 1024} KB: {out_path}")
