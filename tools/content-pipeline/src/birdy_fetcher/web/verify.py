"""Automatic verification (spec 2026-09-25 Revision 2026-10-05): replaces Albin reviewing
every fact sheet by hand. V1 here is the second model that checks every fact against its
own quote. V2 and V3 (added in Task 14c) are code, no model."""

from __future__ import annotations

import html
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker
from .checks import _normalize
from .datamod import record_status_contradiction
from .facts import REQUIRED_TOPICS, STATUS_SV, TOPIC_SV
from .llm import MODELS, JsonModelClient, record_cost
from .record import Record
from .wiki_full import WikiArticle

PROMPT_VERSION = "verify-v1"
Verdict = Literal["supported", "partial", "unsupported"]

# Severity order: unsupported > partial > supported (highest to lowest)
VERDICT_SEVERITY = {"unsupported": 3, "partial": 2, "supported": 1}

NUMBER_TOLERANCE = 0.15  # 15 %, a startvärde (se tasktexten)
RED_LIST_ASSESSED_NONE = (None, "not_listed")

Kind = Literal["length", "wingspan", "weight", "clutch"]

# Thousands separator inside a number: plain space, NBSP (U+00A0) or narrow NBSP (U+202F).
_THOUSANDS_CHARS = "   "  # noqa: RUF001 -- NBSP + narrow NBSP, not plain spaces
_NUM = rf"\d{{1,3}}(?:[{_THOUSANDS_CHARS}]\d{{3}})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?"
_RANGE_SEP = r"(?:till|to|bis|-|–|—)"  # noqa: RUF001
_UNIT = r"(?:cm|mm|kg|g|ägg|eggs|egg|Eier)"

NUMBER_RE = re.compile(
    rf"(?P<low>{_NUM})\s*{_RANGE_SEP}\s*(?P<high>{_NUM})\s*(?P<unit_range>{_UNIT})\b"
    rf"|(?P<value>{_NUM})\s*(?P<unit_single>{_UNIT})\b",
    re.IGNORECASE,
)

# sv/en/de words that mean "wingspan" rather than "(body) length" for a cm/mm number.
WINGSPAN_KEYWORDS = (
    "vingbredd",
    "vingspann",
    "vingspannet",
    "spännvidd",
    "wingspan",
    "wing span",
    "flügelspannweite",
    "spannweite",
)

# sv/en/de words that mean "(body) length" for a cm/mm number.
LENGTH_KEYWORDS = (
    "längd",
    "längden",
    "lång",
    "length",
    "long",
    "länge",
    "körperlänge",
    "lang",
)

_WINGSPAN_WINDOW = 40


@dataclass(frozen=True)
class Measurement:
    low: float
    high: float
    unit: str
    kind: Kind


def _num(text: str) -> float:
    cleaned = re.sub(rf"[{_THOUSANDS_CHARS}]", "", text)
    return float(cleaned.replace(",", "."))


def _length_kind(text: str, start: int) -> Kind:
    """Determine if a measurement is "wingspan" or "length" based on the nearest keyword
    occurring earlier in the same sentence, within `_WINGSPAN_WINDOW` characters before
    the number. If both wingpsan and length keywords are present, the nearest one wins.
    If neither is found, default to "length" (spec V2)."""
    segment = text[max(0, start - _WINGSPAN_WINDOW) : start]
    for punct in ".!?\n":
        index = segment.rfind(punct)
        if index != -1:
            segment = segment[index + 1 :]
    lowered = segment.lower()

    # Find the position of the nearest wingspan and length keywords.
    wingspan_pos = -1
    for keyword in WINGSPAN_KEYWORDS:
        pos = lowered.rfind(keyword)
        if pos > wingspan_pos:
            wingspan_pos = pos

    length_pos = -1
    for keyword in LENGTH_KEYWORDS:
        pos = lowered.rfind(keyword)
        if pos > length_pos:
            length_pos = pos

    # If both found, use the nearest; if only one found, use it; default to "length".
    if wingspan_pos >= 0 and length_pos >= 0:
        return "wingspan" if wingspan_pos > length_pos else "length"
    elif wingspan_pos >= 0:
        return "wingspan"
    else:
        return "length"


def _normalise(low: float, high: float, unit_raw: str, text: str, start: int) -> Measurement:
    """Length/wingspan normalise to cm, weight to g, clutch size stays a plain egg count,
    so values of the same measure can be compared across articles (spec V2)."""
    unit = unit_raw.lower()
    if unit == "mm":
        return Measurement(low / 10, high / 10, "cm", _length_kind(text, start))
    if unit == "cm":
        return Measurement(low, high, "cm", _length_kind(text, start))
    if unit == "kg":
        return Measurement(low * 1000, high * 1000, "g", "weight")
    if unit == "g":
        return Measurement(low, high, "g", "weight")
    return Measurement(low, high, "ägg", "clutch")  # ägg/eggs/egg/Eier


def extract_measurements(text: str) -> list[Measurement]:
    """Every length/wingspan/weight/clutch-size number or range in the text, normalised to a
    shared unit per kind (cm, g, ägg) so the same measure can be compared across articles."""
    found: list[Measurement] = []
    for match in NUMBER_RE.finditer(text):
        unit_range = match.group("unit_range")
        if unit_range:
            low = _num(match.group("low"))
            high = _num(match.group("high"))
            unit_raw = unit_range
        else:
            value = _num(match.group("value"))
            low, high, unit_raw = value, value, match.group("unit_single")
        found.append(_normalise(low, high, unit_raw, text, match.start()))
    return found


def _padded(m: Measurement, tolerance: float) -> tuple[float, float]:
    margin = max(m.high, m.low, 1.0) * tolerance
    return m.low - margin, m.high + margin


def _overlaps(a: Measurement, b: Measurement, tolerance: float) -> bool:
    a_lo, a_hi = _padded(a, tolerance)
    b_lo, b_hi = _padded(b, tolerance)
    return a_lo <= b_hi and b_lo <= a_hi


KIND_LABEL: dict[Kind, str] = {
    "length": "cm",
    "wingspan": "cm",
    "weight": "g",
    "clutch": "ägg",
}


def number_flag(
    fact: dict[str, Any], articles: dict[str, WikiArticle], *, tolerance: float = NUMBER_TOLERANCE
) -> str | None:
    """None when the fact's own numbers agree with at least one measurement of the same kind
    (length/wingspan/weight/clutch size) in another cached article, or when no other article
    states that kind at all — that article then gives no evidence either way (spec V2)."""
    if fact["topic"] == "data" or fact.get("edited"):
        # An edited fact is Albin's own text (web import); its quote predates the edit
        # (follow-up 1, wave A review).
        return None
    own_article = fact["sources"][0]["article"] if fact.get("sources") else None
    own_all = extract_measurements(fact["sv"])
    for kind, label in KIND_LABEL.items():
        own = [m for m in own_all if m.kind == kind]
        if not own:
            continue
        others: list[Measurement] = []
        for lang, article in articles.items():
            if lang == own_article:
                continue
            others += [m for m in extract_measurements(article.text) if m.kind == kind]
        if not others:
            continue
        if not any(_overlaps(m, o, tolerance) for m in own for o in others):
            low, high = min(o.low for o in others), max(o.high for o in others)
            return (
                f"{fact['id']} anger ett tal i {label} som inte stämmer med de andra "
                f"artiklarna ({low:g} till {high:g} {label})"
            )
    return None


def number_flags(record: Record, articles: dict[str, WikiArticle]) -> list[dict[str, Any]]:
    flags = []
    for fact in record.get("facts", []):
        message = number_flag(fact, articles)
        if message:
            flags.append({"check": "V2", "factId": fact["id"], "message": message})
    return flags


def redlist_occurrence_flag(status_value: str, red_list: str | None) -> str | None:
    """None unless the status says the species is absent or a rare visitor while the red
    list has actually assessed it (spec V3: not `not_listed`, Sweden's stand-in for NA/NE)."""
    if status_value not in ("absent", "rare_visitor") or red_list in RED_LIST_ASSESSED_NONE:
        return None
    return (
        f"Statusen säger {STATUS_SV[status_value].lower()}, men arten har kategorin "
        f"{red_list} i Svenska rödlistan 2025."
    )


def status_flags(record: Record) -> list[dict[str, Any]]:
    """V3: the existing status-vs-Artportalen contradiction (spec 9.2) and the red list
    check above, both about the status fact s01. None once Albin kept the status on a V3
    flag (`review.statusConfirmed`): a forced re-verify after the import must not ask him
    again (follow-up 2, wave A review). `apply_facts` clears the confirmation whenever the
    facts change, and a status he sets with `ändra` clears it too (review_sheet)."""
    status_fact = next((f for f in record.get("facts", []) if f.get("id") == "s01"), None)
    if status_fact is None or record.get("review", {}).get("statusConfirmed"):
        return []
    flags = []
    # From the current data, never the stored statusSignal (I3, final review 2026-10-06).
    contradicts = record_status_contradiction(record)
    if contradicts:
        flags.append({"check": "V3", "factId": "s01", "message": contradicts})
    red_flag = redlist_occurrence_flag(status_fact["value"], record.get("swedishRedList"))
    if red_flag:
        flags.append({"check": "V3", "factId": "s01", "message": red_flag})
    return flags


class FactVerdict(BaseModel):
    fact_id: str
    verdict: Verdict
    reason: str


class FactVerifyOutput(BaseModel):
    verdicts: list[FactVerdict]


class FactCheckFailed(RuntimeError):  # noqa: N818
    pass


def _paragraph(article_text: str, quote: str) -> str:
    """The paragraph the quote sits in. Blank lines split paragraphs. Both sides are
    normalised like `quote_in_sources` does (typographic dashes and quotes, case,
    whitespace), or a quote that passed that check could lose its paragraph (Minor 4,
    final review 2026-10-06)."""
    start = _normalize(quote)[:40]
    for paragraph in article_text.split("\n\n"):
        if start in _normalize(paragraph):
            return paragraph.strip()
    return quote


def _claim(fact: dict[str, Any]) -> str:
    """A bare status label ("Stannfågel") says nothing about where; the checker is told it
    is the status in Sweden (C1, final review 2026-10-06)."""
    if fact["topic"] == "status":
        return f"Status i Sverige: {fact['sv']}"
    return str(fact["sv"])


def _fact_tag(fact: dict[str, Any]) -> str:
    """The opening tag, with the topic so the checker can tell a look-alike fact (which may
    describe the other species, see the prompt) from the rest, and that other species' name."""
    attrs = f'id="{fact["id"]}" topic="{fact["topic"]}"'
    other = (fact.get("other") or {}).get("scientific")
    if fact["topic"] == "lookalike" and other:
        attrs += f' other="{html.escape(str(other), quote=True)}"'
    return f"<fact {attrs}>"


def _render_sources(sources: list[dict[str, Any]], articles: dict[str, WikiArticle]) -> str:
    """Every quote of the fact with its article's language, each followed by the paragraph
    it sits in. V1 saw only the first quote before the R3 trial (2026-10-07), so a status
    backed by its second quote was struck for every Swedish species. A paragraph two quotes
    share is sent once, after the first of them."""
    parts: list[str] = []
    shown: set[str] = set()
    for source in sources:
        article = articles.get(source["article"])
        paragraph = _paragraph(article.text, source["quote"]) if article else source["quote"]
        lang = html.escape(str(source["article"]), quote=True)
        parts.append(f'<quote article="{lang}">{source["quote"]}</quote>')
        if paragraph not in shown:
            shown.add(paragraph)
            parts.append(f"<paragraph>{paragraph}</paragraph>")
    return "\n".join(parts)


def render_facts_for_check(facts: list[dict[str, Any]], articles: dict[str, WikiArticle]) -> str:
    blocks = []
    for fact in facts:
        if fact["topic"] == "data":
            continue
        blocks.append(
            f"{_fact_tag(fact)}\n<claim>{_claim(fact)}</claim>\n"
            f"{_render_sources(fact['sources'], articles)}\n</fact>"
        )
    return "\n\n".join(blocks)


@dataclass
class FactChecker:
    client: JsonModelClient
    cost: CostTracker
    prompt_path: Path
    model_key: str = "sonnet"
    effort: str = "high"

    async def check(
        self, facts: list[dict[str, Any]], articles: dict[str, WikiArticle], *, about: str
    ) -> dict[str, tuple[Verdict, str]]:
        """Fact id to (verdict, reason), for every non-`partial`/`unsupported`-free fact
        that is not a data fact. A fact the model did not answer for counts as unsupported.
        `about` names the species ("Talgoxe / Great Tit (Parus major)"): the prompt only
        counts a quote that describes this species (C1, final review 2026-10-06)."""
        # Edited facts are Albin's decisions from the sheet: their quote predates the edit
        # (an edited s01 has none at all), so V1 has nothing to judge them by (follow-up 1,
        # wave A review). They are never in `verdicts`, so they are always kept.
        checkable = [f for f in facts if f["topic"] != "data" and not f.get("edited")]
        if not checkable:
            return {}
        template = self.prompt_path.read_text(encoding="utf-8")
        # `about` first: the facts hold Wikipedia text, which must never be scanned for a
        # placeholder after it is in.
        system, user = _split_prompt(
            template, about=about, facts=render_facts_for_check(checkable, articles)
        )
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=[{"role": "user", "content": user}],
            effort=self.effort,
            schema=FactVerifyOutput,
        )
        record_cost(self.cost, self.model_key, reply)
        if reply.parsed is None:
            raise FactCheckFailed(
                f"kontrollen gav inget giltigt svar (stop_reason={reply.stop_reason})"
            )
        # Keep the most severe verdict per fact id (unsupported > partial > supported).
        by_id: dict[str, FactVerdict] = {}
        for v in reply.parsed.verdicts:
            existing = by_id.get(v.fact_id)
            if existing is None or VERDICT_SEVERITY[v.verdict] > VERDICT_SEVERITY[existing.verdict]:
                by_id[v.fact_id] = v
        result: dict[str, tuple[Verdict, str]] = {}
        for fact in checkable:
            verdict = by_id.get(fact["id"])
            if verdict is None:
                result[fact["id"]] = ("unsupported", "kontrollen gav inget svar för faktumet")
            elif verdict.verdict != "supported":
                result[fact["id"]] = (verdict.verdict, verdict.reason)
        return result


def strike_unsupported(
    facts: list[dict[str, Any]], verdicts: dict[str, tuple[Verdict, str]]
) -> tuple[list[dict[str, Any]], list[str]]:
    """Facts with a `partial` or `unsupported` verdict are struck; everything else (including
    every data fact, which is never in `verdicts`) is kept."""
    notes = [f"{fid} ströks ({label}): {reason}" for fid, (label, reason) in verdicts.items()]
    kept = [f for f in facts if f["id"] not in verdicts]
    return kept, notes


def status_strike_flag(verdicts: dict[str, tuple[Verdict, str]]) -> dict[str, Any] | None:
    """When V1 strikes the status fact (s01), `strike_unsupported` drops it from `kept` like
    any other rejected fact — but "status" is not in REQUIRED_TOPICS, so
    `missing_required_topics` never notices, there is no retry, and nothing else tells Albin
    the status vanished: a species could be verified with zero flags and a null status.
    Turn the strike itself into a flag instead, so it is never silent."""
    verdict = verdicts.get("s01")
    if verdict is None:
        return None
    _, reason = verdict
    return {
        "check": "V1",
        "factId": "s01",
        "message": (
            f"Statusen i Sverige ströks av faktakontrollen: {reason}. "
            "Bestäm status eller lämna tom."
        ),
    }


def missing_required_topics(facts: list[dict[str, Any]]) -> list[str]:
    return [TOPIC_SV[t] for t in REQUIRED_TOPICS if not any(f["topic"] == t for f in facts)]
