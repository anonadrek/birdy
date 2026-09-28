"""The species record in website/src/data/species/<QID>.json (spec appendix C)."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any

from .images import ImageOut
from .model import LangText, WebTextOutput
from .slugs import slugify
from .source import SpeciesSource
from .wiki_full import WikiArticle
from .writer import PROMPT_VERSION


def _lang(t: LangText) -> dict[str, Any]:
    size, status = t.facts.size, t.facts.sweden_status
    return {
        "lead": t.lead,
        "fieldMarks": list(t.field_marks),
        "voice": t.voice,
        "whereWhen": t.where_when,
        "metaDescription": t.meta_description,
        "facts": {
            "size": size.model_dump() if size else None,
            "swedenStatus": status.model_dump() if status else None,
        },
    }


def build_record(
    *,
    source: SpeciesSource,
    group: str,
    text: WebTextOutput | None,
    articles: dict[str, WikiArticle],
    images: list[ImageOut],
    errors: list[str],
    model_id: str,
    effort: str,
    generated_at: datetime,
) -> dict[str, Any]:
    has_marginalia = source.marginalia_sv or source.marginalia_en
    status = "failed" if errors or text is None else "ok"
    lang_text = {"sv": _lang(text.sv), "en": _lang(text.en)} if text is not None else None
    record: dict[str, Any] = {
        "qid": source.qid,
        "status": status,
        "review": "unreviewed",
        "slug": {"sv": slugify(source.name_sv, "sv"), "en": slugify(source.name_en, "en")},
        "names": {"sv": source.name_sv, "en": source.name_en, "scientific": source.scientific_name},
        "family": {"latin": source.family, "sv": source.family_sv},
        "group": group,
        "iucn": source.iucn,
        "marginalia": (
            {"sv": source.marginalia_sv, "en": source.marginalia_en} if has_marginalia else None
        ),
        "images": [
            {
                "role": i.role,
                "file": i.file,
                "width": i.width,
                "height": i.height,
                "author": i.author,
                "license": i.license,
                "licenseUrl": i.license_url,
                "sourceUrl": i.source_url,
            }
            for i in images
        ],
        "wikipedia": {
            lang: (
                {"title": articles[lang].title, "revision": articles[lang].revision}
                if lang in articles
                else None
            )
            for lang in ("sv", "en")
        },
        # `text` is only ever populated on an "ok" record. The website schema enforces
        # text's length limits, so a failed record (unresolved checks, or no model answer
        # at all) must never put anything there -- the rejected attempt, if any, goes under
        # `rejectedText` instead, so it stays readable without ever reaching the schema.
        "text": lang_text if status == "ok" else None,
        "generated": {
            "model": model_id,
            "prompt": PROMPT_VERSION,
            "effort": effort,
            "at": generated_at.isoformat(),
        },
        "errors": errors,
    }
    if status == "failed":
        record["rejectedText"] = lang_text
    return record


def is_approved(path: Path) -> bool:
    if not path.exists():
        return False
    data: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    return data.get("review") == "approved"


def write_record(record: dict[str, Any], out_dir: Path, *, force: bool) -> str:
    """Writes the record. A file already marked review: approved is kept unless `force`."""
    path = out_dir / f"{record['qid']}.json"
    if not force and is_approved(path):
        return "skipped"
    out_dir.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return "written"
