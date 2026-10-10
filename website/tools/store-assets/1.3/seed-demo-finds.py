"""Seed a demo field season into the Birdy debug app on the emulator, for the 1.3.1 store screenshots (2026-10-10).

Every find photo is the species' own CC0 or public-domain hero image from the app's bundled assets (asset-pack), and
every location is a made-up point at a public birding spot in Skaraborg, never a real personal place.

Recipe (Git Bash on Windows: export MSYS_NO_PATHCONV=1 first, or device paths get rewritten):
  1. Install the debug APK on the emulator, set the app language (cmd locale set-app-locales se.birdy.android.debug
     --locales sv-SE), open the app once and skip the intro, then force-stop it.
  2. adb exec-out run-as se.birdy.android.debug cat databases/birdy-observations.db > seed-obs.db
  3. python seed-demo-finds.py seed-obs.db seed-photos   (needs Pillow; ASSETS below points at a 1.3.x checkout)
  4. adb push seed-photos/. /data/local/tmp/seed/ and adb push seed-obs.db /data/local/tmp/birdy-observations.db,
     chmod -R a+r both, then run-as: cp the JPEGs to files/observations/, cp the db to databases/ and remove the
     -journal file. Start the app.
"""
import random
import sqlite3
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

from PIL import Image

PKG = 'se.birdy.android.debug'
FILES = f'/data/user/0/{PKG}/files/observations'
ASSETS = Path('C:/w/birdy-130/asset-pack/src/main/assets/images')

# (local date, local time, QID, confidence)
FINDS = [
    ('2026-01-03', '10:12', 'Q26135', 0.94),     # Sidensvans
    ('2026-01-03', '10:20', 'Q25382', 0.97),     # Domherre
    ('2026-01-06', '09:05', 'Q25485', 0.98),     # Talgoxe
    ('2026-01-17', '13:40', 'Q214571', 0.91),    # Tallbit
    ('2026-01-24', '11:15', 'Q20754771', 0.88),  # Gråsiska
    ('2026-02-07', '10:30', 'Q26209', 0.96),     # Större hackspett
    ('2026-02-14', '09:50', 'Q170831', 0.95),    # Stjärtmes
    ('2026-02-21', '14:10', 'Q193593', 0.89),    # Trädkrypare
    ('2026-02-21', '14:30', 'Q25485', 0.97),     # Talgoxe
    ('2026-03-14', '08:40', 'Q25612', 0.96),     # Sångsvan
    ('2026-03-21', '07:55', 'Q25882', 0.95),     # Grågås
    ('2026-03-28', '12:20', 'Q25385', 0.90),     # Ormvråk
    ('2026-03-29', '08:10', 'Q25334', 0.98),     # Rödhake
    ('2026-04-04', '07:30', 'Q25399', 0.97),     # Sädesärla
    ('2026-04-11', '06:45', 'Q18854', 0.92),     # Storspov
    ('2026-04-11', '19:50', 'Q26114', 0.86),     # Morkulla
    ('2026-04-18', '08:15', 'Q25928', 0.97),     # Strandskata
    ('2026-04-18', '08:40', 'Q28106837', 0.93),  # Bläsand
    ('2026-04-25', '05:55', 'Q26349', 0.91),     # Taltrast
    ('2026-04-25', '06:10', 'Q25612', 0.95),     # Sångsvan
    ('2026-05-02', '09:20', 'Q25429', 0.96),     # Ladusvala
    ('2026-05-09', '06:30', 'Q188446', 0.90),    # Svarthätta
    ('2026-05-09', '10:05', 'Q26055', 0.93),     # Hussvala
    ('2026-05-16', '05:40', 'Q206130', 0.87),    # Näktergal
    ('2026-05-16', '07:10', 'Q25422', 0.95),     # Skäggdopping
    ('2026-05-23', '06:20', 'Q159080', 0.88),    # Rörsångare
    ('2026-05-23', '07:00', 'Q25984', 0.92),     # Gulärla
    ('2026-05-30', '08:30', 'Q46143', 0.89),     # Rosenfink
    ('2026-05-30', '09:00', 'Q25485', 0.98),     # Talgoxe
    ('2026-06-06', '11:10', 'Q18875', 0.94),     # Fisktärna
    ('2026-06-13', '07:30', 'Q192817', 0.92),    # Skäggmes
    ('2026-06-13', '21:40', 'Q185099', 0.85),    # Vattenrall
    ('2026-06-20', '08:00', 'Q155869', 0.91),    # Buskskvätta
    ('2026-07-11', '09:45', 'Q25700', 0.93),     # Kricka
    ('2026-07-18', '10:30', 'Q18859', 0.94),     # Rödbena
    ('2026-08-08', '08:20', 'Q18850', 0.92),     # Drillsnäppa
    ('2026-08-15', '07:50', 'Q25273', 0.98),     # Gråhäger
    ('2026-08-22', '06:40', 'Q25692', 0.87),     # Enkelbeckasin
    ('2026-09-05', '09:30', 'Q26650', 0.90),     # Kärrsnäppa
    ('2026-09-12', '08:10', 'Q21148', 0.91),     # Ljungpipare
    ('2026-09-19', '10:00', 'Q180991', 0.96),    # Storskrake
    ('2026-09-26', '09:15', 'Q25612', 0.94),     # Sångsvan
    ('2026-10-03', '08:50', 'Q29865', 0.95),     # Knipa
    ('2026-10-08', '08:05', 'Q25485', 0.98),     # Talgoxe
    ('2026-10-09', '08:30', 'Q25382', 0.96),     # Domherre
    ('2026-10-10', '08:55', 'Q25345384', 0.94),  # Kaja
]

# Made-up spots for the finds, spread over well-known public birding places in Skaraborg so the map shows separate
# pins (the map fits all pins): the shores of Hornborgasjön, a garden in Skara, Varnhem, Kinnekulle, Lidköping's shore.
SPOTS = {
    'A': (58.3045, 13.5820),  # Hornborgasjön, east shore
    'B': (58.3465, 13.5075),  # Hornborgasjön, north-west shore
    'C': (58.2790, 13.5480),  # Hornborgasjön, south end
    'D': (58.3860, 13.4390),  # a garden in Skara
    'E': (58.3850, 13.6550),  # Varnhem, the edge of Billingen
    'G': (58.1720, 13.5420),  # Mösseberg, Falköping
    'I': (58.5850, 13.4050),  # Kinnekulle
    'L': (58.5050, 13.1600),  # Vänern's shore at Lidköping
}
SPOT_OF = {
    'Q26135': 'D', 'Q25382': 'D', 'Q214571': 'E', 'Q20754771': 'E', 'Q26209': 'E', 'Q170831': 'E', 'Q193593': 'E',
    'Q25612': 'A', 'Q25882': 'A', 'Q25385': 'G', 'Q25334': 'D', 'Q25399': 'D', 'Q18854': 'C', 'Q26114': 'E',
    'Q25928': 'L', 'Q28106837': 'B', 'Q26349': 'E', 'Q25429': 'C', 'Q188446': 'I', 'Q26055': 'D', 'Q206130': 'I',
    'Q25422': 'B', 'Q159080': 'B', 'Q25984': 'C', 'Q46143': 'A', 'Q18875': 'L', 'Q192817': 'A', 'Q185099': 'A',
    'Q155869': 'C', 'Q25700': 'B', 'Q18859': 'A', 'Q18850': 'L', 'Q25273': 'A', 'Q25692': 'C', 'Q26650': 'A',
    'Q21148': 'C', 'Q180991': 'L', 'Q29865': 'L', 'Q25485': 'D', 'Q25345384': 'D',
}

# Summer time in Sweden 2026: 29 March 01:00 UTC to 25 October 01:00 UTC.
DST_START = datetime(2026, 3, 29, 1, 0, tzinfo=timezone.utc)
DST_END = datetime(2026, 10, 25, 1, 0, tzinfo=timezone.utc)


def to_utc_ms(day: str, hhmm: str) -> int:
    naive = datetime.fromisoformat(f'{day}T{hhmm}:00')
    for offset in (2, 1):
        utc = (naive - timedelta(hours=offset)).replace(tzinfo=timezone.utc)
        in_dst = DST_START <= utc < DST_END
        if (offset == 2) == in_dst:
            return int(utc.timestamp() * 1000)
    raise ValueError(day)


def main(db_path: str, out_dir: str) -> None:
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(41)
    loc_rng = random.Random(7)
    con = sqlite3.connect(db_path)
    con.execute('DELETE FROM observation')
    rows = []
    for stamp, (day, hhmm, qid, conf) in enumerate(sorted(FINDS, key=lambda f: (f[0], f[1])), start=1):
        src = ASSETS / qid / 'hero.webp'
        if not src.exists():
            raise SystemExit(f'no hero image for {qid}')
        name = f'{uuid.UUID(int=rng.getrandbits(128))}.jpg'
        img = Image.open(src).convert('RGB')
        img.thumbnail((1024, 1024), Image.LANCZOS)
        img.save(out / name, 'JPEG', quality=85)
        captured = to_utc_ms(day, hhmm)
        spot_lat, spot_lon = SPOTS[SPOT_OF[qid]]
        lat = round(spot_lat + loc_rng.uniform(-0.0015, 0.0015), 5)
        lon = round(spot_lon + loc_rng.uniform(-0.0025, 0.0025), 5)
        rows.append((str(uuid.UUID(int=rng.getrandbits(128))), qid, captured, captured + 40_000,
                     f'{FILES}/{name}', '', conf, lat, lon, None, stamp, None, 'photo'))
    con.executemany(
        'INSERT INTO observation (id, species_id, captured_at_ms, saved_at_ms, photo_path, note, confidence, '
        'latitude, longitude, location_label, stamp_number, audio_path, source) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', rows)
    con.commit()
    print(f'{len(rows)} finds, {len({r[1] for r in rows})} species, photos in {out}')
    con.close()


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
