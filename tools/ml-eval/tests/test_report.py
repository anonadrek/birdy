from __future__ import annotations

from pathlib import Path

import pytest

from birdy_eval.corpus import CorpusItem
from birdy_eval.metrics import EvalResult, compute_metrics
from birdy_eval.report import render_report
from birdy_eval.runner import Prediction

_GOLDEN = Path(__file__).parent / "golden" / "expected_report.md"


def _item(qid: str, family: str) -> CorpusItem:
    return CorpusItem(path=Path(f"corpus/{family}/{qid}.jpg"), qid=qid, family=family)


def _pred(item: CorpusItem, top_indices: list[int], top_scores: list[float]) -> Prediction:
    return Prediction(item=item, top_scores=list(zip(top_indices, top_scores, strict=True)))


_MAPPING: dict[int, str] = {0: "Q18009", 1: "Q13364"}

_PREDICTIONS = [
    _pred(_item("Q18009", "paridae"), [0, 1], [0.8, 0.1]),
    _pred(_item("Q13364", "accipitridae"), [1, 0], [0.7, 0.2]),
]

# Fixed timestamp so golden output is deterministic across runs.
_FIXED_TS = "2026-05-07T00:00:00Z"


def _make_eval_result() -> EvalResult:
    return compute_metrics(_PREDICTIONS, _MAPPING)


def _render(result: EvalResult) -> str:
    return render_report(
        result,
        model_path="aiy_birds_V1_3.tflite",
        corpus_size=2,
        generated_at=_FIXED_TS,
    )


def test_render_report_creates_golden() -> None:
    result = _make_eval_result()
    report = _render(result)

    if not _GOLDEN.exists():
        _GOLDEN.parent.mkdir(parents=True, exist_ok=True)
        _GOLDEN.write_text(report, encoding="utf-8", newline="\n")
        pytest.skip("Golden file created — re-run to validate")

    expected = _GOLDEN.read_text(encoding="utf-8")
    assert report == expected, (
        f"Report output differs from golden.\n"
        f"Expected:\n{expected}\n\nActual:\n{report}"
    )


def test_render_report_contains_accuracy() -> None:
    result = _make_eval_result()
    report = _render(result)
    assert "top-1" in report.lower() or "top1" in report.lower()
    assert "100.0%" in report or "100%" in report


def test_render_report_contains_family_section() -> None:
    result = _make_eval_result()
    report = _render(result)
    assert "paridae" in report
    assert "accipitridae" in report
