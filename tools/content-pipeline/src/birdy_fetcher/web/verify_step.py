"""Step between the fact sheet and the exception sheet (spec 2026-09-25 Revision 2026-10-05):
V1 to V4, run once per species, no Albin. A species with an empty `flags` list needs no row
in the exception sheet at all (Task 16)."""

from __future__ import annotations

import asyncio
import contextlib
import hashlib
import os
import shutil
import tempfile
from dataclasses import dataclass, field, replace
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from ..cache import Cache
from ..cost import CostTracker, MaxCostExceeded
from .audio import audio_record, convert_to_mp3, rejection
from .audio_check import AudioCheckFailed, AudioVerdict, audio_verdict, classify_clip
from .checks import without_dashes
from .defaults import EFFORTS, FACTS_EFFORT, FACTS_MODEL_KEY
from .facts import apply_facts
from .facts_step import PROMPT_VERSION as FACTS_PROMPT_VERSION
from .facts_step import FactExtractor, facts_generated
from .llm import MODELS, AnthropicJsonClient, JsonModelClient
from .paths import WebPaths
from .record import (
    STAGED_VOICE_FILE,
    VOICE_FILE,
    Record,
    audio_id,
    delete_voice,
    facts_hash,
    file_sha256,
    load_record,
    record_path,
    save_record,
    sweep_orphan_voices,
)
from .report import StepOutcome, render_step_report, sweep_outcome, write_step_report
from .source import SpeciesSource, load_approved, load_name_index
from .sources_step import ArticleSource, AudioSource
from .verify import (
    FactChecker,
    missing_required_topics,
    number_flags,
    status_flags,
    status_strike_flag,
    strike_unsupported,
)
from .wiki_full import FullWikiClient

PROMPT_VERSION = "verify-v1"
# A short tracked clip the audio model is run on once before any species (follow-up 3, wave
# A review): it proves ffmpeg, uv and TensorFlow work, and does the first `uv sync`.
PREFLIGHT_CLIP = Path("fixtures") / "chirp_3s_48k.wav"
# How many other allowed Commons recordings V4 tries when the model does not keep the
# species' recording (fix wave 2026-10-07).
MAX_ALTERNATIVES = 3


class AudioPreflightFailed(RuntimeError):  # noqa: N818
    """The audio model cannot run at all, so the whole run stops before any paid call
    and before anything is written."""


@dataclass(frozen=True)
class VerifyOptions:
    qids: tuple[str, ...] = ()
    model_key: str = "sonnet"
    effort: str = "high"
    max_cost: float | None = None
    force: bool = False
    workers: int = 4
    # The V1 retry writes a new fact sheet. None (the default) means the record's own
    # `generated.facts` model and effort, else defaults.FACTS_* (Minor 3 + follow-up 7);
    # a value overrides it for every species.
    facts_model_key: str | None = None
    facts_effort: str | None = None


def retry_settings(record: Record, options: VerifyOptions) -> tuple[str, str]:
    """(model key, effort) for this species' V1 retry."""
    generated = record.get("generated", {}).get("facts") or {}
    by_id = {model_id: key for key, model_id in MODELS.items()}
    own_model = by_id.get(str(generated.get("model")), FACTS_MODEL_KEY)
    model_key = options.facts_model_key or own_model
    own_effort = generated.get("effort")
    effort = options.facts_effort or (own_effort if own_effort in EFFORTS else FACTS_EFFORT)
    return model_key, effort


def _skip(record: Record | None, options: VerifyOptions) -> tuple[str, str] | None:
    """(status, reason) for a species that does not run; the preflight asks the same."""
    if record is None or not record.get("facts"):
        return "failed", "faktabladet saknas: kör web facts först"
    if record.get("status") == "failed":
        return "skipped", "faktabladet är failed: ingenting att kontrollera"
    if _current(record) and not options.force:
        return "skipped", "redan kontrollerat ur samma faktablad"
    if record.get("publish"):
        # N5 (review fix 2026-10-06): the V1-retry path below (and --force) would
        # otherwise null the text and set status pending while publish stays true,
        # breaking the fas 2 build.
        return "failed", "publicerad: sätt publish: false först"
    return None


def _audio_kept(record: Record) -> bool:
    """Albin kept this exact recording on an earlier V4 flag (follow-up 2)."""
    audio = record.get("audio")
    if not audio:
        return False
    return bool(record.get("review", {}).get("audioKept") == audio_id(audio))


def _needs_audio_model(record: Record) -> bool:
    """A species the sound model does not cover gets a flag whatever the model says, and a
    kept recording is not checked again: neither needs the model (follow-up 3)."""
    return (
        bool(record.get("audio"))
        and bool(record.get("identifiable", {}).get("sound"))
        and not _audio_kept(record)
    )


def _needs_preflight(path: Path, options: VerifyOptions) -> bool:
    """Whether this species will run V4 on the model. A file that cannot be read or is not
    a record counts as "no" (A2, wave A review): `_one` reports it as failed on its own,
    and the rest of the run goes on."""
    try:
        record = load_record(path)
        return record is not None and _skip(record, options) is None and _needs_audio_model(record)
    except Exception:  # a broken file is `_one`'s to report, not the preflight's
        return False


async def _preflight_audio_model(paths: WebPaths) -> None:
    try:
        await asyncio.to_thread(classify_clip, paths.flexref / PREFLIGHT_CLIP, paths.flexref)
    except AudioCheckFailed as exc:
        raise AudioPreflightFailed(
            f"Ljudmodellen kunde inte köras: {exc}; inga anrop gjordes"
        ) from exc


def species_about(source: SpeciesSource) -> str:
    """The species line V1 is told it checks (C1, final review 2026-10-06)."""
    return f"{source.name_sv} / {source.name_en} ({source.scientific_name})"


def _current(record: Record) -> bool:
    stored = record.get("generated", {}).get("verify", {}).get("factsHash")
    return bool(stored == facts_hash(record))


async def run_verify(
    paths: WebPaths,
    options: VerifyOptions,
    *,
    client: JsonModelClient | None = None,
    wiki: ArticleSource | None = None,
    audio: AudioSource | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    """`audio` (Commons, from the CLI) lets V4 try the species' next allowed recording when
    the model does not keep the current one; without it a miss is struck as before."""
    now = now or datetime.now(UTC)
    cache = Cache(paths.pipeline_root / ".cache")
    sources = load_approved(paths.species_root, options.qids)
    if any(_needs_preflight(record_path(paths.data_out, s.qid), options) for s in sources):
        # Before the client exists and before anything is written (follow-up 3): a broken
        # audio setup stops the run at $0 instead of flagging every species.
        await _preflight_audio_model(paths)
    wiki = wiki or FullWikiClient(cache=cache)
    owned = client is None
    model_client: JsonModelClient = client or AnthropicJsonClient()
    cost = CostTracker(max_usd=options.max_cost)
    checker = FactChecker(
        client=model_client,
        cost=cost,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        model_key=options.model_key,
        effort=options.effort,
    )
    names = load_name_index(paths.species_root)
    extractor = FactExtractor(
        cache=cache,
        cost=cost,
        client=model_client,
        prompt_path=paths.prompt_file(FACTS_PROMPT_VERSION),
        scientific_index=names.qids,
        scientific_families=names.families,
        scientific_common=names.common,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> StepOutcome:
        async with semaphore:
            return await _one(source, paths, options, wiki, checker, extractor, stop, now, audio)

    try:
        outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    sweep = sweep_orphan_voices(paths.data_out, paths.images_out)
    outcomes += sweep_outcome(sweep.removed, sweep.errors)
    report = render_step_report(
        title="Automatisk kontroll",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=(
            f"Kontroll: `{MODELS[options.model_key]}`. Omförsök av faktabladet: "
            + (
                f"`{MODELS[options.facts_model_key]}`"
                if options.facts_model_key
                else "faktabladets egen modell"
            )
            + f" (effort: {options.facts_effort or 'faktabladets egen'})."
        ),
    )
    write_step_report(paths.reports, "verify", now, report)
    return outcomes


@dataclass
class Replacement:
    """Another allowed Commons recording V4 took instead of the species' own: its `audio`
    object, the converted file (moved to voice.mp3 once the record is saved) and the
    recordings it replaces (the old one and any alternative tried in vain)."""

    audio: dict[str, Any]
    mp3: Path
    title: str
    struck: list[str]


@dataclass
class AudioOutcome:
    verdict: AudioVerdict | str | None
    replacement: Replacement | None = None
    tried: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)


async def _audio_check(
    record: Record,
    source: SpeciesSource,
    paths: WebPaths,
    audio_source: AudioSource | None,
    workdir: Path,
) -> AudioOutcome:
    """V4, run before the paid V1 call (I4, final review 2026-10-06) so an audio problem
    never wastes V1 work, and in a thread so the subprocess does not block the other
    workers. No verdict without a recording or for one Albin kept; a string when the model
    could not run.
    Only the verdict is computed here; the record is changed after V1, as before."""
    if not record.get("audio") or _audio_kept(record):
        return AudioOutcome(None)
    if not record.get("identifiable", {}).get("sound"):
        # Not covered: the verdict is a flag whatever the model says (follow-up 3).
        return AudioOutcome(audio_verdict(None, source.qid, identifiable_sound=False))
    try:
        result = await asyncio.to_thread(
            classify_clip, paths.images_out / source.qid / VOICE_FILE, paths.flexref
        )
    except AudioCheckFailed as exc:
        return AudioOutcome(str(exc))
    verdict = audio_verdict(result, source.qid, identifiable_sound=True)
    if verdict.action == "keep" or audio_source is None:
        return AudioOutcome(verdict)
    return await _try_other_recordings(record, source, paths, audio_source, workdir, verdict)


async def _try_other_recordings(
    record: Record,
    source: SpeciesSource,
    paths: WebPaths,
    audio_source: AudioSource,
    workdir: Path,
    verdict: AudioVerdict,
) -> AudioOutcome:
    """Up to MAX_ALTERNATIVES other allowed Commons recordings, in the sources step's
    order, skipping every one struck before. The first the model keeps replaces the
    recording; otherwise a weak original stays (flag), else the first weak alternative
    replaces it (flag), else the strike stands. Downloads come from the cache when there."""
    current = str(record["audio"].get("sourceUrl", ""))
    struck = set(record.get("review", {}).get("audioStruckSources", []))
    notes: list[str] = []
    try:
        candidates = await audio_source.candidates(source.qid, source.scientific_name)
    except Exception as exc:  # Commons down: V4 decides on the recording it has
        notes.append(f"andra inspelningar kunde inte hämtas ({type(exc).__name__})")
        return AudioOutcome(verdict, notes=notes)
    others = [
        c
        for c in candidates
        if rejection(c, source.scientific_name) is None
        and c.page_url != current
        and c.page_url not in struck
    ][:MAX_ALTERNATIVES]
    tried: list[str] = []
    weak: tuple[AudioVerdict, Replacement] | None = None
    for number, candidate in enumerate(others):
        try:
            raw = await audio_source.download(source.qid, candidate)
            mp3 = workdir / f"alternative-{number}.mp3"
            await asyncio.to_thread(convert_to_mp3, raw, mp3)
            result = await asyncio.to_thread(classify_clip, mp3, paths.flexref)
        except Exception as exc:  # one broken alternative must not end the others
            notes.append(f"{candidate.title} kunde inte prövas ({type(exc).__name__})")
            continue
        alternative = audio_verdict(result, source.qid, identifiable_sound=True)
        replacement = Replacement(
            audio={
                **audio_record(candidate, source.qid),
                "sha256": hashlib.sha256(raw).hexdigest(),
                "mp3Sha256": file_sha256(mp3),
            },
            mp3=mp3,
            title=candidate.title,
            struck=[current, *tried],
        )
        if alternative.action == "keep":
            return AudioOutcome(alternative, replacement, notes=notes)
        if alternative.action == "flag" and weak is None:
            weak = (alternative, replacement)
        tried.append(candidate.page_url)
    if verdict.action == "flag":
        # The species' own recording is as good as anything else: Albin listens to it.
        return AudioOutcome(verdict, tried=tried, notes=notes)
    if weak is not None:
        alternative, replacement = weak
        replacement.struck = [current, *(t for t in tried if t != replacement.audio["sourceUrl"])]
        return AudioOutcome(alternative, replacement, notes=notes)
    return AudioOutcome(verdict, tried=tried, notes=notes)


def _remember_struck(record: Record, urls: list[str]) -> None:
    """Every recording V4 struck or tried in vain, so neither V4 nor `web sources` chooses
    it again (fix wave 2026-10-07)."""
    struck = record.setdefault("review", {}).setdefault("audioStruckSources", [])
    struck.extend(u for u in urls if u and u not in struck)


def _apply_audio(
    record: Record,
    outcome: AudioOutcome,
    flags: list[dict[str, Any]],
    notes: list[str],
) -> Path | bool:
    """Writes V4's verdict into the record and the flags. True when the recording was
    struck, so its file is deleted once the record is saved (Minor 11, final review
    2026-10-06: a struck voice.mp3 left on disk could be committed without credits); the
    new file's path when another recording replaced it, moved into place after the save."""
    audio = outcome.verdict
    notes.extend(outcome.notes)
    if audio is None:
        return False
    if outcome.replacement is not None:
        replacement = outcome.replacement
        _remember_struck(record, replacement.struck)
        record["audio"] = replacement.audio
        record["review"].pop("audioStruck", None)
        notes.append(f"inspelningen byttes mot {replacement.title}")
        if isinstance(audio, AudioVerdict) and audio.action == "flag":
            flags.append({"check": "V4", "factId": None, "message": audio.reason})
        return replacement.mp3
    if isinstance(audio, str):
        flags.append(
            {
                "check": "V4",
                "factId": None,
                "message": (
                    f"Ljudmodellen kunde inte köras ({audio}). Lyssna och besluta, "
                    "eller kör om när felet är åtgärdat."
                ),
            }
        )
    elif audio.action == "strike":
        _remember_struck(record, [str(record["audio"].get("sourceUrl", "")), *outcome.tried])
        record.pop("audio", None)
        record.setdefault("review", {})["audioStruck"] = True
        notes.append(f"inspelningen ströks: {audio.reason}")
        return True
    elif audio.action == "flag":
        flags.append({"check": "V4", "factId": None, "message": audio.reason})
    return False


PLACE_FAILED_FLAG = (
    "Den nya inspelningen kunde inte läggas på plats (filen var låst) och den gamla togs "
    "bort. Kör web verify --force för arten."
)


def _swap(staged: Path, voice: Path) -> None:
    os.replace(staged, voice)


def _place_voice(images_out: Path, qid: str, mp3: Path) -> str | None:
    """Moves a replacement recording into place after its record was saved. On failure the
    old file is deleted rather than left under the new credits (if that fails too, the
    sweep removes it by its hash), and nothing staged is left; returns why."""
    voice = images_out / qid / VOICE_FILE
    staged = voice.with_name(STAGED_VOICE_FILE)
    try:
        shutil.copyfile(mp3, staged)
        _swap(staged, voice)
    except OSError as exc:
        with contextlib.suppress(OSError):  # else the sweep at the end of the run does
            staged.unlink(missing_ok=True)
        error = delete_voice(images_out, qid)
        return (
            f"{qid}: den nya inspelningen kunde inte läggas på plats "
            f"({type(exc).__name__}: {exc})" + (f"; {error}" if error else "")
        )
    return None


def _fail(record: Record, path: Path, kept: list[dict[str, Any]], missing: list[str]) -> None:
    record["facts"] = kept
    record["status"] = "failed"
    record["errors"] = [f"saknas efter V1-omförsöket: {', '.join(missing)}"]
    # A verification left over from an earlier, better-looking run must not survive this
    # failure (review fix 2026-10-06, C1), and neither may its metadata: `web facts`
    # refuses a species with `generated.verify` unless forced, so a stale one would make
    # `web facts --regenerate` skip the species this failure asks it to redo (Minor 1).
    record.pop("verification", None)
    record.get("generated", {}).pop("verify", None)
    save_record(path, record)


def _retry_feedback(strike_notes: list[str], missing: list[str]) -> str:
    return (
        "A fact checker rejected some of the facts you gave earlier for this species:\n"
        + "\n".join(f"- {n}" for n in strike_notes)
        + f"\nWrite the whole fact sheet again. Keep every fact the checker did not reject, "
        f"and add what is missing so it still covers: {', '.join(missing)}."
    )


async def _one(
    source: SpeciesSource,
    paths: WebPaths,
    options: VerifyOptions,
    wiki: ArticleSource,
    checker: FactChecker,
    extractor: FactExtractor,
    stop: asyncio.Event,
    now: datetime,
    audio_source: AudioSource | None = None,
) -> StepOutcome:
    # Alternative recordings are converted here; a file Windows still holds must not turn
    # into an error after the species is done.
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        return await _one_in(
            source, paths, options, wiki, checker, extractor, stop, now, audio_source, Path(tmp)
        )


async def _one_in(
    source: SpeciesSource,
    paths: WebPaths,
    options: VerifyOptions,
    wiki: ArticleSource,
    checker: FactChecker,
    extractor: FactExtractor,
    stop: asyncio.Event,
    now: datetime,
    audio_source: AudioSource | None,
    workdir: Path,
) -> StepOutcome:
    def out(
        status: str, errors: list[str] | None = None, notes: list[str] | None = None
    ) -> StepOutcome:
        return StepOutcome(source.qid, source.name_sv, status, errors or [], notes or [])

    path = record_path(paths.data_out, source.qid)
    try:
        record = load_record(path)
        skip = _skip(record, options)
        if skip is not None:
            return out(skip[0], [skip[1]])
        assert record is not None  # _skip gives a reason for a missing record
        if stop.is_set():
            return out("skipped", ["kostnadstaket nåddes: körs vid nästa körning"])
        articles = await wiki.articles(source.qid)
        about = species_about(source)
        audio = await _audio_check(record, source, paths, audio_source, workdir)
        if stop.is_set():
            # Another worker reached the cap while the audio model ran.
            return out("skipped", ["kostnadstaket nåddes: körs vid nästa körning"])

        notes: list[str] = []
        try:
            verdicts = await checker.check(record["facts"], articles, about=about)
        except MaxCostExceeded as exc:
            stop.set()
            return out("skipped", [f"kostnadstaket nåddes: {exc}"])
        kept, strike_notes = strike_unsupported(record["facts"], verdicts)
        notes += strike_notes
        status_flag = status_strike_flag(verdicts)
        missing = missing_required_topics(kept)
        if missing and strike_notes:
            model_key, effort = retry_settings(record, options)
            extractor = replace(extractor, model_key=model_key, effort=effort)
            try:
                check, _, _ = await extractor.extract(
                    source, articles, extra_feedback=_retry_feedback(strike_notes, missing)
                )
            except MaxCostExceeded as exc:
                stop.set()
                return out("skipped", [f"kostnadstaket nåddes: {exc}"])
            if check.fatal:
                _fail(record, path, kept, missing)
                return out("failed", record["errors"], notes)
            # The record now holds the retried sheet: say what wrote it (Minor 3, final
            # review 2026-10-06), not the original `web facts` run's model and time.
            apply_facts(
                record, check, generated={**facts_generated(extractor, now), "v1Retry": True}
            )

            # The retried sheet is brand new text the model just wrote: it gets the same V1
            # pass the original facts did, so a fact it invents cannot slip through
            # unchecked (item 3, 2026-10-05 review fix).
            try:
                verdicts = await checker.check(record["facts"], articles, about=about)
            except MaxCostExceeded as exc:
                stop.set()
                return out("skipped", [f"kostnadstaket nåddes: {exc}"])
            kept, strike_notes = strike_unsupported(record["facts"], verdicts)
            notes += strike_notes
            status_flag = status_strike_flag(verdicts)
            missing = missing_required_topics(kept)
            if missing:
                _fail(record, path, kept, missing)
                return out("failed", record["errors"], notes)
        record["facts"] = kept

        flags = number_flags(record, articles) + status_flags(record)
        if status_flag:
            flags.append(status_flag)

        audio_change = _apply_audio(record, audio, flags, notes)

        # V1's reason and an audio error are free text; fas 2's dash guard reads the
        # record (I7, final review 2026-10-06).
        record["flags"] = [{**f, "message": without_dashes(str(f["message"]))} for f in flags]
        record.setdefault("generated", {})["verify"] = {
            "model": MODELS[options.model_key],
            "prompt": PROMPT_VERSION,
            "at": now.isoformat(),
            "factsHash": facts_hash(record),
        }
        if flags:
            # A `verification` from an earlier, cleaner run must not survive once this
            # rerun finds something to flag (review fix 2026-10-06, C1): otherwise `web
            # write` would trust a decision that no longer matches these facts.
            record.pop("verification", None)
        else:
            # Ändrat 2026-10-05 (b): inga flaggor betyder inget att vänta på. Arten
            # behöver aldrig gå via undantagsarket eller `web import` (Task 17).
            record["verification"] = {
                "method": "auto",
                "at": now.date().isoformat(),
                "model": MODELS[options.model_key],
                "spotChecked": False,
            }
        save_record(path, record)
        if isinstance(audio_change, Path):
            error = _place_voice(paths.images_out, source.qid, audio_change)
            if error:
                # The saved record names the new recording, which is not in place: it must
                # not stay verified (re-review 2026-10-07).
                notes.append(error)
                record.pop("verification", None)
                record["flags"] = [
                    *record["flags"],
                    {"check": "V4", "factId": None, "message": PLACE_FAILED_FLAG},
                ]
                save_record(path, record)
        elif audio_change:
            error = delete_voice(paths.images_out, source.qid)
            if error:
                notes.append(error)  # the sweep at the end tries again and reports it
        return out("ok", [], [*notes, *([f"{len(flags)} flaggor"] if flags else [])])
    except Exception as exc:  # one species' error must not stop the run or overwrite a file
        return out("failed", [f"{type(exc).__name__}: {exc}"])
