"""Builds ../grid/preview.html: the three grid tiles pinned on Instagram and TikTok, each tile alone in the feed,
the posting and pinning order and the captions. Images are embedded as WebP, so the page is one file.

Run after split-grid.py: python build-grid-preview.py
"""
import base64
import html
import io
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
OUT = HERE.parent
GRID = OUT / 'grid'
REPO = HERE.parents[5]
WEEK1 = REPO / 'tools/social/out/week1'
TILES = ['tile-1-left', 'tile-2-middle', 'tile-3-right']


def webp(im, size, q):
    im = im.convert('RGB').resize(size, Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, 'WEBP', quality=q, method=6)
    return 'data:image/webp;base64,' + base64.b64encode(buf.getvalue()).decode('ascii')


css = []
for i, name in enumerate(TILES, 1):
    css.append(f'.i-t{i}{{background-image:url({webp(Image.open(GRID / f"{name}.png"), (720, 960), 84)})}}')
# The grids show a Reel or a TikTok video as the centre 3:4 of its 9:16 cover.
for key, slug in [('sparrow', 'house-sparrow'), ('crane', 'common-crane'), ('bluetit', 'eurasian-blue-tit')]:
    cover = Image.open(WEEK1 / slug / 'cover.jpg').crop((0, 240, 1080, 1680))
    css.append(f'.i-{key}{{background-image:url({webp(cover, (300, 400), 72)})}}')
css.append(f'.i-av{{background-image:url({webp(Image.open(OUT / "profile-flock.png"), (240, 240), 86)})}}')

TAGS = '#birds #birdwatching #birdsong #birding #birdy #fåglar #fågelskådning'
CAPTIONS = {
    3: ('tile-3-right.png', 'läggs upp först',
        'One bird in this flock for every species in Birdy.\n\nIdentification is free: by camera, by photo or by song. Birdy for Android is on Google Play, and the iPhone app is coming. Link in bio.\n\n' + TAGS),
    2: ('tile-2-middle.png', 'läggs upp som nummer två',
        'A new bird every morning at 08:00 Swedish time. Turn the sound on and listen first; the name comes after.\n\nFollow along, and read more about each bird on birdy.community.\n\n' + TAGS),
    1: ('tile-1-left.png', 'läggs upp sist',
        'Know the bird. Keep the moment.\n\nBirdy is a bird guide and field journal for your phone. Point the camera, pick a photo or let the bird sing, and Birdy suggests the species. Every sighting goes into your own journal and stays on your phone.\n\nFree on Android, iPhone coming. Link in bio.\n\n' + TAGS),
}
for _, (_, _, text) in CAPTIONS.items():
    assert chr(0x2014) not in text and chr(0x2013) not in text

PIN = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M15 3l6 6-3 1-4 4 1 5-2 1-4-4-5 5-1-1 5-5-4-4 1-2 5 1 4-4z" fill="#fff"/></svg>'
REEL = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="5" fill="none" stroke="#fff" stroke-width="2"/><path d="M10 9.5v5l4.2-2.5z" fill="#fff"/></svg>'
PLAY = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z" fill="#fff"/></svg>'


def ig_tile(cls, icon, label):
    return f'<i class="{cls}" role="img" aria-label="{label}">{icon}</i>'


ig_grid = ''.join([
    ig_tile('i-t1', PIN, 'Ruta 1, vänster, fäst'), ig_tile('i-t2', PIN, 'Ruta 2, mitten, fäst'), ig_tile('i-t3', PIN, 'Ruta 3, höger, fäst'),
    ig_tile('i-sparrow', REEL, 'Video söndag, gråsparv'), ig_tile('i-crane', REEL, 'Video lördag, trana'), ig_tile('i-bluetit', REEL, 'Video fredag, blåmes'),
])
tt_grid = ''.join(
    [f'<i class="{c}" role="img" aria-label="{a}"><span class="tt-pin">Pinned</span><span class="tt-views">{PLAY}0</span></i>'
     for c, a in [('i-t1', 'Ruta 1, fäst'), ('i-t2', 'Ruta 2, fäst'), ('i-t3', 'Ruta 3, fäst')]]
    + [f'<i class="{c}" role="img" aria-label="{a}"><span class="tt-views">{PLAY}0</span></i>'
       for c, a in [('i-sparrow', 'Video, gråsparv'), ('i-crane', 'Video, trana'), ('i-bluetit', 'Video, blåmes')]])


def ig_feed(n):
    first = CAPTIONS[n][2].split('\n')[0]
    return f'''<figure>
  <div class="phone"><div class="ph">
    <div class="bar"><span>9:41</span><span class="bat"></span></div>
    <div class="igf-head"><i class="av i-av"></i><b>app.birdy</b><span class="dots">···</span></div>
    <i class="igf-img i-t{n}" role="img" aria-label="Ruta {n} ensam i Instagram-flödet"></i>
    <div class="igf-actions"><span class="heart"></span><span class="bubble"></span><span class="plane"></span><span class="mark"></span></div>
    <p class="igf-cap"><b>app.birdy</b> {html.escape(first)} <span class="more">more</span></p>
  </div></div>
  <figcaption><b>Ruta {n}</b> i Instagram-flödet</figcaption>
</figure>'''


def tt_feed(n):
    first = CAPTIONS[n][2].split('\n')[0]
    return f'''<figure>
  <div class="phone dark"><div class="ph">
    <div class="bar light"><span>9:41</span><span class="bat"></span></div>
    <p class="ttf-top"><span>Following</span><b>For You</b></p>
    <i class="ttf-img i-t{n}" role="img" aria-label="Ruta {n} ensam i TikTok-flödet"></i>
    <div class="ttf-side"><i class="av i-av"></i><span class="c"></span><span class="c"></span><span class="c"></span><span class="c"></span></div>
    <div class="ttf-cap"><b>@birdy.community</b><p>{html.escape(first)}</p></div>
  </div></div>
  <figcaption><b>Ruta {n}</b> i TikTok-flödet</figcaption>
</figure>'''


caption_cards = ''.join(f'''<article class="cap">
  <header><h3>Ruta {n}</h3><span class="tag">{html.escape(when)}</span></header>
  <p class="cap-file"><code>{file}</code></p>
  <p class="cap-text" id="cap-{n}">{html.escape(text)}</p>
  <div class="row"><button type="button" class="copy" data-copy="cap-{n}">Kopiera</button><span class="status" role="status"></span></div>
</article>''' for n, (file, when, text) in sorted(CAPTIONS.items(), reverse=True))

IG_BIO = 'Know the bird. Keep the moment. Identify birds by camera, photo or song, free on Android (iPhone coming). Your sightings stay on your phone.'

page = f'''<title>Birdys rutnätsrad</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Caveat:wght@700&family=DM+Serif+Display:ital@0;1&family=Inter:wght@400;600&display=swap">
<style>
:root {{
  --ground: #F6EFE2; --card: #FFFAF1; --ink: #2A1D17; --muted: #6E584B; --rule: #E2D5C0; --accent: #9A4526;
  --chip: #EFE3CF; --stage: #EBE0CD; --shadow: 0 1px 2px rgba(42, 29, 23, .08), 0 10px 28px rgba(42, 29, 23, .12);
  --serif: 'DM Serif Display', Georgia, serif; --script: 'Caveat', 'Segoe Print', cursive;
  --sans: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  --ui: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{ --ground: #1A120E; --card: #251A15; --ink: #F6EFE2; --muted: #C9B6A4; --rule: #3E2F26; --accent: #F2B27A; --chip: #33251D; --stage: #2A1F19; --shadow: 0 1px 2px rgba(0,0,0,.3), 0 12px 30px rgba(0,0,0,.35); color-scheme: dark; }}
}}
:root[data-theme="dark"] {{ --ground: #1A120E; --card: #251A15; --ink: #F6EFE2; --muted: #C9B6A4; --rule: #3E2F26; --accent: #F2B27A; --chip: #33251D; --stage: #2A1F19; --shadow: 0 1px 2px rgba(0,0,0,.3), 0 12px 30px rgba(0,0,0,.35); color-scheme: dark; }}
{chr(10).join(css)}
* {{ box-sizing: border-box; }}
body {{ margin: 0; background: var(--ground); color: var(--ink); font: 16px/1.55 var(--sans); -webkit-font-smoothing: antialiased; }}
.wrap {{ max-width: 1080px; margin: 0 auto; padding-inline: 18px; padding-block: 30px 90px; }}
h1, h2, h3 {{ font-family: var(--serif); font-weight: 400; margin: 0; text-wrap: balance; }}
p {{ margin: 0; }}
code {{ font: 600 .86em/1.4 ui-monospace, Consolas, monospace; background: var(--chip); padding: .1em .4em; border-radius: 5px; overflow-wrap: anywhere; }}
:focus-visible {{ outline: 3px solid var(--accent); outline-offset: 3px; }}
.kicker {{ font: 700 26px/1 var(--script); color: var(--accent); }}
h1 {{ font-size: clamp(34px, 6vw, 54px); line-height: 1.04; margin-top: 10px; }}
h1 em {{ color: var(--accent); }}
.lede {{ margin-top: 12px; max-width: 64ch; color: var(--muted); font-size: 17px; }}
.sec {{ font-size: clamp(26px, 4vw, 34px); line-height: 1.1; margin-top: 52px; }}
.note {{ margin-top: 8px; max-width: 66ch; color: var(--muted); }}
.row3 {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 22px; margin-top: 20px; }}
.stage {{ margin-top: 20px; padding: 24px 20px; background: var(--stage); border-radius: 18px; }}
figure {{ margin: 0; display: grid; gap: 10px; justify-items: center; min-width: 0; }}
figcaption {{ font-size: 14px; color: var(--muted); text-align: center; }}
figcaption b {{ color: var(--ink); }}
.whole {{ width: 100%; aspect-ratio: 3240 / 1440; display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }}
.whole i {{ display: block; background-size: cover; background-position: center; border-radius: 3px; }}

/* phones */
.phone {{ width: 100%; max-width: 300px; aspect-ratio: 9 / 18.5; overflow: hidden; border-radius: 30px; border: 7px solid #17110E; background: #fff; color: #0f0f0f; box-shadow: var(--shadow); container-type: inline-size; position: relative; }}
.phone.dark {{ background: #000; color: #fff; }}
.ph {{ font: 400 4.1cqw/1.35 var(--ui); height: 100%; position: relative; }}
.bar {{ display: flex; justify-content: space-between; align-items: center; height: 2.3em; padding: 0 1.4em; font-weight: 600; font-size: .95em; position: relative; z-index: 2; }}
.bar.light {{ color: #fff; }}
.bat {{ width: 1.9em; height: .9em; border: 1.5px solid currentColor; border-radius: .25em; position: relative; }}
.bat::after {{ content: ''; position: absolute; inset: 1.5px; right: 35%; background: currentColor; border-radius: .1em; }}
.av {{ display: block; border-radius: 50%; background-size: cover; background-position: center; flex: none; }}
.ico {{ position: absolute; top: .45em; right: .45em; width: 1.25em; height: 1.25em; filter: drop-shadow(0 1px 2px rgba(0,0,0,.5)); }}

/* Instagram profile */
.ig-top {{ display: flex; align-items: center; gap: .35em; padding: .3em 1.1em .7em; font-weight: 700; font-size: 1.2em; }}
.ig-head {{ display: flex; align-items: center; gap: 1.2em; padding: 0 1.1em; }}
.ig-head .av {{ width: 6.4em; height: 6.4em; }}
.ig-stats {{ display: flex; flex: 1; justify-content: space-around; text-align: center; }}
.ig-stats b {{ display: block; font-size: 1.15em; }}
.ig-stats span {{ font-size: .9em; }}
.ig-txt {{ padding: .6em 1.1em 0; display: grid; gap: .1em; font-size: .95em; }}
.ig-txt .cat {{ color: #737373; }}
.ig-btns {{ display: grid; grid-template-columns: 1fr 1fr; gap: .45em; padding: .7em 1.1em .8em; }}
.ig-btns span {{ text-align: center; font-weight: 600; padding: .5em 0; border-radius: .6em; background: #efefef; }}
.ig-btns .pri {{ background: #0095f6; color: #fff; }}
.ig-grid, .tt-grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 2px; }}
.ig-grid i, .tt-grid i {{ display: block; aspect-ratio: 3 / 4; background-size: cover; background-position: center; position: relative; }}

/* TikTok profile */
.tt-head {{ display: grid; justify-items: center; gap: .35em; padding: .4em 1em .8em; text-align: center; }}
.tt-head .av {{ width: 6em; height: 6em; }}
.tt-handle {{ font-weight: 700; font-size: 1.1em; }}
.tt-stats {{ display: flex; gap: 1.6em; }}
.tt-stats b {{ display: block; font-size: 1.1em; }}
.tt-stats span {{ font-size: .85em; color: #757575; }}
.tt-btn {{ background: #fe2c55; color: #fff; font-weight: 700; padding: .5em 2.6em; border-radius: .3em; }}
.tt-bio {{ font-size: .9em; max-width: 92%; }}
.tt-tabs {{ display: flex; justify-content: space-around; border-bottom: 1px solid #e3e3e3; padding: .4em 0; font-weight: 700; font-size: .9em; }}
.tt-tabs span:first-child {{ box-shadow: inset 0 -2px 0 #000; padding-bottom: .3em; }}
.tt-pin {{ position: absolute; top: .4em; left: .4em; background: #fe2c55; color: #fff; font: 700 .72em/1 var(--ui); padding: .3em .45em; border-radius: .25em; }}
.tt-views {{ position: absolute; bottom: .35em; left: .4em; display: flex; align-items: center; gap: .15em; color: #fff; font: 700 .8em/1 var(--ui); text-shadow: 0 1px 2px rgba(0,0,0,.6); }}
.tt-views svg {{ width: 1.1em; height: 1.1em; }}

/* Instagram feed */
.igf-head {{ display: flex; align-items: center; gap: .6em; padding: .4em .9em .6em; }}
.igf-head .av {{ width: 2.4em; height: 2.4em; }}
.igf-head .dots {{ margin-left: auto; font-weight: 700; }}
.igf-img {{ display: block; width: 100%; aspect-ratio: 3 / 4; background-size: cover; background-position: center; }}
.igf-actions {{ display: flex; gap: 1em; padding: .7em .9em .4em; }}
.igf-actions span {{ width: 1.6em; height: 1.6em; border: 2px solid #111; border-radius: 50%; }}
.igf-actions .mark {{ margin-left: auto; border-radius: .2em; width: 1.3em; }}
.igf-cap {{ padding: 0 .9em; font-size: .95em; }}
.igf-cap .more {{ color: #737373; }}

/* TikTok feed: a 3:4 photo fitted to the width of the 9:16 screen */
.ttf-top {{ position: absolute; top: 2.6em; left: 0; right: 0; display: flex; justify-content: center; gap: 1.2em; font-size: .95em; color: rgba(255,255,255,.7); z-index: 2; }}
.ttf-top b {{ color: #fff; }}
.ttf-img {{ position: absolute; left: 0; right: 0; top: 50%; transform: translateY(-50%); aspect-ratio: 3 / 4; background-size: cover; background-position: center; }}
.ttf-side {{ position: absolute; right: .6em; bottom: 11%; display: grid; gap: 1.1em; justify-items: center; z-index: 2; }}
.ttf-side .av {{ width: 2.8em; height: 2.8em; border: 2px solid #fff; }}
.ttf-side .c {{ width: 2.2em; height: 2.2em; border-radius: 50%; background: rgba(255,255,255,.85); box-shadow: 0 1px 3px rgba(0,0,0,.4); }}
.ttf-cap {{ position: absolute; left: .9em; right: 4.2em; bottom: 1.4em; font-size: .9em; text-shadow: 0 1px 2px rgba(0,0,0,.6); z-index: 2; }}

/* order and captions */
.steps {{ margin: 18px 0 0; padding-left: 22px; display: grid; gap: 10px; max-width: 70ch; }}
.steps li::marker {{ color: var(--accent); font-weight: 700; }}
.fine {{ margin-top: 14px; font-size: 14px; color: var(--muted); max-width: 72ch; }}
.caps {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr)); gap: 16px; margin-top: 18px; align-items: start; }}
.cap {{ background: var(--card); border: 1px solid var(--rule); border-radius: 14px; padding: 16px; display: grid; gap: 10px; }}
.cap header {{ display: flex; justify-content: space-between; align-items: baseline; gap: 10px; }}
.cap h3 {{ font-size: 24px; }}
.tag {{ font: 600 12px/1 var(--sans); color: var(--accent); background: var(--chip); padding: 5px 8px; border-radius: 999px; white-space: nowrap; }}
.cap-text {{ white-space: pre-wrap; overflow-wrap: anywhere; font-size: 15px; }}
.row {{ display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }}
.copy {{ font: 600 14px/1 var(--sans); color: var(--ink); background: var(--chip); border: 1px solid var(--rule); border-radius: 999px; padding: 9px 14px; cursor: pointer; }}
.status {{ font-size: 13px; color: var(--muted); }}
@media (max-width: 700px) {{ .row3.scroll {{ grid-template-columns: repeat(3, minmax(250px, 1fr)); overflow-x: auto; scroll-snap-type: x mandatory; padding-bottom: 8px; }} .row3.scroll > figure {{ scroll-snap-align: start; }} }}
</style>

<div class="wrap">
  <p class="kicker">Instagram och TikTok</p>
  <h1>Första raden i <em>rutnätet</em></h1>
  <p class="lede">Omslaget från Flocken delat i tre bilder, 1080 × 1440 var, som tillsammans blir en bild på 3240 × 1440. De tre fästs överst på profilen, och dagens video hamnar under dem. Ingen text går över en skarv; bara flocken flyger över.</p>

  <h2 class="sec">Hela raden</h2>
  <div class="stage">
    <div class="whole"><i class="i-t1" role="img" aria-label="Ruta 1: Know the bird. Keep the moment."></i><i class="i-t2" role="img" aria-label="Ruta 2: flocken och birdy.community"></i><i class="i-t3" role="img" aria-label="Ruta 3: fågeln av fåglar och anteckningen"></i></div>
  </div>
  <p class="note">Filer i <code>grid/</code>: <code>tile-1-left.png</code>, <code>tile-2-middle.png</code>, <code>tile-3-right.png</code> och hela <code>grid-row.png</code>. Rutorna är exakta utsnitt ur den renderade raden.</p>

  <h2 class="sec">På profilen</h2>
  <p class="note">De tre rutorna fästa överst, under dem de tre första videorna (nyaste först: söndag, lördag, fredag).</p>
  <div class="stage"><div class="row3">
    <figure>
      <div class="phone"><div class="ph">
        <div class="bar"><span>9:41</span><span class="bat"></span></div>
        <div class="ig-top">app.birdy</div>
        <div class="ig-head"><i class="av i-av" role="img" aria-label="Profilbilden"></i><div class="ig-stats"><p><b>6</b><span>posts</span></p><p><b>0</b><span>followers</span></p><p><b>0</b><span>following</span></p></div></div>
        <div class="ig-txt"><b>Birdy</b><span class="cat">App page</span><span>{html.escape(IG_BIO)}</span></div>
        <div class="ig-btns"><span class="pri">Follow</span><span>Message</span></div>
        <div class="ig-grid">{ig_grid}</div>
      </div></div>
      <figcaption><b>Instagram</b>: nålen uppe till höger visar de fästa.</figcaption>
    </figure>
    <figure>
      <div class="phone"><div class="ph">
        <div class="bar"><span>9:41</span><span class="bat"></span></div>
        <div class="tt-head"><i class="av i-av" role="img" aria-label="Profilbilden"></i><p class="tt-handle">@birdy.community</p>
          <div class="tt-stats"><p><b>0</b><span>Following</span></p><p><b>0</b><span>Followers</span></p><p><b>0</b><span>Likes</span></p></div>
          <span class="tt-btn">Follow</span><p class="tt-bio">Listen first, the name comes next. Free bird ID app: birdy.community</p></div>
        <div class="tt-tabs"><span>Posts</span><span>Liked</span></div>
        <div class="tt-grid">{tt_grid}</div>
      </div></div>
      <figcaption><b>TikTok</b>: etiketten Pinned uppe till vänster och visningarna nere till vänster täcker hörnen, så där ligger ingen text.</figcaption>
    </figure>
  </div></div>

  <h2 class="sec">Så postar och fäster du</h2>
  <p class="note">Samma ordning på båda plattformarna.</p>
  <ol class="steps">
    <li>Lägg upp <code>tile-3-right.png</code> först, med bildtexten för ruta 3. På Instagram som ett vanligt foto (inte karusell) och välj Original/3:4 i beskärningen; på TikTok som ett fotoinlägg.</li>
    <li>Lägg upp <code>tile-2-middle.png</code>, med bildtexten för ruta 2.</li>
    <li>Lägg upp <code>tile-1-left.png</code> sist, med bildtexten för ruta 1. Rutnätet visar det nyaste överst till vänster, så raden läses rätt redan nu.</li>
    <li>Fäst i samma ordning: först ruta 3, sedan ruta 2, sist ruta 1. Det senast fästa hamnar längst till vänster på både Instagram och TikTok.</li>
    <li>Videorna läggs sedan ut som vanligt, en om dagen klockan 08.00, och hamnar under raden.</li>
  </ol>
  <p class="fine">Fästordningen är kontrollerad 8 oktober mot aktuella guider för båda plattformarna (det senast fästa visas först), inte mot Metas eller TikToks egna hjälpsidor. Titta efter när ruta 1 är fäst; byter två rutor plats, lossa dem och fäst om i rätt ordning. Båda plattformarna tillåter tre fästa inlägg.</p>

  <h2 class="sec">Varje ruta ensam i flödet</h2>
  <p class="note">Varje ruta är också ett eget inlägg och ska fungera ensam.</p>
  <div class="stage"><div class="row3 scroll">{ig_feed(1)}{ig_feed(2)}{ig_feed(3)}</div></div>
  <div class="stage"><div class="row3 scroll">{tt_feed(1)}{tt_feed(2)}{tt_feed(3)}</div></div>

  <h2 class="sec">Bildtexterna</h2>
  <p class="note">I postningsordning. Engelska, utan streck och utan löften om träffsäkerhet; samma hashtaggar som videorna.</p>
  <div class="caps">{caption_cards}</div>
</div>

<script>
document.querySelectorAll('.copy').forEach((b) => {{
  const status = b.nextElementSibling;
  b.addEventListener('click', () => {{
    const el = document.getElementById(b.dataset.copy);
    const select = () => {{ const r = document.createRange(); r.selectNodeContents(el); const s = window.getSelection(); s.removeAllRanges(); s.addRange(r); status.textContent = 'Markerat. Tryck Ctrl+C.'; }};
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(el.textContent).then(() => {{ status.textContent = 'Kopierat.'; }}, select);
    else select();
  }});
}});
</script>
'''
assert chr(0x2014) not in page
(GRID / 'preview.html').write_text(page, encoding='utf-8')
print(f'grid/preview.html {len(page.encode("utf-8")) / 1024:.0f} KB')
