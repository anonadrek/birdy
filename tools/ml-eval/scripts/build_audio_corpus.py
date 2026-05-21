"""build_audio_corpus.py — fetch CC-licensed xeno-canto recordings for audio eval.

Fetches up to 3 recordings per species from xeno-canto using their public API v3.
Only recordings with CC-BY, CC0, or public-domain licences are downloaded.

API v3 requires a free API key:
  1. Register at https://xeno-canto.org/
  2. Retrieve your key at https://xeno-canto.org/account
  3. Export it before running this script:
       export XC_API_KEY=<your-key>
     (or write to tools/ml-eval/.xc_key — a one-line text file with the key)

Each audio file is:
  1. Downloaded as MP3 (xeno-canto only provides MP3).
  2. Converted to 48 kHz mono PCM_16 WAV via ffmpeg (must be on PATH).
  3. Cropped to the highest-RMS 3-second window using numpy.

Output:
  tools/ml-eval/corpus_audio/<family>/<qid>_<sci_underscored>_<xenoId>.wav
  tools/ml-eval/corpus_audio/manifest_audio.yaml

ffmpeg is required. If it is not on PATH the script exits with a clear error.

Preprocessing mirrors Android:
  - 48 000 Hz sample rate (AudioRecord default in AndroidAudioRecorder)
  - Mono, signed 16-bit PCM
  - 3 s window → 144 000 samples
  - Normalisation to float32 in [-1, 1] is done in eval_birdnet_audio.py,
    not here.
"""
from __future__ import annotations

import json
import math
import os
import shutil
import struct
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

import yaml

# ---------------------------------------------------------------------------
# Paths (script is at tools/ml-eval/scripts/<file>.py → 4 levels up = repo root)
# ---------------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).resolve().parent
EVAL_DIR = SCRIPT_DIR.parent
ROOT = EVAL_DIR.parent.parent

MAPPING_PATH = (
    ROOT
    / "shared"
    / "ml"
    / "src"
    / "commonMain"
    / "composeResources"
    / "files"
    / "ml"
    / "birdnet_lite_to_qid.json"
)
SPECIES_DIR = ROOT / "shared" / "content" / "species"
CORPUS_DIR = EVAL_DIR / "corpus_audio"
MANIFEST_PATH = CORPUS_DIR / "manifest_audio.yaml"

SAMPLE_RATE = 48_000
DURATION_S = 3
EXPECTED_SAMPLES = SAMPLE_RATE * DURATION_S  # 144 000

# Recordings to fetch per species (3 gives variety while keeping download time sane)
RECORDINGS_PER_SPECIES = 3

# Species targeted for eval (chosen to cover diverse families + be common in EU)
TARGET_SCI_NAMES: list[str] = [
    "Parus major",
    "Turdus merula",
    "Erithacus rubecula",
    "Fringilla coelebs",
    "Passer domesticus",
    "Columba palumbus",
    "Cuculus canorus",
    "Hirundo rustica",
    "Sylvia atricapilla",
    "Phoenicurus ochruros",
    "Carduelis carduelis",
    "Cyanistes caeruleus",
    "Sitta europaea",
    "Troglodytes troglodytes",
    "Phylloscopus collybita",
    "Motacilla alba",
    "Garrulus glandarius",
    "Corvus corone",
    "Accipiter nisus",
    "Falco tinnunculus",
    "Aegithalos caudatus",
    "Anas platyrhynchos",
    "Larus argentatus",
    "Dendrocopos major",
    "Sturnus vulgaris",
    "Regulus regulus",
    "Luscinia megarhynchos",
    "Buteo buteo",
    "Alauda arvensis",
    "Alcedo atthis",
]

XENO_API = "https://xeno-canto.org/api/3/recordings"
XC_KEY_FILE = EVAL_DIR / ".xc_key"

# Licences considered CC/public-domain
CC_PREFIXES = ("//creativecommons.org/licenses/by", "//creativecommons.org/publicdomain")


def load_xc_api_key() -> str:
    """Return the xeno-canto API key from env or .xc_key file. Exit with message if missing."""
    env_key = os.environ.get("XC_API_KEY", "").strip()
    if env_key:
        return env_key
    if XC_KEY_FILE.exists():
        file_key = XC_KEY_FILE.read_text(encoding="utf-8").strip()
        if file_key:
            return file_key
    raise SystemExit(
        "ERROR: xeno-canto API key not found.\n"
        "xeno-canto API v3 requires a free API key. To obtain one:\n"
        "  1. Register at https://xeno-canto.org/\n"
        "  2. Retrieve your key at https://xeno-canto.org/account\n"
        "  3. Either:\n"
        f"       export XC_API_KEY=<your-key>   (in this shell)\n"
        f"       echo <your-key> > {XC_KEY_FILE}   (persist locally)\n"
    )


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def check_ffmpeg() -> None:
    if shutil.which("ffmpeg") is None:
        raise SystemExit(
            "ERROR: ffmpeg not found on PATH.\n"
            "Install ffmpeg and ensure it is accessible from this shell, then re-run.\n"
            "  Windows: winget install ffmpeg  OR  choco install ffmpeg\n"
            "  macOS:   brew install ffmpeg\n"
            "  Linux:   sudo apt install ffmpeg"
        )


def load_birdnet_mapping() -> dict[str, str]:
    """Load birdnet_lite_to_qid.json → {qid: None} set for fast membership checks.

    Returns mapping from QID → BirdNET-class-index (inverted for our use),
    but we only need the *set of mapped QIDs* here so we return just the values.
    Actually returns {qid: first_index_str} — caller only uses .keys().
    """
    data = json.loads(MAPPING_PATH.read_text(encoding="utf-8"))
    raw: dict[str, str] = data["mapping"]  # singular key per spec
    # Invert: index_str -> qid  →  we want qid -> index_str (first occurrence)
    qid_to_idx: dict[str, str] = {}
    for idx_str, qid in raw.items():
        if qid not in qid_to_idx:
            qid_to_idx[qid] = idx_str
    return qid_to_idx


def load_species_index() -> dict[str, tuple[str, str, str]]:
    """Return {sci_name: (qid, family_lower, sci_name)} for all species in content dir."""
    index: dict[str, tuple[str, str, str]] = {}
    for family_dir in sorted(SPECIES_DIR.iterdir()):
        if not family_dir.is_dir():
            continue
        family = family_dir.name  # already lowercase directory name
        for yaml_file in sorted(family_dir.glob("Q*.yaml")):
            qid = yaml_file.stem
            try:
                d = yaml.safe_load(yaml_file.read_text(encoding="utf-8"))
            except Exception:
                continue
            sci = (d.get("scientific_name") or "").strip()
            if sci:
                index[sci] = (qid, family, sci)
    return index


def is_cc_licensed(lic_url: str) -> bool:
    """Return True if the licence URL refers to a CC-BY / CC0 / public-domain licence."""
    url_lower = lic_url.lower()
    return any(prefix in url_lower for prefix in CC_PREFIXES)


def xeno_query(sci_name: str, api_key: str, page: int = 1) -> dict:  # type: ignore[type-arg]
    """Query xeno-canto API v3 for recordings of *sci_name*. Returns parsed JSON."""
    params = urllib.parse.urlencode({
        "query": f'"{sci_name}" q:A q:B',
        "key": api_key,
        "page": str(page),
    })
    url = f"{XENO_API}?{params}"
    ua = "birdy-eval/1.0 (github.com/anonadrek/birdy)"
    req = urllib.request.Request(url, headers={"User-Agent": ua})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        print(f"    xeno-canto HTTP {exc.code} for {sci_name!r} — retrying in 2 s …")
        time.sleep(2.0)
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))


def download_bytes(url: str) -> bytes | None:
    """Download *url* with one retry. Returns bytes or None on failure."""
    if not url.startswith("http"):
        url = "https:" + url
    ua = "birdy-eval/1.0 (github.com/anonadrek/birdy)"
    req = urllib.request.Request(url, headers={"User-Agent": ua})
    for attempt in range(2):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read()
        except Exception as exc:
            if attempt == 0:
                print(f"    Download error ({exc}) — retrying in 2 s …")
                time.sleep(2.0)
            else:
                print(f"    Download failed: {exc}")
    return None


def convert_to_wav(mp3_bytes: bytes, out_path: Path) -> bool:
    """Convert *mp3_bytes* to 48 kHz mono PCM_16 WAV at *out_path* via ffmpeg."""
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as tmp:
        tmp_path = Path(tmp.name)
        tmp.write(mp3_bytes)
    try:
        result = subprocess.run(
            [
                "ffmpeg", "-y",
                "-i", str(tmp_path),
                "-ar", str(SAMPLE_RATE),
                "-ac", "1",
                "-sample_fmt", "s16",
                str(out_path),
            ],
            capture_output=True,
            timeout=60,
        )
        return result.returncode == 0
    except Exception as exc:
        print(f"    ffmpeg error: {exc}")
        return False
    finally:
        tmp_path.unlink(missing_ok=True)


def read_wav_samples(wav_path: Path) -> list[int] | None:
    """Read PCM_16 samples from a WAV file. Returns list of int16 values or None."""
    with wav_path.open("rb") as f:
        header = f.read(44)
        if len(header) < 44 or header[:4] != b"RIFF":
            return None
        # Number of bytes of audio data
        data_size = struct.unpack_from("<I", header, 40)[0]
        pcm_bytes = f.read(data_size)
    n_samples = len(pcm_bytes) // 2
    if n_samples < EXPECTED_SAMPLES:
        return None  # too short
    samples = list(struct.unpack_from(f"<{n_samples}h", pcm_bytes))
    return samples


def highest_rms_window(samples: list[int], window: int = EXPECTED_SAMPLES) -> list[int]:
    """Return the *window*-length slice with the highest RMS energy."""
    if len(samples) <= window:
        return samples[:window] + [0] * max(0, window - len(samples))

    best_start = 0
    best_rms = -1.0
    step = window // 10  # stride = 10 % of window for speed
    for start in range(0, len(samples) - window + 1, step):
        chunk = samples[start : start + window]
        rms = math.sqrt(sum(s * s for s in chunk) / window)
        if rms > best_rms:
            best_rms = rms
            best_start = start

    return samples[best_start : best_start + window]


def write_wav(samples: list[int], path: Path) -> None:
    """Write *samples* as a 48 kHz mono PCM_16 WAV file."""
    n = len(samples)
    byte_rate = SAMPLE_RATE * 1 * 2  # 1 channel, 2 bytes/sample
    data_size = n * 2
    with path.open("wb") as f:
        f.write(b"RIFF")
        f.write(struct.pack("<I", 36 + data_size))
        f.write(b"WAVE")
        f.write(b"fmt ")
        f.write(struct.pack("<IHHIIHH", 16, 1, 1, SAMPLE_RATE, byte_rate, 2, 16))
        f.write(b"data")
        f.write(struct.pack("<I", data_size))
        f.write(struct.pack(f"<{n}h", *samples))


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    check_ffmpeg()
    api_key = load_xc_api_key()
    print(f"Using xeno-canto API key (length={len(api_key)}) for v3 endpoint")

    print("Loading birdnet_lite_to_qid.json …")
    qid_to_idx = load_birdnet_mapping()
    mapped_qids: set[str] = set(qid_to_idx.keys())
    print(f"  {len(mapped_qids)} QIDs mapped in BirdNET model")

    print("Loading species index from shared/content/species/ …")
    species_index = load_species_index()
    print(f"  {len(species_index)} species found")

    # Filter TARGET_SCI_NAMES
    valid_targets: list[tuple[str, str, str]] = []  # (sci, qid, family)
    print("\nFiltering target species …")
    for sci in TARGET_SCI_NAMES:
        if sci not in species_index:
            print(f"  SKIP {sci!r} — not found in species content dir")
            continue
        qid, family, _ = species_index[sci]
        if qid not in mapped_qids:
            print(f"  SKIP {sci!r} ({qid}) — not mapped in birdnet_lite_to_qid.json")
            continue
        valid_targets.append((sci, qid, family))
        print(f"  OK   {sci!r} ({qid}, {family})")

    print(f"\n{len(valid_targets)} species will be fetched "
          f"({len(TARGET_SCI_NAMES) - len(valid_targets)} skipped)\n")

    CORPUS_DIR.mkdir(parents=True, exist_ok=True)

    manifest_items: list[dict[str, str]] = []
    total_wav = 0

    for sci, qid, family in valid_targets:
        print(f"[{sci}]  ({qid}, {family})")
        sci_slug = sci.replace(" ", "_")

        try:
            result = xeno_query(sci, api_key)
        except Exception as exc:
            print(f"  ERROR querying xeno-canto: {exc} — skipping species")
            continue

        recordings = result.get("recordings", [])
        if not recordings:
            print("  No recordings found on xeno-canto — skipping")
            continue

        # Filter CC-licensed recordings
        cc_recs = [r for r in recordings if is_cc_licensed(r.get("lic", ""))]
        if not cc_recs:
            print("  No CC-licensed recordings found — skipping")
            continue

        fetched = 0
        for rec in cc_recs:
            if fetched >= RECORDINGS_PER_SPECIES:
                break

            xeno_id = rec.get("id", "unknown")
            mp3_url = rec.get("file", "") or rec.get("sono", {}).get("med", "")
            if not mp3_url:
                continue

            print(f"  Downloading xeno-canto #{xeno_id} …")
            mp3_bytes = download_bytes(mp3_url)
            if not mp3_bytes:
                continue

            dest_dir = CORPUS_DIR / family
            dest_dir.mkdir(parents=True, exist_ok=True)
            wav_name = f"{qid}_{sci_slug}_{xeno_id}.wav"
            wav_path = dest_dir / wav_name

            # Convert MP3 → 48 kHz mono WAV via ffmpeg
            tmp_wav = dest_dir / f"_tmp_{xeno_id}.wav"
            if not convert_to_wav(mp3_bytes, tmp_wav):
                print(f"    ffmpeg conversion failed — skipping #{xeno_id}")
                tmp_wav.unlink(missing_ok=True)
                continue

            # Read samples, crop to highest-RMS 3s window
            samples = read_wav_samples(tmp_wav)
            tmp_wav.unlink(missing_ok=True)
            if samples is None:
                print(f"    WAV too short or unreadable — skipping #{xeno_id}")
                continue

            cropped = highest_rms_window(samples)
            assert len(cropped) == EXPECTED_SAMPLES

            write_wav(cropped, wav_path)
            rel_path = f"corpus_audio/{family}/{wav_name}"
            manifest_items.append({
                "path": rel_path,
                "qid": qid,
                "family": family,
                "sci_name": sci,
                "xeno_id": xeno_id,
            })
            print(f"    Saved {wav_name} ({EXPECTED_SAMPLES} samples, "
                  f"{DURATION_S}s @ {SAMPLE_RATE} Hz)")
            fetched += 1
            total_wav += 1

            time.sleep(0.5)  # polite rate-limiting

        if fetched == 0:
            print(f"  WARNING: no usable recordings saved for {sci!r}")

    # Write manifest
    MANIFEST_PATH.write_text(
        yaml.dump(
            {"items": manifest_items},
            allow_unicode=True,
            default_flow_style=False,
            sort_keys=False,
        ),
        encoding="utf-8",
    )

    print(f"\n{'='*60}")
    print(f"Done. {total_wav} WAV file(s) saved to {CORPUS_DIR}/")
    print(f"Manifest written to {MANIFEST_PATH}")
    n_with_recs = len({m["qid"] for m in manifest_items})
    print(f"Species with recordings: {n_with_recs}/{len(valid_targets)}")


if __name__ == "__main__":
    main()
