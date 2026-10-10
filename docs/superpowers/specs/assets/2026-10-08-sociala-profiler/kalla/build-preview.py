"""Builds ../index.html (the page for Albin) from preview.html: direction C, Flocken, in two slogan versions.

The rendered PNGs and the week-one video covers are embedded as compressed WebP data URIs, each once, so
the page is one self-contained file; the whole files with their safe areas, the device mocks for each slogan
and the bio cards are filled in here.

Run after render.mjs: python build-preview.py
"""
import base64
import html
import io
import re
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
OUT = HERE.parent
REPO = HERE.parents[5]
COVERS = REPO / 'tools/social/out/week1'
SLOGANS = ('know', 'name')


def webp(img, size, quality=84):
    im = img.convert('RGB')
    if size:
        im = im.resize(size, Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, 'WEBP', quality=quality, method=6)
    return 'data:image/webp;base64,' + base64.b64encode(buf.getvalue()).decode('ascii')


css = [f".i-av-flock{{background-image:url({webp(Image.open(OUT / 'profile-flock.png'), (480, 480), 86)})}}"]
for s in SLOGANS:
    css.append(f".i-fb-{s}{{background-image:url({webp(Image.open(OUT / f'facebook-cover-flock-{s}.png'), (1148, 504), 82)})}}")
    css.append(f".i-yt-{s}{{background-image:url({webp(Image.open(OUT / f'youtube-banner-flock-{s}.png'), (1600, 900), 80)})}}")

# Instagram shows a Reel as a 3:4 crop of its 9:16 cover (the centre, y 240 to 1680 of 1920). Newest first.
for i, slug in enumerate(['great-tit', 'common-buzzard', 'common-blackbird', 'mallard', 'house-sparrow', 'common-crane'], 1):
    im = Image.open(COVERS / slug / 'cover.jpg').crop((0, 240, 1080, 1680))
    css.append(f".i-ig-{i}{{background-image:url({webp(im, (210, 280), 68)})}}")

# ---- The texts (the same as bios.md; the build checks they are there word for word) ----
BIOS = [
    ('ig', 'Instagram', 'bio', 150,
     'Know the bird. Keep the moment. Identify birds by camera, photo or song, free on Android (iPhone coming). Your sightings stay on your phone.',
     'Länkfältet: https://birdy.community. Namnfältet går att söka på, till exempel ”Birdy: bird ID & field guide”.'),
    ('fb-short', 'Facebook', 'intro', 101,
     'Identify birds by camera, photo or song. Free on Android, iPhone coming. birdy.community',
     'Sidans korta presentation (Intro). Webbplats under Detaljer: https://birdy.community'),
    ('fb-long', 'Facebook', 'om sidan', None,
     "Birdy is a bird guide and field journal for your phone. Point the camera at a bird, pick a photo or let it sing, and Birdy suggests the species right on your phone and shows how sure it is. Identification by camera, photo and sound is free for everyone.\n\n"
     "The field guide covers hundreds of bird species with photos and descriptions. Save what you see in your own field journal, with the date, a note and the place if you like. Your journal stays on your phone: no account, no ads, and Birdy does not upload your sightings.\n\n"
     "Birdy Premium is an optional extra with a map of your finds, your journal as a PDF, season statistics and seven extra badges.\n\n"
     "Birdy is made by AlbIT AB in Solna, Sweden. Available now for Android on Google Play. The iPhone app is coming.\n\n"
     "https://birdy.community",
     'Den längre texten under Om (Detaljer, beskrivning).'),
    ('yt', 'YouTube', 'kanalbeskrivning', 1000,
     "See the song: short videos where you hear a bird first and meet it after. Turn the sound on, listen and guess, then see the bird and its name.\n\n"
     "The videos come from Birdy, a bird guide and field journal for your phone. Point the camera at a bird, pick a photo or record its song, and Birdy suggests the species and shows how sure it is. Identification, sound ID included, is free. Your field journal stays on your phone: no account and no ads. Birdy Premium is an optional extra.\n\n"
     "Free on Android via Google Play. The iPhone app is coming.\n\n"
     "Photos and recordings in the videos come from Wikimedia Commons and are credited in each video's description.\n\n"
     "https://birdy.community",
     'Lägg även https://birdy.community som länk i kanalens Länkar.'),
    ('tt', 'TikTok', 'bio, senare', 80,
     'Listen first, the name comes next. Free bird ID app: birdy.community',
     'När TikTok-kontot skapas. Webbplatsfältet kräver ofta ett företagskonto.'),
]

bio_md = (OUT / 'bios.md').read_text(encoding='utf-8')
cards = {}
for key, platform, what, limit, text, extra in BIOS:
    for para in text.split('\n\n'):
        assert para in bio_md, f'bios.md is missing the {platform} {what} text: {para[:60]}'
    n = len(text)
    if limit:
        assert n <= limit, (platform, what, n, limit)
    count = f'{n} / {limit}' if limit else f'{n} tecken'
    cards[key] = f'''    <article class="bio">
      <header><h3>{platform}<span class="letter" style="font-size:.8em"> {html.escape(what)}</span></h3><span class="count">{count}</span></header>
      <p class="bio-text" id="bio-{key}">{html.escape(text)}</p>
      <p class="bio-extra">{html.escape(extra)}</p>
      <button type="button" class="copy" data-copy="bio-{key}">Kopiera</button><span class="status" role="status"></span>
    </article>'''

YT_DESC = 'See the song: short videos where you hear a bird first and meet it after.'
IG_BIO = BIOS[0][4]
FB_SHORT = BIOS[1][4]
LINK_SVG = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" d="M10 14a4.5 4.5 0 0 0 6.4 0l3.2-3.2a4.5 4.5 0 0 0-6.4-6.4L12 5.6M14 10a4.5 4.5 0 0 0-6.4 0l-3.2 3.2a4.5 4.5 0 0 0 6.4 6.4L12 18.4"/></svg>'
BAR = '<div class="ph-bar"><span>9:41</span><span class="ph-bat"></span></div>'
NAMES = {'know': 'Know the bird. Keep the moment.', 'name': 'Every bird has a name.'}


def fulls(s):
    n = NAMES[s]
    return f'''    <figure>
      <div class="full fb i-fb-{s}" role="img" aria-label="Facebook-omslaget med {n}, 1640 gånger 720">
        <div class="g cut" style="left:0;right:0;top:0;height:6.67%"></div>
        <div class="g cut" style="left:0;right:0;bottom:0;height:6.67%"></div>
        <div class="g cut soft" style="left:0;top:6.67%;bottom:6.67%;width:10.98%"></div>
        <div class="g cut soft" style="right:0;top:6.67%;bottom:6.67%;width:10.98%"></div>
        <div class="g safe" style="left:10.98%;right:10.98%;top:6.67%;bottom:6.67%"></div>
        <div class="g ring" style="left:14.1%;width:28.1%;aspect-ratio:1;top:67.9%"></div>
      </div>
      <figcaption><b>Facebook-omslag</b> <code>facebook-cover-flock-{s}.png</code></figcaption>
    </figure>
    <figure>
      <div class="full yt i-yt-{s}" role="img" aria-label="YouTube-bannern med {n}, 2560 gånger 1440">
        <div class="g cut" style="left:0;right:0;top:0;height:35.3%"></div>
        <div class="g cut" style="left:0;right:0;bottom:0;height:35.3%"></div>
        <div class="g cut soft" style="left:0;top:35.3%;bottom:35.3%;width:19.8%"></div>
        <div class="g cut soft" style="right:0;top:35.3%;bottom:35.3%;width:19.8%"></div>
        <div class="g safe" style="left:19.8%;right:19.8%;top:35.3%;bottom:35.3%"></div>
        <span class="g-tag" style="left:1.2%;top:2%">bara TV</span>
      </div>
      <figcaption><b>YouTube-banner</b> <code>youtube-banner-flock-{s}.png</code></figcaption>
    </figure>'''


def mocks(s):
    n = NAMES[s]
    grid = ''.join(f'<i class="i-ig-{i}"></i>' for i in range(1, 7))
    hidden = '' if s == 'know' else ' hidden'
    return f'''<div class="mockset" data-slogan="{s}"{hidden}>
  <div class="stage">
    <div class="phones">
      <figure>
        <div class="phone"><div class="ph">{BAR}
          <div class="ig-top">birdy.community<i></i></div>
          <div class="ig-head"><i class="av ig-av i-av-flock" role="img" aria-label="Profilbilden på Instagram"></i>
            <div class="ig-stats"><p><b>7</b><span>posts</span></p><p><b>0</b><span>followers</span></p><p><b>0</b><span>following</span></p></div></div>
          <div class="ig-txt"><p class="ig-name">Birdy</p><p class="ig-cat">App page</p><p>{html.escape(IG_BIO)}</p><p class="ig-link">{LINK_SVG}birdy.community</p></div>
          <div class="ig-btns"><span class="pri">Follow</span><span>Message</span></div>
          <div class="ig-grid" role="img" aria-label="Rutnät med omslagen från veckans videor">{grid}</div>
        </div></div>
        <figcaption><b>Instagram</b>, mobil, med föreslaget namn (i dag @app.birdy).</figcaption>
      </figure>
      <figure>
        <div class="phone"><div class="ph fb">{BAR}
          <div class="fb-cover i-fb-{s}" role="img" aria-label="Facebook-omslaget med {n} i mobilen"></div>
          <i class="av fb-av i-av-flock"></i>
          <div class="fb-txt"><p class="fb-name">Birdy</p><p class="fb-meta"><b>0</b> followers · App page</p><p>{html.escape(FB_SHORT)}</p></div>
          <div class="fb-btns"><span class="pri">Follow</span><span>Message</span></div>
          <div class="tabs"><span class="on">Posts</span><span>About</span><span>Photos</span><span>Videos</span></div>
          <div class="post"><div class="post-h"><i class="av i-av-flock"></i><div><b>Birdy</b><span>1 h · Public</span></div></div><p>Whose song is this? Turn the sound on.</p><i class="pic i-ig-1"></i></div>
        </div></div>
        <figcaption><b>Facebook</b>, mobil: 16:9, sidorna beskärs.</figcaption>
      </figure>
      <figure>
        <div class="phone"><div class="ph">{BAR}
          <div class="yt-banner i-yt-{s}" role="img" aria-label="YouTube-bannern med {n} i mobilen"></div>
          <div class="yt-id"><i class="av yt-av i-av-flock"></i><div><p class="yt-name">Birdy</p><p class="yt-meta">@birdy.community</p><p class="yt-meta">0 subscribers · 7 videos</p></div></div>
          <p class="yt-desc">{html.escape(YT_DESC)}<b> ...more</b></p>
          <p class="yt-links">birdy.community and 1 more link</p>
          <span class="yt-sub">Subscribe</span>
          <div class="tabs"><span class="on">Home</span><span>Shorts</span><span>Videos</span><span>Posts</span></div>
          <div class="shelf"><p class="shelf-h">Shorts</p><div class="shorts"><i class="i-ig-1"></i><i class="i-ig-2"></i><i class="i-ig-3"></i></div></div>
        </div></div>
        <figcaption><b>YouTube</b>, mobil: bara den säkra ytan.</figcaption>
      </figure>
    </div>
    <div class="desks">
      <figure>
        <div class="desk"><div class="dk">
          <div class="fbd-cover i-fb-{s}" role="img" aria-label="Facebook-omslaget med {n} på datorn"></div>
          <div class="fbd-id"><i class="av fbd-av i-av-flock"></i><div class="fbd-txt"><p class="fbd-name">Birdy</p><p class="fbd-meta">0 followers</p></div><div class="fbd-btns"><span class="pri">Follow</span><span>Message</span></div></div>
          <div class="tabs"><span class="on">Posts</span><span>About</span><span>Mentions</span><span>Followers</span><span>Photos</span><span>Videos</span></div>
        </div></div>
        <figcaption><b>Facebook</b>, dator: hela bredden, toppen och botten beskärs.</figcaption>
      </figure>
      <figure>
        <div class="desk"><div class="dk">
          <div class="ytd-banner i-yt-{s}" role="img" aria-label="YouTube-bannern med {n} på datorn"></div>
          <div class="ytd-id"><i class="av ytd-av i-av-flock"></i><div class="ytd-txt"><p class="ytd-name">Birdy</p><p class="ytd-meta"><b>@birdy.community</b> · 0 subscribers · 7 videos</p><p class="ytd-desc">{html.escape(YT_DESC)}<b> ...more</b></p><p class="ytd-links">birdy.community and 1 more link</p><span class="ytd-sub">Subscribe</span></div></div>
          <div class="tabs"><span class="on">Home</span><span>Videos</span><span>Shorts</span><span>Playlists</span><span>Posts</span></div>
        </div></div>
        <figcaption><b>YouTube</b>, dator: mittbandet i hela bredden.</figcaption>
      </figure>
    </div>
  </div>
</div>'''


page = (HERE / 'preview.html').read_text(encoding='utf-8')
page = page.replace('/*{{IMAGES}}*/', '\n'.join(css))
for s in SLOGANS:
    page = page.replace(f'<!--{{{{FULLS:{s}}}}}-->', fulls(s))
    page = page.replace(f'<!--{{{{MOCKS:{s}}}}}-->', mocks(s))
page = page.replace('<!--{{BIOS_SHORT}}-->', '\n'.join(cards[k] for k in ('ig', 'fb-short', 'tt')))
page = page.replace('<!--{{BIOS_LONG}}-->', '\n'.join(cards[k] for k in ('fb-long', 'yt')))
assert '{{' not in page, re.findall(r'\{\{[^}]+\}\}', page)
assert chr(0x2014) not in page, 'em dash in the page'
for banned in ('839', 'Europe', 'Europa'):
    hits = [m.start() for m in re.finditer(banned, re.sub(r'data:image/webp;base64,[A-Za-z0-9+/=]+', '', page))]
    print(f'{banned}: {len(hits)}')
(OUT / 'index.html').write_text(page, encoding='utf-8')
print(f'index.html {len(page.encode("utf-8")) / 1024:.0f} KB')
