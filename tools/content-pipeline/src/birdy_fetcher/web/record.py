"""The species record (website/src/data/species/<QID>.json) is the state between the steps
(spec 2026-09-25 appendix C, revised 2026-10-05). Each step owns some keys and leaves the
rest alone: sources owns SOURCE_KEYS, facts owns `facts` and `generated.facts`, verify owns
`flags`, waves owns `review.wave`, import owns `review` (minus `wave`) and `verification`,
write owns `text`, `status` and `generated.text`, publish owns `publish`."""

from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .images import ImageOut

Record = dict[str, Any]

SOURCE_KEYS = (
    "slug",
    "names",
    "family",
    "group",
    "iucn",
    "swedishRedList",
    "identifiable",
    "marginalia",
    "images",
    "audio",
    "wikipedia",
    "data",
)


def record_path(out_dir: Path, qid: str) -> Path:
    return out_dir / f"{qid}.json"


def load_record(path: Path) -> Record | None:
    if not path.exists():
        return None
    data: Record = json.loads(path.read_text(encoding="utf-8"))
    return data


def save_record(path: Path, record: Record) -> None:
    """Atomic (Minor 5, final review 2026-10-06): written to a temporary file next to the
    record and swapped in with `os.replace`, so a crash or a full disk mid-write leaves the
    previous record intact. The temp name does not match `Q*.json`."""
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(record, ensure_ascii=False, indent=2) + "\n"
    tmp = path.with_name(path.name + ".tmp")
    try:
        tmp.write_text(text, encoding="utf-8")
        os.replace(tmp, path)
    except BaseException:
        tmp.unlink(missing_ok=True)
        raise


def load_all(out_dir: Path) -> dict[str, Record]:
    records: dict[str, Record] = {}
    for path in sorted(out_dir.glob("Q*.json")):
        record = load_record(path)
        if record is not None:
            records[path.stem] = record
    return records


def new_record(qid: str) -> Record:
    return {
        "qid": qid,
        "status": "pending",
        "publish": False,
        "review": {},
        "facts": [],
        "text": None,
        "generated": {},
        "errors": [],
    }


def merge_sources(existing: Record | None, qid: str, sources: dict[str, Any]) -> Record:
    """Writes the source keys. A key whose value is None is removed (spec appendix C: e.g.
    `audio` is missing when no free recording exists)."""
    record = existing if existing is not None else new_record(qid)
    for key in SOURCE_KEYS:
        value = sources.get(key)
        if value is None:
            record.pop(key, None)
        else:
            record[key] = value
    return record


def image_dict(image: ImageOut) -> dict[str, Any]:
    return {
        "role": image.role,
        "file": image.file,
        "width": image.width,
        "height": image.height,
        "author": image.author,
        "license": image.license,
        "licenseUrl": image.license_url,
        "sourceUrl": image.source_url,
    }


def is_reviewed(record: Record) -> bool:
    """True once the automatic verification (Revision 2026-10-05) has set `verification`,
    directly or after Albin's decisions on a flag. The old `review.facts` is gone."""
    return bool(record.get("verification"))


def audio_id(audio: dict[str, Any]) -> str:
    """Identifies one recording (its whole `audio` object: source page, author, length), so
    a decision about it (`review.audioKept`) never carries over to a different one."""
    payload = json.dumps(audio, ensure_ascii=False, sort_keys=True)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]


def facts_hash(record: Record) -> str:
    payload = json.dumps(record.get("facts", []), ensure_ascii=False, sort_keys=True)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]


VOICE_FILE = "voice.mp3"


def delete_voice(images_out: Path, qid: str) -> str | None:
    """Deletes `<qid>/voice.mp3` if it is there. Never raises (follow-up 1, wave B review):
    every caller has saved the record already, and a file another program holds open
    (PermissionError on Windows) must not stop the run or the report after it. Returns
    why the file is still there, or None."""
    voice = images_out / qid / VOICE_FILE
    try:
        voice.unlink(missing_ok=True)
    except OSError as exc:
        return (
            f"{qid}: {voice} kunde inte tas bort ({type(exc).__name__}: {exc}); "
            "ta bort filen innan något committas"
        )
    return None


@dataclass
class VoiceSweep:
    removed: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


def sweep_orphan_voices(data_out: Path, images_out: Path) -> VoiceSweep:
    """A `voice.mp3` whose record has no `audio` (a struck recording whose delete failed,
    a crash between the download and the save) would go online without credits if it were
    committed. Deleted here, at the end of `web sources`, `web verify` and `web import`
    (follow-up 1, wave B review). A record that cannot be read is left to the step that
    reports it."""
    sweep = VoiceSweep()
    if not images_out.exists():
        return sweep
    for voice in sorted(images_out.glob(f"Q*/{VOICE_FILE}")):
        qid = voice.parent.name
        try:
            record = load_record(record_path(data_out, qid))
        except Exception:  # unreadable: not this sweep's to judge
            continue
        if record is not None and record.get("audio"):
            continue
        error = delete_voice(images_out, qid)
        if error is None:
            sweep.removed.append(qid)
        else:
            sweep.errors.append(error)
    return sweep
